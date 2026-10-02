package jmx2gatling.parse;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Splits a JMeter property value into literal text, {@code ${variable}} references and
 * {@code ${__function(args)}} calls, following JMeter's own rules:
 * <ul>
 *   <li>{@code \${} is a literal {@code ${}.</li>
 *   <li>Inside function arguments, {@code \,} is a literal comma and parentheses nest.</li>
 *   <li>{@code ${__name}} without parentheses is a call only when {@code __name} is a JMeter
 *       function; otherwise it is a variable (for example {@code ${__jm__Loop__idx}}).</li>
 * </ul>
 */
public final class JmeterExpression {

    public sealed interface Part permits Text, Var, Call {
    }

    public record Text(String text) implements Part {
    }

    public record Var(String name, String original) implements Part {
    }

    /** A function call. Arguments are unescaped, but nested {@code ${...}} references stay raw. */
    public record Call(String name, List<String> args, String original) implements Part {
    }

    /** JMeter 5.6 built-in function names (with the leading {@code __}). */
    public static final Set<String> BUILT_IN_FUNCTIONS = Set.of(
        "__BeanShell", "__changeCase", "__char", "__counter", "__CSVRead", "__dateTimeConvert", "__digest",
        "__escapeHtml", "__escapeOroRegexpChars", "__escapeXml", "__eval", "__evalVar", "__FileToString",
        "__groovy", "__intSum", "__isPropDefined", "__isVarDefined", "__javaScript", "__jexl2", "__jexl3",
        "__log", "__logn", "__longSum", "__machineIP", "__machineName", "__P", "__property", "__Random",
        "__RandomDate", "__RandomFromMultipleVars", "__RandomString", "__regexFunction", "__samplerName",
        "__setProperty", "__split", "__StringFromFile", "__StringToFile", "__TestPlanName", "__threadGroupName",
        "__threadNum", "__time", "__timeShift", "__unescape", "__unescapeHtml", "__urldecode", "__urlencode",
        "__UUID", "__V", "__XPath");

    private JmeterExpression() {
    }

    public static List<Part> parse(String s) {
        List<Part> parts = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            if (s.charAt(i) == '\\' && s.startsWith("${", i + 1)) {
                text.append("${");
                i += 3;
                continue;
            }
            if (s.startsWith("${", i)) {
                Parsed ref = parseReference(s, i);
                if (ref != null) {
                    if (text.length() > 0) {
                        parts.add(new Text(text.toString()));
                        text.setLength(0);
                    }
                    parts.add(ref.part);
                    i = ref.end;
                    continue;
                }
            }
            text.append(s.charAt(i));
            i++;
        }
        if (text.length() > 0) {
            parts.add(new Text(text.toString()));
        }
        return parts;
    }

    /** True when the text contains at least one variable reference or function call. */
    public static boolean hasReferences(String s) {
        return parse(s).stream().anyMatch(p -> !(p instanceof Text));
    }

    private record Parsed(Part part, int end) {
    }

    private static Parsed parseReference(String s, int start) {
        int nameStart = start + 2;
        if (s.startsWith("__", nameStart)) {
            int nameEnd = nameStart;
            while (nameEnd < s.length() && isNameChar(s.charAt(nameEnd))) {
                nameEnd++;
            }
            String name = s.substring(nameStart, nameEnd);
            if (nameEnd < s.length() && s.charAt(nameEnd) == '(') {
                int close = matchingParen(s, nameEnd);
                if (close < 0 || close + 1 >= s.length() || s.charAt(close + 1) != '}') {
                    return null;
                }
                String original = s.substring(start, close + 2);
                return new Parsed(new Call(name, splitArgs(s.substring(nameEnd + 1, close)), original), close + 2);
            }
            if (nameEnd < s.length() && s.charAt(nameEnd) == '}' && BUILT_IN_FUNCTIONS.contains(name)) {
                return new Parsed(new Call(name, List.of(), s.substring(start, nameEnd + 1)), nameEnd + 1);
            }
        }
        int close = s.indexOf('}', nameStart);
        if (close < 0) {
            return null;
        }
        String name = s.substring(nameStart, close);
        if (name.isEmpty() || name.contains("${")) {
            return null;
        }
        return new Parsed(new Var(name, s.substring(start, close + 1)), close + 1);
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /** Index of the ')' that closes the '(' at {@code open}, honouring backslash escapes. */
    private static int matchingParen(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static List<String> splitArgs(String raw) {
        List<String> args = new ArrayList<>();
        if (raw.isEmpty()) {
            return args;
        }
        StringBuilder current = new StringBuilder();
        int depth = 0;
        int i = 0;
        while (i < raw.length()) {
            char c = raw.charAt(i);
            if (raw.startsWith("${", i)) {
                Parsed nested = parseReference(raw, i);
                if (nested != null) {
                    current.append(raw, i, nested.end);
                    i = nested.end;
                    continue;
                }
            }
            if (c == '\\' && i + 1 < raw.length()) {
                current.append(raw.charAt(i + 1));
                i += 2;
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == ',' && depth == 0) {
                args.add(current.toString());
                current.setLength(0);
                i++;
                continue;
            }
            current.append(c);
            i++;
        }
        args.add(current.toString());
        return args;
    }
}
