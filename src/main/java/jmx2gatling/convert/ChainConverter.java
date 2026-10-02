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

/** Converts samplers and controllers (with everything in their scope) into chain steps. */
final class ChainConverter {

    /** What the enclosing thread group decides for every sampler inside it. */
    record ThreadGroupContext(boolean exitOnError) {
    }

    /** Config elements handled outside the chain walk (per request, per thread group or per simulation). */
    private static final Set<String> HANDLED_CONFIGS = Set.of(
        "HeaderManager", "ConfigTestElement (HttpDefaultsGui)", "CSVDataSet", "CookieManager", "CacheManager", "Arguments");

    private final Conversion c;
    private final ElTranslator el;
    private final StaticValues statics;
    private final RequestMapper requests;
    private final CheckMapper checks;
    private final Timers timers;
    private final Conditions conditions;

    ChainConverter(Conversion c, ElTranslator el, StaticValues statics, RequestMapper requests, CheckMapper checks,
                   Timers timers, Conditions conditions) {
        this.c = c;
        this.el = el;
        this.statics = statics;
        this.requests = requests;
        this.checks = checks;
        this.timers = timers;
        this.conditions = conditions;
    }

    /** The steps for the children of {@code container}, which is in {@code scope}. */
    List<Step> children(JmxElement container, Scope scope, ThreadGroupContext tg) {
        Scope inner = scope.enter(container);
        List<Step> steps = new ArrayList<>();
        for (JmxElement child : container.children()) {
            steps.addAll(element(child, inner, tg));
        }
        return steps;
    }

    /** The steps for one child element; {@code scope} is the scope of its parent's children. */
    List<Step> element(JmxElement e, Scope scope, ThreadGroupContext tg) {
        if (!e.enabled()) {
            disabled(e);
            return List.of();
        }
        ElementKind kind = SupportMatrix.kindOf(e);
        switch (kind) {
            case SAMPLER:
                return sampler(e, scope, tg);
            case CONTROLLER:
                return controller(e, scope, tg);
            case LISTENER:
                c.ignore(e, "Listener (reporting only)");
                return List.of();
            case CONFIG:
                if (!HANDLED_CONFIGS.contains(SupportMatrix.typeOf(e))) {
                    return List.of(new Step.Note(c.todo(e, SupportMatrix.typeOf(e) + " not converted; it applied to every sampler in this scope", "")));
                }
                return List.of();
            case PRE_PROCESSOR, POST_PROCESSOR, ASSERTION, TIMER:
                return List.of(); // applied to each sampler through the scope
            default:
                c.ignore(e, "Not valid at this position in a test plan");
                return List.of();
        }
    }

    /** The element's path below its thread group: the scenario field comment already shows the rest. */
    static String stepPath(JmxElement e) {
        for (JmxElement a = e.parent(); a != null; a = a.parent()) {
            if (SupportMatrix.kindOf(a) == ElementKind.THREAD_GROUP) {
                return e.pathBelow(a);
            }
        }
        return e.path();
    }

    void disabled(JmxElement e) {
        int inside = countDescendants(e);
        c.ignore(e, inside == 0 ? "Disabled" : "Disabled, with " + inside + " element(s) inside");
    }

    private static int countDescendants(JmxElement e) {
        int n = 0;
        for (JmxElement child : e.children()) {
            n += 1 + countDescendants(child);
        }
        return n;
    }

    // Samplers

