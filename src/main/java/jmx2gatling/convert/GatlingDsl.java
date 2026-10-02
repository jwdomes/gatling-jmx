package jmx2gatling.convert;

import java.util.List;

/**
 * Every Gatling Java DSL call the converter emits, in one place.
 *
 * <p>Target: Gatling 3.16.0. Each group links the documentation page it was checked against;
 * all of them are also compiled against 3.16.0 by the compile-check build. When moving to a new
 * Gatling version, this is the file to review.
 */
final class GatlingDsl {

    static final String GATLING_VERSION = "3.16.0";

    private GatlingDsl() {
    }

    // Expression Language built-ins: https://docs.gatling.io/concepts/session/el/

    static final String EL_RANDOM_UUID = "#{randomUuid()}";
    static final String EL_CURRENT_TIME_MILLIS = "#{currentTimeMillis()}";
    static final String EL_USER_ID = "#{userId()}";
    /** A backslash makes Gatling treat {@code #{} literally. */
    static final String EL_ESCAPED_OPEN = "\\#{";

    static String elAttribute(String name) {
        return "#{" + name + "}";
    }

    /** Pattern syntax is java.time.format.DateTimeFormatter's. */
    static String elCurrentDate(String pattern) {
        return "#{currentDate(" + pattern + ")}";
    }

    /** Gatling's upper bound is exclusive. */
    static String elRandomInt(long min, long maxExclusive) {
        return "#{randomInt(" + min + "," + maxExclusive + ")}";
    }

    static String elRandomLong(long min, long maxExclusive) {
        return "#{randomLong(" + min + "," + maxExclusive + ")}";
    }

    // Injection and setUp: https://docs.gatling.io/concepts/injection/ and https://docs.gatling.io/concepts/simulation/

    static String atOnceUsers(String users) {
        return "atOnceUsers(" + users + ")";
    }

    static String rampUsers(String users, String duration) {
        return "rampUsers(" + users + ").during(" + duration + ")";
    }

    static String nothingFor(String duration) {
        return "nothingFor(" + duration + ")";
    }

    static String injectOpen(String scenario, List<String> steps) {
        return scenario + ".injectOpen(" + String.join(", ", steps) + ")";
    }

    static String andThen(String population, List<String> children) {
        return population + "\n    .andThen(" + String.join(", ", children) + ")";
    }

    static String protocols(String protocol) {
        return ".protocols(" + protocol + ")";
    }

    static String maxDuration(String duration) {
        return ".maxDuration(" + duration + ")";
    }

    static String seconds(String amount) {
        return "Duration.ofSeconds(" + amount + ")";
    }

    static String millis(String amount) {
        return "Duration.ofMillis(" + amount + ")";
    }

    // Scenario structure: https://docs.gatling.io/concepts/scenario/
    // Block heads are completed by the writer with "(" children ")".

    static String repeat(String times) {
        return "repeat(" + times + ").on";
    }

    static final String FOREVER = "forever().on";

    static String during(String duration) {
        return "during(" + duration + ").on";
    }

    static String asLongAs(String condition) {
        return "asLongAs(" + condition + ").on";
    }

    static String doIf(String condition) {
        return "doIf(" + condition + ").then";
    }

    static String foreach(String sequenceEl, String attribute) {
        return "foreach(" + JavaText.quote(sequenceEl) + ", " + JavaText.quote(attribute) + ").on";
    }

    static String group(String nameEl) {
        return "group(" + JavaText.quote(nameEl) + ").on";
    }

    static final String UNIFORM_RANDOM_SWITCH = "uniformRandomSwitch().on";
    static final String RANDOM_SWITCH = "randomSwitch().on";

    static String percent(double percentage) {
        return "percent(" + percentage + ").then";
    }

    /** Wraps several executables into one, for places that take a single chain. */
    static String exec(List<String> executables) {
        return "exec(" + String.join(", ", executables) + ")";
    }

