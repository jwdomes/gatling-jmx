package jmx2gatling.convert;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Derives Java identifiers and file names from JMeter names, keeping each one unique. */
public final class Naming {

    private static final Set<String> JAVA_KEYWORDS = Set.of(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue",
        "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if",
        "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private",
        "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
        "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false", "null", "var",
        "record", "yield", "sealed", "permits");

    private final Set<String> used = new HashSet<>();

    /** Reserves a name that generated code uses literally, such as {@code httpProtocol}. */
    void reserve(String name) {
        used.add(name);
    }

    /** {@code login-flow.jmx} → {@code LoginFlow}; a leading digit gets a {@code Jmx} prefix. */
    public static String pascalCase(String text) {
        StringBuilder out = new StringBuilder();
        for (String word : words(text)) {
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        if (out.length() == 0) {
            out.append("Plan");
        }
        if (Character.isDigit(out.charAt(0))) {
            out.insert(0, "Jmx");
        }
        return out.toString();
    }

    /** {@code Login Flow} → {@code loginFlow}, unique within this naming scope. */
    String field(String text, String fallback) {
        List<String> words = words(text);
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            out.append(out.length() == 0 ? lowerFirst(word) : Character.toUpperCase(word.charAt(0)) + word.substring(1));
        }
        String name = out.length() == 0 ? fallback : out.toString();
        if (Character.isDigit(name.charAt(0))) {
            name = fallback + name;
        }
        if (JAVA_KEYWORDS.contains(name)) {
            name = name + "_";
        }
        return unique(name, "");
    }

    /** {@code think.time} → {@code THINK_TIME}, unique within this naming scope. */
    String constant(String text, String fallback) {
        String name = String.join("_", words(text)).toUpperCase(Locale.ROOT);
        if (name.isEmpty()) {
            name = fallback;
        }
        if (Character.isDigit(name.charAt(0))) {
            name = "P_" + name;
        }
        return unique(name, "_");
    }

    /** {@code POST /token} → {@code post-token}, unique within this naming scope. */
    String fileStem(String text, String fallback) {
        String name = String.join("-", words(text)).toLowerCase(Locale.ROOT);
        return unique(name.isEmpty() ? fallback : name, "-");
    }

    private String unique(String name, String separator) {
        String candidate = name;
        for (int i = 2; !used.add(candidate); i++) {
            candidate = name + separator + i;
        }
        return candidate;
    }

    /** ASCII letter/digit runs, also split where a lowercase letter or digit meets an uppercase one ({@code heartbeatMillis}). */
    static List<String> words(String text) {
        List<String> words = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean upper = c >= 'A' && c <= 'Z';
            if ((c >= 'a' && c <= 'z') || upper || (c >= '0' && c <= '9')) {
                char previous = current.length() == 0 ? ' ' : current.charAt(current.length() - 1);
                if (upper && ((previous >= 'a' && previous <= 'z') || (previous >= '0' && previous <= '9'))) {
                    words.add(current.toString());
                    current.setLength(0);
                }
                current.append(c);
            } else if (current.length() > 0) {
                words.add(current.toString());
                current.setLength(0);
            }
        }
        if (current.length() > 0) {
            words.add(current.toString());
        }
        return words;
    }

    private static String lowerFirst(String word) {
        // Keep all-caps words readable: "GET" → "get", "API" → "api".
        if (word.equals(word.toUpperCase(Locale.ROOT))) {
            return word.toLowerCase(Locale.ROOT);
        }
        return Character.toLowerCase(word.charAt(0)) + word.substring(1);
    }
}
