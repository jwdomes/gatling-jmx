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
| src/test/resources/fixtures/15-review-regressions.jmx | Jmx15ReviewRegressionsSimulation | 8 | 0 | 5 | 10 | 2 |

## src/test/resources/fixtures/15-review-regressions.jmx → Jmx15ReviewRegressionsSimulation

- Samplers converted: 8
- Samplers replaced by a no-op: 0
- TODOs: 5
- Approximations: 10
- Disabled or ignored elements: 2
- Files written: `java/golden/Jmx15ReviewRegressionsSimulation.java`

### TODOs

| # | Element path | Type | Reason |
|---|---|---|---|
| #1 | TestPlan > Loops By Property (ThreadGroup) > Inner Forever By Property (LoopController) | LoopController | Loop count ${__P(inner,-1)} defaults to -1 (loop forever) and is converted as infinite; -Dinner is not read. Replace with repeat(...) if you pass a count |
| #2 | TestPlan > Loops By Property (ThreadGroup) | ThreadGroup | Loop count ${__P(loops,-1)} defaults to -1 (loop forever) and is converted as infinite; -Dloops is not read. Replace with repeat(...) if you pass a count |
| #3 | TestPlan > Edge Values (ThreadGroup) > GET /edges (HTTPSamplerProxy) > Basic Auth Under Sampler (AuthManager) | AuthManager | AuthManager not converted; it applied to this sampler |
| #4 | TestPlan > Edge Values (ThreadGroup) > GET /edges (HTTPSamplerProxy) > Counter Under Sampler (CounterConfig) | CounterConfig | CounterConfig not converted; it applied to this sampler |
| #5 | TestPlan > Edge Values (ThreadGroup) > GET /edges (HTTPSamplerProxy) > Huge Match Number (RegexExtractor) | RegexExtractor | Match number `99999999999` for `huge` is not a literal integer in range; using the first match |

<details><summary>#1 original content</summary>

```
${__P(inner,-1)}
```

</details>

<details><summary>#2 original content</summary>

```
${__P(loops,-1)}
```

</details>

### Approximations

| Element path | What was done | How it differs from JMeter |
|---|---|---|
| TestPlan > HTTP (ThreadGroup) > Credentials (CSVDataSet) | Column names come from the file's header row | JMeter skipped the first line and used variableNames=username,password; Gatling uses the header row itself, so its names must match. |
| TestPlan > HTTP (ThreadGroup) | Closed model (1 threads, ramp-up 0 s) → open model atOnceUsers | Each Gatling user runs the scenario once and exits, while JMeter keeps its threads looping. Concurrency matches only while every user is still in its loop. |
| TestPlan > Loops By Property (ThreadGroup) > Pages By Property (LoopController) | Loop count from property `pages` | A negative value (JMeter's "infinite") makes Gatling's repeat run zero times; pass a positive count. |
| TestPlan > Loops By Property (ThreadGroup) > Pages By Property (LoopController) | JMeter property `pages` read as Java system property `pages` | Pass `-Dpages=...` to Gatling instead of `-Jpages=...` to JMeter. Default when unset: `2`. |
| TestPlan > Loops By Property (ThreadGroup) | Infinite loops with a 60 s scheduler duration → during(60 s) | JMeter stops every thread when the duration has passed since the thread group started; each Gatling user loops for the duration from its own start. |
| TestPlan > Loops By Property (ThreadGroup) | Closed model (1 threads, ramp-up 0 s) → open model atOnceUsers | Each Gatling user runs the scenario once and exits, while JMeter keeps its threads looping. Concurrency matches only while every user is still in its loop. |
| TestPlan > Edge Values (ThreadGroup) > Backslash Delimited (CSVDataSet) | Column names come from the file's header row | JMeter skipped the first line and used variableNames=a\b; Gatling uses the header row itself, so its names must match. |
| TestPlan > Edge Values (ThreadGroup) | Closed model (1 threads, ramp-up 0 s) → open model atOnceUsers | Each Gatling user runs the scenario once and exits, while JMeter keeps its threads looping. Concurrency matches only while every user is still in its loop. |
| TestPlan > Edge Values (ThreadGroup) > GET /edges (HTTPSamplerProxy) > EL Default (RegexExtractor) | Variable `fallbackToken` is used but no converted element sets it | It is probably set by a script, a JMeter built-in or an unconverted element. Gatling fails the request when the attribute is missing. |
| TestPlan > Edge Values (ThreadGroup) > Leading Zero Index (ForeachController) | Variable `item_1` is used but no converted element sets it | It is probably set by a script, a JMeter built-in or an unconverted element. Gatling fails the request when the attribute is missing. |

### Ignored elements

| Element path | Type | Why |
|---|---|---|
| TestPlan > Edge Values (ThreadGroup) > GET /edges (HTTPSamplerProxy) > Disabled Under Sampler (HeaderManager) | HeaderManager | Disabled |
| TestPlan > Edge Values (ThreadGroup) > Debug After Edges (DebugSampler) | DebugSampler | Debug Sampler has no load effect |

