package jmx2gatling.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import jmx2gatling.Fixtures;
import jmx2gatling.parse.JmxParser;
import org.junit.jupiter.api.Test;

class InventoryTest {

    @Test
    void countsEnabledAndDisabledElementsAndFunctions() throws IOException {
        Inventory inventory = new Inventory();
        inventory.add(new JmxParser().parse(Fixtures.fixturesDir().resolve("03-disabled-subtrees.jmx")));
        String md = inventory.toMarkdown();

        assertTrue(md.contains("| HTTPSamplerProxy | 1 | 4 | supported |"), md);
        assertTrue(md.contains("| ThreadGroup | 1 | 1 | supported |"), md);
        assertTrue(md.contains("| JSR223PostProcessor | 0 | 1 | unsupported |"), md);
        assertTrue(md.contains("| HeaderManager | 0 | 1 | supported |"), md);
        assertTrue(md.contains("| __UUID | 0 | 1 | supported |"), md);
        assertTrue(md.indexOf("JSR223PostProcessor") < md.indexOf("HTTPSamplerProxy"), "unsupported rows come first");
    }

    @Test
    void jsonHasTheSameRows() throws IOException {
        Inventory inventory = new Inventory();
        inventory.add(new JmxParser().parse(Fixtures.fixturesDir().resolve("03-disabled-subtrees.jmx")));
        inventory.addFailure();
        String json = inventory.toJson();
        assertTrue(json.contains("\"filesScanned\": 1,"), json);
        assertTrue(json.contains("\"filesFailed\": 1,"), json);
        assertTrue(json.contains("{\"type\": \"HTTPSamplerProxy\", \"enabled\": 1, \"disabled\": 4, \"support\": \"supported\"}"), json);
        assertTrue(json.contains("{\"function\": \"__UUID\", \"enabled\": 0, \"disabled\": 1, \"support\": \"supported\"}"), json);
    }

    @Test
    void unknownTypesAreUnsupportedExceptListeners() {
        assertEquals(SupportStatus.UNSUPPORTED, SupportMatrix.statusOf("com.example.FancySampler"));
        assertEquals(SupportStatus.SUPPORTED, SupportMatrix.statusOf("kg.apc.jmeter.vizualizers.CorrectedResultCollector"));
        assertEquals(SupportStatus.UNSUPPORTED, SupportMatrix.functionStatus("__urlencode"));
    }
}
