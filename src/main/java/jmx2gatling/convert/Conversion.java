package jmx2gatling.convert;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import jmx2gatling.model.JmxElement;
import jmx2gatling.report.Findings;

/** State shared by everything that converts one JMX file. */
final class Conversion {

    static final String TODO_MARKER = "TODO(jmx2gatling)";
    /** Prefix of session attributes the converter invents, so they never clash with plan variables. */
    static final String INTERNAL_PREFIX = "jmx2g_";

    final Findings findings = new Findings();
    final Naming identifiers = new Naming();
    final Naming bodyFileNames = new Naming();
    final Constants constants = new Constants(identifiers);
    final Origins origins = new Origins();
    final String bodiesResourceDir;
    final boolean allBodiesToFiles;

    /** User-defined variables in plan order: JMeter name → raw value. */
    final Map<String, String> userDefinedVariables = new LinkedHashMap<>();
    final Map<String, JmxElement> userDefinedVariableSources = new HashMap<>();
    /** Session attributes holding JMeter properties that are used inside EL strings: attribute → constant. */
    final Map<String, String> propertyAttributes = new TreeMap<>();
    /** Resource path → content of request bodies written to files. */
    final Map<String, String> bodyFiles = new LinkedHashMap<>();
    final Set<String> definedVariables = new TreeSet<>();
    /** Variables an extractor saves as a list (match number -1), usable directly by foreach. */
    final Set<String> listVariables = new TreeSet<>();
    final Map<String, JmxElement> referencedVariables = new LinkedHashMap<>();
    final Set<Helper> helpers = new TreeSet<>();
    final List<Step.Request> requests = new ArrayList<>();
    /** Scoped elements (headers, timers, extractors, ...) that reached at least one sampler. */
    final Set<JmxElement> appliedElements = Collections.newSetFromMap(new IdentityHashMap<>());
    boolean usesPattern;
    boolean feedsColumnsFromFileHeader;

    private final Map<String, String> renamedVariables = new HashMap<>();
    private int sequence;

    enum Helper { RANDOM_STRING, COLLECT_SERIES }

    Conversion(String bodiesResourceDir, boolean allBodiesToFiles) {
        this.bodiesResourceDir = bodiesResourceDir;
        this.allBodiesToFiles = allBodiesToFiles;
        identifiers.reserve("httpProtocol");
        // A field named http would hide HttpDsl.http, which the protocol declaration uses.
        identifiers.reserve("http");
    }

    int nextSequence() {
        return ++sequence;
    }

    String internalAttribute(String purpose) {
        return INTERNAL_PREFIX + purpose + "_" + nextSequence();
    }

    /**
     * The Gatling session attribute for a JMeter variable. Gatling EL reads {@code #{a.b}} as
     * "field b of a", so characters other than letters, digits and underscores become underscores.
     */
    String attribute(String jmeterName, JmxElement where) {
        String safe = jmeterName.replaceAll("[^A-Za-z0-9_]", "_");
        if (!safe.equals(jmeterName) && renamedVariables.putIfAbsent(jmeterName, safe) == null) {
            findings.approximate(where, "Variable `" + jmeterName + "` renamed to `" + safe + "`",
                "Gatling EL treats `.` and other punctuation in names as attribute access, so the name was made safe everywhere it is used.");
        }
        return safe;
    }

    void defineVariable(String jmeterName) {
        definedVariables.add(jmeterName);
    }

    void referenceVariable(String jmeterName, JmxElement where) {
        referencedVariables.putIfAbsent(jmeterName, where);
    }

    /** Registers a TODO and returns the comment lines that mark it in the generated code. */
    List<String> todo(JmxElement element, String reason, String original) {
        Findings.TodoRef ref = findings.todo(element, reason, original);
        List<String> lines = new ArrayList<>();
        lines.add(TODO_MARKER + " #" + ref.todo().id() + " " + reason + " [" + ref.todo().type() + "]");
        if (!original.isBlank()) {
            if (ref.first()) {
                lines.add("Original:");
                for (String line : original.replace("\r\n", "\n").split("\n", -1)) {
                    lines.add("    " + line);
                }
            } else {
                lines.add("(original shown at the first #" + ref.todo().id() + " marker and in conversion-report.md)");
            }
        }
        return lines;
    }

    void approximate(JmxElement element, String what, String difference) {
        findings.approximate(element, what, difference);
    }

    void ignore(JmxElement element, String reason) {
        findings.ignore(element, reason);
    }
}
