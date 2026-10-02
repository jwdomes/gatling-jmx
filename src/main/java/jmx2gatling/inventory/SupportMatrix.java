package jmx2gatling.inventory;

import static jmx2gatling.inventory.ElementKind.ASSERTION;
import static jmx2gatling.inventory.ElementKind.CONFIG;
import static jmx2gatling.inventory.ElementKind.CONTROLLER;
import static jmx2gatling.inventory.ElementKind.LISTENER;
import static jmx2gatling.inventory.ElementKind.POST_PROCESSOR;
import static jmx2gatling.inventory.ElementKind.PRE_PROCESSOR;
import static jmx2gatling.inventory.ElementKind.SAMPLER;
import static jmx2gatling.inventory.ElementKind.TEST_PLAN;
import static jmx2gatling.inventory.ElementKind.THREAD_GROUP;
import static jmx2gatling.inventory.ElementKind.TIMER;
import static jmx2gatling.inventory.SupportStatus.PARTIAL;
import static jmx2gatling.inventory.SupportStatus.SUPPORTED;
import static jmx2gatling.inventory.SupportStatus.UNSUPPORTED;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jmx2gatling.model.JmxElement;

/**
 * The single table of what the converter does with each JMeter element type and function. The
 * inventory command, the converter's element classification and the README matrix all read it.
 */
public final class SupportMatrix {

    public record ElementEntry(String type, ElementKind kind, SupportStatus status, String gatling) {
    }

    public record FunctionEntry(String name, SupportStatus status, String gatling) {
    }

    private static final Map<String, ElementEntry> ELEMENTS = new LinkedHashMap<>();
    private static final Map<String, FunctionEntry> FUNCTIONS = new LinkedHashMap<>();

