package jmx2gatling.inventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import jmx2gatling.model.JmxElement;
import jmx2gatling.model.Property;
import jmx2gatling.parse.JmeterExpression;

/**
 * Counts element types and JMeter functions across test plans.
 *
 * <p>The output is meant to leave the network it was produced on, so it contains only element
 * type names, function names, counts and support status. Element names, file names, property
 * values and scripts are never rendered; type and function names that do not look like Java
 * class or JMeter function names are replaced by a placeholder.
 */
public final class Inventory {

    private static final Pattern SAFE_TYPE = Pattern.compile("[A-Za-z0-9_.$]+( \\([A-Za-z0-9_.$]*\\))?");
    private static final Pattern SAFE_FUNCTION = Pattern.compile("__[A-Za-z0-9_]+");
    private static final String UNRECOGNIZED_TYPE = "(unrecognized type name)";
    private static final String UNRECOGNIZED_FUNCTION = "(unrecognized function name)";

    private record Row(String name, SupportStatus status, int enabled, int disabled) {
    }

    /** name → {enabled count, disabled count} */
    private final Map<String, int[]> elementCounts = new TreeMap<>();
    private final Map<String, int[]> functionCounts = new TreeMap<>();
    private int filesScanned;
    private int filesFailed;

    public void add(JmxElement root) {
        filesScanned++;
        countTree(root);
    }

    public void addFailure() {
        filesFailed++;
    }

    private void countTree(JmxElement element) {
        int column = element.effectivelyDisabled() ? 1 : 0;
        String type = SupportMatrix.typeOf(element);
        elementCounts.computeIfAbsent(SAFE_TYPE.matcher(type).matches() ? type : UNRECOGNIZED_TYPE, k -> new int[2])[column]++;
        countFunctions(element.name(), column);
        for (Property p : element.props().all().values()) {
            countFunctions(p, column);
        }
        for (JmxElement child : element.children()) {
            countTree(child);
        }
    }

    private void countFunctions(Property property, int column) {
        if (property instanceof Property.Value v) {
            countFunctions(v.text(), column);
        } else if (property instanceof Property.Element e) {
            for (Property p : e.props().all().values()) {
                countFunctions(p, column);
            }
        } else if (property instanceof Property.Collection c) {
            for (Property p : c.items()) {
                countFunctions(p, column);
            }
        }
    }

    private void countFunctions(String text, int column) {
        for (JmeterExpression.Part part : JmeterExpression.parse(text)) {
            if (part instanceof JmeterExpression.Call call) {
                String name = SAFE_FUNCTION.matcher(call.name()).matches() ? call.name() : UNRECOGNIZED_FUNCTION;
                functionCounts.computeIfAbsent(name, k -> new int[2])[column]++;
                for (String arg : call.args()) {
                    countFunctions(arg, column);
                }
            }
        }
    }

    private List<Row> elementRows() {
        List<Row> rows = new ArrayList<>();
        elementCounts.forEach((name, c) -> rows.add(new Row(name, SupportMatrix.statusOf(name), c[0], c[1])));
        rows.sort(BY_STATUS_THEN_NAME);
        return rows;
    }

    private List<Row> functionRows() {
        List<Row> rows = new ArrayList<>();
        functionCounts.forEach((name, c) -> rows.add(new Row(name, SupportMatrix.functionStatus(name), c[0], c[1])));
        rows.sort(BY_STATUS_THEN_NAME);
        return rows;
    }

    /** Unsupported first, so the output reads as a work list. */
    private static final Comparator<Row> BY_STATUS_THEN_NAME =
        Comparator.comparing((Row r) -> -r.status().ordinal()).thenComparing(Row::name);

    public String toMarkdown() {
        StringBuilder out = new StringBuilder();
        out.append("# jmx2gatling inventory\n\n");
        out.append("- Files scanned: ").append(filesScanned).append('\n');
        out.append("- Files that could not be parsed: ").append(filesFailed).append('\n');
        out.append("\n## Element types\n\n");
        out.append("Disabled counts include every element inside a disabled subtree.\n\n");
        out.append("| Element type | Enabled | Disabled | Support |\n|---|---:|---:|---|\n");
        for (Row r : elementRows()) {
            out.append("| ").append(r.name()).append(" | ").append(r.enabled()).append(" | ").append(r.disabled())
                .append(" | ").append(r.status().label()).append(" |\n");
        }
        out.append("\n## Functions\n\n");
        List<Row> functions = functionRows();
        if (functions.isEmpty()) {
            out.append("No JMeter functions used.\n");
        } else {
            out.append("| Function | Uses in enabled elements | Uses in disabled elements | Support |\n|---|---:|---:|---|\n");
            for (Row r : functions) {
                out.append("| ").append(r.name()).append(" | ").append(r.enabled()).append(" | ").append(r.disabled())
                    .append(" | ").append(r.status().label()).append(" |\n");
            }
        }
        return out.toString();
    }

    public String toJson() {
        StringBuilder out = new StringBuilder();
        out.append("{\n");
        out.append("  \"filesScanned\": ").append(filesScanned).append(",\n");
        out.append("  \"filesFailed\": ").append(filesFailed).append(",\n");
        out.append("  \"elements\": ");
        appendJsonRows(out, elementRows(), "type");
        out.append(",\n  \"functions\": ");
        appendJsonRows(out, functionRows(), "function");
        out.append("\n}\n");
        return out.toString();
    }

    /** Names are already restricted to [A-Za-z0-9_.$ ()], so they need no JSON escaping. */
    private static void appendJsonRows(StringBuilder out, List<Row> rows, String nameKey) {
        if (rows.isEmpty()) {
            out.append("[]");
            return;
        }
        out.append("[\n");
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            out.append("    {\"").append(nameKey).append("\": \"").append(r.name()).append("\", \"enabled\": ")
                .append(r.enabled()).append(", \"disabled\": ").append(r.disabled()).append(", \"support\": \"")
                .append(r.status().label()).append("\"}").append(i < rows.size() - 1 ? ",\n" : "\n");
        }
        out.append("  ]");
    }
}