    private List<Step> sampler(JmxElement e, Scope scope, ThreadGroupContext tg) {
        Scope s = scope.enter(e);
        StepNotes notes = new StepNotes();
        samplerChildren(e, notes);
        List<Step> steps = new ArrayList<>();
        timers.pause(e, s.all(ElementKind.TIMER), notes).ifPresent(steps::add);
        if (e.testClass().equals("DebugSampler")) {
            // JMeter still runs the timers in scope before a Debug Sampler.
            c.ignore(e, "Debug Sampler has no load effect");
            if (!notes.comments.isEmpty()) {
                steps.add(new Step.Note(concat(List.of(stepPath(e)), notes.comments)));
            }
            return steps;
        }
        if (!e.testClass().equals("HTTPSamplerProxy")) {
            c.findings.countSampler(false);
            List<String> comments = new ArrayList<>();
            comments.add(stepPath(e));
            if (Scripts.isScripted(e)) {
                comments.addAll(c.todo(e, "Script sampler not converted; replaced by a no-op", Scripts.of(e)));
            } else {
                comments.addAll(c.todo(e, "Non-HTTP sampler " + SupportMatrix.typeOf(e) + " not converted; replaced by a no-op", ""));
            }
            comments.addAll(notes.comments);
            steps.add(new Step.Action(comments, GatlingDsl.NO_OP));
            return steps;
        }
        for (JmxElement pre : s.all(ElementKind.PRE_PROCESSOR)) {
            c.appliedElements.add(pre);
            if (Scripts.isScripted(pre)) {
                notes.comments.addAll(c.todo(pre, "Script pre-processor not converted", Scripts.of(pre)));
            } else {
                notes.comments.addAll(c.todo(pre, SupportMatrix.typeOf(pre) + " not converted", ""));
            }
        }
        RequestSpec spec = requests.map(e, s, notes);
        checks.addChecks(e, s, spec, notes);
        List<String> comments = new ArrayList<>();
        comments.add(stepPath(e));
        comments.addAll(notes.comments);
        Step.Request request = new Step.Request(comments, spec);
        c.requests.add(request);
        c.findings.countSampler(true);
        steps.addAll(notes.before);
        steps.add(request);
        if (tg.exitOnError()) {
            steps.add(new Step.Action(List.of(), GatlingDsl.EXIT_HERE_IF_FAILED));
        }
        return steps;
    }

    /** A sampler's own children that the scope does not handle: disabled ones, listeners, unsupported configs. */
    private void samplerChildren(JmxElement sampler, StepNotes notes) {
        for (JmxElement child : sampler.children()) {
            if (!child.enabled()) {
                disabled(child);
                continue;
            }
            ElementKind kind = SupportMatrix.kindOf(child);
            if (kind == ElementKind.LISTENER) {
                c.ignore(child, "Listener (reporting only)");
            } else if (kind == ElementKind.CONFIG && !HANDLED_CONFIGS.contains(SupportMatrix.typeOf(child))) {
                notes.comments.addAll(c.todo(child, SupportMatrix.typeOf(child) + " not converted; it applied to this sampler", ""));
            } else if (!Scope.isScoped(kind)) {
                c.ignore(child, "Not valid under a sampler");
            }
        }
    }

    // Controllers

    private List<Step> controller(JmxElement e, Scope scope, ThreadGroupContext tg) {
        Props p = e.props();
        List<String> path = List.of(stepPath(e));
        switch (e.testClass()) {
            case "GenericController", "RecordingController": {
                List<Step> body = children(e, scope, tg);
                if (body.isEmpty()) {
                    return List.of();
                }
                List<Step> steps = new ArrayList<>();
                steps.add(new Step.Note(path));
                steps.addAll(body);
                return steps;
            }
            case "TransactionController": {
                StepNotes notes = new StepNotes();
                String head = GatlingDsl.group(el.toEl(e.name(), e, notes));
                List<Step> steps = new ArrayList<>(notes.before);
                steps.add(new Step.Block(concat(path, notes.comments), head, children(e, scope, tg)));
                return steps;
            }
            case "LoopController":
                return List.of(loop(e, scope, tg));
            case "WhileController": {
                StepNotes notes = new StepNotes();
                String condition = p.string("WhileController.condition");
                String lambda = condition(e, condition, Conditions.Kind.WHILE, notes);
                return List.of(new Step.Block(concat(path, notes.comments), GatlingDsl.asLongAs(lambda), children(e, scope, tg)));
            }
            case "IfController": {
                StepNotes notes = new StepNotes();
                Conditions.Kind kind = p.bool("IfController.useExpression", false) ? Conditions.Kind.IF_EXPRESSION : Conditions.Kind.IF_JAVASCRIPT;
                String lambda = condition(e, p.string("IfController.condition"), kind, notes);
                if (p.bool("IfController.evaluateAll", false)) {
                    c.approximate(e, "\"Evaluate for all children\" not converted", "The condition is evaluated once before the children, not before each child.");
                }
                return List.of(new Step.Block(concat(path, notes.comments), GatlingDsl.doIf(lambda), children(e, scope, tg)));
            }
            case "RandomController": {
                List<Step.Branch> branches = new ArrayList<>();
                for (List<Step> child : executableChildren(e, scope, tg)) {
                    branches.add(new Step.Branch(null, child));
                }
                if (branches.isEmpty()) {
                    return children(e, scope, tg);
                }
                return List.of(new Step.Switch(path, GatlingDsl.UNIFORM_RANDOM_SWITCH, branches));
            }
            case "OnceOnlyController":
                return List.of(onceOnly(e, scope, tg));
            case "ForeachController":
                return foreach(e, scope, tg);
            case "ThroughputController":
                return throughput(e, scope, tg);
            case "ModuleController", "IncludeController":
                return List.of(new Step.Action(concat(path, c.todo(e, SupportMatrix.typeOf(e)
                    + " not converted; convert the referenced test fragment and call it here", referencedFragment(e))), GatlingDsl.NO_OP));
            default: {
                List<Step> steps = new ArrayList<>();
                steps.add(new Step.Note(concat(path, c.todo(e, SupportMatrix.typeOf(e)
                    + " not converted; its children are converted inline as if in a Simple Controller", ""))));
                List<Step> body = children(e, scope, tg);
                steps.addAll(body.isEmpty() ? List.of(new Step.Action(List.of(), GatlingDsl.NO_OP)) : body);
                return steps;
            }
        }
    }

