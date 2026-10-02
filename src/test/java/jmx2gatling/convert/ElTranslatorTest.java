package jmx2gatling.convert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import jmx2gatling.model.JmxElement;
import jmx2gatling.model.Props;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ElTranslatorTest {

    private Conversion c;
    private ElTranslator el;
    private Conditions conditions;
    private StaticValues statics;
    private StepNotes notes;
    private final JmxElement owner = new JmxElement("HTTPSamplerProxy", "", "s", true, new Props(Map.of()));

    @BeforeEach
    void setUp() {
        c = new Conversion("bodies/x/", false);
        statics = new StaticValues(c);
        el = new ElTranslator(c, statics);
        conditions = new Conditions(c);
        notes = new StepNotes();
    }

    private String el(String jmeter) {
        return el.toEl(jmeter, owner, notes);
    }

    @Test
    void variablesBecomeGatlingEl() {
        assertEquals("/api/#{version}/items/#{id}", el("/api/${version}/items/${id}"));
        assertEquals("#{a_b}", el("${a.b}"));
        assertTrue(notes.before.isEmpty());
    }

    @Test
    void literalGatlingElIsEscapedAndEscapedJmeterRefsStayLiteral() {
        assertEquals("price \\#{x} and ${y}", el("price #{x} and \\${y}"));
    }

    @Test
    void builtInFunctionsMapToGatlingElBuiltIns() {
        assertEquals("#{randomUuid()}", el("${__UUID()}"));
        assertEquals("#{randomUuid()}", el("${__UUID}"));
        assertEquals("#{currentTimeMillis()}", el("${__time()}"));
        assertEquals("#{currentDate(yyyy-MM-dd)}", el("${__time(yyyy-MM-dd)}"));
        assertEquals("#{randomInt(1,11)}", el("${__Random(1,10)}"), "JMeter's upper bound is inclusive, Gatling's exclusive");
        assertEquals("#{userId()}", el("${__threadNum}"));
        assertTrue(notes.before.isEmpty());
        assertTrue(c.findings.todos().isEmpty());
    }

    @Test
    void functionsWithoutBuiltInUseASessionFunctionBeforeTheRequest() {
        assertEquals("#{jmx2g_fn_1}", el("${__time(/1000)}"));
        assertEquals("#{jmx2g_fn_2}", el("${__time(YYYY-ww)}"));
        assertEquals("#{stamp}", el("${__time(yyyy,stamp)}"));
        assertEquals("#{jmx2g_fn_3}", el("${__RandomString(5,ab)}"));
        assertEquals(4, notes.before.size());
        assertTrue(((Step.Action) notes.before.get(0)).code().contains("System.currentTimeMillis() / 1000"));
        assertTrue(((Step.Action) notes.before.get(1)).code().contains("new java.text.SimpleDateFormat(\"YYYY-ww\")"));
        assertTrue(((Step.Action) notes.before.get(3)).code().contains("randomString(5, \"ab\")"));
        assertTrue(c.helpers.contains(Conversion.Helper.RANDOM_STRING));
    }

    @Test
    void countersArePerUserOrGlobal() {
        assertEquals("#{jmx2g_counter_1_value}", el("${__counter(TRUE,)}"));
        assertTrue(((Step.Action) notes.before.get(0)).code().contains("session.contains(\"jmx2g_counter_1\")"));
        assertEquals("#{total}", el("${__counter(FALSE,total)}"));
        assertTrue(((Step.Action) notes.before.get(1)).code().contains("COUNTER.incrementAndGet()"));
    }

    @Test
    void propertiesBecomeSystemPropertiesWithJmeterDefaults() {
        assertEquals("#{jmx2g_HOST}", el("${__P(host,example.org)}"));
        assertEquals("#{jmx2g_SIZE}", el("${__P(size)}"));
        assertEquals("#{jmx2g_REGION}", el("${__property(region)}"));
        String constants = c.constants.all().toString();
        assertTrue(constants.contains("System.getProperty(\"host\", \"example.org\")"), constants);
        assertTrue(constants.contains("System.getProperty(\"size\", \"1\")"), constants);
        assertTrue(constants.contains("System.getProperty(\"region\", \"region\")"), constants);
        assertEquals("HOST", c.propertyAttributes.get("jmx2g_HOST"));
    }

    @Test
    void unsupportedFunctionsAreKeptVerbatimWithATodo() {
        assertEquals("x=${__urlencode(a b)}", el("x=${__urlencode(a b)}"));
        assertEquals("${__groovy(1+1)}", el("${__groovy(1+1)}"));
        assertEquals(2, c.findings.todos().size());
        assertEquals("${__urlencode(a b)}", c.findings.todos().get(0).original());
        assertTrue(notes.comments.get(0).startsWith("TODO(jmx2gatling) #1 "));
    }

    @Test
    void staticValuesResolveLiteralsVariablesAndProperties() {
        c.userDefinedVariables.put("host", "api.example.org");
        c.userDefinedVariables.put("users", "${__P(users,25)}");
        assertEquals(Optional.of("\"https://api.example.org/x\""), statics.string("https://${host}/x", owner));
        assertEquals(Optional.of("\"https://\" + HOST"), statics.string("https://${__P(host,a.example)}", owner));
        assertEquals(Optional.of("USERS"), statics.integer("${users}", owner));
        assertEquals(Optional.of("42"), statics.integer(" 42 ", owner));
        assertEquals(Optional.empty(), statics.integer("${runtimeVar}", owner));
        assertEquals(Optional.empty(), statics.string("${__UUID()}", owner));
    }

    @Test
    void simpleConditions() {
        assertEquals(Optional.of("session -> \"x\".equals(session.getString(\"v\"))"),
            conditions.toLambda("${__jexl3(\"${v}\" == \"x\")}", Conditions.Kind.IF_EXPRESSION, owner));
        assertEquals(Optional.of("session -> !\"x\".equals(session.getString(\"v\"))"),
            conditions.toLambda("${__groovy('${v}' != 'x',)}", Conditions.Kind.IF_EXPRESSION, owner));
        assertEquals(Optional.of("session -> \"x\".equals(session.getString(\"v\"))"),
            conditions.toLambda("\"x\" === \"${v}\"", Conditions.Kind.IF_JAVASCRIPT, owner));
        assertEquals(Optional.of("session -> \"x\".equals(session.getString(\"v\"))"),
            conditions.toLambda("${__groovy(vars.get(\"v\") == \"x\")}", Conditions.Kind.IF_EXPRESSION, owner));
        assertEquals(Optional.of("session -> \"true\".equalsIgnoreCase(session.getString(\"flag\"))"),
            conditions.toLambda("${flag}", Conditions.Kind.IF_EXPRESSION, owner));
        assertEquals(Optional.of("session -> !\"false\".equalsIgnoreCase(session.getString(\"flag\"))"),
            conditions.toLambda("${flag}", Conditions.Kind.WHILE, owner));
        assertEquals(Optional.of("session -> !session.isFailed()"),
            conditions.toLambda("${JMeterThread.last_sample_ok}", Conditions.Kind.IF_EXPRESSION, owner));
    }

    @Test
    void otherConditionsAreNotSimple() {
        assertEquals(Optional.empty(), conditions.toLambda("${__jexl3(${a} > 5)}", Conditions.Kind.IF_EXPRESSION, owner));
        assertEquals(Optional.empty(), conditions.toLambda("\"${a}\" == \"x\" && \"${b}\" == \"y\"", Conditions.Kind.IF_JAVASCRIPT, owner));
        // In variable-expression mode a raw comparison is just text that never equals "true".
        assertEquals(Optional.empty(), conditions.toLambda("\"${a}\" == \"x\"", Conditions.Kind.IF_EXPRESSION, owner));
        assertEquals(Optional.empty(), conditions.toLambda("", Conditions.Kind.WHILE, owner));
    }

    @Test
    void safeDatePatterns() {
        assertTrue(ElTranslator.isSafeDatePattern("yyyy-MM-dd'T'HH:mm:ss.SSS"));
        assertTrue(ElTranslator.isSafeDatePattern("EEE d MMM yyyy hh a"));
        assertFalse(ElTranslator.isSafeDatePattern("YYYY"));
        assertFalse(ElTranslator.isSafeDatePattern("yyyy-MM-dd'T'HH:mm:ss.S"));
        assertFalse(ElTranslator.isSafeDatePattern("u"));
        assertFalse(ElTranslator.isSafeDatePattern("dd, MMM"));
    }
}
