package jmx2gatling.convert;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import jmx2gatling.model.JmxElement;
import jmx2gatling.parse.JmeterExpression;
import jmx2gatling.parse.JmeterExpression.Call;
import jmx2gatling.parse.JmeterExpression.Part;
import jmx2gatling.parse.JmeterExpression.Text;
import jmx2gatling.parse.JmeterExpression.Var;

/**
 * Resolves JMeter values that must be known when the simulation class is built (thread counts,
 * durations, host names) into Java expressions. Literals, user-defined variables and
 * {@code __P}/{@code __property} calls resolve; anything that needs a running user does not.
 */
final class StaticValues {

    private static final int MAX_VARIABLE_DEPTH = 10;

    private final Conversion c;

    StaticValues(Conversion c) {
        this.c = c;
    }

    /** A piece of a String expression: literal text, or a Java expression such as a property constant. */
    private record Piece(boolean literal, String value) {
    }

    /** A Java String expression for {@code text}, or empty when it depends on runtime state. */
    Optional<String> string(String text, JmxElement owner) {
        return pieces(text, owner, 0).map(StaticValues::render);
    }

    private Optional<List<Piece>> pieces(String text, JmxElement owner, int depth) {
        List<Piece> pieces = new ArrayList<>();
        for (Part part : JmeterExpression.parse(text)) {
            if (part instanceof Text t) {
                pieces.add(new Piece(true, t.text()));
            } else if (part instanceof Var v) {
                Optional<List<Piece>> resolved = userDefinedVariable(v.name(), depth).flatMap(raw -> pieces(raw, owner, depth + 1));
                if (resolved.isEmpty()) {
                    return Optional.empty();
                }
                pieces.addAll(resolved.get());
            } else if (part instanceof Call call && isProperty(call)) {
                Optional<String> constant = propertyAsString(call, owner);
                if (constant.isEmpty()) {
                    return Optional.empty();
                }
                pieces.add(new Piece(false, constant.get()));
            } else {
                return Optional.empty();
            }
        }
        return Optional.of(pieces);
    }

    /** Joins pieces with " + ", merging neighbouring literals into one quoted string. */
    private static String render(List<Piece> pieces) {
        List<String> terms = new ArrayList<>();
        StringBuilder literal = new StringBuilder();
        boolean pendingLiteral = false;
        for (Piece p : pieces) {
            if (p.literal()) {
                literal.append(p.value());
                pendingLiteral = true;
            } else {
                if (pendingLiteral) {
                    terms.add(JavaText.quote(literal.toString()));
                    literal.setLength(0);
                    pendingLiteral = false;
                }
                terms.add(p.value());
            }
        }
        if (pendingLiteral || terms.isEmpty()) {
            terms.add(JavaText.quote(literal.toString()));
        }
        return String.join(" + ", terms);
    }

    /** A Java int expression for {@code text}, or empty when it is not a resolvable integer. */
    Optional<String> integer(String text, JmxElement owner) {
        return integer(text, owner, 0);
    }

    private Optional<String> integer(String text, JmxElement owner, int depth) {
        String trimmed = text.trim();
        if (trimmed.matches("-?\\d{1,9}")) {
            return Optional.of(String.valueOf(Integer.parseInt(trimmed)));
        }
        List<Part> parts = JmeterExpression.parse(trimmed);
        if (parts.size() != 1) {
            return Optional.empty();
        }
        if (parts.get(0) instanceof Var v) {
            return userDefinedVariable(v.name(), depth).flatMap(raw -> integer(raw, owner, depth + 1));
        }
        if (parts.get(0) instanceof Call call && isProperty(call)) {
            String name = call.args().isEmpty() ? "" : call.args().get(0).trim();
            String defaultValue = propertyDefault(call).trim();
            if (name.isEmpty() || !defaultValue.matches("-?\\d{1,9}")) {
                return Optional.empty();
            }
            recordProperty(owner, name, defaultValue);
            return Optional.of(c.constants.intProperty(name, Integer.parseInt(defaultValue)));
        }
        return Optional.empty();
    }

    /** The plain literal value of {@code text} after resolving user-defined variables, if it has no properties or functions. */
    Optional<String> literal(String text) {
        return literal(text, 0);
    }

    private Optional<String> literal(String text, int depth) {
        StringBuilder out = new StringBuilder();
        for (Part part : JmeterExpression.parse(text)) {
            if (part instanceof Text t) {
                out.append(t.text());
            } else if (part instanceof Var v) {
                Optional<String> resolved = userDefinedVariable(v.name(), depth).flatMap(raw -> literal(raw, depth + 1));
                if (resolved.isEmpty()) {
                    return Optional.empty();
                }
                out.append(resolved.get());
            } else {
                return Optional.empty();
            }
        }
        return Optional.of(out.toString());
    }

    /**
     * True for a loop count read from a property whose default is -1 (infinite). Gatling's repeat
     * runs zero times for a negative count, so such loops are converted as infinite and a TODO
     * says the property is no longer read. Other property counts get a note about negative values.
     */
    boolean isInfiniteLoopProperty(String loops, JmxElement owner, List<String> comments) {
        List<Part> parts = JmeterExpression.parse(loops.trim());
        if (parts.size() != 1 || !(parts.get(0) instanceof Call call) || !isProperty(call)) {
            return false;
        }
        String name = call.args().isEmpty() ? "" : call.args().get(0).trim();
        if (propertyDefault(call).trim().equals("-1")) {
            comments.addAll(c.todo(owner, "Loop count " + loops.trim() + " defaults to -1 (loop forever) and is converted as infinite;"
                + " -D" + name + " is not read. Replace with repeat(...) if you pass a count", loops.trim()));
            return true;
        }
        c.approximate(owner, "Loop count from property `" + name + "`",
            "A negative value (JMeter's \"infinite\") makes Gatling's repeat run zero times; pass a positive count.");
        return false;
    }

    static boolean isProperty(Call call) {
        return call.name().equals("__P") || call.name().equals("__property");
    }

    /** The constant for a {@code __P}/{@code __property} call, or empty when its name or default is dynamic. */
    Optional<String> propertyAsString(Call call, JmxElement owner) {
        String name = call.args().isEmpty() ? "" : call.args().get(0).trim();
        String defaultValue = propertyDefault(call);
        if (name.isEmpty() || JmeterExpression.hasReferences(name) || JmeterExpression.hasReferences(defaultValue)) {
            return Optional.empty();
        }
        recordProperty(owner, name, defaultValue);
        return Optional.of(c.constants.stringProperty(name, defaultValue));
    }

    /** JMeter's defaults: {@code __P(name)} is "1", {@code __property(name)} is the name itself. */
    private static String propertyDefault(Call call) {
        List<String> args = call.args();
        if (call.name().equals("__P")) {
            return args.size() > 1 ? args.get(1) : "1";
        }
        String name = args.isEmpty() ? "" : args.get(0).trim();
        return args.size() > 2 ? args.get(2) : name;
    }

    private void recordProperty(JmxElement owner, String name, String defaultValue) {
        c.approximate(owner, "JMeter property `" + name + "` read as Java system property `" + name + "`",
            "Pass `-D" + name + "=...` to Gatling instead of `-J" + name + "=...` to JMeter. Default when unset: `" + defaultValue + "`.");
    }

    private Optional<String> userDefinedVariable(String name, int depth) {
        if (depth >= MAX_VARIABLE_DEPTH) {
            return Optional.empty();
        }
        return Optional.ofNullable(c.userDefinedVariables.get(name));
    }
}
