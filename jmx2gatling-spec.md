# Task: JMeter JMX → Gatling Java DSL Converter (`jmx2gatling`)

## Context

I need a command-line tool that converts JMeter test plans (`.jmx`) into Gatling simulations written in the Java DSL.

You are building this **off my enterprise network**. The tool will then be run **on** the enterprise network, where no AI tooling is available. You will never see the real `.jmx` files. That has three consequences for how you build it:

- **Develop and test against synthetic fixtures** that you author yourself, following JMeter 5.6.x serialization.
- **The tool must be fully self-sufficient.** Its output and its conversion report must make it obvious what was converted, what was approximated, and what needs manual work, without anyone having to read the converter's source.
- **The source must be easy to review.** Enterprise reviewers may read it before approving it.

## Hard Constraints

- **Java 17**, using **JDK-only production code** with zero third-party dependencies. Parse XML with `javax.xml.parsers` (DOM). Generate code with plain string building or text blocks (no JavaPoet, no template engines).
- **The tool must be buildable with plain `javac` and `jar`.** Provide a `build.sh` and a `build.cmd` that compile `src/main/java` into a runnable jar with no build tool and no network access. You may also provide a Gradle build for development convenience, but it must not be required.
- Test-only dependencies (JUnit 5, and Gatling for compile checks) are allowed in the development build only. The production jar must not contain or require them.
- **Generated simulations target the Gatling 3.x Java DSL.** Use the latest stable 3.x release. Isolate version-sensitive API calls (EL built-ins, injection DSL) in one place, and verify each one against Gatling's documentation for that version rather than from memory.
- **Generated code must always compile.** Unsupported elements become comments and `// TODO(jmx2gatling)` markers, never broken code.
- **Never silently drop behavior.** Anything not converted faithfully must appear in the conversion report.

## CLI

```
java -jar jmx2gatling.jar inventory <file-or-dir>...
java -jar jmx2gatling.jar convert   <file-or-dir>... --out <dir> --package <pkg>
                                    [--bodies-dir <dir>] [--strict]
```

### `inventory`

The inventory command scans `.jmx` files and prints:
- the count of each `testclass`, including disabled elements counted separately,
- the count of each JMeter function used (`__UUID`, `__P`, `__groovy`, and so on),
- the support status of each item: `supported`, `partial`, or `unsupported`.

**The output must contain no payload content, URLs, hostnames, variable values, or script text.** Only element type names, function names, counts, and support status are allowed. This lets me carry the inventory off the network to prioritize further work. Write it as Markdown to stdout, with an optional `--json` flag.

### `convert`

- Generates one Gatling `Simulation` class per `.jmx` file, named after the file (sanitized to PascalCase plus the suffix `Simulation`).
- Writes request bodies longer than about 200 characters (or all bodies, with `--bodies-dir`) as resource files, which the generated code references with `ElFileBody("bodies/<simulation>/<sampler>.json")`. Short bodies stay inline as `StringBody` with Java text blocks.
- Writes `conversion-report.md` next to the output (see "Conversion report").
- With `--strict`, exits non-zero if any TODO was emitted.
- Processes a directory recursively.

## JMX Parsing Rules

- **Children are siblings.** In JMX, an element's children are not nested inside it; they are inside the `<hashTree>` element that immediately follows it. Walk element/`hashTree` pairs. Build an internal tree model first, and only then generate code.
- **Skip disabled elements.** Skip `enabled="false"` elements and their entire subtree, and list them in the report.
- **Parse property types.** Read `stringProp`, `boolProp`, `intProp`, `longProp`, `elementProp`, and `collectionProp`. Treat missing properties as JMeter's defaults.
- **Use element paths in messages.** Report every element with its tree path, for example `TestPlan > Login Flow (ThreadGroup) > Auth (TransactionController) > POST /token (HTTPSamplerProxy)`.

## Scoping Semantics

These rules must match JMeter's behavior:

- **Header Managers** apply to every sampler in their scope. Nested managers merge, and closer managers win per header name.
- **HTTP Request Defaults** (`ConfigTestElement` with `HttpDefaultsGui`) fill in missing protocol, domain, port, and path prefix on samplers in scope.
- **Assertions, post-processors, and pre-processors** placed at a controller level apply to **every sampler** under that controller. Placed as a sampler's child, they apply to that sampler only. Propagate them down to each affected sampler.
- **Timers** in scope apply **before each sampler** in that scope. Multiple timers in scope add together.