    private static String referencedFragment(JmxElement e) {
        if (e.testClass().equals("IncludeController")) {
            return "[include file: " + e.props().string("IncludeController.includepath") + "]";
        }
        List<String> nodes = new ArrayList<>();
        for (Property item : e.props().collection("ModuleController.node_path")) {
            if (item instanceof Property.Value v) {
                nodes.add(v.text());
            }
        }
        return nodes.isEmpty() ? "" : "[module: " + String.join(" > ", nodes) + "]";
    }

    private Step loop(JmxElement e, Scope scope, ThreadGroupContext tg) {
        String loops = e.props().string("LoopController.loops").trim();
        List<String> comments = new ArrayList<>(List.of(stepPath(e)));
        String head;
        if (loops.equals("-1") || statics.isInfiniteLoopProperty(loops, e, comments)) {
            head = GatlingDsl.FOREVER;
        } else {
            Optional<String> count = statics.integer(loops.isEmpty() ? "1" : loops, e);
            if (count.isPresent()) {
                head = GatlingDsl.repeat(count.get());
            } else if (loops.matches("\\$\\{[^${}]+\\}")) {
                StepNotes notes = new StepNotes();
                head = GatlingDsl.repeat(JavaText.quote(el.toEl(loops, e, notes)));
                comments.addAll(notes.comments);
            } else {
                comments.addAll(c.todo(e, "Loop count `" + loops + "` could not be converted; using 1", loops));
                head = GatlingDsl.repeat("1");
            }
        }
        return new Step.Block(comments, head, children(e, scope, tg));
    }

    private String condition(JmxElement e, String condition, Conditions.Kind kind, StepNotes notes) {
        Optional<String> lambda = conditions.toLambda(condition, kind, e);
        if (lambda.isPresent()) {
            return lambda.get();
        }
        String reason;
        if (kind == Conditions.Kind.WHILE && condition.isBlank()) {
            reason = "Empty condition (loop until a sampler in the loop fails) not converted; placeholder is session -> true";
        } else if (kind == Conditions.Kind.WHILE && (condition.trim().equals("LAST") || condition.trim().equals("OTHER"))) {
            reason = "Condition " + condition.trim() + " (stop when the last sample failed) not converted; placeholder is session -> true";
        } else {
            reason = "Condition not converted; placeholder is session -> true";
        }
        notes.comments.addAll(c.todo(e, reason, condition));
        return "session -> true";
    }

    /** Nested Once Only Controllers: a per-user flag makes the children run on the first pass only. */
    private Step onceOnly(JmxElement e, Scope scope, ThreadGroupContext tg) {
        String flag = c.internalAttribute("once");
        List<Step> body = new ArrayList<>();
        body.add(new Step.Action(List.of(), GatlingDsl.sessionFunction("session.set(" + JavaText.quote(flag) + ", true)")));
        body.addAll(children(e, scope, tg));
        return new Step.Block(List.of(stepPath(e)), GatlingDsl.doIf("session -> !session.contains(" + JavaText.quote(flag) + ")"), body);
    }

