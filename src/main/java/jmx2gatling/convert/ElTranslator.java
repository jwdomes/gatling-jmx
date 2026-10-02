package jmx2gatling.convert;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import jmx2gatling.model.JmxElement;
import jmx2gatling.parse.JmeterExpression;
import jmx2gatling.parse.JmeterExpression.Call;
import jmx2gatling.parse.JmeterExpression.Part;
import jmx2gatling.parse.JmeterExpression.Text;
import jmx2gatling.parse.JmeterExpression.Var;

/**
 * Translates JMeter values ({@code ${var}}, {@code ${__function(...)}}) into Gatling EL
 * ({@code #{var}}, EL built-ins, or session attributes filled by a session function just before
 * the request). Unsupported functions stay in the text verbatim and get a TODO.
 */
final class ElTranslator {

    static final Set<String> SCRIPT_FUNCTIONS = Set.of("__groovy", "__jexl3", "__jexl2", "__javaScript", "__BeanShell");

    /** Letters that mean the same in SimpleDateFormat (JMeter) and DateTimeFormatter (Gatling). */
    private static final String SAFE_DATE_LETTERS = "yMdHhmsaE";

    private final Conversion c;
    private final StaticValues statics;

    ElTranslator(Conversion c, StaticValues statics) {
        this.c = c;
        this.statics = statics;
    }

    /** Gatling EL for {@code text}; steps it needs go to {@code notes.before}, TODO lines to {@code notes.comments}. */
    String toEl(String text, JmxElement owner, StepNotes notes) {
        StringBuilder out = new StringBuilder();
        for (Part part : JmeterExpression.parse(text)) {
            if (part instanceof Text t) {
                out.append(escape(t.text()));
            } else if (part instanceof Var v) {
                out.append(variable(v, owner, notes));
            } else if (part instanceof Call call) {
                out.append(call(call, owner, notes));
            }
        }
        return out.toString();
    }

    /** Makes literal text safe in Gatling EL. */
    static String escape(String literal) {
        return literal.replace("#{", GatlingDsl.EL_ESCAPED_OPEN);
    }

    private String variable(Var v, JmxElement owner, StepNotes notes) {
        if (v.name().startsWith("__jm__") || v.name().equals("JMeterThread.last_sample_ok")) {
            notes.comments.addAll(c.todo(owner, "JMeter built-in variable " + v.original() + " has no direct Gatling equivalent; kept verbatim", v.original()));
            return escape(v.original());
        }
        c.referenceVariable(v.name(), owner);
        return GatlingDsl.elAttribute(c.attribute(v.name(), owner));
    }

    private String call(Call call, JmxElement owner, StepNotes notes) {
        List<String> args = call.args();
        switch (call.name()) {
            case "__UUID":
                return GatlingDsl.EL_RANDOM_UUID;
            case "__time":
                return time(call, owner, notes);
            case "__Random":
                return random(call, owner, notes);
            case "__RandomString":
                return randomString(call, owner, notes);
            case "__threadNum":
                c.approximate(owner, "`${__threadNum}` → `#{userId()}`",
                    "JMeter numbers threads from 1 within each thread group; Gatling's user id is unique across the whole simulation.");
                return GatlingDsl.EL_USER_ID;
            case "__counter":
                return counter(call, owner, notes);
            case "__P":
            case "__property": {
                if (call.name().equals("__property") && args.size() > 1 && !args.get(1).isBlank()) {
                    return unsupported(call, owner, notes, "__property with a variable name argument (it also stores the value in a variable)");
                }
                Optional<String> constant = statics.propertyAsString(call, owner);
                if (constant.isEmpty()) {
                    return unsupported(call, owner, notes, "Property name or default is not a literal");
                }
                String attribute = Conversion.INTERNAL_PREFIX + constant.get();
                c.propertyAttributes.put(attribute, constant.get());
                return GatlingDsl.elAttribute(attribute);
            }
            default:
                if (SCRIPT_FUNCTIONS.contains(call.name())) {
                    return unsupported(call, owner, notes, "Script function " + call.name() + " not converted; kept verbatim");
                }
                return unsupported(call, owner, notes, "Function " + call.name() + " not supported; kept verbatim");
        }
    }

    private String unsupported(Call call, JmxElement owner, StepNotes notes, String reason) {
        notes.comments.addAll(c.todo(owner, reason, call.original()));
        return escape(call.original());
    }

    private String time(Call call, JmxElement owner, StepNotes notes) {
        String format = arg(call, 0);
        String variable = arg(call, 1).trim();
        switch (format) {
            case "YMD" -> format = "yyyyMMdd";
            case "HMS" -> format = "HHmmss";
            case "YMDHMS" -> format = "yyyyMMdd-HHmmss";
            default -> { }
        }
        if (JmeterExpression.hasReferences(format) || format.equals("USER1") || format.equals("USER2")) {
            return unsupported(call, owner, notes, "__time with a dynamic or property-defined format");
        }
        boolean divisor = format.matches("/0*[1-9]\\d{0,17}");
        if (variable.isEmpty() && !divisor) {
            if (format.isEmpty()) {
                return GatlingDsl.EL_CURRENT_TIME_MILLIS;
            }
            if (isSafeDatePattern(format)) {
                return GatlingDsl.elCurrentDate(format);
            }
        }
        String expr;
        if (format.isEmpty()) {
            expr = "String.valueOf(System.currentTimeMillis())";
        } else if (divisor) {
            expr = "String.valueOf(System.currentTimeMillis() / " + Long.parseLong(format.substring(1)) + "L)";
        } else {
            expr = "new java.text.SimpleDateFormat(" + JavaText.quote(format) + ").format(new java.util.Date())";
        }
        return sessionValue(expr, variable, "__time", owner, notes);
    }

