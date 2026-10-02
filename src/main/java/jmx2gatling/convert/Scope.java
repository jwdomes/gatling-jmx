package jmx2gatling.convert;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import jmx2gatling.inventory.ElementKind;
import jmx2gatling.inventory.SupportMatrix;
import jmx2gatling.model.JmxElement;
import jmx2gatling.model.Property;

/**
 * The scoped elements (config elements, pre/post-processors, assertions, timers) that apply at a
 * point of the tree. In JMeter these apply to every sampler below the element that contains
 * them, whatever their position among their siblings.
 */
final class Scope {

    record Header(String name, String value, JmxElement source) {
    }

    /** The HTTP Request Defaults fields that apply, closest manager first per field. */
    record Defaults(String protocol, String domain, String port, String path, List<JmxElement> sources) {
    }

    private final Scope parent;
    private final List<JmxElement> own;

    private Scope(Scope parent, List<JmxElement> own) {
        this.parent = parent;
        this.own = own;
    }

    static Scope root() {
        return new Scope(null, List.of());
    }

    /** The scope inside {@code container}: this scope plus its enabled scoped children. */
    Scope enter(JmxElement container) {
        List<JmxElement> scoped = new ArrayList<>();
        for (JmxElement child : container.children()) {
            if (child.enabled() && isScoped(SupportMatrix.kindOf(child))) {
                scoped.add(child);
            }
        }
        return new Scope(this, scoped);
    }

    static boolean isScoped(ElementKind kind) {
        return switch (kind) {
            case CONFIG, PRE_PROCESSOR, POST_PROCESSOR, ASSERTION, TIMER -> true;
            default -> false;
        };
    }

    /** Elements of {@code kind}, outermost scope first, in tree order within a level. */
    List<JmxElement> all(ElementKind kind) {
        List<JmxElement> result = new ArrayList<>();
        for (Scope s : outermostFirst()) {
            for (JmxElement e : s.own) {
                if (SupportMatrix.kindOf(e) == kind) {
                    result.add(e);
                }
            }
        }
        return result;
    }

    List<JmxElement> configs(String type) {
        List<JmxElement> result = new ArrayList<>();
        for (JmxElement e : all(ElementKind.CONFIG)) {
            if (SupportMatrix.typeOf(e).equals(type)) {
                result.add(e);
            }
        }
        return result;
    }

    /**
     * Header Manager headers merged across scopes. Names compare case-insensitively and the
     * closest manager wins per name; a name keeps the position where it first appeared.
     */
    List<Header> headers() {
        Map<String, Header> merged = new LinkedHashMap<>();
        for (JmxElement manager : configs("HeaderManager")) {
            for (Property item : manager.props().collection("HeaderManager.headers")) {
                if (item instanceof Property.Element header) {
                    String name = header.props().string("Header.name").trim();
                    if (!name.isEmpty()) {
                        merged.put(name.toLowerCase(Locale.ROOT), new Header(name, header.props().string("Header.value"), manager));
                    }
                }
            }
        }
        return List.copyOf(merged.values());
    }

    /** HTTP Request Defaults merged across scopes; the closest non-blank value wins per field. */
    Defaults defaults() {
        List<JmxElement> managers = new ArrayList<>(configs("ConfigTestElement (HttpDefaultsGui)"));
        Collections.reverse(managers);
        return new Defaults(
            closest(managers, "HTTPSampler.protocol"),
            closest(managers, "HTTPSampler.domain"),
            closest(managers, "HTTPSampler.port"),
            closest(managers, "HTTPSampler.path"),
            managers);
    }

    private static String closest(List<JmxElement> closestFirst, String property) {
        for (JmxElement e : closestFirst) {
            String value = e.props().string(property).trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private List<Scope> outermostFirst() {
        List<Scope> chain = new ArrayList<>();
        for (Scope s = this; s != null; s = s.parent) {
            chain.add(s);
        }
        Collections.reverse(chain);
        return chain;
    }
}
