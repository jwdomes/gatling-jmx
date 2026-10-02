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
| src/test/resources/fixtures/08-functions-variables.jmx | Jmx08FunctionsVariablesSimulation | 7 | 0 | 5 | 8 | 0 |

## src/test/resources/fixtures/08-functions-variables.jmx → Jmx08FunctionsVariablesSimulation

- Samplers converted: 7
- Samplers replaced by a no-op: 0
- TODOs: 5
- Approximations: 8
- Disabled or ignored elements: 0
- Files written: `java/golden/Jmx08FunctionsVariablesSimulation.java`

### TODOs

| # | Element path | Type | Reason |
|---|---|---|---|
| #1 | TestPlan > Function Users (ThreadGroup) > GET random (HTTPSamplerProxy) | HTTPSamplerProxy | __RandomString with non-literal arguments |
| #2 | TestPlan > Function Users (ThreadGroup) > GET unsupported functions (HTTPSamplerProxy) | HTTPSamplerProxy | Script function __groovy not converted; kept verbatim |
| #3 | TestPlan > Function Users (ThreadGroup) > GET unsupported functions (HTTPSamplerProxy) | HTTPSamplerProxy | Function __urlencode not supported; kept verbatim |
| #4 | TestPlan > Function Users (ThreadGroup) > GET unsupported functions (HTTPSamplerProxy) | HTTPSamplerProxy | JMeter built-in variable ${__jm__Function Users__idx} has no direct Gatling equivalent; kept verbatim |
| #5 | TestPlan | TestPlan | User-defined variable `startedAt` uses functions or runtime variables; kept as literal text |

<details><summary>#1 original content</summary>

```
${__RandomString(${len},abc)}
```

</details>

<details><summary>#2 original content</summary>

```
${__groovy(new Date().format('yyyy'),)}
```

</details>

<details><summary>#3 original content</summary>

```
${__urlencode(fn value with spaces)}
```

</details>

<details><summary>#4 original content</summary>

```
${__jm__Function Users__idx}
```

</details>

<details><summary>#5 original content</summary>

```
${__time()}
```

</details>

### Approximations

| Element path | What was done | How it differs from JMeter |
|---|---|---|
| TestPlan > Function Users (ThreadGroup) > GET ids (HTTPSamplerProxy) | `${__threadNum}` → `#{userId()}` | JMeter numbers threads from 1 within each thread group; Gatling's user id is unique across the whole simulation. |
| TestPlan > Function Users (ThreadGroup) > GET ids (HTTPSamplerProxy) | Variable `api.key` renamed to `api_key` | Gatling EL treats `.` and other punctuation in names as attribute access, so the name was made safe everywhere it is used. |
| TestPlan > Function Users (ThreadGroup) > GET random (HTTPSamplerProxy) | `__RandomString` without a character set uses letters and digits | JMeter picks from all characters when no set is given; the converter uses A-Z, a-z and 0-9. |
| TestPlan > Function Users (ThreadGroup) > GET properties (HTTPSamplerProxy) | JMeter property `apiVersion` read as Java system property `apiVersion` | Pass `-DapiVersion=...` to Gatling instead of `-JapiVersion=...` to JMeter. Default when unset: `v1`. |
| TestPlan > Function Users (ThreadGroup) > GET properties (HTTPSamplerProxy) | JMeter property `region` read as Java system property `region` | Pass `-Dregion=...` to Gatling instead of `-Jregion=...` to JMeter. Default when unset: `eu-west-functions`. |
| TestPlan > Function Users (ThreadGroup) > GET properties (HTTPSamplerProxy) | JMeter property `pageSize` read as Java system property `pageSize` | Pass `-DpageSize=...` to Gatling instead of `-JpageSize=...` to JMeter. Default when unset: `1`. |
| TestPlan > Function Users (ThreadGroup) | Closed model (2 threads, ramp-up 2 s) → open model rampUsers ... during | Each Gatling user runs the scenario once and exits, while JMeter keeps its threads looping. Concurrency matches only while every user is still in its loop. |
| TestPlan | JMeter property `greeting` read as Java system property `greeting` | Pass `-Dgreeting=...` to Gatling instead of `-Jgreeting=...` to JMeter. Default when unset: `hello`. |

### Ignored elements

None.