    static {
        element("TestPlan", TEST_PLAN, SUPPORTED, "One Simulation class; user-defined variables become initial session values");

        element("ThreadGroup", THREAD_GROUP, SUPPORTED, "Scenario + open injection profile (approximation of the closed model)");
        element("SetupThreadGroup", THREAD_GROUP, SUPPORTED, "Scenario chained before the main scenarios with andThen");
        element("PostThreadGroup", THREAD_GROUP, SUPPORTED, "Scenario chained after the main scenarios with andThen");
        element("OpenModelThreadGroup", THREAD_GROUP, UNSUPPORTED, "TODO: write the injection profile by hand");
        element("kg.apc.jmeter.threads.UltimateThreadGroup", THREAD_GROUP, UNSUPPORTED, "TODO: write the injection profile by hand");
        element("kg.apc.jmeter.threads.SteppingThreadGroup", THREAD_GROUP, UNSUPPORTED, "TODO: write the injection profile by hand");
        element("com.blazemeter.jmeter.threads.concurrency.ConcurrencyThreadGroup", THREAD_GROUP, UNSUPPORTED, "TODO: write the injection profile by hand");
        element("com.blazemeter.jmeter.threads.arrivals.ArrivalsThreadGroup", THREAD_GROUP, UNSUPPORTED, "TODO: write the injection profile by hand");
        element("com.blazemeter.jmeter.threads.arrivals.FreeFormArrivalsThreadGroup", THREAD_GROUP, UNSUPPORTED, "TODO: write the injection profile by hand");

        element("HTTPSamplerProxy", SAMPLER, SUPPORTED, "http(name).<method>(path); embedded resources and some file uploads are TODOs");
        element("DebugSampler", SAMPLER, SUPPORTED, "Skipped (no load effect), listed in the report");
        element("JSR223Sampler", SAMPLER, UNSUPPORTED, "TODO + no-op; script kept as a comment");
        element("BeanShellSampler", SAMPLER, UNSUPPORTED, "TODO + no-op; script kept as a comment");
        element("TestAction", SAMPLER, UNSUPPORTED, "TODO + no-op (Flow Control Action)");
        for (String type : List.of("JDBCSampler", "TCPSampler", "JMSSampler", "PublisherSampler", "SubscriberSampler",
                "FTPSampler", "LDAPSampler", "LDAPExtSampler", "SmtpSampler", "MailReaderSampler", "JavaSampler",
                "SystemSampler", "AjpSampler", "AccessLogSampler")) {
            element(type, SAMPLER, UNSUPPORTED, "TODO + no-op (non-HTTP sampler)");
        }

        element("TransactionController", CONTROLLER, SUPPORTED, "group(name).on(...)");
        element("LoopController", CONTROLLER, SUPPORTED, "repeat(n).on(...), or forever() for infinite loops");
        element("WhileController", CONTROLLER, PARTIAL, "asLongAs(...) for simple conditions, TODO otherwise");
        element("IfController", CONTROLLER, PARTIAL, "doIf(...) for simple conditions, TODO otherwise");
        element("RandomController", CONTROLLER, SUPPORTED, "uniformRandomSwitch()");
        element("OnceOnlyController", CONTROLLER, SUPPORTED, "Hoisted before the loop, or guarded by a per-user session flag");
        element("ForeachController", CONTROLLER, PARTIAL, "foreach(list, var); input_N series are collected into a list first");
        element("ThroughputController", CONTROLLER, PARTIAL, "randomSwitch() approximation for percent mode, TODO for total-executions mode");
        element("GenericController", CONTROLLER, SUPPORTED, "Children inlined (Simple Controller)");
        element("RecordingController", CONTROLLER, SUPPORTED, "Children inlined");
        for (String type : List.of("InterleaveControl", "RandomOrderController", "SwitchController", "RunTime",
                "ModuleController", "IncludeController", "CriticalSectionController")) {
            element(type, CONTROLLER, UNSUPPORTED, "TODO; children are converted inline as if in a Simple Controller");
        }

        element("HeaderManager", CONFIG, SUPPORTED, ".header(...) per request; headers common to every request move to httpProtocol");
        element("ConfigTestElement (HttpDefaultsGui)", CONFIG, SUPPORTED, "Fills missing protocol, host, port, path and arguments; host becomes the base URL");
        element("CSVDataSet", CONFIG, PARTIAL, "csv(...) feeder; TODO when the file has no header row");
        element("CookieManager", CONFIG, SUPPORTED, "Gatling's built-in cookie handling; user-defined cookies become addCookie(...)");
        element("CacheManager", CONFIG, SUPPORTED, "Skipped: Gatling caches by default (caching is disabled when no Cache Manager exists)");
        element("Arguments", CONFIG, SUPPORTED, "Initial session values at scenario start");
        for (String type : List.of("AuthManager", "DNSCacheManager", "KeystoreConfig", "CounterConfig",
                "RandomVariableConfig", "JDBCDataSource", "LoginConfig")) {
            element(type, CONFIG, UNSUPPORTED, "TODO");
        }

        element("JSONPostProcessor", POST_PROCESSOR, SUPPORTED, "check(jsonPath(...).saveAs(...)), one per variable");
        element("RegexExtractor", POST_PROCESSOR, PARTIAL, "check(regex(...).saveAs(...)); only the $1$ template and response body");
        element("BoundaryExtractor", POST_PROCESSOR, SUPPORTED, "check(regex(quoted boundaries).saveAs(...)); response body only");
        element("DebugPostProcessor", POST_PROCESSOR, SUPPORTED, "Skipped (no load effect), listed in the report");
        for (String type : List.of("JSR223PostProcessor", "BeanShellPostProcessor")) {
            element(type, POST_PROCESSOR, UNSUPPORTED, "TODO; script kept as a comment");
        }
        for (String type : List.of("XPathExtractor", "XPath2Extractor", "HtmlExtractor", "JMESPathExtractor",
                "ResultAction", "JDBCPostProcessor")) {
            element(type, POST_PROCESSOR, UNSUPPORTED, "TODO");
        }

        for (String type : List.of("JSR223PreProcessor", "BeanShellPreProcessor")) {
            element(type, PRE_PROCESSOR, UNSUPPORTED, "TODO; script kept as a comment");
        }
        for (String type : List.of("UserParameters", "RegExUserParameters", "AnchorModifier", "URLRewritingModifier",
                "SampleTimeout", "JDBCPreProcessor")) {
            element(type, PRE_PROCESSOR, UNSUPPORTED, "TODO");
        }

        element("ResponseAssertion", ASSERTION, PARTIAL, "status()/substring()/regex()/bodyString()/headerRegex() checks; Or and some fields are TODOs");
        element("JSONPathAssertion", ASSERTION, SUPPORTED, "jsonPath(...).exists()/is(...) checks");
        for (String type : List.of("JSR223Assertion", "BeanShellAssertion")) {
            element(type, ASSERTION, UNSUPPORTED, "TODO; script kept as a comment");
        }
        for (String type : List.of("DurationAssertion", "SizeAssertion", "XPathAssertion", "XPath2Assertion",
                "MD5HexAssertion", "HTMLAssertion", "XMLAssertion", "XMLSchemaAssertion", "CompareAssertion",
                "SMIMEAssertionTestElement", "JMESPathAssertion")) {
            element(type, ASSERTION, UNSUPPORTED, "TODO");
        }

        element("ConstantTimer", TIMER, SUPPORTED, "pause(Duration.ofMillis(d)) before each sampler in scope");
        element("UniformRandomTimer", TIMER, SUPPORTED, "pause(min, max) before each sampler in scope");
        element("GaussianRandomTimer", TIMER, PARTIAL, "Uniform pause with the same mean (Gatling has no per-pause normal distribution)");
        for (String type : List.of("ConstantThroughputTimer", "PreciseThroughputTimer", "SyncTimer")) {
            element(type, TIMER, UNSUPPORTED, "TODO; throttle(...) is the closest Gatling equivalent");
        }
        for (String type : List.of("PoissonRandomTimer", "JSR223Timer", "BeanShellTimer")) {
            element(type, TIMER, UNSUPPORTED, "TODO");
        }

        for (String type : List.of("ResultCollector", "Summariser", "BackendListener", "JSR223Listener", "BeanShellListener")) {
            element(type, LISTENER, SUPPORTED, "Ignored (reporting only)");
        }

        function("__UUID", SUPPORTED, "#{randomUuid()}");
        function("__time", SUPPORTED, "#{currentTimeMillis()}, #{currentDate(fmt)} or a session function");
        function("__Random", SUPPORTED, "#{randomInt(min,max+1)} (Gatling's upper bound is exclusive)");
        function("__RandomString", SUPPORTED, "Session function");
        function("__threadNum", SUPPORTED, "#{userId()} (numbering differs, see report)");
        function("__counter", SUPPORTED, "Per-user session counter or global AtomicLong");
        function("__P", SUPPORTED, "Java system property (-Dname=value)");
        function("__property", SUPPORTED, "Java system property (-Dname=value)");
        for (String name : List.of("__groovy", "__jexl3", "__jexl2", "__javaScript", "__BeanShell")) {
            function(name, PARTIAL, "Simple conditions only; TODO otherwise");
        }
    }

