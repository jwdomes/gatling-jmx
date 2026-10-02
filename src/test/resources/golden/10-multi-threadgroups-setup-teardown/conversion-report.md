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
| src/test/resources/fixtures/10-multi-threadgroups-setup-teardown.jmx | Jmx10MultiThreadgroupsSetupTeardownSimulation | 5 | 0 | 0 | 7 | 0 |

## src/test/resources/fixtures/10-multi-threadgroups-setup-teardown.jmx → Jmx10MultiThreadgroupsSetupTeardownSimulation

- Samplers converted: 5
- Samplers replaced by a no-op: 0
- TODOs: 0
- Approximations: 7
- Disabled or ignored elements: 0
- Files written: `java/golden/Jmx10MultiThreadgroupsSetupTeardownSimulation.java`

### TODOs

None.

### Approximations

| Element path | What was done | How it differs from JMeter |
|---|---|---|
| TestPlan > Seed Data (SetupThreadGroup) | Closed model (1 threads, ramp-up 1 s) → open model rampUsers ... during | Each Gatling user runs the scenario once and exits, while JMeter keeps its threads looping. Concurrency matches only while every user is still in its loop. |
| TestPlan > Browsers (ThreadGroup) | Closed model (20 threads, ramp-up 10 s) → open model rampUsers ... during | Each Gatling user runs the scenario once and exits, while JMeter keeps its threads looping. Concurrency matches only while every user is still in its loop. |
| TestPlan > Buyers (ThreadGroup) | Closed model (5 threads, ramp-up 10 s) → open model rampUsers ... during | Each Gatling user runs the scenario once and exits, while JMeter keeps its threads looping. Concurrency matches only while every user is still in its loop. |
| TestPlan > Browsers (ThreadGroup) | Closed model (1 threads, ramp-up 1 s) → open model rampUsers ... during | Each Gatling user runs the scenario once and exits, while JMeter keeps its threads looping. Concurrency matches only while every user is still in its loop. |
| TestPlan > Browsers (ThreadGroup) | Scenario renamed to "Browsers (2)" | Gatling requires unique scenario names. |
| TestPlan > Cleanup (PostThreadGroup) | Closed model (1 threads, ramp-up 1 s) → open model rampUsers ... during | Each Gatling user runs the scenario once and exits, while JMeter keeps its threads looping. Concurrency matches only while every user is still in its loop. |
| TestPlan | The next group of thread groups starts after "Browsers" finishes | Gatling's andThen chains from one scenario; JMeter waited for all of: Browsers, Buyers, Browsers (2). |

### Ignored elements

None.