## Element Mapping

### Plan and thread groups

| JMeter | Gatling |
|---|---|
| `TestPlan` user-defined variables | Constants in the class, or initial session values via `exec(session -> session.set(...))` at scenario start |
| `TestPlan` `serialize_threadgroups=true` | Chain the scenarios with `andThen` |
| `ThreadGroup` | One `ScenarioBuilder` and one injection profile |
| `SetupThreadGroup` / `PostThreadGroup` | Separate scenarios chained before or after the main scenarios with `andThen` |

Map the injection profile from JMeter's closed model as follows, recording the mapping in the report:

| JMeter thread group | Gatling |
|---|---|
| N threads, ramp R, loops L (finite) | `rampUsers(N).during(R)` with the scenario body wrapped in `repeat(L)` |
| N threads, ramp R, loops infinite (`-1`) with scheduler duration D | `rampUsers(N).during(R)` with the scenario body wrapped in `during(D)` |
| Scheduler startup delay | `nothingFor(delay)` before the injection steps |
| Infinite loops with no duration | Same as the duration case, plus a TODO and a `maxDuration` on the simulation |

- **Properties.** `${__P(name,default)}` and `${__property(...)}` in thread counts, durations, or hostnames become `Integer.getInteger("name", default)` or `System.getProperty("name", "default")` at class level, so they stay overridable at run time.
- **Base URL.** Generate the base URL as `System.getProperty("baseUrl", "<value from JMX>")`. If samplers use different hosts, use full URLs per request and note this in the report.
- **On-sample-error actions** other than "continue" become a TODO with a note. Use `exitHereIfFailed()` only for "start next thread loop" and "stop thread", and flag the approximation.

### Samplers

`HTTPSamplerProxy` maps to `http("<sampler name>").<method>(path)`, with these details:
- When `HTTPSampler.postBodyRaw=true`, use the `Argument.value` of the first argument as the body.
- Otherwise, map the arguments to `.queryParam(...)` for GET requests and `.formParam(...)` for POST and PUT requests.
- File uploads (`HTTPFileArgs`) become `RawFileBody` or `bodyPart` where they map cleanly, and a TODO otherwise.
- `follow_redirects=false` becomes `.disableFollowRedirect()`.
- `image_parser=true` (download embedded resources) becomes a TODO, with a note about `inferHtmlResources()`.

The following samplers are **not supported** and become a TODO comment plus a no-op:
- `JSR223Sampler` and `BeanShellSampler`: include the original script as a comment, escaping `*/`.
- `DebugSampler`: skip it, noting this in the report.
- Any non-HTTP sampler (JDBC, JMS, TCP, and so on).

### Controllers

| JMeter | Gatling |
|---|---|
| `TransactionController` | `group("<name>").on(...)` |
| `LoopController` (nested) | `repeat(n)` |
| `WhileController` | `asLongAs(...)` when the condition is simple (see below), TODO otherwise |
| `IfController` | `doIf(...)` when the condition is simple, TODO otherwise |
| `RandomController` | `randomSwitch()` with equal weights, or `uniformRandomSwitch()` |
| `OnceOnlyController` | Run once per virtual user: place the children before the repeat or during loop |
| `ForeachController` | `foreach("#{inputVar}", "outputVar")` when the input is a JMeter `_N` variable series, TODO otherwise |
| `ThroughputController` | `randomSwitch` approximation for percentage mode, TODO for total-executions mode |
| `GenericController` (Simple Controller) | Inline its children |

**Simple conditions** are pattern-matched forms such as `"${var}" == "literal"`, `"${var}" != "literal"`, `${__jexl3(...)}` or `${__groovy(...)}` wrapping one of those, and `${var}` alone as a boolean. Convert them to `session -> "literal".equals(session.getString("var"))` and the equivalent forms. Any other condition becomes a TODO, with the original expression in a comment and a placeholder of `session -> true`.

### Config elements

| JMeter | Gatling |
|---|---|
| `HeaderManager` | `.header(...)` on each request in scope. Promote headers common to every request into `httpProtocol` |
| `CSVDataSet` | `csv("<filename>")` feeder with `.circular()` (recycle on EOF) or `.queue()` (stop on EOF). Map `variableNames` to column names; if the CSV has no header row, emit a TODO about adding one. Add `shareMode` other than "all threads" to the report |
| `CookieManager` | Gatling handles cookies by default. If a JMX has **no** cookie manager, add a report note that Gatling will keep cookies where JMeter did not |
| `CacheManager` | Skip, noting this in the report |
| `Arguments` (User Defined Variables) | Same as the test plan variables |

