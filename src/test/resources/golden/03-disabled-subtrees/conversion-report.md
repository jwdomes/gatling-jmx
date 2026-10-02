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
| src/test/resources/fixtures/03-disabled-subtrees.jmx | Jmx03DisabledSubtreesSimulation | 1 | 0 | 0 | 2 | 4 |

## src/test/resources/fixtures/03-disabled-subtrees.jmx → Jmx03DisabledSubtreesSimulation

- Samplers converted: 1
- Samplers replaced by a no-op: 0
- TODOs: 0
- Approximations: 2
- Disabled or ignored elements: 4
- Files written: `java/golden/Jmx03DisabledSubtreesSimulation.java`

### TODOs

None.

### Approximations

| Element path | What was done | How it differs from JMeter |
|---|---|---|
| TestPlan > Active Flow (ThreadGroup) | No HTTP Cookie Manager in this thread group | JMeter sent no cookies back; Gatling always keeps and resends cookies. Add flushCookieJar() calls if the difference matters. |
| TestPlan > Active Flow (ThreadGroup) | Closed model (2 threads, ramp-up 4 s) → open model rampUsers ... during | Each Gatling user runs the scenario once and exits, while JMeter keeps its threads looping. Concurrency matches only while every user is still in its loop. |

### Ignored elements

| Element path | Type | Why |
|---|---|---|
| TestPlan > Active Flow (ThreadGroup) > Disabled Debug Header (HeaderManager) | HeaderManager | Disabled |
| TestPlan > Active Flow (ThreadGroup) > GET /legacy-disabled (HTTPSamplerProxy) | HTTPSamplerProxy | Disabled |
| TestPlan > Active Flow (ThreadGroup) > Old Checkout (TransactionController) | TransactionController | Disabled, with 3 element(s) inside |
| TestPlan > Retired Flow (ThreadGroup) | ThreadGroup | Disabled, with 1 element(s) inside |

