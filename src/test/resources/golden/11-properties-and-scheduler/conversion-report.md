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
| src/test/resources/fixtures/11-properties-and-scheduler.jmx | Jmx11PropertiesAndSchedulerSimulation | 5 | 0 | 5 | 20 | 0 |

## src/test/resources/fixtures/11-properties-and-scheduler.jmx → Jmx11PropertiesAndSchedulerSimulation

- Samplers converted: 5
- Samplers replaced by a no-op: 0
- TODOs: 5
- Approximations: 20
- Disabled or ignored elements: 0
- Files written: `java/golden/Jmx11PropertiesAndSchedulerSimulation.java`

### TODOs

| # | Element path | Type | Reason |
|---|---|---|---|
| #1 | TestPlan > Forever Without Duration (ThreadGroup) | ThreadGroup | Action after a sampler error is "stoptest"; Gatling does not stop the whole test on a failure. Consider stopLoadGenerator(...) or assertions |
| #2 | TestPlan > Forever Without Duration (ThreadGroup) | ThreadGroup | Thread group loops forever with no scheduler duration; using -DdurationSeconds (default 600 s) and a maxDuration on the simulation |
| #3 | TestPlan > Unconvertible Numbers (ThreadGroup) | ThreadGroup | Loop count `${__groovy(2 * 3)}` could not be converted; using 1 |
| #4 | TestPlan > Unconvertible Numbers (ThreadGroup) | ThreadGroup | Number of threads `${usersFromSomewhere}` could not be converted; using 1 |
| #5 | TestPlan > Unconvertible Numbers (ThreadGroup) | ThreadGroup | Ramp-up `${__P(ramp,fast)}` could not be converted; using 0 |

<details><summary>#3 original content</summary>

```
${__groovy(2 * 3)}
```

</details>

<details><summary>#4 original content</summary>

```
${usersFromSomewhere}
```

</details>

<details><summary>#5 original content</summary>

```
${__P(ramp,fast)}
```

</details>

### Approximations

| Element path | What was done | How it differs from JMeter |
|---|---|---|
| TestPlan > Steady Load (ThreadGroup) | "Start Next Thread Loop" on error → exitHereIfFailed() after each request | JMeter starts the thread's next iteration after a failure; the Gatling user stops instead. |
| TestPlan > Steady Load (ThreadGroup) > GET /steady (HTTPSamplerProxy) | JMeter property `host` read as Java system property `host` | Pass `-Dhost=...` to Gatling instead of `-Jhost=...` to JMeter. Default when unset: `load.example-props.test`. |
| TestPlan > Steady Load (ThreadGroup) > GET /steady/next (HTTPSamplerProxy) | JMeter property `host` read as Java system property `host` | Pass `-Dhost=...` to Gatling instead of `-Jhost=...` to JMeter. Default when unset: `load.example-props.test`. |
| TestPlan > Steady Load (ThreadGroup) | JMeter property `duration` read as Java system property `duration` | Pass `-Dduration=...` to Gatling instead of `-Jduration=...` to JMeter. Default when unset: `300`. |
| TestPlan > Steady Load (ThreadGroup) | Infinite loops with a DURATION s scheduler duration → during(DURATION s) | JMeter stops every thread when the duration has passed since the thread group started; each Gatling user loops for the duration from its own start. |
| TestPlan > Steady Load (ThreadGroup) | JMeter property `users` read as Java system property `users` | Pass `-Dusers=...` to Gatling instead of `-Jusers=...` to JMeter. Default when unset: `50`. |
| TestPlan > Steady Load (ThreadGroup) | JMeter property `rampup` read as Java system property `rampup` | Pass `-Drampup=...` to Gatling instead of `-Jrampup=...` to JMeter. Default when unset: `60`. |
| TestPlan > Steady Load (ThreadGroup) | Closed model (${__P(users,50)} threads, ramp-up ${__P(rampup,60)} s) → open model rampUsers ... during | Each Gatling user runs the scenario once and exits, while JMeter keeps its threads looping. Concurrency matches only while every user is still in its loop. |
| TestPlan > Fixed Iterations (ThreadGroup) > GET /fixed (HTTPSamplerProxy) | JMeter property `host` read as Java system property `host` | Pass `-Dhost=...` to Gatling instead of `-Jhost=...` to JMeter. Default when unset: `load.example-props.test`. |
| TestPlan > Fixed Iterations (ThreadGroup) | Loop count from property `loops` | A negative value (JMeter's "infinite") makes Gatling's repeat run zero times; pass a positive count. |
| TestPlan > Fixed Iterations (ThreadGroup) | JMeter property `loops` read as Java system property `loops` | Pass `-Dloops=...` to Gatling instead of `-Jloops=...` to JMeter. Default when unset: `10`. |
| TestPlan > Fixed Iterations (ThreadGroup) | Scheduler duration 600 s not enforced | The loop count (${__P(loops,10)}) alone decides when each user stops. |
| TestPlan > Fixed Iterations (ThreadGroup) | JMeter property `users` read as Java system property `users` | Pass `-Dusers=...` to Gatling instead of `-Jusers=...` to JMeter. Default when unset: `50`. |
| TestPlan > Fixed Iterations (ThreadGroup) | Closed model (${__P(users,50)} threads, ramp-up 30 s) → open model rampUsers ... during | Each Gatling user runs the scenario once and exits, while JMeter keeps its threads looping. Concurrency matches only while every user is still in its loop. |
| TestPlan > Forever Without Duration (ThreadGroup) > GET /forever (HTTPSamplerProxy) | JMeter property `host` read as Java system property `host` | Pass `-Dhost=...` to Gatling instead of `-Jhost=...` to JMeter. Default when unset: `load.example-props.test`. |
| TestPlan > Forever Without Duration (ThreadGroup) | JMeter property `threads` read as Java system property `threads` | Pass `-Dthreads=...` to Gatling instead of `-Jthreads=...` to JMeter. Default when unset: `5`. |
| TestPlan > Forever Without Duration (ThreadGroup) | Closed model (${threads} threads, ramp-up 0 s) → open model atOnceUsers | Each Gatling user runs the scenario once and exits, while JMeter keeps its threads looping. Concurrency matches only while every user is still in its loop. |
| TestPlan > Unconvertible Numbers (ThreadGroup) > GET /odd (HTTPSamplerProxy) | JMeter property `host` read as Java system property `host` | Pass `-Dhost=...` to Gatling instead of `-Jhost=...` to JMeter. Default when unset: `load.example-props.test`. |
| TestPlan > Unconvertible Numbers (ThreadGroup) | Closed model (${usersFromSomewhere} threads, ramp-up ${__P(ramp,fast)} s) → open model atOnceUsers | Each Gatling user runs the scenario once and exits, while JMeter keeps its threads looping. Concurrency matches only while every user is still in its loop. |
| TestPlan | JMeter property `threads` read as Java system property `threads` | Pass `-Dthreads=...` to Gatling instead of `-Jthreads=...` to JMeter. Default when unset: `5`. |

### Ignored elements

None.

