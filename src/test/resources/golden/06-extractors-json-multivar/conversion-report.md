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
| src/test/resources/fixtures/06-extractors-json-multivar.jmx | Jmx06ExtractorsJsonMultivarSimulation | 3 | 0 | 5 | 4 | 1 |

## src/test/resources/fixtures/06-extractors-json-multivar.jmx → Jmx06ExtractorsJsonMultivarSimulation

- Samplers converted: 3
- Samplers replaced by a no-op: 0
- TODOs: 5
- Approximations: 4
- Disabled or ignored elements: 1
- Files written: `java/golden/Jmx06ExtractorsJsonMultivarSimulation.java`

### TODOs

| # | Element path | Type | Reason |
|---|---|---|---|
| #1 | TestPlan > Extract Things (ThreadGroup) > POST /oauth/token (HTTPSamplerProxy) > Token Fields (JSONPostProcessor) | JSONPostProcessor | Match number -1 for `all_scopes`: Gatling saves all matches as one list, while JMeter creates all_scopes_1..all_scopes_N and all_scopes_matchNr |
| #2 | TestPlan > Extract Things (ThreadGroup) > GET /login-page (HTTPSamplerProxy) > Empty Default (RegexExtractor) | RegexExtractor | Match number -1 for `banner`: Gatling saves all matches as one list, while JMeter creates banner_1..banner_N and banner_matchNr |
| #3 | TestPlan > Extract Things (ThreadGroup) > GET /login-page (HTTPSamplerProxy) > Two Groups (RegexExtractor) | RegexExtractor | Regex template $2$-$1$ not supported (only $1$) |
| #4 | TestPlan > Extract Things (ThreadGroup) > GET /login-page (HTTPSamplerProxy) > Request Id From Headers (RegexExtractor) | RegexExtractor | Extracting from "true" is not supported (only the response body and URL) |
| #5 | TestPlan > Extract Things (ThreadGroup) > GET /login-page (HTTPSamplerProxy) > Page Title (XPathExtractor) | XPathExtractor | XPathExtractor not supported |

<details><summary>#3 original content</summary>

```
(\d+)-(\d+)
```

</details>

### Approximations

| Element path | What was done | How it differs from JMeter |
|---|---|---|
| TestPlan > Extract Things (ThreadGroup) > POST /oauth/token (HTTPSamplerProxy) > Token Fields (JSONPostProcessor) | Variable `token.type` renamed to `token_type` | Gatling EL treats `.` and other punctuation in names as attribute access, so the name was made safe everywhere it is used. |
| TestPlan > Extract Things (ThreadGroup) > POST /oauth/token (HTTPSamplerProxy) > Expensive Item (JSONPostProcessor) | JSONPath filter in `$.items[?(@.price > 100)].sku` | JMeter uses Jayway JsonPath and Gatling its own implementation; filter expressions can behave differently. |
| TestPlan > Extract Things (ThreadGroup) | Closed model (1 threads, ramp-up 0 s) → open model atOnceUsers | Each Gatling user runs the scenario once and exits, while JMeter keeps its threads looping. Concurrency matches only while every user is still in its loop. |
| TestPlan > Extract Things (ThreadGroup) > POST /submit (HTTPSamplerProxy) | Variable `traceIdFromScript` is used but no converted element sets it | It is probably set by a script, a JMeter built-in or an unconverted element. Gatling fails the request when the attribute is missing. |

### Ignored elements

| Element path | Type | Why |
|---|---|---|
| TestPlan > Extract Things (ThreadGroup) > GET /login-page (HTTPSamplerProxy) > Debug PostProcessor (DebugPostProcessor) | DebugPostProcessor | Debug PostProcessor has no load effect |

