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
| src/test/resources/fixtures/13-unsupported-elements.jmx | Jmx13UnsupportedElementsSimulation | 7 | 7 | 21 | 3 | 5 |

## src/test/resources/fixtures/13-unsupported-elements.jmx → Jmx13UnsupportedElementsSimulation

- Samplers converted: 7
- Samplers replaced by a no-op: 7
- TODOs: 21
- Approximations: 3
- Disabled or ignored elements: 5
- Files written: `java/golden/Jmx13UnsupportedElementsSimulation.java`

### TODOs

| # | Element path | Type | Reason |
|---|---|---|---|
| #1 | TestPlan > Client Certificates (KeystoreConfig) | KeystoreConfig | KeystoreConfig not converted; it applied to every sampler in this scope |
| #2 | TestPlan > Unsupported Things (ThreadGroup) > Groovy Setup Script (JSR223Sampler) | JSR223Sampler | Script sampler not converted; replaced by a no-op |
| #3 | TestPlan > Unsupported Things (ThreadGroup) > BeanShell Sampler (BeanShellSampler) | BeanShellSampler | Script sampler not converted; replaced by a no-op |
| #4 | TestPlan > Unsupported Things (ThreadGroup) > Query Orders DB (JDBCSampler) | JDBCSampler | Non-HTTP sampler JDBCSampler not converted; replaced by a no-op |
| #5 | TestPlan > Unsupported Things (ThreadGroup) > Raw TCP Ping (TCPSampler) | TCPSampler | Non-HTTP sampler TCPSampler not converted; replaced by a no-op |
| #6 | TestPlan > Unsupported Things (ThreadGroup) > Publish To Queue (PublisherSampler) | PublisherSampler | Non-HTTP sampler PublisherSampler not converted; replaced by a no-op |
| #7 | TestPlan > Unsupported Things (ThreadGroup) > Pause 1s (Flow Control) (TestAction) | TestAction | Non-HTTP sampler TestAction not converted; replaced by a no-op |
| #8 | TestPlan > Unsupported Things (ThreadGroup) > Plugin Sampler (com.example.jmeter.FancySampler) | com.example.jmeter.FancySampler | Non-HTTP sampler com.example.jmeter.FancySampler not converted; replaced by a no-op |
| #9 | TestPlan > Unsupported Things (ThreadGroup) > GET /target-of-unsupported (HTTPSamplerProxy) > Ten Per Minute (ConstantThroughputTimer) | ConstantThroughputTimer | ConstantThroughputTimer not converted; the closest Gatling equivalent is throttle(...) on the setUp |
| #10 | TestPlan > Unsupported Things (ThreadGroup) > GET /target-of-unsupported (HTTPSamplerProxy) > Poisson Pause (PoissonRandomTimer) | PoissonRandomTimer | PoissonRandomTimer not converted |
| #11 | TestPlan > Unsupported Things (ThreadGroup) > GET /target-of-unsupported (HTTPSamplerProxy) > Legacy Signer (BeanShellPreProcessor) | BeanShellPreProcessor | Script pre-processor not converted |
| #12 | TestPlan > Unsupported Things (ThreadGroup) > GET /target-of-unsupported (HTTPSamplerProxy) > Per User Params (UserParameters) | UserParameters | UserParameters not converted |
| #13 | TestPlan > Unsupported Things (ThreadGroup) > Basic Auth (AuthManager) | AuthManager | AuthManager not converted; it applied to every sampler in this scope |
| #14 | TestPlan > Unsupported Things (ThreadGroup) > DNS Cache (DNSCacheManager) | DNSCacheManager | DNSCacheManager not converted; it applied to every sampler in this scope |
| #15 | TestPlan > Unsupported Things (ThreadGroup) > Order Counter (CounterConfig) | CounterConfig | CounterConfig not converted; it applied to every sampler in this scope |
| #16 | TestPlan > Unsupported Things (ThreadGroup) > Random Amount (RandomVariableConfig) | RandomVariableConfig | RandomVariableConfig not converted; it applied to every sampler in this scope |
| #17 | TestPlan > Unsupported Things (ThreadGroup) > Interleave (InterleaveControl) | InterleaveControl | InterleaveControl not converted; its children are converted inline as if in a Simple Controller |
| #18 | TestPlan > Unsupported Things (ThreadGroup) > Run For 30s (RunTime) | RunTime | RunTime not converted; its children are converted inline as if in a Simple Controller |
| #19 | TestPlan > Unsupported Things (ThreadGroup) > Include Shared Login (IncludeController) | IncludeController | IncludeController not converted; convert the referenced test fragment and call it here |
| #20 | TestPlan > Unsupported Things (ThreadGroup) > One At A Time (CriticalSectionController) | CriticalSectionController | CriticalSectionController not converted; its children are converted inline as if in a Simple Controller |
| #21 | TestPlan > Ultimate Thread Group (kg.apc.jmeter.threads.UltimateThreadGroup) | kg.apc.jmeter.threads.UltimateThreadGroup | kg.apc.jmeter.threads.UltimateThreadGroup load profile not converted; placeholder atOnceUsers(1). Write the injection profile by hand |

<details><summary>#2 original content</summary>

```
[language: groovy]
import groovy.json.JsonSlurper
/* a block comment that ends here */ def x = "\u0041 is A"
def unicodeLooking = "\\u0022 should stay text"
vars.put("fromScript", new JsonSlurper().parseText(prev?.getResponseDataAsString() ?: "{}").id ?: "unsupported-script-value")
```

</details>

<details><summary>#3 original content</summary>

```
return "unsupported-beanshell-result";
```

</details>

<details><summary>#11 original content</summary>

```
vars.put("sig", "unsupported-bsh-signature");
```

</details>

<details><summary>#19 original content</summary>

```
[include file: fragments/shared-login.jmx]
```

</details>

### Approximations

| Element path | What was done | How it differs from JMeter |
|---|---|---|
| TestPlan > Unsupported Things (ThreadGroup) | No HTTP Cookie Manager in this thread group | JMeter sent no cookies back; Gatling always keeps and resends cookies. Add flushCookieJar() calls if the difference matters. |
| TestPlan > Unsupported Things (ThreadGroup) | Closed model (1 threads, ramp-up 0 s) → open model atOnceUsers | Each Gatling user runs the scenario once and exits, while JMeter keeps its threads looping. Concurrency matches only while every user is still in its loop. |
| TestPlan > Ultimate Thread Group (kg.apc.jmeter.threads.UltimateThreadGroup) | No HTTP Cookie Manager in this thread group | JMeter sent no cookies back; Gatling always keeps and resends cookies. Add flushCookieJar() calls if the difference matters. |

### Ignored elements

| Element path | Type | Why |
|---|---|---|
| TestPlan > Unsupported Things (ThreadGroup) > Debug Sampler (DebugSampler) | DebugSampler | Debug Sampler has no load effect |
| TestPlan > Unsupported Things (ThreadGroup) > Aggregate (ResultCollector) | ResultCollector | Listener (reporting only) |
| TestPlan > Unsupported Things (ThreadGroup) > jp@gc - Response Times Over Time (kg.apc.jmeter.vizualizers.CorrectedResultCollector) | kg.apc.jmeter.vizualizers.CorrectedResultCollector | Listener (reporting only) |
| TestPlan > Unsupported Things (ThreadGroup) > InfluxDB (BackendListener) | BackendListener | Listener (reporting only) |
| TestPlan > Unsupported Things (ThreadGroup) > Log Failures (JSR223Listener) | JSR223Listener | Listener (reporting only) |

