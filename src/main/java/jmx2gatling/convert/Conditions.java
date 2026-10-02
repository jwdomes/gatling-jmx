package jmx2gatling.convert;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jmx2gatling.model.JmxElement;
import jmx2gatling.parse.JmeterExpression;
import jmx2gatling.parse.JmeterExpression.Call;
import jmx2gatling.parse.JmeterExpression.Part;
import jmx2gatling.parse.JmeterExpression.Var;

/**
 * Converts the simple If/While Controller conditions to Gatling session lambdas:
 * {@code "${v}" == "literal"}, {@code "${v}" != "literal"} (either operand order, either quote),
 * {@code vars.get("v") == "literal"}, those wrapped in {@code __jexl3}/{@code __groovy}/..., and
 * {@code ${v}} alone. Anything else is left to the caller as a TODO.
 */
final class Conditions {

    enum Kind {
        /** If Controller, "Interpret Condition as Variable Expression" on: true when the value is "true". */
        IF_EXPRESSION,
        /** If Controller evaluating the condition as JavaScript (the old default). */
        IF_JAVASCRIPT,
        /** While Controller: loops until the value is "false". */
        WHILE
    }

    private static final String OP = "(===?|!==?)";
    private static final String LITERAL = "([\"'])([^\"'$\\\\]*)";
    private static final Pattern VAR_FIRST = Pattern.compile("([\"'])\\$\\{([^${}]+)\\}\\1\\s*" + OP + "\\s*" + LITERAL + "\\4");
    private static final Pattern LITERAL_FIRST = Pattern.compile(LITERAL + "\\1\\s*" + OP + "\\s*([\"'])\\$\\{([^${}]+)\\}\\4");
    private static final Pattern VARS_GET = Pattern.compile("vars\\.get\\(([\"'])([^\"']+)\\1\\)\\s*" + OP + "\\s*" + LITERAL + "\\4");

    static final String LAST_SAMPLE_OK = "JMeterThread.last_sample_ok";

    private final Conversion c;

    Conditions(Conversion c) {
        this.c = c;
    }

    Optional<String> toLambda(String condition, Kind kind, JmxElement owner) {
        String trimmed = condition.trim();
        List<Part> parts = JmeterExpression.parse(trimmed);
        if (parts.size() == 1 && parts.get(0) instanceof Call call && ElTranslator.SCRIPT_FUNCTIONS.contains(call.name())
                && (call.args().size() == 1 || call.args().size() == 2 && call.args().get(1).isBlank())) {
            return expression(call.args().get(0).trim(), kind, owner);
        }
        if (kind == Kind.IF_JAVASCRIPT) {
            return expression(trimmed, kind, owner);
        }
        // Variable-expression mode compares the evaluated text to "true"/"false", so only a lone variable is meaningful.
        if (parts.size() == 1 && parts.get(0) instanceof Var v) {
            return Optional.of("session -> " + truth(v.name(), kind, owner));
        }
        return Optional.empty();
    }

    private Optional<String> expression(String expr, Kind kind, JmxElement owner) {
        Matcher m = VAR_FIRST.matcher(expr);
        if (m.matches()) {
            return Optional.of(compare(m.group(2), m.group(3), m.group(5), owner));
        }
        m = LITERAL_FIRST.matcher(expr);
        if (m.matches()) {
            return Optional.of(compare(m.group(5), m.group(3), m.group(2), owner));
        }
        m = VARS_GET.matcher(expr);
        if (m.matches()) {
            return Optional.of(compare(m.group(2), m.group(3), m.group(5), owner));
        }
        List<Part> parts = JmeterExpression.parse(expr);
        if (parts.size() == 1 && parts.get(0) instanceof Var v) {
            return Optional.of("session -> " + truth(v.name(), kind, owner));
        }
        return Optional.empty();
    }

    private String compare(String variable, String operator, String literal, JmxElement owner) {
        String equals = JavaText.quote(literal) + ".equals(" + value(variable, owner) + ")";
        return "session -> " + (operator.startsWith("!") ? "!" : "") + equals;
    }

    private String truth(String variable, Kind kind, JmxElement owner) {
        if (variable.equals(LAST_SAMPLE_OK)) {
            approximateLastSampleOk(owner);
            return "!session.isFailed()";
        }
        String value = value(variable, owner);
        return kind == Kind.WHILE ? "!\"false\".equalsIgnoreCase(" + value + ")" : "\"true\".equalsIgnoreCase(" + value + ")";
    }

    private String value(String variable, JmxElement owner) {
        if (variable.equals(LAST_SAMPLE_OK)) {
            approximateLastSampleOk(owner);
            return "String.valueOf(!session.isFailed())";
        }
        c.referenceVariable(variable, owner);
        return "session.getString(" + JavaText.quote(c.attribute(variable, owner)) + ")";
    }

    private void approximateLastSampleOk(JmxElement owner) {
        c.approximate(owner, "`${" + LAST_SAMPLE_OK + "}` → `!session.isFailed()`",
            "JMeter looks at the last sample only; Gatling's failed flag stays set after any failure until the user exits a tryMax/exitBlockOnFail block.");
    }
}
