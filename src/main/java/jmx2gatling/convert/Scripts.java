package jmx2gatling.convert;

import jmx2gatling.model.JmxElement;
import jmx2gatling.model.Props;

/** The script text of JSR223 and BeanShell elements, for TODO comments and the report. */
final class Scripts {

    private Scripts() {
    }

    static boolean isScripted(JmxElement e) {
        String type = e.testClass();
        return type.startsWith("JSR223") || type.startsWith("BeanShell");
    }

    static String of(JmxElement e) {
        Props p = e.props();
        String script = p.string("script");
        String file = p.string("filename");
        if (e.testClass().equals("BeanShellSampler") || e.testClass().equals("BeanShellAssertion")) {
            script = p.string(e.testClass() + ".query");
            file = p.string(e.testClass() + ".filename");
        }
        String language = p.string("scriptLanguage").trim();
        StringBuilder out = new StringBuilder();
        if (!language.isEmpty()) {
            out.append("[language: ").append(language).append("]\n");
        }
        if (!file.isBlank()) {
            out.append("[script file: ").append(file.trim()).append("]\n");
        }
        out.append(script);
        return out.toString();
    }
}