    static String pause(String duration) {
        return "pause(" + duration + ")";
    }

    static String pause(String min, String max) {
        return "pause(" + min + ", " + max + ")";
    }

    static final String EXIT_HERE_IF_FAILED = "exitHereIfFailed()";
    static final String NO_OP = "exec(session -> session)";

    /** {@code exec(session -> session.set(k1, v1).set(k2, v2))}; values are Java expressions. */
    static String sessionSet(List<String[]> keyValues) {
        StringBuilder out = new StringBuilder("exec(session -> session");
        for (String[] kv : keyValues) {
            out.append("\n    .set(").append(JavaText.quote(kv[0])).append(", ").append(kv[1]).append(')');
        }
        return out.append(')').toString();
    }

    static String sessionFunction(String body) {
        return "exec(session -> " + body + ")";
    }

    static String feed(String feeder) {
        return "feed(" + feeder + ")";
    }

    // Feeders: https://docs.gatling.io/concepts/session/feeders/

    /** {@code fileExpr} is a Java String expression (a quoted literal or a property constant). */
    static String csvFeeder(String fileExpr, char separator) {
        return switch (separator) {
            case ',' -> "csv(" + fileExpr + ")";
            case '\t' -> "tsv(" + fileExpr + ")";
            default -> "separatedValues(" + fileExpr + ", " + JavaText.charLiteral(separator) + ")";
        };
    }

    static final String FEEDER_CIRCULAR = ".circular()";
    static final String FEEDER_QUEUE = ".queue()";
    static final String FEEDER_TYPE = "FeederBuilder<String>";

    // HTTP protocol and requests: https://docs.gatling.io/reference/script/http/protocol/ and .../http/request/

    static String protocolBaseUrl(String urlExpr) {
        return ".baseUrl(" + urlExpr + ")";
    }

    static final String PROTOCOL_DISABLE_CACHING = ".disableCaching()";
    /** Gatling otherwise sends a warm-up request to gatling.io, which JMeter never did. */
    static final String PROTOCOL_DISABLE_WARM_UP = ".disableWarmUp()";

    static String httpRequest(String nameEl, String method, String urlExpr) {
        String lower = method.toLowerCase(java.util.Locale.ROOT);
        return switch (lower) {
            case "get", "post", "put", "delete", "patch", "head", "options" ->
                "http(" + JavaText.quote(nameEl) + ")." + lower + "(" + urlExpr + ")";
            default -> "http(" + JavaText.quote(nameEl) + ").httpRequest(" + JavaText.quote(method) + ", " + urlExpr + ")";
        };
    }

    static String header(String name, String valueEl) {
        return ".header(" + JavaText.quote(name) + ", " + JavaText.quote(valueEl) + ")";
    }

    static String queryParam(String nameEl, String valueEl) {
        return ".queryParam(" + JavaText.quote(nameEl) + ", " + JavaText.quote(valueEl) + ")";
    }

    static String formParam(String nameEl, String valueEl) {
        return ".formParam(" + JavaText.quote(nameEl) + ", " + JavaText.quote(valueEl) + ")";
    }

    static String stringBody(String literal) {
        return ".body(StringBody(" + literal + "))";
    }

    static String elFileBody(String resourcePath) {
        return ".body(ElFileBody(" + JavaText.quote(resourcePath) + "))";
    }

    static String rawFileBody(String path) {
        return ".body(RawFileBody(" + JavaText.quote(path) + "))";
    }

    static String rawFileBodyPart(String partName, String path, String contentType, String fileName) {
        StringBuilder out = new StringBuilder(".bodyPart(RawFileBodyPart(").append(JavaText.quote(partName)).append(", ")
            .append(JavaText.quote(path)).append(')');
        if (!contentType.isEmpty()) {
            out.append(".contentType(").append(JavaText.quote(contentType)).append(')');
        }
        return out.append(".fileName(").append(JavaText.quote(fileName)).append("))").toString();
    }

