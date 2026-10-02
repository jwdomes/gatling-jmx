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
| src/test/resources/fixtures/02-nested-controllers.jmx | Jmx02NestedControllersSimulation | 8 | 0 | 0 | 1 | 0 |

## src/test/resources/fixtures/02-nested-controllers.jmx → Jmx02NestedControllersSimulation

- Samplers converted: 8
- Samplers replaced by a no-op: 0
- TODOs: 0
- Approximations: 1
- Disabled or ignored elements: 0
- Files written: `java/golden/Jmx02NestedControllersSimulation.java`

### TODOs

None.

### Approximations

| Element path | What was done | How it differs from JMeter |
|---|---|---|
| TestPlan > Checkout Flow (ThreadGroup) | Closed model (10 threads, ramp-up 0 s) → open model atOnceUsers | Each Gatling user runs the scenario once and exits, while JMeter keeps its threads looping. Concurrency matches only while every user is still in its loop. |

### Ignored elements

None.