    private List<Step> foreach(JmxElement e, Scope scope, ThreadGroupContext tg) {
        Props p = e.props();
        String input = p.string("ForeachController.inputVal").trim();
        String output = p.string("ForeachController.returnVal").trim();
        String start = p.string("ForeachController.startIndex").trim();
        String end = p.string("ForeachController.endIndex").trim();
        List<String> comments = new ArrayList<>(List.of(stepPath(e)));
        if (input.isEmpty() || output.isEmpty() || !start.matches("\\d{0,9}") || !end.matches("\\d{0,9}")) {
            comments.addAll(c.todo(e, "ForEach input, output or index range is missing or not literal", ""));
            List<Step> steps = new ArrayList<>();
            steps.add(new Step.Note(comments));
            steps.addAll(children(e, scope, tg));
            return steps;
        }
        c.defineVariable(output);
        String outputAttribute = c.attribute(output, e);
        List<Step> steps = new ArrayList<>();
        String sequence;
        if (c.listVariables.contains(input) && start.isEmpty() && end.isEmpty()) {
            sequence = c.attribute(input, e);
        } else {
            String separator = p.bool("ForeachController.useSeparator", true) ? "_" : "";
            String prefix = (input + separator).replaceAll("[^A-Za-z0-9_]", "_");
            sequence = c.internalAttribute("series");
            c.helpers.add(Conversion.Helper.COLLECT_SERIES);
            c.referenceVariable(input + separator + "1", e);
            String code = GatlingDsl.sessionFunction("session.set(" + JavaText.quote(sequence) + ", collectSeries(session, "
                + JavaText.quote(prefix) + ", " + (start.isEmpty() ? 0 : Integer.parseInt(start)) + ", " + (end.isEmpty() ? -1 : Integer.parseInt(end)) + "))");
            steps.add(new Step.Action(List.of("Collect " + input + separator + "1.." + input + separator + "N into a list for foreach"), code));
        }
        steps.add(new Step.Block(comments, GatlingDsl.foreach(GatlingDsl.elAttribute(sequence), outputAttribute), children(e, scope, tg)));
        return steps;
    }

    private List<Step> throughput(JmxElement e, Scope scope, ThreadGroupContext tg) {
        Props p = e.props();
        List<String> path = List.of(stepPath(e));
        boolean percentMode = p.integer("ThroughputController.style", 0) == 1;
        String percent = p.string("ThroughputController.percentThroughput").trim();
        if (percentMode && percent.matches("\\d+(\\.\\d+)?")) {
            double value = Double.parseDouble(percent);
            if (value >= 100.0) {
                return children(e, scope, tg);
            }
            c.approximate(e, "Percent Executions " + percent + "% → randomSwitch with " + percent + "% weight",
                "JMeter runs the children on exactly that share of passes; Gatling picks randomly, so the share is only reached on average.");
            return List.of(new Step.Switch(path, GatlingDsl.RANDOM_SWITCH, List.of(new Step.Branch(GatlingDsl.percent(value), children(e, scope, tg)))));
        }
        String reason = percentMode
            ? "Throughput percentage `" + percent + "` is not a literal; children always run"
            : "Total Executions mode (" + p.string("ThroughputController.maxThroughput") + " executions"
                + (p.bool("ThroughputController.perThread", false) ? " per user" : " in total") + ") not converted; children always run";
        List<Step> steps = new ArrayList<>();
        steps.add(new Step.Note(concat(path, c.todo(e, reason, ""))));
        steps.addAll(children(e, scope, tg));
        return steps;
    }

    /** One step list per enabled sampler or controller child (for switches, where each child is a branch). */
    private List<List<Step>> executableChildren(JmxElement container, Scope scope, ThreadGroupContext tg) {
        Scope inner = scope.enter(container);
        List<List<Step>> result = new ArrayList<>();
        List<Step> pendingNotes = new ArrayList<>();
        for (JmxElement child : container.children()) {
            ElementKind kind = SupportMatrix.kindOf(child);
            List<Step> steps = element(child, inner, tg);
            if (child.enabled() && (kind == ElementKind.SAMPLER || kind == ElementKind.CONTROLLER)) {
                List<Step> branch = new ArrayList<>(pendingNotes);
                pendingNotes.clear();
                if (steps.isEmpty()) {
                    branch.add(new Step.Action(List.of(stepPath(child), "(nothing to run)"), GatlingDsl.NO_OP));
                } else {
                    branch.addAll(steps);
                }
                result.add(branch);
            } else {
                pendingNotes.addAll(steps);
            }
        }
        if (!pendingNotes.isEmpty() && !result.isEmpty()) {
            result.get(result.size() - 1).addAll(pendingNotes);
        }
        return result;
    }

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> result = new ArrayList<>(a);
        result.addAll(b);
        return result;
    }
}
