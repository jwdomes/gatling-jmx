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
| src/test/resources/fixtures/14-mixed-hosts.jmx | Jmx14MixedHostsSimulation | 8 | 0 | 0 | 3 | 0 |

## src/test/resources/fixtures/14-mixed-hosts.jmx → Jmx14MixedHostsSimulation

- Samplers converted: 8
- Samplers replaced by a no-op: 0
- TODOs: 0
- Approximations: 3
- Disabled or ignored elements: 0
- Files written: `java/golden/Jmx14MixedHostsSimulation.java`

### TODOs

None.

### Approximations

| Element path | What was done | How it differs from JMeter |
|---|---|---|
| TestPlan > Host Hopper (ThreadGroup) > GET tenant home (runtime host) (HTTPSamplerProxy) | Host depends on runtime variables, so the request uses a full URL | This request does not use the protocol's base URL. |
| TestPlan > Host Hopper (ThreadGroup) | Closed model (1 threads, ramp-up 0 s) → open model atOnceUsers | Each Gatling user runs the scenario once and exits, while JMeter keeps its threads looping. Concurrency matches only while every user is still in its loop. |
| TestPlan | Requests go to 4 hosts | The most used host (https://api.example-hosts.test) is the base URL; requests to https://auth.example-hosts.test, http://api.example-hosts.test:8080, https://cdn.example-hosts.test use full URLs built from their own BASE_URL_n constants. |

### Ignored elements

None.