    private String random(Call call, JmxElement owner, StepNotes notes) {
        String min = arg(call, 0).trim();
        String max = arg(call, 1).trim();
        String variable = arg(call, 2).trim();
        if (!min.matches("-?\\d{1,18}") || !max.matches("-?\\d{1,18}")) {
            return unsupported(call, owner, notes, "__Random with non-literal bounds");
        }
        long lo = Long.parseLong(min);
        long hi = Long.parseLong(max);
        if (variable.isEmpty()) {
            boolean fitsInt = lo >= Integer.MIN_VALUE && hi < Integer.MAX_VALUE;
            // JMeter's upper bound is inclusive, Gatling's exclusive.
            return fitsInt ? GatlingDsl.elRandomInt(lo, hi + 1) : GatlingDsl.elRandomLong(lo, hi + 1);
        }
        String expr = "String.valueOf(java.util.concurrent.ThreadLocalRandom.current().nextLong(" + lo + "L, " + hi + "L + 1))";
        return sessionValue(expr, variable, "__Random", owner, notes);
    }

    private String randomString(Call call, JmxElement owner, StepNotes notes) {
        String length = arg(call, 0).trim();
        String chars = arg(call, 1);
        String variable = arg(call, 2).trim();
        if (!length.matches("\\d{1,6}") || JmeterExpression.hasReferences(chars)) {
            return unsupported(call, owner, notes, "__RandomString with non-literal arguments");
        }
        if (chars.isEmpty()) {
            c.approximate(owner, "`__RandomString` without a character set uses letters and digits",
                "JMeter picks from all characters when no set is given; the converter uses A-Z, a-z and 0-9.");
            chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        }
        c.helpers.add(Conversion.Helper.RANDOM_STRING);
        return sessionValue("randomString(" + Integer.parseInt(length) + ", " + JavaText.quote(chars) + ")", variable, "__RandomString", owner, notes);
    }

    private String counter(Call call, JmxElement owner, StepNotes notes) {
        boolean perUser = !arg(call, 0).trim().equalsIgnoreCase("FALSE");
        String variable = arg(call, 1).trim();
        String attribute = c.internalAttribute("counter");
        String valueAttribute = variable.isEmpty() ? attribute + "_value" : c.attribute(variable, owner);
        String code;
        if (perUser) {
            code = "exec(session -> {\n"
                + "    long next = session.contains(" + JavaText.quote(attribute) + ") ? session.getLong(" + JavaText.quote(attribute) + ") + 1 : 1;\n"
                + "    return session.set(" + JavaText.quote(attribute) + ", next).set(" + JavaText.quote(valueAttribute) + ", String.valueOf(next));\n"
                + "})";
        } else {
            String counter = c.constants.add("counter:" + attribute, "COUNTER", "java.util.concurrent.atomic.AtomicLong",
                "new java.util.concurrent.atomic.AtomicLong()", "Global counter for " + call.original());
            code = GatlingDsl.sessionFunction("session.set(" + JavaText.quote(valueAttribute) + ", String.valueOf(" + counter + ".incrementAndGet()))");
        }
        if (!variable.isEmpty()) {
            c.defineVariable(variable);
        }
        notes.before.add(new Step.Action(List.of(call.original() + " for the request below"), code));
        return GatlingDsl.elAttribute(valueAttribute);
    }

    /** Computes {@code javaExpr} in a session function before the request and returns its EL reference. */
    private String sessionValue(String javaExpr, String variable, String function, JmxElement owner, StepNotes notes) {
        String attribute = variable.isEmpty() ? c.internalAttribute("fn") : c.attribute(variable, owner);
        if (!variable.isEmpty()) {
            c.defineVariable(variable);
        }
        notes.before.add(new Step.Action(List.of(function + " for the request below"),
            GatlingDsl.sessionFunction("session.set(" + JavaText.quote(attribute) + ", " + javaExpr + ")")));
        return GatlingDsl.elAttribute(attribute);
    }

    static boolean isSafeDatePattern(String pattern) {
        if (pattern.matches(".*[(){}#,].*")) {
            return false;
        }
        boolean quoted = false;
        for (int i = 0; i < pattern.length(); i++) {
            char ch = pattern.charAt(i);
            if (ch == '\'') {
                quoted = !quoted;
            } else if (!quoted && Character.isLetter(ch)) {
                if (ch == 'S') {
                    int run = 1;
                    while (i + run < pattern.length() && pattern.charAt(i + run) == 'S') {
                        run++;
                    }
                    if (run != 3) {
                        return false;
                    }
                    i += run - 1;
                } else if (SAFE_DATE_LETTERS.indexOf(ch) < 0) {
                    return false;
                }
            }
        }
        return !quoted;
    }

    private static String arg(Call call, int index) {
        return call.args().size() > index ? call.args().get(index) : "";
    }
}
