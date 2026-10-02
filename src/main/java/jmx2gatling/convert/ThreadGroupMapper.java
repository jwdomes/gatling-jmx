package jmx2gatling.convert;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import jmx2gatling.inventory.ElementKind;
import jmx2gatling.inventory.SupportMatrix;
import jmx2gatling.model.JmxElement;
import jmx2gatling.model.Property;
import jmx2gatling.model.Props;

/** A thread group → one scenario, its loop wrapper and its injection profile. */
final class ThreadGroupMapper {

    enum Stage { SETUP, MAIN, TEARDOWN }

    record Result(SimulationModel.ScenarioField scenario, List<SimulationModel.ChainField> chains,
                  List<String> injectionComments, String injection, Stage stage) {
    }

    private static final Set<String> STANDARD_THREAD_GROUPS = Set.of("ThreadGroup", "SetupThreadGroup", "PostThreadGroup");
    static final String DURATION_PROPERTY = "durationSeconds";
    static final int DEFAULT_DURATION_SECONDS = 600;

    private final Conversion c;
    private final StaticValues statics;
    private final ElTranslator el;
    private final ChainConverter chain;
    private final CsvFeeders feeders;
    private boolean needsMaxDuration;

    ThreadGroupMapper(Conversion c, StaticValues statics, ElTranslator el, ChainConverter chain, CsvFeeders feeders) {
        this.c = c;
        this.statics = statics;
        this.el = el;
        this.chain = chain;
        this.feeders = feeders;
    }

    /** True when some thread group loops forever with no duration, so the simulation needs a maxDuration. */
    boolean needsMaxDuration() {
        return needsMaxDuration;
    }

    String durationConstant() {
        return c.constants.add("converter:duration", "DURATION_SECONDS", "int",
            "Integer.getInteger(\"" + DURATION_PROPERTY + "\", " + DEFAULT_DURATION_SECONDS + ")",
            "How long thread groups that looped forever in JMeter run (also the simulation's maxDuration); override with -D"
                + DURATION_PROPERTY + "=...");
    }

    Result convert(JmxElement tg, Scope planScope, Set<String> scenarioNames) {
        Props p = tg.props();
        boolean standard = STANDARD_THREAD_GROUPS.contains(tg.testClass());
        Stage stage = switch (tg.testClass()) {
            case "SetupThreadGroup" -> Stage.SETUP;
            case "PostThreadGroup" -> Stage.TEARDOWN;
            default -> Stage.MAIN;
        };
        List<String> scenarioComments = new ArrayList<>(List.of(tg.path()));
        boolean exitOnError = onSampleError(tg, scenarioComments);
        ChainConverter.ThreadGroupContext context = new ChainConverter.ThreadGroupContext(exitOnError);
        Scope inner = planScope.enter(tg);

        List<Step> start = new ArrayList<>();
        List<Step> iteration = new ArrayList<>();
        cookies(tg, inner, start, iteration);
        for (JmxElement csv : csvDataSets(tg, inner)) {
            iteration.add(feeders.feed(csv));
        }

        List<Step> hoisted = new ArrayList<>();
        List<SimulationModel.ChainField> chains = new ArrayList<>();
        long topLevelControllers = tg.children().stream()
            .filter(e -> e.enabled() && SupportMatrix.kindOf(e) == ElementKind.CONTROLLER).count();
        // Hoisting before the loop would run the children before the first iteration's feed and cookie
        // flush; when those exist, the nested once-only flag keeps JMeter's order instead.
        boolean executableSeen = !iteration.isEmpty();
        for (JmxElement child : tg.children()) {
            ElementKind kind = SupportMatrix.kindOf(child);
            boolean executable = child.enabled() && (kind == ElementKind.SAMPLER || kind == ElementKind.CONTROLLER);
            if (executable && !executableSeen && child.testClass().equals("OnceOnlyController")) {
                hoisted.add(new Step.Note(List.of(ChainConverter.stepPath(child), "Once Only Controller: runs once per user, before the loop")));
                hoisted.addAll(chain.children(child, inner, context));
            } else {
                List<Step> steps = chain.element(child, inner, context);
                if (kind == ElementKind.CONTROLLER && topLevelControllers > 1 && !steps.isEmpty()) {
                    String field = c.identifiers.field(child.name(), "chain");
                    chains.add(new SimulationModel.ChainField(List.of(child.path()), field, steps));
                    iteration.add(new Step.Action(List.of(ChainConverter.stepPath(child)), field));
                } else {
                    iteration.addAll(steps);
                }
            }
            executableSeen |= executable;
        }

        List<Step> steps = new ArrayList<>(start);
        steps.addAll(hoisted);
        if (standard) {
            steps.addAll(loop(tg, iteration, scenarioComments));
        } else {
            steps.addAll(iteration);
        }

        List<String> injectionComments = new ArrayList<>(List.of("Injection for " + tg.path()));
        String injection = standard ? injection(tg, injectionComments)
            : unsupportedInjection(tg, injectionComments);

        String displayName = tg.name().isBlank() ? "Thread Group" : tg.name();
        String unique = displayName;
        for (int i = 2; !scenarioNames.add(unique); i++) {
            unique = displayName + " (" + i + ")";
        }
        if (!unique.equals(displayName)) {
            c.approximate(tg, "Scenario renamed to \"" + unique + "\"", "Gatling requires unique scenario names.");
        }
        String field = c.identifiers.field(displayName, "scenario");
        return new Result(new SimulationModel.ScenarioField(scenarioComments, field, unique, steps), chains, injectionComments, injection, stage);
    }

