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
| src/test/resources/fixtures/01-basic-get-post.jmx | Jmx01BasicGetPostSimulation | 4 | 0 | 0 | 1 | 3 |

## src/test/resources/fixtures/01-basic-get-post.jmx → Jmx01BasicGetPostSimulation

- Samplers converted: 4
- Samplers replaced by a no-op: 0
- TODOs: 0
- Approximations: 1
- Disabled or ignored elements: 3
- Files written: `java/golden/Jmx01BasicGetPostSimulation.java`, `resources/bodies/jmx01-basic-get-post/post-api-orders.json`

### TODOs

None.

### Approximations

| Element path | What was done | How it differs from JMeter |
|---|---|---|
| TestPlan > Browse and Order (ThreadGroup) | Closed model (5 threads, ramp-up 10 s) → open model rampUsers ... during | Each Gatling user runs the scenario once and exits, while JMeter keeps its threads looping. Concurrency matches only while every user is still in its loop. |

### Ignored elements

| Element path | Type | Why |
|---|---|---|
| TestPlan > Browse and Order (ThreadGroup) > View Results Tree (ResultCollector) | ResultCollector | Listener (reporting only) |
| TestPlan > Summary Report (ResultCollector) | ResultCollector | Listener (reporting only) |
| TestPlan > Browse and Order (ThreadGroup) > HTTP Cache Manager (CacheManager) | CacheManager | Gatling's built-in HTTP cache plays this role |

