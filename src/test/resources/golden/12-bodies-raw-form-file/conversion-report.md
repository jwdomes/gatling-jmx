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
| src/test/resources/fixtures/12-bodies-raw-form-file.jmx | Jmx12BodiesRawFormFileSimulation | 17 | 0 | 3 | 6 | 1 |

## src/test/resources/fixtures/12-bodies-raw-form-file.jmx → Jmx12BodiesRawFormFileSimulation

- Samplers converted: 17
- Samplers replaced by a no-op: 0
- TODOs: 3
- Approximations: 6
- Disabled or ignored elements: 1
- Files written: `java/golden/Jmx12BodiesRawFormFileSimulation.java`, `resources/bodies/jmx12-bodies-raw-form-file/post-soap-quote.xml`

### TODOs

| # | Element path | Type | Reason |
|---|---|---|---|
| #1 | TestPlan > Body Senders (ThreadGroup) > GET /page-with-assets (HTTPSamplerProxy) | HTTPSamplerProxy | "Retrieve all embedded resources" is on; Gatling does not fetch them unless you add http.inferHtmlResources() to the protocol or list them with .resources(...) |
| #2 | TestPlan > Body Senders (ThreadGroup) > GET with body (HTTPSamplerProxy) | HTTPSamplerProxy | Request body on a GET request is not sent by Gatling's GET DSL |
| #3 | TestPlan > Body Senders (ThreadGroup) > POST mixed upload (HTTPSamplerProxy) | HTTPSamplerProxy | File upload mixing a raw file body with parameters |

<details><summary>#2 original content</summary>

```
{"q":"get-with-body"}
```

</details>

### Approximations

| Element path | What was done | How it differs from JMeter |
|---|---|---|
| TestPlan > Body Senders (ThreadGroup) > POST /documents (multipart) (HTTPSamplerProxy) | Upload file `/uploads/bodies-report.pdf` referenced by path | Gatling resolves the path on its classpath (the resources folder) first, then on the file system. Copy the file next to the simulation's resources or keep it at this path. |
| TestPlan > Body Senders (ThreadGroup) > POST /import (file as body) (HTTPSamplerProxy) | Upload file `data/bodies-import.bin` referenced by path | Gatling resolves the path on its classpath (the resources folder) first, then on the file system. Copy the file next to the simulation's resources or keep it at this path. |
| TestPlan > Body Senders (ThreadGroup) > GET /slow (HTTPSamplerProxy) | HTTPSampler.connect_timeout = 2000 not converted | Gatling has no per-request timeout; set gatling.http.requestTimeout in gatling.conf if it matters. |
| TestPlan > Body Senders (ThreadGroup) > GET /slow (HTTPSamplerProxy) | HTTPSampler.response_timeout = 10000 not converted | Gatling has no per-request timeout; set gatling.http.requestTimeout in gatling.conf if it matters. |
| TestPlan > Body Senders (ThreadGroup) > GET /slow (HTTPSamplerProxy) | Content encoding ISO-8859-1 not converted | Gatling encodes request bodies and parameters as UTF-8 by default. |
| TestPlan > Body Senders (ThreadGroup) | Closed model (1 threads, ramp-up 0 s) → open model atOnceUsers | Each Gatling user runs the scenario once and exits, while JMeter keeps its threads looping. Concurrency matches only while every user is still in its loop. |

### Ignored elements

| Element path | Type | Why |
|---|---|---|
| TestPlan > HTTP Cache Manager (CacheManager) | CacheManager | Gatling's built-in HTTP cache plays this role |

