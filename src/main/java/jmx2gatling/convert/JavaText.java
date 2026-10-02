package jmx2gatling.convert;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns arbitrary text from a JMX file into Java source fragments that always compile.
 *
 * <p>Generated literals are ASCII-only (non-ASCII becomes {@code \}{@code uXXXX}), so the output
 * compiles whatever source encoding the Gatling build uses. Comments neutralize backslash-u
 * sequences, which javac would otherwise decode even inside comments.
 */
final class JavaText {

    private JavaText() {
    }

    /** A Java string literal, quotes included. */
    static String quote(String s) {
        StringBuilder out = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> appendEscaped(out, c);
            }
        }
        return out.append('"').toString();
    }

    /** A Java char literal, quotes included. */
    static String charLiteral(char c) {
        return switch (c) {
            case '\\' -> "'\\\\'";
            case '\'' -> "'\\''";
            case '\t' -> "'\\t'";
            default -> {
                StringBuilder out = new StringBuilder("'");
                appendEscaped(out, c);
                yield out.append('\'').toString();
            }
        };
    }

    /**
     * False when a line ends in whitespace other than space or tab: javac decodes the escape for it
     * before stripping trailing whitespace from text-block lines, so a text block would lose it.
     */
    static boolean textBlockSafe(String s) {
        for (String line : s.split("\n", -1)) {
            if (!line.isEmpty()) {
                char last = line.charAt(line.length() - 1);
                // Space, tab and CR are written as \s, \t and \r, which javac decodes after stripping.
                if (Character.isWhitespace(last) && last != ' ' && last != '\t' && last != '\r') {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * A Java text block for {@code s} whose content lines are indented by {@code indent}, with no
     * trailing newline added: the last line ends with a line continuation and the closing
     * delimiter sits alone on its own line, so incidental-indentation stripping removes exactly
     * {@code indent}.
     */
    static String textBlock(String s, String indent) {
        StringBuilder out = new StringBuilder("\"\"\"\n");
        String[] lines = s.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            out.append(indent).append(textBlockLine(lines[i]));
            out.append(i == lines.length - 1 ? "\\\n" : "\n");
        }
        return out.append(indent).append("\"\"\"").toString();
    }

    private static String textBlockLine(String line) {
        StringBuilder out = new StringBuilder();
        int quoteRun = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            quoteRun = c == '"' ? quoteRun + 1 : 0;
            switch (c) {
                case '\\' -> out.append("\\\\");
                // Escaping every third quote of a run means three unescaped quotes never meet.
                case '"' -> out.append(quoteRun % 3 == 0 ? "\\\"" : "\"");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append(i == line.length() - 1 ? "\\t" : "\t");
                case ' ' -> out.append(i == line.length() - 1 ? "\\s" : " ");
                default -> appendEscaped(out, c);
            }
        }
        return out.toString();
    }

    /** Text safe to place after {@code //}: one line, ASCII-only, no live backslash-u escapes. */
    static String comment(String s) {
        String oneLine = s.replace("\r\n", " ").replace('\r', ' ').replace('\n', ' ');
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < oneLine.length()) {
            char c = oneLine.charAt(i);
            if (c == '\\') {
                int run = 0;
                while (i < oneLine.length() && oneLine.charAt(i) == '\\') {
                    run++;
                    i++;
                }
                out.append("\\".repeat(run));
                // An odd run of backslashes before 'u' would start a unicode escape.
                if (run % 2 == 1 && i < oneLine.length() && oneLine.charAt(i) == 'u') {
                    out.append('\\');
                }
                continue;
            }
            appendEscaped(out, c);
            i++;
        }
        return out.toString();
    }

    /** {@link #comment} for each line of a multi-line text. */
    static List<String> commentLines(String s) {
        List<String> lines = new ArrayList<>();
        for (String line : s.replace("\r\n", "\n").split("\n", -1)) {
            lines.add(comment(line));
        }
        return lines;
    }

    private static void appendEscaped(StringBuilder out, char c) {
        if (c < 0x20 || c == 0x7f) {
            out.append(String.format("\\%03o", (int) c));
        } else if (c > 0x7f) {
            out.append(String.format("\\u%04x", (int) c));
        } else {
            out.append(c);
        }
    }
}