    /** Returns whether each request must be followed by exitHereIfFailed(). */
    private boolean onSampleError(JmxElement tg, List<String> comments) {
        String action = tg.props().string("ThreadGroup.on_sample_error", "continue").trim();
        switch (action) {
            case "", "continue":
                return false;
            case "stopthread":
                return true;
            case "startnextloop":
                c.approximate(tg, "\"Start Next Thread Loop\" on error → exitHereIfFailed() after each request",
                    "JMeter starts the thread's next iteration after a failure; the Gatling user stops instead.");
                return true;
            default:
                comments.addAll(c.todo(tg, "Action after a sampler error is \"" + action + "\"; Gatling does not stop the whole test on a failure."
                    + " Consider stopLoadGenerator(...) or assertions", ""));
                return false;
        }
    }

    private void cookies(JmxElement tg, Scope inner, List<Step> start, List<Step> iteration) {
        List<JmxElement> managers = new ArrayList<>(inner.configs("CookieManager"));
        for (JmxElement nested : descendants(tg, "CookieManager")) {
            if (!managers.contains(nested)) {
                managers.add(nested);
                c.approximate(nested, "Cookie Manager inside a controller treated as thread-group wide",
                    "Gatling keeps one cookie jar per user.");
            }
        }
        if (managers.isEmpty()) {
            c.approximate(tg, "No HTTP Cookie Manager in this thread group",
                "JMeter sent no cookies back; Gatling always keeps and resends cookies. Add flushCookieJar() calls if the difference matters.");
        }
        boolean flush = !tg.props().bool("ThreadGroup.same_user_on_next_iteration", true);
        for (JmxElement manager : managers) {
            c.appliedElements.add(manager);
            flush |= manager.props().bool("CookieManager.clearEachIteration", false);
            for (Property item : manager.props().collection("CookieManager.cookies")) {
                if (item instanceof Property.Element cookie) {
                    Props cp = cookie.props();
                    StepNotes notes = new StepNotes();
                    String code = GatlingDsl.addCookie(cookie.name(), el.toEl(cp.string("Cookie.value"), manager, notes),
                        cp.string("Cookie.domain").trim(), cp.string("Cookie.path").trim());
                    List<String> comments = new ArrayList<>(List.of("User-defined cookie from " + manager.path()));
                    comments.addAll(notes.comments);
                    start.addAll(notes.before);
                    start.add(new Step.Action(comments, code));
                }
            }
        }
        if (flush) {
            iteration.add(new Step.Action(List.of("Cookies are cleared at each iteration, as in JMeter"), GatlingDsl.FLUSH_COOKIE_JAR));
        }
    }

    private List<JmxElement> csvDataSets(JmxElement tg, Scope inner) {
        List<JmxElement> result = new ArrayList<>(inner.configs("CSVDataSet"));
        for (JmxElement nested : descendants(tg, "CSVDataSet")) {
            if (!result.contains(nested)) {
                result.add(nested);
                c.approximate(nested, "CSV Data Set inside a controller read once per thread-group iteration",
                    "The feeder is fed at the start of each iteration of the thread group, wherever the element was placed.");
            }
        }
        return result;
    }

    /** Enabled elements of {@code type} below the direct children of {@code tg}. */
    private static List<JmxElement> descendants(JmxElement tg, String type) {
        List<JmxElement> result = new ArrayList<>();
        for (JmxElement child : tg.children()) {
            if (child.enabled()) {
                collect(child, type, result);
            }
        }
        return result;
    }

    private static void collect(JmxElement e, String type, List<JmxElement> out) {
        for (JmxElement child : e.children()) {
            if (child.enabled()) {
                if (SupportMatrix.typeOf(child).equals(type)) {
                    out.add(child);
                }
                collect(child, type, out);
            }
        }
    }

