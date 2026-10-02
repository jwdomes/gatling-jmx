package jmx2gatling.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One test element of a JMeter plan together with its children (the following {@code hashTree}). */
public final class JmxElement {

    private final String testClass;
    private final String guiClass;
    private final String name;
    private final boolean enabled;
    private final Props props;
    private final List<JmxElement> children = new ArrayList<>();
    private JmxElement parent;

    public JmxElement(String testClass, String guiClass, String name, boolean enabled, Props props) {
        this.testClass = testClass;
        this.guiClass = guiClass;
        this.name = name;
        this.enabled = enabled;
        this.props = props;
    }

    public void addChild(JmxElement child) {
        child.parent = this;
        children.add(child);
    }

    public String testClass() {
        return testClass;
    }

    public String guiClass() {
        return guiClass;
    }

    public String name() {
        return name;
    }

    public boolean enabled() {
        return enabled;
    }

    public Props props() {
        return props;
    }

    public List<JmxElement> children() {
        return Collections.unmodifiableList(children);
    }

    public JmxElement parent() {
        return parent;
    }

    /** True when this element or any ancestor is disabled, which makes JMeter skip it. */
    public boolean effectivelyDisabled() {
        for (JmxElement e = this; e != null; e = e.parent) {
            if (!e.enabled) {
                return true;
            }
        }
        return false;
    }

    /**
     * The element's tree path for messages, for example
     * {@code TestPlan > Login Flow (ThreadGroup) > POST /token (HTTPSamplerProxy)}.
     */
    public String path() {
        return pathBelow(null);
    }

    /** The path from just below {@code ancestor} down to this element; the full path when it is not an ancestor. */
    public String pathBelow(JmxElement ancestor) {
        List<String> parts = new ArrayList<>();
        for (JmxElement e = this; e != null && e != ancestor; e = e.parent) {
            parts.add(e.parent == null ? e.testClass : e.name + " (" + e.testClass + ")");
        }
        Collections.reverse(parts);
        return String.join(" > ", parts);
    }

    @Override
    public String toString() {
        return path();
    }
}
