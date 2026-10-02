package jmx2gatling.inventory;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import jmx2gatling.Fixtures;
import jmx2gatling.model.JmxElement;
import jmx2gatling.model.Property;
import jmx2gatling.parse.JmxParser;
import org.junit.jupiter.api.Test;

/**
 * The inventory leaves the enterprise network, so it must not contain anything from the plans
 * except type names, function names, counts and support status.
 */
class InventoryLeakTest {

    /** Values too generic to be sensitive, which may legitimately appear in the output. */
    private static final Pattern HARMLESS = Pattern.compile("(?i)true|false|-?\\d+(\\.\\d+)?|=|\\s*");

    @Test
    void outputContainsNoFixtureContent() throws IOException {
        Inventory inventory = new Inventory();
        Set<String> sensitive = new TreeSet<>();
        for (Path fixture : Fixtures.all()) {
            JmxElement root = new JmxParser().parse(fixture);
            inventory.add(root);
            collect(root, sensitive);
        }
        sensitive.add("example-");
        sensitive.add("http://");
        sensitive.add("https://");

        // Type and function names are the allowed vocabulary (and short values like "code" occur inside
        // names like __urlencode), so the check runs on everything else.
        String markdown = withoutNameColumn(inventory.toMarkdown());
        String json = inventory.toJson().replaceAll("\"(type|function)\": \"[^\"]*\"", "");
        for (String s : sensitive) {
            assertTrue(!markdown.contains(s), () -> "Markdown inventory leaks: " + s);
            assertTrue(!json.contains(s), () -> "JSON inventory leaks: " + s);
        }
        for (Path fixture : Fixtures.all()) {
            String file = fixture.getFileName().toString();
            assertTrue(!markdown.contains(file) && !json.contains(file), () -> "Inventory leaks a file name: " + file);
        }
    }

    @Test
    void outputLinesUseOnlyTheAllowedVocabulary() throws IOException {
        Inventory inventory = new Inventory();
        for (Path fixture : Fixtures.all()) {
            inventory.add(new JmxParser().parse(fixture));
        }
        Pattern tableRow = Pattern.compile("\\| [A-Za-z0-9_.$ ()]+ \\| \\d+ \\| \\d+ \\| (supported|partial|unsupported) \\|");
        for (String line : inventory.toMarkdown().split("\n")) {
            if (line.startsWith("| ") && !line.startsWith("| Element type") && !line.startsWith("| Function")) {
                assertTrue(tableRow.matcher(line).matches(), () -> "Unexpected table row: " + line);
            }
        }
    }

    private static String withoutNameColumn(String markdown) {
        StringBuilder out = new StringBuilder();
        for (String line : markdown.split("\n")) {
            out.append(line.startsWith("| ") ? line.replaceFirst("^\\| [^|]*\\|", "|") : line).append('\n');
        }
        return out.toString();
    }

    private static void collect(JmxElement element, Set<String> out) {
        add(element.name(), out);
        for (Property p : element.props().all().values()) {
            collect(p, out);
        }
        for (JmxElement child : element.children()) {
            collect(child, out);
        }
    }

    private static void collect(Property property, Set<String> out) {
        if (property.name().equals("scriptLanguage")) {
            return; // "groovy" etc. are JMeter vocabulary and also part of function names like __groovy
        }
        if (property instanceof Property.Value v) {
            add(v.text(), out);
            for (String line : v.text().split("\n")) {
                add(line.trim(), out);
            }
        } else if (property instanceof Property.Element e) {
            add(e.name(), out);
            e.props().all().values().forEach(p -> collect(p, out));
        } else if (property instanceof Property.Collection c) {
            c.items().forEach(p -> collect(p, out));
        }
    }

    private static void add(String value, Set<String> out) {
        if (value != null && value.length() >= 3 && !HARMLESS.matcher(value).matches()) {
            out.add(value);
        }
    }
}
