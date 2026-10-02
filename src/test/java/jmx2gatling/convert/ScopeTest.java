package jmx2gatling.convert;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import jmx2gatling.inventory.ElementKind;
import jmx2gatling.model.JmxElement;
import jmx2gatling.parse.JmxParser;
import org.junit.jupiter.api.Test;

class ScopeTest {

    private static String headers(String name, String... nameValues) {
        StringBuilder s = new StringBuilder("<HeaderManager testclass=\"HeaderManager\" testname=\"" + name + "\"><collectionProp name=\"HeaderManager.headers\">");
        for (int i = 0; i < nameValues.length; i += 2) {
            s.append("<elementProp name=\"\" elementType=\"Header\"><stringProp name=\"Header.name\">").append(nameValues[i])
                .append("</stringProp><stringProp name=\"Header.value\">").append(nameValues[i + 1]).append("</stringProp></elementProp>");
        }
        return s.append("</collectionProp></HeaderManager><hashTree/>").toString();
    }

    private static String defaults(String name, String protocol, String domain, String port) {
        return "<ConfigTestElement guiclass=\"HttpDefaultsGui\" testclass=\"ConfigTestElement\" testname=\"" + name + "\">"
            + "<stringProp name=\"HTTPSampler.protocol\">" + protocol + "</stringProp>"
            + "<stringProp name=\"HTTPSampler.domain\">" + domain + "</stringProp>"
            + "<stringProp name=\"HTTPSampler.port\">" + port + "</stringProp></ConfigTestElement><hashTree/>";
    }

    private static String timer(String name) {
        return "<ConstantTimer testclass=\"ConstantTimer\" testname=\"" + name + "\"><stringProp name=\"ConstantTimer.delay\">1</stringProp></ConstantTimer><hashTree/>";
    }

    private static final String PLAN = headers("plan", "Accept", "*/*", "X-Plan", "p")
        + defaults("planDefaults", "http", "plan.example", "8080")
        + timer("planTimer")
        + "<ThreadGroup testclass=\"ThreadGroup\" testname=\"tg\"/><hashTree>"
        + headers("tg", "accept", "application/json")
        + defaults("tgDefaults", "https", "", "")
        + timer("tgTimer")
        + "  <TransactionController testclass=\"TransactionController\" testname=\"tx\"/><hashTree>"
        + "    <HTTPSamplerProxy testclass=\"HTTPSamplerProxy\" testname=\"s\"/><hashTree>"
        + headers("sampler", "X-Plan", "sampler", "X-Own", "o")
        + timer("samplerTimer")
        + "    </hashTree>"
        + headers("tx", "X-Tx", "t")
        + timer("txTimer")
        + "  </hashTree>"
        + "  <HTTPSamplerProxy testclass=\"HTTPSamplerProxy\" testname=\"sibling\"/><hashTree/>"
        + "</hashTree>";

    private static JmxElement plan() throws IOException {
        String xml = "<jmeterTestPlan><hashTree><TestPlan testclass=\"TestPlan\" testname=\"Plan\"/><hashTree>" + PLAN
            + "</hashTree></hashTree></jmeterTestPlan>";
        return new JmxParser().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    /** The scope a sampler sees: every container from the plan down, plus the sampler's own children. */
    private static Scope scopeOf(JmxElement sampler) {
        List<JmxElement> chain = new java.util.ArrayList<>();
        for (JmxElement e = sampler; e != null; e = e.parent()) {
            chain.add(0, e);
        }
        Scope scope = Scope.root();
        for (JmxElement e : chain) {
            scope = scope.enter(e);
        }
        return scope;
    }

    @Test
    void closerHeaderManagersWinPerNameCaseInsensitively() throws IOException {
        JmxElement plan = plan();
        JmxElement sampler = plan.children().get(3).children().get(3).children().get(0);
        List<String> headers = scopeOf(sampler).headers().stream().map(h -> h.name() + "=" + h.value()).toList();
        assertEquals(List.of("accept=application/json", "X-Plan=sampler", "X-Tx=t", "X-Own=o"), headers);
    }

    @Test
    void siblingsDoNotSeeEachOthersScopedChildren() throws IOException {
        JmxElement plan = plan();
        JmxElement sibling = plan.children().get(3).children().get(4);
        List<String> headers = scopeOf(sibling).headers().stream().map(h -> h.name() + "=" + h.value()).toList();
        assertEquals(List.of("accept=application/json", "X-Plan=p"), headers);
    }

    @Test
    void defaultsFillFieldByFieldClosestFirst() throws IOException {
        JmxElement plan = plan();
        JmxElement sampler = plan.children().get(3).children().get(3).children().get(0);
        Scope.Defaults d = scopeOf(sampler).defaults();
        assertEquals("https", d.protocol());
        assertEquals("plan.example", d.domain());
        assertEquals("8080", d.port());
    }

    @Test
    void timersApplyOutermostFirstIncludingTheSamplersOwn() throws IOException {
        JmxElement plan = plan();
        JmxElement sampler = plan.children().get(3).children().get(3).children().get(0);
        List<String> timers = scopeOf(sampler).all(ElementKind.TIMER).stream().map(JmxElement::name).toList();
        assertEquals(List.of("planTimer", "tgTimer", "txTimer", "samplerTimer"), timers);

        JmxElement sibling = plan.children().get(3).children().get(4);
        assertEquals(List.of("planTimer", "tgTimer"), scopeOf(sibling).all(ElementKind.TIMER).stream().map(JmxElement::name).toList());
    }
}
