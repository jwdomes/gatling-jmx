# jmx2gatling

Converts JMeter test plans (`.jmx`, JMeter 5.6.x) into Gatling simulations written in the
**Gatling 3.16 Java DSL**, plus a report that says exactly what was converted, what was
approximated and what still needs a person.

- One `Simulation` class per `.jmx` file. The generated code always compiles; anything that could
  not be converted is a numbered `// TODO(jmx2gatling) #<id>` marker with a matching row in the report.
- `inventory` counts element types and functions across many plans **without** revealing names,
  URLs, values or scripts, so its output can leave the network it was produced on.
- JDK only: no third-party libraries, no build tool, no network access needed.

## Build

You need a JDK, version 17 or newer. Nothing else.

```sh
./build.sh          # Linux / macOS / Unix
```

The script compiles `src/main/java` with `javac --release 17` and packages
`build/jmx2gatling.jar`. The jar contains only the converter's own classes.

The Gradle build (`./gradlew check`) is for development only: it runs the tests and compiles every
generated test simulation against Gatling. It downloads JUnit and Gatling, so it needs network
access; `build.sh` does not.

## Usage

```
java -jar jmx2gatling.jar inventory <file-or-dir>... [--json]
java -jar jmx2gatling.jar convert   <file-or-dir>... --out <dir> --package <pkg>
                                    [--bodies-dir <dir>] [--strict] [--no-timestamp]
```

Directories are searched recursively for `.jmx` files.

### inventory

Prints, as Markdown (or JSON with `--json`):

- each element type with its count in enabled and in disabled elements, and its support status,
- each JMeter function used, with counts and support status.

Rows are sorted unsupported first, so the output reads as a work list. Only type names, function
names, counts and support status are printed. Errors about unreadable files go to stderr, never
into the inventory.

### convert

| Option | Meaning |
|---|---|
| `--out <dir>` | Output root. Simulations go to `<dir>/java/<package path>/`, body files to `<dir>/resources/bodies/`, the report to `<dir>/conversion-report.md`. |
| `--package <pkg>` | Java package of the generated classes. |
| `--bodies-dir <dir>` | Write **every** request body to a file under `<dir>/bodies/...` (instead of `<dir>/resources`). Without it, bodies longer than 200 characters go to files and shorter ones stay inline. |
| `--strict` | Exit with code 2 if any TODO was emitted. |
| `--no-timestamp` | Leave the date out of the class comment and the report, so repeated runs are byte-identical. |

Exit codes: `0` success, `1` usage error or a file that could not be converted (the others are
still converted and listed in the report), `2` TODOs in `--strict` mode.

Class names come from the file name: `login-flow.jmx` → `LoginFlowSimulation`
(`Jmx` is prepended when the name starts with a digit, and a number is added if two files have the same name).

### Running a generated simulation

Copy `<out>/java/...` into the Java source folder of a Gatling 3.16 project (`src/gatling/java`
with the Gradle plugin, `src/test/java` with the Maven plugin) and `<out>/resources/...` into its
resources folder. Values that came from the plan are overridable at run time with Java system properties:

| JMeter | Gatling |
|---|---|
| Server name / protocol / port | `-DbaseUrl=https://host:port` (more hosts: `-DbaseUrl2=...`, ...) |
| `-Jname=value` read with `${__P(name,default)}` | `-Dname=value` (same name, same default) |
| Thread group that loops forever without a duration | `-DdurationSeconds=...` (default 600) |

## Reading the report

`conversion-report.md` starts with a summary table (one row per input file), then one section per file:

- **TODOs**: behavior that was not converted. Each row's `#<id>` matches a
  `// TODO(jmx2gatling) #<id>` marker in that file's simulation (numbers restart per file). Scripts
  and expressions are shown in a collapsed block under the table. A TODO always leaves compiling code:
  a no-op step, a placeholder value (for example `session -> true` for a condition) or a missing check.
- **Approximations**: converted, but Gatling behaves differently, and the row says how.
- **Ignored elements**: listeners, debug elements, disabled elements (with how many elements were
  inside them), and scoped elements that no sampler is in the scope of.

Every element appears with its path in the JMeter tree, for example
`TestPlan > Login Flow (ThreadGroup) > Auth (TransactionController) > POST /token (HTTPSamplerProxy)`.
The same paths (relative to the thread group) are the comments above each step in the generated code.

## Behavior differences worth knowing

These are reported per element where they apply; this is the overview.

- **Load model.** A JMeter thread group is a closed model (N threads that keep looping). It becomes
  `rampUsers(N).during(ramp)` where every Gatling user runs the loop once (`repeat(loops)` or
  `during(duration)`) and then exits.
