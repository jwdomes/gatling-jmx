package jmx2gatling.convert;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import jmx2gatling.inventory.ElementKind;
import jmx2gatling.inventory.SupportMatrix;
import jmx2gatling.model.JmxElement;
import jmx2gatling.model.Property;
import jmx2gatling.report.Findings;

/** Converts one parsed test plan into a Gatling simulation class, its body files and its findings. */
public final class SimulationConverter {

    public static final String GATLING_VERSION = GatlingDsl.GATLING_VERSION;

    public record Options(String packageName, boolean allBodiesToFiles, String generatedOn) {
    }

    public record Result(String className, String javaSource, Map<String, String> bodyFiles, Findings findings) {
    }

    private static final Set<String> SCOPED_TYPES_TRACKED = Set.of("HeaderManager", "ConfigTestElement (HttpDefaultsGui)", "CSVDataSet");

    public Result convert(JmxElement plan, String sourceFileName, String className, Options options) {
        // From the class name, which Main keeps unique, so two plan.jmx files never share a body folder.
        String simulation = className.endsWith("Simulation") ? className.substring(0, className.length() - "Simulation".length()) : className;
        String stem = String.join("-", Naming.words(simulation)).toLowerCase(java.util.Locale.ROOT);
        Conversion c = new Conversion("bodies/" + (stem.isEmpty() ? "plan" : stem) + "/", options.allBodiesToFiles());
        StaticValues statics = new StaticValues(c);
        ElTranslator el = new ElTranslator(c, statics);
        ChainConverter chain = new ChainConverter(c, el, statics, new RequestMapper(c, el, statics), new CheckMapper(c, el),
            new Timers(c, statics), new Conditions(c));
        CsvFeeders feeders = new CsvFeeders(c, statics);
        ThreadGroupMapper threadGroups = new ThreadGroupMapper(c, statics, el, chain, feeders);

        collectUserDefinedVariables(plan, c);
        Scope planScope = Scope.root().enter(plan);
        List<Step> planNotes = new ArrayList<>();
        Map<ThreadGroupMapper.Stage, List<ThreadGroupMapper.Result>> byStage = new EnumMap<>(ThreadGroupMapper.Stage.class);
        List<ThreadGroupMapper.Result> mainInOrder = new ArrayList<>();
        Set<String> scenarioNames = new HashSet<>();
        for (JmxElement child : plan.children()) {
            if (!child.enabled()) {
                chain.disabled(child);
                continue;
            }
            ElementKind kind = SupportMatrix.kindOf(child);
            if (kind == ElementKind.THREAD_GROUP) {
                ThreadGroupMapper.Result result = threadGroups.convert(child, planScope, scenarioNames);
                byStage.computeIfAbsent(result.stage(), s -> new ArrayList<>()).add(result);
                if (result.stage() == ThreadGroupMapper.Stage.MAIN) {
                    mainInOrder.add(result);
                }
            } else if (kind == ElementKind.SAMPLER || kind == ElementKind.CONTROLLER) {
                c.ignore(child, "Not inside a thread group, so JMeter never runs it");
            } else {
                planNotes.addAll(chain.element(child, planScope, new ChainConverter.ThreadGroupContext(false)));
            }
        }

        c.origins.decide(c.constants);
        List<String> protocolComments = new ArrayList<>();
        List<String> protocolCalls = protocol(plan, c, protocolComments);
        reportUnusedScopedElements(plan, c);
        reportUndefinedVariables(c);
        Optional<Step> init = initialSessionValues(c, statics);

        List<SimulationModel.ChainField> chains = new ArrayList<>();
        List<SimulationModel.ScenarioField> scenarios = new ArrayList<>();
        List<ThreadGroupMapper.Result> all = new ArrayList<>();
        for (ThreadGroupMapper.Stage stage : ThreadGroupMapper.Stage.values()) {
            all.addAll(byStage.getOrDefault(stage, List.of()));
        }
        for (ThreadGroupMapper.Result r : all) {
            chains.addAll(r.chains());
            List<Step> steps = new ArrayList<>(planNotes);
            init.ifPresent(steps::add);
            steps.addAll(r.scenario().steps());
            scenarios.add(new SimulationModel.ScenarioField(r.scenario().comments(), r.scenario().name(), r.scenario().displayName(), steps));
        }

        if (scenarios.isEmpty()) {
            // Plan-level notes normally ride on each scenario; with none, they go above the protocol.
            protocolComments.addAll(c.todo(plan, "The plan has no enabled thread group, so the simulation injects no users", ""));
            for (Step note : planNotes) {
                protocolComments.addAll(note.comments());
            }
            init.ifPresent(step -> protocolComments.addAll(step.comments()));
        }
        List<List<ThreadGroupMapper.Result>> stages = new ArrayList<>();
        addStage(stages, byStage.get(ThreadGroupMapper.Stage.SETUP));
        if (plan.props().bool("TestPlan.serialize_threadgroups", false)) {
            mainInOrder.forEach(r -> addStage(stages, List.of(r)));
        } else {
            addStage(stages, mainInOrder);
        }
        addStage(stages, byStage.get(ThreadGroupMapper.Stage.TEARDOWN));
        List<SimulationModel.Population> populations = populations(stages, 0, plan, c);

        String maxDuration = threadGroups.needsMaxDuration()
            ? GatlingDsl.maxDuration(GatlingDsl.seconds(threadGroups.durationConstant())) : null;
        SimulationModel model = new SimulationModel(options.packageName(), className, sourceFileName, options.generatedOn(),
            c.findings.todos().size(), c.constants.all(), feeders.fields(), protocolComments, protocolCalls, chains, scenarios,
            populations, maxDuration, c.helpers, c.usesPattern);
        return new Result(className, new JavaWriter().write(model), c.bodyFiles, c.findings);
    }

