package jmx2gatling.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import jmx2gatling.parse.JmeterExpression.Call;
import jmx2gatling.parse.JmeterExpression.Text;
import jmx2gatling.parse.JmeterExpression.Var;
import org.junit.jupiter.api.Test;

class JmeterExpressionTest {

    @Test
    void splitsTextVariablesAndCalls() {
        assertEquals(List.of(new Text("/api/"), new Var("version", "${version}"), new Text("/x?id="),
                new Call("__UUID", List.of(), "${__UUID()}")),
            JmeterExpression.parse("/api/${version}/x?id=${__UUID()}"));
    }

    @Test
    void callWithoutParenthesesOnlyForKnownFunctions() {
        assertEquals(List.of(new Call("__threadNum", List.of(), "${__threadNum}")), JmeterExpression.parse("${__threadNum}"));
        assertEquals(List.of(new Var("__jm__Loop__idx", "${__jm__Loop__idx}")), JmeterExpression.parse("${__jm__Loop__idx}"));
    }

    @Test
    void argumentsHonourEscapesAndNesting() {
        Call call = (Call) JmeterExpression.parse("${__time(EEE\\, d MMM yyyy,stamp)}").get(0);
        assertEquals(List.of("EEE, d MMM yyyy", "stamp"), call.args());

        Call nested = (Call) JmeterExpression.parse("${__P(host,${__P(fallback,a\\,b)})}").get(0);
        assertEquals(List.of("host", "${__P(fallback,a\\,b)}"), nested.args());

        Call groovy = (Call) JmeterExpression.parse("${__groovy(vars.get(\"a\").size() > 1,)}").get(0);
        assertEquals(List.of("vars.get(\"a\").size() > 1", ""), groovy.args());
    }

    @Test
    void escapedDollarBraceIsText() {
        assertEquals(List.of(new Text("price ${notAVar}")), JmeterExpression.parse("price \\${notAVar}"));
    }

    @Test
    void unterminatedReferencesStayText() {
        assertEquals(List.of(new Text("a ${b")), JmeterExpression.parse("a ${b"));
        assertEquals(List.of(new Text("${__P(x}")), JmeterExpression.parse("${__P(x}"));
    }
}
