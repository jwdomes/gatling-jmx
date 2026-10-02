package jmx2gatling.convert;

/**
 * Decodes {@code Assertion.test_type} of a Response Assertion. JMeter stores it as a bitmask:
 * Matches=1, Contains=2, Not=4, Equals=8, Substring=16, Or=32. When several match bits are set,
 * JMeter tests them in the order Contains, Equals, Substring, Matches, so that order wins here.
 */
record ResponseAssertionRule(Match match, boolean not, boolean or) {

    enum Match { MATCHES, CONTAINS, EQUALS, SUBSTRING }

    static final int MATCHES = 1;
    static final int CONTAINS = 2;
    static final int NOT = 4;
    static final int EQUALS = 8;
    static final int SUBSTRING = 16;
    static final int OR = 32;

    static ResponseAssertionRule decode(int testType) {
        Match match;
        if ((testType & CONTAINS) != 0) {
            match = Match.CONTAINS;
        } else if ((testType & EQUALS) != 0) {
            match = Match.EQUALS;
        } else if ((testType & SUBSTRING) != 0) {
            match = Match.SUBSTRING;
        } else {
            match = Match.MATCHES;
        }
        return new ResponseAssertionRule(match, (testType & NOT) != 0, (testType & OR) != 0);
    }
}
