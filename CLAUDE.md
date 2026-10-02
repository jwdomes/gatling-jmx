# CLAUDE.md — jmx2gatling

`jmx2gatling` converts JMeter 5.6.x `.jmx` plans into Gatling 3.16 Java DSL simulations, plus a
`conversion-report.md`. `jmx2gatling-spec.md` is the original spec; `README.md` is the user guide.
It is built off the enterprise network where it will run, so the real `.jmx` files are never available here.

## Commands

```sh
./gradlew check                       # all tests + compile every golden simulation against Gatling 3.16.0
./gradlew test -Dgolden.update=true   # regenerate goldens and the README support matrix after an intended change
./build.sh                            # production jar: build/jmx2gatling.jar (javac + jar only, no network)
java -jar build/jmx2gatling.jar convert src/test/resources/fixtures --out /tmp/x --package demo --no-timestamp
```

After regenerating goldens, read the diff of `src/test/resources/golden/` before accepting it (no git here:
copy the folder aside first and `diff -r`).

## Hard constraints (from the spec)

- Production code is JDK-only, Java 17 (`--release 17`; the machine has JDK 21). No third-party libraries.
- Generated code must always compile. Anything not converted is a `// TODO(jmx2gatling) #<id>` marker
  registered through `Conversion.todo(...)`, with a matching report row. Never drop behavior silently:
  everything else is an approximation or an ignored entry in `Findings`.
- `inventory` output may contain only type names, function names, counts and support status.
- Output is deterministic (`--no-timestamp` gives byte-identical runs).
- Unix/Linux/macOS only: there is no Windows build script, by the user's choice.

## Where things are

- `convert/GatlingDsl.java`: every Gatling API string the converter emits. Check a new API against
  the docs and the compile check.
- `inventory/SupportMatrix.java`: the single table of element/function support. It also drives the README matrix.
- `convert/SimulationConverter` orchestrates one file → `ThreadGroupMapper` → `ChainConverter`
  (samplers/controllers) → `RequestMapper`, `CheckMapper`, `Timers`, `CsvFeeders`.
  Code is built as a `Step` model first and then written by `JavaWriter`.
- `Scope` handles JMeter scoping (headers, defaults, timers, assertions, processors down the tree).
- `ElTranslator` handles `${...}` → Gatling EL; `StaticValues` resolves build-time values (`__P`, user-defined variables).
- Tests: fixtures in `src/test/resources/fixtures/` (01–16; 15 and 16 are regressions from a code review),
  goldens in `src/test/resources/golden/`, `compile-check/` Gradle subproject.
  `JavaTextTest` compiles generated literals with javac.

## Adding or changing a mapping

1. Change the converter, and `SupportMatrix` if the support status changes.
2. Add or extend a fixture (hand-written JMeter 5.6.3 XML) that exercises it.
3. `./gradlew test -Dgolden.update=true`, review the golden diff, then `./gradlew check`.

## Status (2026-10-02)

All 7 spec phases are done: 94 tests pass and all 16 golden simulations compile. The offline JDK 17
build in Docker (`eclipse-temurin:17-jdk --network none`) produces output identical to the goldens.

Not done, by the user's choice: loading fixtures in real JMeter, and the WireMock end-to-end comparison.
Never done: running a generated simulation against a live server.