    static final String AS_MULTIPART_FORM = ".asMultipartForm()";
    static final String DISABLE_FOLLOW_REDIRECT = ".disableFollowRedirect()";

    static final String FLUSH_COOKIE_JAR = "exec(flushCookieJar())";

    static String addCookie(String name, String valueEl, String domain, String path) {
        StringBuilder out = new StringBuilder("exec(addCookie(Cookie(").append(JavaText.quote(name)).append(", ")
            .append(JavaText.quote(valueEl)).append(')');
        if (!domain.isEmpty()) {
            out.append(".withDomain(").append(JavaText.quote(domain)).append(')');
        }
        if (!path.isEmpty()) {
            out.append(".withPath(").append(JavaText.quote(path)).append(')');
        }
        return out.append("))").toString();
    }

    // Checks: https://docs.gatling.io/concepts/checks/ and https://docs.gatling.io/reference/script/http/checks/

    static String check(String check) {
        return ".check(" + check + ")";
    }

    static String statusIs(int code) {
        return "status().is(" + code + ")";
    }

    static String statusNot(int code) {
        return "status().not(" + code + ")";
    }

    static String statusIn(List<Integer> codes) {
        return "status().in(" + String.join(", ", codes.stream().map(String::valueOf).toList()) + ")";
    }

    static String statusMatches(String regex, boolean expected) {
        return "status().transform(code -> String.valueOf(code).matches(" + JavaText.quote(regex) + ")).is(" + expected + ")";
    }

    static String substring(String el) {
        return "substring(" + JavaText.quote(el) + ")";
    }

    static String regex(String patternExpr) {
        return "regex(" + patternExpr + ")";
    }

    static final String BODY_STRING = "bodyString()";

    static String header(String name) {
        return "header(" + JavaText.quote(name) + ")";
    }

    static String headerRegex(String name, String patternExpr) {
        return "headerRegex(" + JavaText.quote(name) + ", " + patternExpr + ")";
    }

    static String currentLocationRegex(String patternExpr) {
        return "currentLocationRegex(" + patternExpr + ")";
    }

    static String jsonPath(String el) {
        return "jsonPath(" + JavaText.quote(el) + ")";
    }

    /** Pattern.quote of a literal, for boundary extractors. Requires the Pattern import. */
    static String patternQuote(String el) {
        return "Pattern.quote(" + JavaText.quote(el) + ")";
    }

    static final String FIND = ".find()";
    static final String FIND_ALL = ".findAll()";
    static final String FIND_RANDOM = ".findRandom()";

    /** {@code index} is 0-based. */
    static String find(int index) {
        return ".find(" + index + ")";
    }

    /** A literal default value. */
    static String withDefault(String value) {
        return ".withDefault(" + JavaText.quote(value) + ")";
    }

    /** A default value that references the session. */
    static String withDefaultEl(String valueEl) {
        return ".withDefaultEl(" + JavaText.quote(valueEl) + ")";
    }

    static final String OPTIONAL = ".optional()";
    static final String EXISTS = ".exists()";
    static final String NOT_EXISTS = ".notExists()";

    static String saveAs(String attribute) {
        return ".saveAs(" + JavaText.quote(attribute) + ")";
    }

    /** {@code is(...)}, or {@code isEL(...)} when the expected value references the session. */
    static String is(String expectedEl) {
        return (expectedEl.contains("#{") ? ".isEL(" : ".is(") + JavaText.quote(expectedEl) + ")";
    }

    static String not(String expectedEl) {
        return (expectedEl.contains("#{") ? ".notEL(" : ".not(") + JavaText.quote(expectedEl) + ")";
    }

    static String matches(String regex, boolean expected) {
        return ".transform(value -> value.matches(" + JavaText.quote(regex) + ")).is(" + expected + ")";
    }
}
