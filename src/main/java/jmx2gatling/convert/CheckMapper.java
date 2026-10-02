package jmx2gatling.convert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jmx2gatling.inventory.ElementKind;
import jmx2gatling.inventory.SupportMatrix;
import jmx2gatling.model.JmxElement;
import jmx2gatling.model.Property;
import jmx2gatling.model.Props;
import jmx2gatling.parse.JmeterExpression;

/**
 * Turns the extractors and assertions in a sampler's scope into Gatling checks. Extractors come
 * first, as JMeter runs post-processors before assertions.
 */
final class CheckMapper {

    private static final Pattern HTTP_CODE = Pattern.compile("\\d{3}");
    private static final Pattern HEADER_LINE = Pattern.compile("([A-Za-z0-9-]+)\\s*:\\s*(.*)");
    private static final Pattern REGEX_META = Pattern.compile("[\\\\^$.|?*+()\\[\\]{}]");

    private final Conversion c;
    private final ElTranslator el;

    CheckMapper(Conversion c, ElTranslator el) {
        this.c = c;
        this.el = el;
    }

    void addChecks(JmxElement sampler, Scope scope, RequestSpec spec, StepNotes notes) {
        for (JmxElement post : scope.all(ElementKind.POST_PROCESSOR)) {
            c.appliedElements.add(post);
            if (appliesToSampleAndSubSamplesOnly(post, notes)) {
                postProcessor(post, spec, notes);
            }
        }
        for (JmxElement assertion : scope.all(ElementKind.ASSERTION)) {
            c.appliedElements.add(assertion);
            if (appliesToSampleAndSubSamplesOnly(assertion, notes)) {
                assertion(assertion, spec, notes);
            }
        }
    }

    /** "Apply to: JMeter variable" has no Gatling equivalent; main sample (the default) maps directly. */
    private boolean appliesToSampleAndSubSamplesOnly(JmxElement e, StepNotes notes) {
        // Extractors store this as Sample.scope, assertions as Assertion.scope.
        String scope = e.props().string(e.props().has("Assertion.scope") ? "Assertion.scope" : "Sample.scope").trim();
        if (scope.isEmpty() || scope.equals("parent")) {
            return true;
        }
        if (scope.equals("variable")) {
            notes.comments.addAll(c.todo(e, "Applies to JMeter variable `" + e.props().string("Scope.variable") + "` instead of the response", ""));
            return false;
        }
        c.approximate(e, "\"Apply to: " + scope + "\" treated as the main sample",
            "Gatling checks apply to the response only, not to redirect or embedded-resource sub-samples.");
        return true;
    }

    private void postProcessor(JmxElement e, RequestSpec spec, StepNotes notes) {
        switch (e.testClass()) {
            case "JSONPostProcessor" -> jsonExtractor(e, spec, notes);
            case "RegexExtractor" -> regexExtractor(e, spec, notes);
            case "BoundaryExtractor" -> boundaryExtractor(e, spec, notes);
            case "DebugPostProcessor" -> c.ignore(e, "Debug PostProcessor has no load effect");
            default -> unsupported(e, notes);
        }
    }

    private void assertion(JmxElement e, RequestSpec spec, StepNotes notes) {
        switch (e.testClass()) {
            case "ResponseAssertion" -> responseAssertion(e, spec, notes);
            case "JSONPathAssertion" -> jsonPathAssertion(e, spec, notes);
            default -> unsupported(e, notes);
        }
    }

    private void unsupported(JmxElement e, StepNotes notes) {
        if (Scripts.isScripted(e)) {
            notes.comments.addAll(c.todo(e, "Script not converted", Scripts.of(e)));
        } else {
            notes.comments.addAll(c.todo(e, SupportMatrix.typeOf(e) + " not supported", ""));
        }
    }

    // Extractors