- **Cookies.** Gatling always keeps cookies per user. Without an HTTP Cookie Manager JMeter sent none.
- **Caching.** Without an HTTP Cache Manager anywhere in the plan, the protocol gets `disableCaching()`.
- **Warm-up.** The protocol gets `disableWarmUp()`; otherwise Gatling sends an extra request to gatling.io.
- **Missing variables.** JMeter sends `${name}` literally when a variable is not set; Gatling fails the
  request. The report lists variables used but never set by a converted element.
- **Extractors.** JMeter extractors never fail a sample, so every extractor check gets `withDefault(...)`
  or `optional()`. JMeter's regex dialect (ORO) and JSONPath implementation (Jayway) differ slightly from Gatling's.
- **Variable names.** Gatling EL reads `#{a.b}` as "field b of a", so names with punctuation are renamed
  (`a.b` → `a_b`) everywhere they appear.
- **Checks.** Gatling checks are all required (AND); Response Assertions with "Or" become TODOs, except
  status codes, which become `status().in(...)`.

## Supported elements and functions

<!-- support-matrix:start (generated by ReadmeMatrixTest; do not edit by hand) -->
### Elements

Element types not listed are unsupported (TODO), except listeners, which are always ignored.

| JMeter element | Role | Support | Gatling |
|---|---|---|---|
| `TestPlan` | test plan | supported | One Simulation class; user-defined variables become initial session values |
| `ThreadGroup` | thread group | supported | Scenario + open injection profile (approximation of the closed model) |
| `SetupThreadGroup` | thread group | supported | Scenario chained before the main scenarios with andThen |
| `PostThreadGroup` | thread group | supported | Scenario chained after the main scenarios with andThen |
| `OpenModelThreadGroup` | thread group | unsupported | TODO: write the injection profile by hand |
| `kg.apc.jmeter.threads.UltimateThreadGroup` | thread group | unsupported | TODO: write the injection profile by hand |
| `kg.apc.jmeter.threads.SteppingThreadGroup` | thread group | unsupported | TODO: write the injection profile by hand |
| `com.blazemeter.jmeter.threads.concurrency.ConcurrencyThreadGroup` | thread group | unsupported | TODO: write the injection profile by hand |
| `com.blazemeter.jmeter.threads.arrivals.ArrivalsThreadGroup` | thread group | unsupported | TODO: write the injection profile by hand |
| `com.blazemeter.jmeter.threads.arrivals.FreeFormArrivalsThreadGroup` | thread group | unsupported | TODO: write the injection profile by hand |
| `HTTPSamplerProxy` | sampler | supported | http(name).<method>(path); embedded resources and some file uploads are TODOs |
| `DebugSampler` | sampler | supported | Skipped (no load effect), listed in the report |
| `JSR223Sampler` | sampler | unsupported | TODO + no-op; script kept as a comment |
| `BeanShellSampler` | sampler | unsupported | TODO + no-op; script kept as a comment |
| `TestAction` | sampler | unsupported | TODO + no-op (Flow Control Action) |
| `JDBCSampler` | sampler | unsupported | TODO + no-op (non-HTTP sampler) |
| `TCPSampler` | sampler | unsupported | TODO + no-op (non-HTTP sampler) |
| `JMSSampler` | sampler | unsupported | TODO + no-op (non-HTTP sampler) |
| `PublisherSampler` | sampler | unsupported | TODO + no-op (non-HTTP sampler) |
| `SubscriberSampler` | sampler | unsupported | TODO + no-op (non-HTTP sampler) |
| `FTPSampler` | sampler | unsupported | TODO + no-op (non-HTTP sampler) |
| `LDAPSampler` | sampler | unsupported | TODO + no-op (non-HTTP sampler) |
| `LDAPExtSampler` | sampler | unsupported | TODO + no-op (non-HTTP sampler) |
| `SmtpSampler` | sampler | unsupported | TODO + no-op (non-HTTP sampler) |
| `MailReaderSampler` | sampler | unsupported | TODO + no-op (non-HTTP sampler) |
| `JavaSampler` | sampler | unsupported | TODO + no-op (non-HTTP sampler) |
| `SystemSampler` | sampler | unsupported | TODO + no-op (non-HTTP sampler) |
| `AjpSampler` | sampler | unsupported | TODO + no-op (non-HTTP sampler) |
| `AccessLogSampler` | sampler | unsupported | TODO + no-op (non-HTTP sampler) |
| `TransactionController` | controller | supported | group(name).on(...) |
| `LoopController` | controller | supported | repeat(n).on(...), or forever() for infinite loops |
| `WhileController` | controller | partial | asLongAs(...) for simple conditions, TODO otherwise |
| `IfController` | controller | partial | doIf(...) for simple conditions, TODO otherwise |
| `RandomController` | controller | supported | uniformRandomSwitch() |
| `OnceOnlyController` | controller | supported | Hoisted before the loop, or guarded by a per-user session flag |
| `ForeachController` | controller | partial | foreach(list, var); input_N series are collected into a list first |
| `ThroughputController` | controller | partial | randomSwitch() approximation for percent mode, TODO for total-executions mode |
| `GenericController` | controller | supported | Children inlined (Simple Controller) |
| `RecordingController` | controller | supported | Children inlined |
| `InterleaveControl` | controller | unsupported | TODO; children are converted inline as if in a Simple Controller |
| `RandomOrderController` | controller | unsupported | TODO; children are converted inline as if in a Simple Controller |
| `SwitchController` | controller | unsupported | TODO; children are converted inline as if in a Simple Controller |
| `RunTime` | controller | unsupported | TODO; children are converted inline as if in a Simple Controller |
| `ModuleController` | controller | unsupported | TODO; children are converted inline as if in a Simple Controller |
| `IncludeController` | controller | unsupported | TODO; children are converted inline as if in a Simple Controller |
| `CriticalSectionController` | controller | unsupported | TODO; children are converted inline as if in a Simple Controller |
| `HeaderManager` | config | supported | .header(...) per request; headers common to every request move to httpProtocol |
| `ConfigTestElement (HttpDefaultsGui)` | config | supported | Fills missing protocol, host, port, path and arguments; host becomes the base URL |
| `CSVDataSet` | config | partial | csv(...) feeder; TODO when the file has no header row |
| `CookieManager` | config | supported | Gatling's built-in cookie handling; user-defined cookies become addCookie(...) |
| `CacheManager` | config | supported | Skipped: Gatling caches by default (caching is disabled when no Cache Manager exists) |
| `Arguments` | config | supported | Initial session values at scenario start |
| `AuthManager` | config | unsupported | TODO |
| `DNSCacheManager` | config | unsupported | TODO |
| `KeystoreConfig` | config | unsupported | TODO |
| `CounterConfig` | config | unsupported | TODO |
| `RandomVariableConfig` | config | unsupported | TODO |
| `JDBCDataSource` | config | unsupported | TODO |
| `LoginConfig` | config | unsupported | TODO |
| `JSONPostProcessor` | post processor | supported | check(jsonPath(...).saveAs(...)), one per variable |
| `RegexExtractor` | post processor | partial | check(regex(...).saveAs(...)); only the $1$ template and response body |
| `BoundaryExtractor` | post processor | supported | check(regex(quoted boundaries).saveAs(...)); response body only |
| `DebugPostProcessor` | post processor | supported | Skipped (no load effect), listed in the report |
| `JSR223PostProcessor` | post processor | unsupported | TODO; script kept as a comment |
| `BeanShellPostProcessor` | post processor | unsupported | TODO; script kept as a comment |
| `XPathExtractor` | post processor | unsupported | TODO |
| `XPath2Extractor` | post processor | unsupported | TODO |
| `HtmlExtractor` | post processor | unsupported | TODO |
| `JMESPathExtractor` | post processor | unsupported | TODO |
| `ResultAction` | post processor | unsupported | TODO |
| `JDBCPostProcessor` | post processor | unsupported | TODO |
| `JSR223PreProcessor` | pre processor | unsupported | TODO; script kept as a comment |
| `BeanShellPreProcessor` | pre processor | unsupported | TODO; script kept as a comment |
| `UserParameters` | pre processor | unsupported | TODO |
| `RegExUserParameters` | pre processor | unsupported | TODO |
| `AnchorModifier` | pre processor | unsupported | TODO |
| `URLRewritingModifier` | pre processor | unsupported | TODO |
| `SampleTimeout` | pre processor | unsupported | TODO |
| `JDBCPreProcessor` | pre processor | unsupported | TODO |
| `ResponseAssertion` | assertion | partial | status()/substring()/regex()/bodyString()/headerRegex() checks; Or and some fields are TODOs |
| `JSONPathAssertion` | assertion | supported | jsonPath(...).exists()/is(...) checks |
| `JSR223Assertion` | assertion | unsupported | TODO; script kept as a comment |
| `BeanShellAssertion` | assertion | unsupported | TODO; script kept as a comment |
| `DurationAssertion` | assertion | unsupported | TODO |
| `SizeAssertion` | assertion | unsupported | TODO |
| `XPathAssertion` | assertion | unsupported | TODO |
| `XPath2Assertion` | assertion | unsupported | TODO |
| `MD5HexAssertion` | assertion | unsupported | TODO |
| `HTMLAssertion` | assertion | unsupported | TODO |
| `XMLAssertion` | assertion | unsupported | TODO |
| `XMLSchemaAssertion` | assertion | unsupported | TODO |
| `CompareAssertion` | assertion | unsupported | TODO |
| `SMIMEAssertionTestElement` | assertion | unsupported | TODO |
| `JMESPathAssertion` | assertion | unsupported | TODO |
| `ConstantTimer` | timer | supported | pause(Duration.ofMillis(d)) before each sampler in scope |
| `UniformRandomTimer` | timer | supported | pause(min, max) before each sampler in scope |
| `GaussianRandomTimer` | timer | partial | Uniform pause with the same mean (Gatling has no per-pause normal distribution) |
| `ConstantThroughputTimer` | timer | unsupported | TODO; throttle(...) is the closest Gatling equivalent |
| `PreciseThroughputTimer` | timer | unsupported | TODO; throttle(...) is the closest Gatling equivalent |
| `SyncTimer` | timer | unsupported | TODO; throttle(...) is the closest Gatling equivalent |
| `PoissonRandomTimer` | timer | unsupported | TODO |
| `JSR223Timer` | timer | unsupported | TODO |
| `BeanShellTimer` | timer | unsupported | TODO |
| `ResultCollector` | listener | supported | Ignored (reporting only) |
| `Summariser` | listener | supported | Ignored (reporting only) |
| `BackendListener` | listener | supported | Ignored (reporting only) |
| `JSR223Listener` | listener | supported | Ignored (reporting only) |
| `BeanShellListener` | listener | supported | Ignored (reporting only) |

