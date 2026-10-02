package jmx2gatling.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The properties of a test element, with typed getters that apply JMeter's defaults when a
 * property is missing or blank (JMeter itself reads a missing string as "" and a missing number
 * or boolean as the element's default).
 */
public final class Props {

    private final Map<String, Property> byName;

    public Props(Map<String, Property> byName) {
        this.byName = Collections.unmodifiableMap(new LinkedHashMap<>(byName));
    }

    public boolean has(String name) {
        return byName.containsKey(name);
    }

    public Map<String, Property> all() {
        return byName;
    }

    /** The raw text of a scalar property, or "" when missing. */
    public String string(String name) {
        return string(name, "");
    }

    public String string(String name, String defaultValue) {
        Property p = byName.get(name);
        if (p instanceof Property.Value v) {
            return v.text();
        }
        return defaultValue;
    }

    public boolean bool(String name, boolean defaultValue) {
        String text = string(name).trim();
        return text.isEmpty() ? defaultValue : Boolean.parseBoolean(text);
    }

    /** An integer property, or {@code defaultValue} when missing, blank or not a plain integer. */
    public int integer(String name, int defaultValue) {
        String text = string(name).trim();
        try {
            return text.isEmpty() ? defaultValue : Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public Optional<Property.Element> element(String name) {
        Property p = byName.get(name);
        return p instanceof Property.Element e ? Optional.of(e) : Optional.empty();
    }

    /** The items of a {@code collectionProp}, or an empty list when missing. */
    public List<Property> collection(String name) {
        Property p = byName.get(name);
        return p instanceof Property.Collection c ? c.items() : List.of();
    }
}