    private void jsonExtractor(JmxElement e, RequestSpec spec, StepNotes notes) {
        Props p = e.props();
        List<String> names = split(p.string("JSONPostProcessor.referenceNames"));
        List<String> paths = split(p.string("JSONPostProcessor.jsonPathExprs"));
        List<String> matchNumbers = split(p.string("JSONPostProcessor.match_numbers"));
        List<String> defaults = split(p.string("JSONPostProcessor.defaultValues"));
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i).trim();
            if (name.isEmpty()) {
                continue;
            }
            String path = i < paths.size() ? paths.get(i).trim() : "";
            if (path.contains("?(")) {
                c.approximate(e, "JSONPath filter in `" + path + "`",
                    "JMeter uses Jayway JsonPath and Gatling its own implementation; filter expressions can behave differently.");
            }
            String find = find(e, i < matchNumbers.size() ? matchNumbers.get(i) : "", name, notes);
            String defaultValue = i < defaults.size() ? defaults.get(i) : "";
            String recover = find.equals(GatlingDsl.FIND_ALL) ? GatlingDsl.OPTIONAL : defaultValue(defaultValue, e, notes);
            spec.checks.add(GatlingDsl.jsonPath(el.toEl(path, e, notes)) + find + recover + GatlingDsl.saveAs(save(name, e, find)));
        }
    }

    private void regexExtractor(JmxElement e, RequestSpec spec, StepNotes notes) {
        Props p = e.props();
        String template = p.string("RegexExtractor.template").trim();
        if (!template.equals("$1$")) {
            notes.comments.addAll(c.todo(e, "Regex template " + template + " not supported (only $1$)", p.string("RegexExtractor.regex")));
            return;
        }
        String pattern = JavaText.quote(el.toEl(p.string("RegexExtractor.regex"), e, notes));
        extractor(e, "RegexExtractor", GatlingDsl.regex(pattern), pattern, spec, notes);
    }

    private void boundaryExtractor(JmxElement e, RequestSpec spec, StepNotes notes) {
        Props p = e.props();
        c.usesPattern = true;
        String pattern = "\"(?s)\" + " + GatlingDsl.patternQuote(el.toEl(p.string("BoundaryExtractor.lboundary"), e, notes))
            + " + \"(.*?)\" + " + GatlingDsl.patternQuote(el.toEl(p.string("BoundaryExtractor.rboundary"), e, notes));
        extractor(e, "BoundaryExtractor", GatlingDsl.regex(pattern), pattern, spec, notes);
    }

    /** Shared by the regex and boundary extractors, whose properties only differ by prefix. */
    private void extractor(JmxElement e, String prefix, String bodyCheck, String pattern, RequestSpec spec, StepNotes notes) {
        Props p = e.props();
        String source = p.string(prefix + ".useHeaders").trim();
        String check;
        switch (source) {
            case "", "false" -> check = bodyCheck;
            case "unescaped" -> {
                c.approximate(e, "\"Body (unescaped)\" read as the raw body", "HTML entities in the response are not decoded before matching.");
                check = bodyCheck;
            }
            case "URL" -> check = GatlingDsl.currentLocationRegex(pattern);
            default -> {
                notes.comments.addAll(c.todo(e, "Extracting from \"" + source + "\" is not supported (only the response body and URL)", ""));
                return;
            }
        }
        String name = p.string(prefix + ".refname").trim();
        String find = find(e, p.string(prefix + ".match_number"), name, notes);
        String defaultValue = p.string(prefix + ".default");
        String recover;
        if (find.equals(GatlingDsl.FIND_ALL)) {
            recover = GatlingDsl.OPTIONAL;
        } else if (!defaultValue.isEmpty() || p.bool(prefix + ".default_empty_value", false)) {
            recover = defaultValue(defaultValue, e, notes);
        } else {
            // JMeter leaves the variable unchanged when nothing matches and no default is set.
            recover = GatlingDsl.OPTIONAL;
        }
        spec.checks.add(check + find + recover + GatlingDsl.saveAs(save(name, e, find)));
    }

    /** withDefault takes a plain value, so EL is only used when the default references variables. */
    private String defaultValue(String jmeterDefault, JmxElement e, StepNotes notes) {
        return JmeterExpression.hasReferences(jmeterDefault)
            ? GatlingDsl.withDefaultEl(el.toEl(jmeterDefault, e, notes))
            : GatlingDsl.withDefault(jmeterDefault);
    }

    /** JMeter match number → Gatling find: 1 → find(), n → find(n-1), 0 or blank → findRandom(), -1 → findAll(). */
    private String find(JmxElement e, String matchNumber, String name, StepNotes notes) {
        String trimmed = matchNumber.trim();
        if (trimmed.isEmpty() || trimmed.equals("0")) {
            return GatlingDsl.FIND_RANDOM;
        }
        if (!trimmed.matches("-?\\d{1,9}")) {
            notes.comments.addAll(c.todo(e, "Match number `" + trimmed + "` for `" + name + "` is not a literal integer in range; using the first match", ""));
            return GatlingDsl.FIND;
        }
        int n = Integer.parseInt(trimmed);
        if (n < 0) {
            notes.comments.addAll(c.todo(e, "Match number -1 for `" + name + "`: Gatling saves all matches as one list, while JMeter creates "
                + name + "_1.." + name + "_N and " + name + "_matchNr", ""));
            return GatlingDsl.FIND_ALL;
        }
        return n == 1 ? GatlingDsl.FIND : GatlingDsl.find(n - 1);
    }

    private String save(String name, JmxElement e, String find) {
        c.defineVariable(name);
        if (find.equals(GatlingDsl.FIND_ALL)) {
            c.listVariables.add(name);
        }
        return c.attribute(name, e);
    }

    // Assertions

    private void responseAssertion(JmxElement e, RequestSpec spec, StepNotes notes) {
        Props p = e.props();
        String field = p.string("Assertion.test_field", "Assertion.response_data").trim();
        ResponseAssertionRule rule = ResponseAssertionRule.decode(p.integer("Assertion.test_type", ResponseAssertionRule.CONTAINS));
        List<String> patterns = patterns(p);
        if (patterns.isEmpty()) {
            c.ignore(e, "Response Assertion without patterns always passes");
            return;
        }
        if (p.bool("Assertion.assume_success", false)) {
            c.approximate(e, "\"Ignore status\" not converted",
                "Gatling still fails non-2xx/3xx responses unless the request also has a status() check.");
        }
        if (rule.or() && patterns.size() > 1) {
            List<Integer> codes = new ArrayList<>();
            boolean allCodes = field.equals("Assertion.response_code") && !rule.not() && rule.match() != ResponseAssertionRule.Match.CONTAINS
                && patterns.stream().allMatch(s -> HTTP_CODE.matcher(s.trim()).matches());
            if (allCodes) {
                patterns.forEach(s -> codes.add(Integer.parseInt(s.trim())));
                spec.checks.add(GatlingDsl.statusIn(codes));
            } else {
                notes.comments.addAll(c.todo(e, "Or across patterns: Gatling checks are all required (AND)", String.join("\n", patterns)));
            }
            return;
        }
        for (String pattern : patterns) {
            switch (field) {
                case "Assertion.response_code" -> statusCheck(e, rule, pattern, spec, notes);
                case "Assertion.response_data" -> bodyCheck(e, rule, pattern, spec, notes);
                case "Assertion.response_headers" -> headerCheck(e, rule, pattern, spec, notes);
                default -> notes.comments.addAll(c.todo(e, "Response Assertion on " + field + " is not supported", pattern));
            }
        }
    }

    private void statusCheck(JmxElement e, ResponseAssertionRule rule, String pattern, RequestSpec spec, StepNotes notes) {
        String trimmed = pattern.trim();
        if (HTTP_CODE.matcher(trimmed).matches()) {
            int code = Integer.parseInt(trimmed);
            spec.checks.add(rule.not() ? GatlingDsl.statusNot(code) : GatlingDsl.statusIs(code));
            return;
        }
        if (JmeterExpression.hasReferences(trimmed)) {
            notes.comments.addAll(c.todo(e, "Response code pattern uses variables", pattern));
            return;
        }
        String regex = switch (rule.match()) {
            case MATCHES -> trimmed;
            case CONTAINS -> ".*(?:" + trimmed + ").*";
            case SUBSTRING -> ".*" + Pattern.quote(trimmed) + ".*";
            case EQUALS -> Pattern.quote(trimmed);
        };
        spec.checks.add(GatlingDsl.statusMatches(regex, !rule.not()));
    }

    private void bodyCheck(JmxElement e, ResponseAssertionRule rule, String pattern, RequestSpec spec, StepNotes notes) {
        String value = el.toEl(pattern, e, notes);
        String validate = rule.not() ? GatlingDsl.NOT_EXISTS : "";
        switch (rule.match()) {
            case SUBSTRING -> spec.checks.add(GatlingDsl.substring(value) + validate);
            case CONTAINS -> spec.checks.add(GatlingDsl.regex(JavaText.quote(value)) + validate);
            case MATCHES -> spec.checks.add(GatlingDsl.regex(JavaText.quote("\\A(?:" + value + ")\\z")) + validate);
            case EQUALS -> spec.checks.add(GatlingDsl.BODY_STRING + (rule.not() ? GatlingDsl.not(value) : GatlingDsl.is(value)));
        }
    }

    private void headerCheck(JmxElement e, ResponseAssertionRule rule, String pattern, RequestSpec spec, StepNotes notes) {
        if (JmeterExpression.hasReferences(pattern)) {
            notes.comments.addAll(c.todo(e, "Response header pattern uses variables", pattern));
            return;
        }
        String validate = rule.not() ? GatlingDsl.NOT_EXISTS : GatlingDsl.EXISTS;
        Matcher m = HEADER_LINE.matcher(pattern.trim());
        if (m.matches()) {
            String name = m.group(1);
            String value = m.group(2);
            String regex = switch (rule.match()) {
                case SUBSTRING -> Pattern.quote(value);
                case CONTAINS -> value;
                case MATCHES -> "\\A(?:" + value + ")\\z";
                case EQUALS -> null;
            };
            if (regex == null) {
                spec.checks.add(GatlingDsl.header(name) + (rule.not() ? GatlingDsl.not(value) : GatlingDsl.is(value)));
            } else {
                spec.checks.add(GatlingDsl.headerRegex(name, JavaText.quote(ElTranslator.escape(regex))) + validate);
            }
            return;
        }
        String trimmed = pattern.trim();
        boolean nameOnly = trimmed.matches("[A-Za-z0-9-]+")
            && (rule.match() == ResponseAssertionRule.Match.SUBSTRING || rule.match() == ResponseAssertionRule.Match.CONTAINS);
        if (nameOnly) {
            spec.checks.add(GatlingDsl.header(trimmed) + validate);
            c.approximate(e, "Header pattern `" + trimmed + "` checked as a header name",
                "JMeter searched the whole header block, so the text could also have matched inside another header's value.");
            return;
        }
        notes.comments.addAll(c.todo(e, "Response header pattern does not map to a single header", pattern));
    }

    private void jsonPathAssertion(JmxElement e, RequestSpec spec, StepNotes notes) {
        Props p = e.props();
        String path = GatlingDsl.jsonPath(el.toEl(p.string("JSON_PATH"), e, notes));
        boolean invert = p.bool("INVERT", false);
        if (!p.bool("JSONVALIDATION", false)) {
            spec.checks.add(path + (invert ? GatlingDsl.NOT_EXISTS : GatlingDsl.EXISTS));
            return;
        }
        if (p.bool("EXPECT_NULL", false)) {
            notes.comments.addAll(c.todo(e, "JSON Assertion expecting null is not supported", p.string("JSON_PATH")));
            return;
        }
        String expected = p.string("EXPECTED_VALUE");
        // ISREGEX defaults to true in JMeter, and the value must then match the whole result.
        if (p.bool("ISREGEX", true) && REGEX_META.matcher(expected).find()) {
            if (JmeterExpression.hasReferences(expected)) {
                notes.comments.addAll(c.todo(e, "JSON Assertion regex uses variables", expected));
                return;
            }
            spec.checks.add(path + GatlingDsl.matches(expected, !invert));
            return;
        }
        String value = el.toEl(expected, e, notes);
        spec.checks.add(path + (invert ? GatlingDsl.not(value) : GatlingDsl.is(value)));
    }

    private static List<String> patterns(Props p) {
        List<Property> items = p.has("Asserion.test_strings") ? p.collection("Asserion.test_strings") : p.collection("Assertion.test_strings");
        List<String> result = new ArrayList<>();
        for (Property item : items) {
            if (item instanceof Property.Value v) {
                result.add(v.text());
            }
        }
        return result;
    }

    private static List<String> split(String semicolonSeparated) {
        if (semicolonSeparated.isEmpty()) {
            return List.of();
        }
        return Arrays.asList(semicolonSeparated.split(";", -1));
    }
}