### Functions

Functions not listed are unsupported: they stay in the request verbatim with a TODO.

| JMeter function | Support | Gatling |
|---|---|---|
| `__UUID` | supported | #{randomUuid()} |
| `__time` | supported | #{currentTimeMillis()}, #{currentDate(fmt)} or a session function |
| `__Random` | supported | #{randomInt(min,max+1)} (Gatling's upper bound is exclusive) |
| `__RandomString` | supported | Session function |
| `__threadNum` | supported | #{userId()} (numbering differs, see report) |
| `__counter` | supported | Per-user session counter or global AtomicLong |
| `__P` | supported | Java system property (-Dname=value) |
| `__property` | supported | Java system property (-Dname=value) |
| `__groovy` | partial | Simple conditions only; TODO otherwise |
| `__jexl3` | partial | Simple conditions only; TODO otherwise |
| `__jexl2` | partial | Simple conditions only; TODO otherwise |
| `__javaScript` | partial | Simple conditions only; TODO otherwise |
| `__BeanShell` | partial | Simple conditions only; TODO otherwise |

<!-- support-matrix:end -->

## Development

```sh
./gradlew check                       # unit, golden, leak and determinism tests + Gatling compile check
./gradlew test -Dgolden.update=true   # regenerate the golden outputs and this README's matrix
```