### Pre- and post-processors

| JMeter | Gatling |
|---|---|
| `JSONPostProcessor` | `check(jsonPath(expr).saveAs(name))` |
| `RegexExtractor` | `check(regex(expr).saveAs(name))` |
| `BoundaryExtractor` | `check(regex(Pattern.quote(left) + "(.*?)" + Pattern.quote(right)).saveAs(name))` |
| JSR223 and BeanShell pre/post-processors | TODO, with the script in a comment |

For `JSONPostProcessor`:
- It may define several variables at once with semicolon-separated `referenceNames`, `jsonPathExprs`, `match_numbers`, and `defaultValues`. Emit one check per variable.
- Map `match_numbers` as follows: `1` → `.find()`, `n` → `.find(n-1)`, `0` → `.findRandom()`, and `-1` → `.findAll()` saved as a list (plus a TODO noting that JMeter instead creates `name_1..name_N` variables).
- Map `defaultValues` to `.withDefault(...)` after verifying that this API exists in the target Gatling version.

For `RegexExtractor`, support only the `$1$` template, use `match_numbers` as for JSON, and map the default value the same way. Other templates become a TODO.

### Assertions

`ResponseAssertion` uses the `Assertion.test_field` and `Assertion.test_type` bitmask: Matches=1, Contains=2, Not=4, Equals=8, Substring=16, Or=32. Map it as follows:
- **Response code** → `status().is(...)`, or `status().not(...)` when Not is set.
- **Response data** → `substring(...)` for Substring, `regex(...)` for Contains or Matches, or `bodyString().is(...)` for Equals. With Not, use `.notExists()` or `.not(...)`.
- **Response headers** → `header(...)` checks where they map cleanly, TODO otherwise.
- **Or across patterns** → TODO, because Gatling checks are AND by default.

Map `JSONPathAssertion` to `jsonPath(expr).exists()`, or to `.is(value)` when a validation value is set. Its invert flag maps to `.notExists()` or `.not(value)`.

JSR223, BeanShell, size, duration, and XPath assertions become a TODO.

### Timers

| JMeter | Gatling |
|---|---|
| `ConstantTimer` | `pause(Duration.ofMillis(d))` |
| `UniformRandomTimer` | `pause(Duration.ofMillis(offset), Duration.ofMillis(offset + range))` |
| `GaussianRandomTimer` | `pause(...)` with a normal distribution, if the Gatling version supports it; otherwise a uniform approximation flagged in the report |
| `ConstantThroughputTimer` and similar | TODO, noting `throttle(...)` as the closest equivalent |

### Listeners

Skip `ResultCollector`, `Summariser`, and all other listeners. List them in the report under "ignored (reporting only)."

## Variables and Functions

- Convert `${var}` to Gatling EL `#{var}` in paths, headers, bodies, and checks.
- Escape any literal `#{` already in the source.
- Map function calls as follows, verifying each EL built-in against the target Gatling version and falling back to `exec(session -> session.set(...))` before the request if a built-in doesn't exist:

| JMeter function | Gatling |
|---|---|
| `__UUID` | `#{randomUuid()}` |
| `__time()` with no format | `#{currentTimeMillis()}` |
| `__time(fmt)` | `#{currentDate(fmt)}` (verify that the formats are compatible) |
| `__Random(min,max)` | `#{randomInt(min,max)}` (check whether the upper bound is inclusive) |
| `__RandomString(len,chars)` | A session function |
| `__threadNum` | `session.userId()` via a session function |
| `__counter(TRUE)` | A per-user counter in the session |
| `__counter(FALSE)` | A global `AtomicLong` |
| `__P` / `__property` | System properties (see the thread group section) |
| `__groovy`, `__jexl3`, `__javaScript`, `__BeanShell` | TODO with the original expression in a comment, unless covered by the simple-condition patterns |

All other functions become a TODO that keeps the original expression.

## Generated Code Shape