    private SupportMatrix() {
    }

    private static void element(String type, ElementKind kind, SupportStatus status, String gatling) {
        ELEMENTS.put(type, new ElementEntry(type, kind, status, gatling));
    }

    private static void function(String name, SupportStatus status, String gatling) {
        FUNCTIONS.put(name, new FunctionEntry(name, status, gatling));
    }

    /**
     * The type name used for counting and lookup: the testclass, except that the generic
     * {@code ConfigTestElement} is qualified by its GUI class (HTTP Request Defaults, TCP config, ...).
     */
    public static String typeOf(JmxElement element) {
        if ("ConfigTestElement".equals(element.testClass())) {
            return "ConfigTestElement (" + element.guiClass() + ")";
        }
        return element.testClass();
    }

    public static ElementKind kindOf(JmxElement element) {
        ElementEntry entry = ELEMENTS.get(typeOf(element));
        return entry != null ? entry.kind() : guessKind(element.testClass());
    }

    /** Unknown types are unsupported, except listeners: dropping a listener never changes the load. */
    public static SupportStatus statusOf(String type) {
        ElementEntry entry = ELEMENTS.get(type);
        if (entry != null) {
            return entry.status();
        }
        return guessKind(type) == LISTENER ? SUPPORTED : UNSUPPORTED;
    }

    public static SupportStatus functionStatus(String name) {
        FunctionEntry entry = FUNCTIONS.get(name);
        return entry != null ? entry.status() : UNSUPPORTED;
    }

    public static List<ElementEntry> elements() {
        return List.copyOf(ELEMENTS.values());
    }

    public static List<FunctionEntry> functions() {
        return List.copyOf(FUNCTIONS.values());
    }

    /** Classifies element types this table does not know (mostly plugins) by naming convention. */
    static ElementKind guessKind(String testClass) {
        String simple = testClass.substring(testClass.lastIndexOf('.') + 1);
        if (simple.contains("ThreadGroup")) {
            return THREAD_GROUP;
        }
        if (simple.endsWith("Sampler")) {
            return SAMPLER;
        }
        if (simple.contains("Controller") || simple.endsWith("Control")) {
            return CONTROLLER;
        }
        if (simple.contains("PreProcessor") || simple.endsWith("Modifier")) {
            return PRE_PROCESSOR;
        }
        if (simple.contains("PostProcessor") || simple.endsWith("Extractor")) {
            return POST_PROCESSOR;
        }
        if (simple.contains("Assertion")) {
            return ASSERTION;
        }
        if (simple.endsWith("Timer")) {
            return TIMER;
        }
        if (simple.contains("Listener") || simple.contains("Collector") || simple.contains("Visualizer")) {
            return LISTENER;
        }
        return CONFIG;
    }
}
