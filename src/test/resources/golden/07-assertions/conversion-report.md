# jmx2gatling conversion report

Target: Gatling 3.16.0 Java DSL.

## How to read this report

- **TODO**: behavior that was not converted. Each one has a numbered marker in the simulation, `// TODO(jmx2gatling) #<id>`, with the same number as its row below. Numbers restart for each file. Generated code always compiles; a TODO is either a no-op, a placeholder value or a missing check.
- **Approximation**: converted, but Gatling behaves differently from JMeter in the way described.
- **Ignored**: elements with no load-test effect (listeners, debug elements), disabled elements, and elements JMeter itself never applies.
- Every element is identified by its path in the JMeter tree, as `Name (type)` from the test plan down.

## Summary

| Input file | Simulation | Samplers converted | Samplers not converted | TODOs | Approximations | Ignored |
|---|---|---:|---:|---:|---:|---:|
| src/test/resources/fixtures/07-assertions.jmx | Jmx07AssertionsSimulation | 6 | 0 | 10 | 3 | 0 |

## src/test/resources/fixtures/07-assertions.jmx → Jmx07AssertionsSimulation

- Samplers converted: 6
- Samplers replaced by a no-op: 0
- TODOs: 10
- Approximations: 3
- Disabled or ignored elements: 0
- Files written: `java/golden/Jmx07AssertionsSimulation.java`

### TODOs

| # | Element path | Type | Reason |
|---|---|---|---|
| #1 | TestPlan > Assert Everything (ThreadGroup) > GET /page-body (HTTPSamplerProxy) > Either Needle (Or) (ResponseAssertion) | ResponseAssertion | Or across patterns: Gatling checks are all required (AND) |
| #2 | TestPlan > Assert Everything (ThreadGroup) > GET /page-body (HTTPSamplerProxy) > Check A Variable (ResponseAssertion) | ResponseAssertion | Applies to JMeter variable `someVar` instead of the response |
| #3 | TestPlan > Assert Everything (ThreadGroup) > GET /plain (HTTPSamplerProxy) > Message Is OK (ResponseAssertion) | ResponseAssertion | Response Assertion on Assertion.response_message is not supported |
| #4 | TestPlan > Assert Everything (ThreadGroup) > GET /headers (HTTPSamplerProxy) > Headers Free Text (ResponseAssertion) | ResponseAssertion | Response header pattern does not map to a single header |
| #5 | TestPlan > Assert Everything (ThreadGroup) > GET /api/order (HTTPSamplerProxy) > Discount Is Null (JSONPathAssertion) | JSONPathAssertion | JSON Assertion expecting null is not supported |
| #6 | TestPlan > Assert Everything (ThreadGroup) > GET /legacy-assertions (HTTPSamplerProxy) > Under Two Seconds (DurationAssertion) | DurationAssertion | DurationAssertion not supported |
| #7 | TestPlan > Assert Everything (ThreadGroup) > GET /legacy-assertions (HTTPSamplerProxy) > Small Response (SizeAssertion) | SizeAssertion | SizeAssertion not supported |
| #8 | TestPlan > Assert Everything (ThreadGroup) > GET /legacy-assertions (HTTPSamplerProxy) > Has Title (XPathAssertion) | XPathAssertion | XPathAssertion not supported |
| #9 | TestPlan > Assert Everything (ThreadGroup) > GET /legacy-assertions (HTTPSamplerProxy) > Script Check (JSR223Assertion) | JSR223Assertion | Script not converted |
| #10 | TestPlan > Assert Everything (ThreadGroup) > GET /legacy-assertions (HTTPSamplerProxy) > BeanShell Check (BeanShellAssertion) | BeanShellAssertion | Script not converted |

<details><summary>#1 original content</summary>

```
needle-one
needle-two
```

</details>

<details><summary>#3 original content</summary>

```
OK
```

</details>

<details><summary>#4 original content</summary>

```
HTTP/1.1 200 OK
```

</details>

<details><summary>#5 original content</summary>

```
$.discount
```

</details>

<details><summary>#9 original content</summary>

```
[language: groovy]
if (!prev.getResponseDataAsString().contains("assert-script-marker")) {
      AssertionResult.setFailure(true)
  }
```

</details>

<details><summary>#10 original content</summary>

```
Failure = !ResponseCode.equals("200");
```

</details>

### Approximations

| Element path | What was done | How it differs from JMeter |
|---|---|---|
| TestPlan > Assert Everything (ThreadGroup) > GET /page-body (HTTPSamplerProxy) > Ignore Status Then Check (ResponseAssertion) | "Ignore status" not converted | Gatling still fails non-2xx/3xx responses unless the request also has a status() check. |
| TestPlan > Assert Everything (ThreadGroup) > GET /headers (HTTPSamplerProxy) > No Debug Header (ResponseAssertion) | Header pattern `X-Debug` checked as a header name | JMeter searched the whole header block, so the text could also have matched inside another header's value. |
| TestPlan > Assert Everything (ThreadGroup) | Closed model (1 threads, ramp-up 0 s) → open model atOnceUsers | Each Gatling user runs the scenario once and exits, while JMeter keeps its threads looping. Concurrency matches only while every user is still in its loop. |

### Ignored elements

None.