- `src/test/resources/fixtures/*.jmx`: hand-written JMeter 5.6.3 plans covering every supported,
  partial and unsupported element.
- `src/test/resources/golden/<fixture>/`: the expected simulation, body files and report for each fixture.
- `compile-check/`: compiles every golden simulation against `gatling-core-java` and `gatling-http-java` 3.16.0.
  Since the golden test proves the output equals the goldens, this proves the output compiles.
- `InventoryLeakTest` asserts the inventory contains no names, URLs, bodies, values or scripts from the fixtures.

### Source map

| Package | What it does |
|---|---|
| `jmx2gatling` | Command line (`Main`). |
| `jmx2gatling.parse` | JMX → element tree (`JmxParser`, DOM with external entities disabled) and JMeter `${...}` expression parsing. |
| `jmx2gatling.model` | The element tree and typed property access with JMeter defaults. |
| `jmx2gatling.inventory` | `SupportMatrix` (the single table of what is supported), the `inventory` command. |
| `jmx2gatling.convert` | Conversion. `GatlingDsl` holds every Gatling API call the converter emits; review it when moving to a new Gatling version. `Scope` propagates headers, defaults, timers and checks down the tree. |
| `jmx2gatling.report` | Findings (TODOs, approximations, ignored elements) and `conversion-report.md`. |