    private List<Step> loop(JmxElement tg, List<Step> iteration, List<String> scenarioComments) {
        Props p = tg.props();
        Props loop = p.element("ThreadGroup.main_controller").map(Property.Element::props).orElse(new Props(java.util.Map.of()));
        String loops = loop.string("LoopController.loops", "1").trim();
        Optional<String> duration = schedulerSeconds(tg, "ThreadGroup.duration", scenarioComments);
        if (loops.equals("-1") || statics.isInfiniteLoopProperty(loops, tg, scenarioComments)) {
            if (duration.isPresent()) {
                c.approximate(tg, "Infinite loops with a " + duration.get() + " s scheduler duration → during(" + duration.get() + " s)",
                    "JMeter stops every thread when the duration has passed since the thread group started; each Gatling user loops for the duration from its own start.");
                return List.of(new Step.Block(List.of("Loop until the scheduler duration ends"), GatlingDsl.during(GatlingDsl.seconds(duration.get())), iteration));
            }
            needsMaxDuration = true;
            List<String> comments = new ArrayList<>(c.todo(tg, "Thread group loops forever with no scheduler duration; using -D"
                + DURATION_PROPERTY + " (default " + DEFAULT_DURATION_SECONDS + " s) and a maxDuration on the simulation", ""));
            return List.of(new Step.Block(comments, GatlingDsl.during(GatlingDsl.seconds(durationConstant())), iteration));
        }
        Optional<String> count = statics.integer(loops, tg);
        if (count.isEmpty()) {
            scenarioComments.addAll(c.todo(tg, "Loop count `" + loops + "` could not be converted; using 1", loops));
            count = Optional.of("1");
        }
        if (duration.isPresent()) {
            c.approximate(tg, "Scheduler duration " + duration.get() + " s not enforced", "The loop count (" + loops + ") alone decides when each user stops.");
        }
        if (count.get().equals("1")) {
            return iteration;
        }
        return List.of(new Step.Block(List.of("Thread group loop count"), GatlingDsl.repeat(count.get()), iteration));
    }

    private String injection(JmxElement tg, List<String> comments) {
        Props p = tg.props();
        String threadsText = p.string("ThreadGroup.num_threads", "1").trim();
        String rampText = p.string("ThreadGroup.ramp_time").trim();
        Optional<String> users = statics.integer(threadsText, tg);
        if (users.isEmpty()) {
            comments.addAll(c.todo(tg, "Number of threads `" + threadsText + "` could not be converted; using 1", threadsText));
        }
        Optional<String> ramp = statics.integer(rampText.isEmpty() ? "0" : rampText, tg);
        if (ramp.isEmpty()) {
            comments.addAll(c.todo(tg, "Ramp-up `" + rampText + "` could not be converted; using 0", rampText));
        }
        List<String> steps = new ArrayList<>();
        schedulerSeconds(tg, "ThreadGroup.delay", comments).ifPresent(delay -> steps.add(GatlingDsl.nothingFor(GatlingDsl.seconds(delay))));
        String u = users.orElse("1");
        String r = ramp.orElse("0");
        steps.add(r.equals("0") ? GatlingDsl.atOnceUsers(u) : GatlingDsl.rampUsers(u, GatlingDsl.seconds(r)));
        c.approximate(tg, "Closed model (" + threadsText + " threads, ramp-up " + (rampText.isEmpty() ? "0" : rampText) + " s) → open model "
                + (r.equals("0") ? "atOnceUsers" : "rampUsers ... during"),
            "Each Gatling user runs the scenario once and exits, while JMeter keeps its threads looping. Concurrency matches only while every user is still in its loop.");
        return String.join(", ", steps);
    }

    private String unsupportedInjection(JmxElement tg, List<String> comments) {
        comments.addAll(c.todo(tg, tg.testClass() + " load profile not converted; placeholder atOnceUsers(1). Write the injection profile by hand", ""));
        return GatlingDsl.atOnceUsers("1");
    }

    /** A scheduler setting in seconds, only when the scheduler is on and the value is set. */
    private Optional<String> schedulerSeconds(JmxElement tg, String property, List<String> comments) {
        Props p = tg.props();
        String text = p.string(property).trim();
        if (!p.bool("ThreadGroup.scheduler", false) || text.isEmpty()) {
            return Optional.empty();
        }
        Optional<String> seconds = statics.integer(text, tg);
        if (seconds.isEmpty()) {
            comments.addAll(c.todo(tg, property + " `" + text + "` could not be converted", text));
        }
        return seconds.filter(s -> !s.equals("0"));
    }
}
