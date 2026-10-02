package jmx2gatling.convert;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jmx2gatling.convert.ResponseAssertionRule.Match;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ResponseAssertionRuleTest {

    @ParameterizedTest(name = "test_type {0} -> {1}, not={2}, or={3}")
    @CsvSource({
        "1, MATCHES, false, false",
        "2, CONTAINS, false, false",
        "8, EQUALS, false, false",
        "16, SUBSTRING, false, false",
        "5, MATCHES, true, false",
        "6, CONTAINS, true, false",
        "12, EQUALS, true, false",
        "20, SUBSTRING, true, false",
        "33, MATCHES, false, true",
        "34, CONTAINS, false, true",
        "40, EQUALS, false, true",
        "48, SUBSTRING, false, true",
        "52, SUBSTRING, true, true",
        "44, EQUALS, true, true",
        "0, MATCHES, false, false",
        "4, MATCHES, true, false",
    })
    void decodesEachCombination(int testType, Match match, boolean not, boolean or) {
        assertEquals(new ResponseAssertionRule(match, not, or), ResponseAssertionRule.decode(testType));
    }

    @Test
    void jmeterPrecedenceWhenSeveralMatchBitsAreSet() {
        assertEquals(Match.CONTAINS, ResponseAssertionRule.decode(2 | 8 | 16 | 1).match());
        assertEquals(Match.EQUALS, ResponseAssertionRule.decode(8 | 16 | 1).match());
        assertEquals(Match.SUBSTRING, ResponseAssertionRule.decode(16 | 1).match());
    }
}