```java
package <pkg>;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;
import java.time.Duration;

/**
 * Generated by jmx2gatling from <file>.jmx on <date>.
 * Conversion notes: see conversion-report.md (<N> TODOs).
 */
public class <Name>Simulation extends Simulation {

    private static final String BASE_URL = System.getProperty("baseUrl", "https://example");
    // property-backed values ...

    private final HttpProtocolBuilder httpProtocol = http
        .baseUrl(BASE_URL)
        // common headers ...
        ;

    private final ScenarioBuilder loginFlow = scenario("Login Flow")
        .repeat(10).on(
            group("Auth").on(
                exec(http("POST /token").post("/token")
                    .body(ElFileBody("bodies/login-flow/post-token.json")).asJson()
                    .check(status().is(200))
                    .check(jsonPath("$.access_token").saveAs("token")))
            )
        );

    {
        setUp(
            loginFlow.injectOpen(rampUsers(Integer.getInteger("users", 50)).during(Duration.ofSeconds(60)))
        ).protocols(httpProtocol);
    }
}
```

Code style requirements:
- Keep sampler names from the JMX as Gatling request names, so reports line up.
- Each scenario field and chain step gets a short comment with its JMX element path.
- Split large scenarios into private `ChainBuilder` fields per top-level controller, for readability.
- Output must be deterministic: the same input always produces byte-identical output, except for the date line, which `--no-timestamp` can suppress.

## Conversion Report (`conversion-report.md`)

For each input file, the report includes:
- a summary of samplers converted, TODOs, approximations, and disabled or ignored elements,
- a **TODO table** with the element path, the element type, the reason, and the original content (for scripts and expressions) in a collapsed code block,
- an **approximations table** with the element path, what was done, and how it differs from JMeter (injection profiles, cookie behavior, `__P` defaults, and so on),
- an **ignored elements** list (listeners, cache manager, disabled subtrees),
- the TODO-marker-to-report-row correspondence: each `// TODO(jmx2gatling) #<id>` in the code matches a row with the same `#<id>`.

## Testing (development machine only)

1. **Synthetic fixtures** in `src/test/resources/fixtures/`. Hand-author realistic JMeter 5.6.x `.jmx` files covering:
   - every supported and partial element,
   - nested controllers,
   - scoped assertions, headers, and timers at multiple levels,
   - disabled subtrees,
   - multiple thread groups, including setUp and tearDown groups,
   - `__P` properties in thread counts,
   - multi-variable JSON extractors,
   - raw bodies and form parameters,
   - each unsupported element type.

   If JMeter can be downloaded on the development machine, validate that each fixture loads with `jmeter -n -t <fixture> -l /dev/null` against a local stub. This step is optional but preferred.
2. **Golden tests.** Each fixture's expected `.java` output, bodies, and report are committed. The tests compare actual output to expected, and `-Dgolden.update=true` regenerates them.
3. **Compile check.** A separate Gradle test project with the Gatling dependency compiles every generated simulation. Any compile failure fails the test suite.
4. **Parser unit tests** for the `hashTree` walking, property parsing, ResponseAssertion bitmask decoding, and scoping propagation.
5. **Inventory leak test.** Assert that `inventory` output contains none of the fixtures' URLs, hostnames, body content, variable values, or script text.
6. **Optional end-to-end check.** Run a fixture through JMeter and through the generated Gatling simulation against the same WireMock instance with request journaling enabled. Diff the method, path, headers, and body of the recorded requests. Ignore timing and volume.

## Implementation Order

Run the tests at the end of each phase.

1. Tree model, JMX parser, and the `inventory` command, with parser and leak tests.
2. A minimal `convert` that handles a test plan with one thread group, HTTP samplers, headers, and defaults, with golden tests and the compile check.
3. Scoping propagation, controllers, and timers.
4. Extractors, assertions, variables, and functions.
5. CSV feeders, multiple and setUp/tearDown thread groups, and property handling.
6. The full conversion report and `--strict`.
7. `build.sh`/`build.cmd` verified from a clean checkout with only a JDK installed, and a `README.md` covering usage, building without Gradle, the supported-element matrix, and how to read the report.

## Acceptance Criteria

- `build.sh` produces a runnable jar from a clean checkout using only JDK 17, with no network access.
- The production jar has zero third-party dependencies.
- Every fixture converts to Gatling code that compiles, and the golden tests pass.
- Every unsupported or approximated element appears in the report with its element path, and every `TODO(jmx2gatling)` marker in the code has a matching report row.
- `inventory` output contains only element type names, function names, counts, and support status.
- Running the converter twice on the same input produces identical output (with `--no-timestamp`).
