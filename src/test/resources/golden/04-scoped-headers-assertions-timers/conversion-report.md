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
| src/test/resources/fixtures/04-scoped-headers-assertions-timers.jmx | Jmx04ScopedHeadersAssertionsTimersSimulation | 3 | 0 | 2 | 4 | 1 |

## src/test/resources/fixtures/04-scoped-headers-assertions-timers.jmx → Jmx04ScopedHeadersAssertionsTimersSimulation

- Samplers converted: 3
- Samplers replaced by a no-op: 0
- TODOs: 2
- Approximations: 4
- Disabled or ignored elements: 1
- Files written: `java/golden/Jmx04ScopedHeadersAssertionsTimersSimulation.java`

### TODOs

| # | Element path | Type | Reason |
|---|---|---|---|
| #1 | TestPlan > Scoped Users (ThreadGroup) > Sign Request (JSR223PreProcessor) | JSR223PreProcessor | Script pre-processor not converted |
| #2 | TestPlan > Scoped Users (ThreadGroup) > Account (TransactionController) > Capture Account Id (JSR223PostProcessor) | JSR223PostProcessor | Script not converted |

<details><summary>#1 original content</summary>

```
[language: groovy]
def nonce = UUID.randomUUID().toString()
vars.put("nonce", nonce) /* scope-fixture-script */
sampler.getHeaderManager()?.add(new org.apache.jmeter.protocol.http.control.Header("X-Nonce", nonce))
```

</details>

<details><summary>#2 original content</summary>

```
[language: groovy]
vars.put("accountId", prev.getResponseHeaders().find(/X-Account: (\d+)/) { m, id -> id })
```

</details>

### Approximations

| Element path | What was done | How it differs from JMeter |
|---|---|---|
| TestPlan > Scoped Users (ThreadGroup) > Account (TransactionController) > Account Gaussian Pause (GaussianRandomTimer) | Gaussian delay → uniform pause between offset - deviation and offset + deviation | Gatling has no per-pause normal distribution. The mean is the same; the spread is narrower and never exceeds offset + deviation. |
| TestPlan > Scoped Users (ThreadGroup) > Account (TransactionController) > GET /account (HTTPSamplerProxy) | Several random timers summed into one uniform pause | The sum of several random delays is not uniformly distributed; the pause keeps the same minimum and maximum. |
| TestPlan > Scoped Users (ThreadGroup) > Account (TransactionController) > GET /account/settings (HTTPSamplerProxy) | Several random timers summed into one uniform pause | The sum of several random delays is not uniformly distributed; the pause keeps the same minimum and maximum. |
| TestPlan > Scoped Users (ThreadGroup) | Closed model (3 threads, ramp-up 3 s) → open model rampUsers ... during | Each Gatling user runs the scenario once and exits, while JMeter keeps its threads looping. Concurrency matches only while every user is still in its loop. |

### Ignored elements

| Element path | Type | Why |
|---|---|---|
| TestPlan > Scoped Users (ThreadGroup) > Timer Without Samplers (GenericController) > Orphan Timer (ConstantTimer) | ConstantTimer | No sampler in its scope, so JMeter never applies it |

