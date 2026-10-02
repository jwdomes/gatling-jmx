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
| src/test/resources/fixtures/05-controllers-all.jmx | Jmx05ControllersAllSimulation | 19 | 0 | 6 | 8 | 0 |

## src/test/resources/fixtures/05-controllers-all.jmx → Jmx05ControllersAllSimulation

- Samplers converted: 19
- Samplers replaced by a no-op: 0
- TODOs: 6
- Approximations: 8
- Disabled or ignored elements: 0
- Files written: `java/golden/Jmx05ControllersAllSimulation.java`

### TODOs

| # | Element path | Type | Reason |
|---|---|---|---|
| #1 | TestPlan > Controller Tour (ThreadGroup) > GET /profile (HTTPSamplerProxy) > Profile Fields (JSONPostProcessor) | JSONPostProcessor | Match number -1 for `ids`: Gatling saves all matches as one list, while JMeter creates ids_1..ids_N and ids_matchNr |
| #2 | TestPlan > Controller Tour (ThreadGroup) > If Many Orders (complex) (IfController) | IfController | Condition not converted; placeholder is session -> true |
| #3 | TestPlan > Controller Tour (ThreadGroup) > Retry While Failing (WhileController) | WhileController | Empty condition (loop until a sampler in the loop fails) not converted; placeholder is session -> true |
| #4 | TestPlan > Controller Tour (ThreadGroup) > Five Exports Per User (ThroughputController) | ThroughputController | Total Executions mode (5 executions per user) not converted; children always run |
| #5 | TestPlan > Controller Tour (ThreadGroup) > Switch By Role (SwitchController) | SwitchController | SwitchController not converted; its children are converted inline as if in a Simple Controller |
| #6 | TestPlan > Controller Tour (ThreadGroup) > Reuse Login Module (ModuleController) | ModuleController | ModuleController not converted; convert the referenced test fragment and call it here |

<details><summary>#2 original content</summary>

```
${__groovy(vars.getObject("ids")?.size() > 5 && vars.get("role") != "guest")}
```

</details>

<details><summary>#6 original content</summary>

```
[module: Test Plan > Login Fragment]
```

</details>

### Approximations

| Element path | What was done | How it differs from JMeter |
|---|---|---|
| TestPlan > Controller Tour (ThreadGroup) > If Not Admin (JavaScript) (IfController) | "Evaluate for all children" not converted | The condition is evaluated once before the children, not before each child. |
| TestPlan > Controller Tour (ThreadGroup) > If Last Sample OK (IfController) | `${JMeterThread.last_sample_ok}` → `!session.isFailed()` | JMeter looks at the last sample only; Gatling's failed flag stays set after any failure until the user exits a tryMax/exitBlockOnFail block. |
| TestPlan > Controller Tour (ThreadGroup) > Pages From Property (LoopController) | Loop count from property `pages` | A negative value (JMeter's "infinite") makes Gatling's repeat run zero times; pass a positive count. |
| TestPlan > Controller Tour (ThreadGroup) > Pages From Property (LoopController) | JMeter property `pages` read as Java system property `pages` | Pass `-Dpages=...` to Gatling instead of `-Jpages=...` to JMeter. Default when unset: `3`. |
| TestPlan > Controller Tour (ThreadGroup) > Quarter Of Users Review (ThroughputController) | Percent Executions 25.0% → randomSwitch with 25.0% weight | JMeter runs the children on exactly that share of passes; Gatling picks randomly, so the share is only reached on average. |
| TestPlan > Controller Tour (ThreadGroup) > Heartbeat Forever (LoopController) > GET /heartbeat (HTTPSamplerProxy) > Heartbeat Interval (ConstantTimer) | JMeter property `heartbeatMillis` read as Java system property `heartbeatMillis` | Pass `-DheartbeatMillis=...` to Gatling instead of `-JheartbeatMillis=...` to JMeter. Default when unset: `5000`. |
| TestPlan > Controller Tour (ThreadGroup) | Closed model (1 threads, ramp-up 1 s) → open model rampUsers ... during | Each Gatling user runs the scenario once and exits, while JMeter keeps its threads looping. Concurrency matches only while every user is still in its loop. |
| TestPlan > Controller Tour (ThreadGroup) > Each Coupon Variable (ForeachController) | Variable `coupon_1` is used but no converted element sets it | It is probably set by a script, a JMeter built-in or an unconverted element. Gatling fails the request when the attribute is missing. |

### Ignored elements

None.

