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
| src/test/resources/fixtures/16-no-thread-groups.jmx | Jmx16NoThreadGroupsSimulation | 0 | 0 | 2 | 0 | 2 |

## src/test/resources/fixtures/16-no-thread-groups.jmx → Jmx16NoThreadGroupsSimulation

- Samplers converted: 0
- Samplers replaced by a no-op: 0
- TODOs: 2
- Approximations: 0
- Disabled or ignored elements: 2
- Files written: `java/golden/Jmx16NoThreadGroupsSimulation.java`

### TODOs

| # | Element path | Type | Reason |
|---|---|---|---|
| #1 | TestPlan > Plan DNS Cache (DNSCacheManager) | DNSCacheManager | DNSCacheManager not converted; it applied to every sampler in this scope |
| #2 | TestPlan | TestPlan | The plan has no enabled thread group, so the simulation injects no users |

### Approximations

None.

### Ignored elements

| Element path | Type | Why |
|---|---|---|
| TestPlan > Only Group, Disabled (ThreadGroup) | ThreadGroup | Disabled, with 1 element(s) inside |
| TestPlan > Defaults (ConfigTestElement) | ConfigTestElement (HttpDefaultsGui) | No sampler in its scope, so JMeter never applies it |

