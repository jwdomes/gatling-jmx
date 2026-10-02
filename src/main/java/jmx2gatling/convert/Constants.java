package jmx2gatling.convert;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The {@code private static final} fields of a generated simulation. */
final class Constants {

    record Constant(String name, String type, String init, String comment) {
    }

    private final Naming naming;
    private final List<Constant> constants = new ArrayList<>();
    private final Map<String, String> byKey = new HashMap<>();

    Constants(Naming naming) {
        this.naming = naming;
    }

    /** Adds a constant once per {@code key}; returns its name. */
    String add(String key, String preferredName, String type, String init, String comment) {
        String existing = byKey.get(key);
        if (existing != null) {
            return existing;
        }
        String name = naming.constant(preferredName, "VALUE");
        constants.add(new Constant(name, type, init, comment));
        byKey.put(key, name);
        return name;
    }

    boolean has(String key) {
        return byKey.containsKey(key);
    }

    /** A JMeter property as a String system property. */
    String stringProperty(String property, String defaultValue) {
        return add("string-property:" + property + "\u0000" + defaultValue, property, "String",
            "System.getProperty(" + JavaText.quote(property) + ", " + JavaText.quote(defaultValue) + ")",
            "JMeter property " + property + "; override with -D" + property + "=...");
    }

    /** A JMeter property as an int system property. */
    String intProperty(String property, int defaultValue) {
        return add("int-property:" + property + "\u0000" + defaultValue, property, "int",
            "Integer.getInteger(" + JavaText.quote(property) + ", " + defaultValue + ")",
            "JMeter property " + property + "; override with -D" + property + "=...");
    }

    List<Constant> all() {
        return List.copyOf(constants);
    }
}