    private static void addStage(List<List<ThreadGroupMapper.Result>> stages, List<ThreadGroupMapper.Result> stage) {
        if (stage != null && !stage.isEmpty()) {
            stages.add(stage);
        }
    }

    /** Each stage starts when the first population of the previous stage has finished (Gatling's andThen). */
    private static List<SimulationModel.Population> populations(List<List<ThreadGroupMapper.Result>> stages, int index,
                                                                JmxElement plan, Conversion c) {
        if (index >= stages.size()) {
            return List.of();
        }
        List<ThreadGroupMapper.Result> stage = stages.get(index);
        List<SimulationModel.Population> next = populations(stages, index + 1, plan, c);
        if (!next.isEmpty() && stage.size() > 1) {
            c.approximate(plan, "The next group of thread groups starts after \"" + stage.get(0).scenario().displayName() + "\" finishes",
                "Gatling's andThen chains from one scenario; JMeter waited for all of: "
                    + String.join(", ", stage.stream().map(r -> r.scenario().displayName()).toList()) + ".");
        }
        List<SimulationModel.Population> result = new ArrayList<>();
        for (int i = 0; i < stage.size(); i++) {
            ThreadGroupMapper.Result r = stage.get(i);
            result.add(new SimulationModel.Population(r.injectionComments(),
                GatlingDsl.injectOpen(r.scenario().name(), List.of(r.injection())), i == 0 ? next : List.of()));
        }
        return result;
    }

    /** TestPlan variables first, then every enabled User Defined Variables element in tree order. */
    private static void collectUserDefinedVariables(JmxElement plan, Conversion c) {
        plan.props().element("TestPlan.user_defined_variables").ifPresent(args -> addArguments(args.props().collection("Arguments.arguments"), plan, c));
        collectArgumentsElements(plan, c);
    }

    private static void collectArgumentsElements(JmxElement e, Conversion c) {
        for (JmxElement child : e.children()) {
            if (!child.enabled()) {
                continue;
            }
            if (child.testClass().equals("Arguments")) {
                addArguments(child.props().collection("Arguments.arguments"), child, c);
            }
            collectArgumentsElements(child, c);
        }
    }

    private static void addArguments(List<Property> items, JmxElement source, Conversion c) {
        for (Property item : items) {
            if (item instanceof Property.Element arg) {
                String name = arg.props().string("Argument.name").trim();
                if (!name.isEmpty()) {
                    c.userDefinedVariables.put(name, arg.props().string("Argument.value"));
                    c.userDefinedVariableSources.put(name, source);
                    c.defineVariable(name);
                }
            }
        }
    }

