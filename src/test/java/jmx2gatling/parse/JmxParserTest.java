package jmx2gatling.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import jmx2gatling.model.JmxElement;
import jmx2gatling.model.Property;
import org.junit.jupiter.api.Test;

class JmxParserTest {

    static JmxElement parse(String hashTreeContent) throws IOException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<jmeterTestPlan version=\"1.2\" properties=\"5.0\" jmeter=\"5.6.3\">"
            + "<hashTree><TestPlan guiclass=\"TestPlanGui\" testclass=\"TestPlan\" testname=\"Plan\"/>"
            + "<hashTree>" + hashTreeContent + "</hashTree></hashTree></jmeterTestPlan>";
        return new JmxParser().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void childrenComeFromTheFollowingHashTree() throws IOException {
        JmxElement plan = parse(
            "<ThreadGroup testclass=\"ThreadGroup\" testname=\"TG\"/>"
                + "<hashTree>"
                + "  <TransactionController testclass=\"TransactionController\" testname=\"T\"/>"
                + "  <hashTree>"
                + "    <HTTPSamplerProxy testclass=\"HTTPSamplerProxy\" testname=\"A\"/><hashTree/>"
                + "    <HTTPSamplerProxy testclass=\"HTTPSamplerProxy\" testname=\"B\"/><hashTree/>"
                + "  </hashTree>"
                + "  <HTTPSamplerProxy testclass=\"HTTPSamplerProxy\" testname=\"C\"/><hashTree/>"
                + "</hashTree>"
                + "<ThreadGroup testclass=\"ThreadGroup\" testname=\"TG2\"/><hashTree/>");

        assertEquals(2, plan.children().size());
        JmxElement tg = plan.children().get(0);
        assertEquals(List.of("T", "C"), names(tg.children()));
        assertEquals(List.of("A", "B"), names(tg.children().get(0).children()));
        assertEquals("TestPlan > TG (ThreadGroup) > T (TransactionController) > B (HTTPSamplerProxy)",
            tg.children().get(0).children().get(1).path());
        assertTrue(plan.children().get(1).children().isEmpty());
    }

    @Test
    void elementWithoutHashTreeHasNoChildren() throws IOException {
        JmxElement plan = parse("<ThreadGroup testclass=\"ThreadGroup\" testname=\"TG\"/>");
        assertTrue(plan.children().get(0).children().isEmpty());
    }

    @Test
    void hashTreeWithoutElementFailsWithThePath() {
        JmxFormatException e = assertThrows(JmxFormatException.class, () -> parse(
            "<ThreadGroup testclass=\"ThreadGroup\" testname=\"TG\"/><hashTree><hashTree/></hashTree>"));
        assertTrue(e.getMessage().contains("TestPlan > TG (ThreadGroup)"), e.getMessage());
    }

    @Test
    void enabledDefaultsToTrueAndDisabledIsInherited() throws IOException {
        JmxElement plan = parse(
            "<ThreadGroup testclass=\"ThreadGroup\" testname=\"TG\" enabled=\"false\"/>"
                + "<hashTree><HTTPSamplerProxy testclass=\"HTTPSamplerProxy\" testname=\"A\"/><hashTree/></hashTree>"
                + "<ThreadGroup testclass=\"ThreadGroup\" testname=\"TG2\"/><hashTree/>");
        JmxElement disabled = plan.children().get(0);
        assertFalse(disabled.enabled());
        assertTrue(disabled.children().get(0).enabled());
        assertTrue(disabled.children().get(0).effectivelyDisabled());
        assertTrue(plan.children().get(1).enabled());
    }

    @Test
    void readsEveryPropertyType() throws IOException {
        JmxElement plan = parse(
            "<ThroughputController testclass=\"ThroughputController\" testname=\"X\">"
                + "<stringProp name=\"s\">text &amp; more</stringProp>"
                + "<boolProp name=\"b\">true</boolProp>"
                + "<intProp name=\"i\">42</intProp>"
                + "<longProp name=\"l\">9000000000</longProp>"
                + "<FloatProperty><name>f</name><value>12.5</value><savedValue>0.0</savedValue></FloatProperty>"
                + "<elementProp name=\"e\" elementType=\"Arguments\" testclass=\"Arguments\">"
                + "  <collectionProp name=\"Arguments.arguments\">"
                + "    <elementProp name=\"a\" elementType=\"Argument\"><stringProp name=\"Argument.value\">v</stringProp></elementProp>"
                + "    <stringProp name=\"123\">plain</stringProp>"
                + "  </collectionProp>"
                + "</elementProp>"
                + "</ThroughputController><hashTree/>");
        var props = plan.children().get(0).props();
        assertEquals("text & more", props.string("s"));
        assertTrue(props.bool("b", false));
        assertEquals(42, props.integer("i", 0));
        assertEquals("9000000000", props.string("l"));
        assertEquals("12.5", props.string("f"));
        Property.Element e = props.element("e").orElseThrow();
        assertEquals("Arguments", e.elementType());
        List<Property> items = e.props().collection("Arguments.arguments");
        assertEquals(2, items.size());
        assertEquals("v", ((Property.Element) items.get(0)).props().string("Argument.value"));
        assertEquals("plain", ((Property.Value) items.get(1)).text());
    }

    @Test
    void missingPropertiesUseDefaults() throws IOException {
        JmxElement plan = parse("<LoopController testclass=\"LoopController\" testname=\"L\">"
            + "<stringProp name=\"blank\"></stringProp><stringProp name=\"expr\">${__P(n,1)}</stringProp></LoopController><hashTree/>");
        var props = plan.children().get(0).props();
        assertEquals("", props.string("missing"));
        assertTrue(props.bool("missing", true));
        assertEquals(7, props.integer("missing", 7));
        assertEquals(7, props.integer("blank", 7));
        assertEquals(7, props.integer("expr", 7));
        assertTrue(props.collection("missing").isEmpty());
    }

    @Test
    void rejectsDoctypeDeclarations() {
        String xml = "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><jmeterTestPlan>&e;</jmeterTestPlan>";
        assertThrows(JmxFormatException.class,
            () -> new JmxParser().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void rejectsNonJmxRoot() {
        assertThrows(JmxFormatException.class,
            () -> new JmxParser().parse(new ByteArrayInputStream("<project/>".getBytes(StandardCharsets.UTF_8))));
    }

    private static List<String> names(List<JmxElement> elements) {
        return elements.stream().map(JmxElement::name).toList();
    }
}