    private static Optional<Step> initialSessionValues(Conversion c, StaticValues statics) {
        List<String[]> values = new ArrayList<>();
        List<String> comments = new ArrayList<>(List.of("Initial session values: user-defined variables and JMeter properties used in requests"));
        c.userDefinedVariables.forEach((name, raw) -> {
            JmxElement source = c.userDefinedVariableSources.get(name);
            Optional<String> expr = statics.string(raw, source);
            if (expr.isEmpty()) {
                comments.addAll(c.todo(source, "User-defined variable `" + name + "` uses functions or runtime variables; kept as literal text", raw));
                expr = Optional.of(JavaText.quote(raw));
            }
            values.add(new String[] {c.attribute(name, source), expr.get()});
        });
        c.propertyAttributes.forEach((attribute, constant) -> values.add(new String[] {attribute, constant}));
        if (values.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Step.Action(comments, GatlingDsl.sessionSet(values)));
    }

    private static List<String> protocol(JmxElement plan, Conversion c, List<String> comments) {
        List<String> calls = new ArrayList<>();
        Origins.Origin primary = c.origins.primary();
        if (primary != null) {
            calls.add(GatlingDsl.protocolBaseUrl(primary.constant));
        }
        List<String> secondary = c.origins.secondary().stream().map(o -> o.key).toList();
        if (!secondary.isEmpty()) {
            c.approximate(plan, "Requests go to " + (secondary.size() + 1) + " hosts",
                "The most used host (" + primary.key + ") is the base URL; requests to " + String.join(", ", secondary)
                    + " use full URLs built from their own BASE_URL_n constants.");
        }
        for (RequestSpec.NameValue header : commonHeaders(c.requests)) {
            calls.add(GatlingDsl.header(header.name(), header.valueEl()));
        }
        List<JmxElement> cacheManagers = enabledOfType(plan, "CacheManager");
        if (cacheManagers.isEmpty()) {
            comments.add("No HTTP Cache Manager in the plan, so caching is disabled as in JMeter");
            calls.add(GatlingDsl.PROTOCOL_DISABLE_CACHING);
        } else {
            cacheManagers.forEach(m -> c.ignore(m, "Gatling's built-in HTTP cache plays this role"));
        }
        calls.add(GatlingDsl.PROTOCOL_DISABLE_WARM_UP);
        return calls;
    }

    /** Headers with the same name and value on every request, removed from the requests. */
    private static List<RequestSpec.NameValue> commonHeaders(List<Step.Request> requests) {
        if (requests.isEmpty()) {
            return List.of();
        }
        List<RequestSpec.NameValue> common = new ArrayList<>();
        for (RequestSpec.NameValue candidate : requests.get(0).spec().headers) {
            boolean everywhere = requests.stream().allMatch(r -> r.spec().headers.stream()
                .anyMatch(h -> h.name().equalsIgnoreCase(candidate.name()) && h.valueEl().equals(candidate.valueEl())));
            if (everywhere) {
                common.add(candidate);
            }
        }
        for (Step.Request r : requests) {
            r.spec().headers.removeIf(h -> common.stream().anyMatch(x -> x.name().equalsIgnoreCase(h.name()) && x.valueEl().equals(h.valueEl())));
        }
        return common;
    }

    private static void reportUnusedScopedElements(JmxElement e, Conversion c) {
        for (JmxElement child : e.children()) {
            if (!child.enabled()) {
                continue;
            }
            ElementKind kind = SupportMatrix.kindOf(child);
            boolean tracked = kind == ElementKind.PRE_PROCESSOR || kind == ElementKind.POST_PROCESSOR || kind == ElementKind.ASSERTION
                || kind == ElementKind.TIMER || SCOPED_TYPES_TRACKED.contains(SupportMatrix.typeOf(child));
            if (tracked && !c.appliedElements.contains(child)) {
                c.ignore(child, "No sampler in its scope, so JMeter never applies it");
            }
            reportUnusedScopedElements(child, c);
        }
    }

    private static void reportUndefinedVariables(Conversion c) {
        c.referencedVariables.forEach((name, where) -> {
            if (!c.definedVariables.contains(name)) {
                c.approximate(where, "Variable `" + name + "` is used but no converted element sets it",
                    "It is probably set by a script, a JMeter built-in or an unconverted element"
                        + (c.feedsColumnsFromFileHeader ? ", unless it is a column of a CSV file with a header row" : "")
                        + ". Gatling fails the request when the attribute is missing.");
            }
        });
    }

    private static List<JmxElement> enabledOfType(JmxElement root, String type) {
        List<JmxElement> result = new ArrayList<>();
        for (JmxElement child : root.children()) {
            if (child.enabled()) {
                if (SupportMatrix.typeOf(child).equals(type)) {
                    result.add(child);
                }
                result.addAll(enabledOfType(child, type));
            }
        }
        return result;
    }
}
