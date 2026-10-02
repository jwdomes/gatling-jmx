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
| src/test/resources/fixtures/09-csv-feeders.jmx | Jmx09CsvFeedersSimulation | 4 | 0 | 1 | 16 | 0 |

## src/test/resources/fixtures/09-csv-feeders.jmx → Jmx09CsvFeedersSimulation

- Samplers converted: 4
- Samplers replaced by a no-op: 0
- TODOs: 1
- Approximations: 16
- Disabled or ignored elements: 0
- Files written: `java/golden/Jmx09CsvFeedersSimulation.java`

### TODOs

| # | Element path | Type | Reason |
|---|---|---|---|
| #1 | TestPlan > Shoppers (ThreadGroup) > Products (CSVDataSet) | CSVDataSet | The CSV file has no header row; Gatling needs one. Add this first line: sku,qty |

### Approximations

| Element path | What was done | How it differs from JMeter |
|---|---|---|
| TestPlan > Shoppers (ThreadGroup) > Shipping (GenericController) > Addresses (CSVDataSet) | CSV Data Set inside a controller read once per thread-group iteration | The feeder is fed at the start of each iteration of the thread group, wherever the element was placed. |
| TestPlan > Shoppers (ThreadGroup) > Products (CSVDataSet) | Recycle on EOF off → queue() | When the file runs out, Gatling stops the whole simulation; JMeter stopped only the thread. |
| TestPlan > Shoppers (ThreadGroup) > Coupons (CSVDataSet) | Variable `coupon.code` renamed to `coupon_code` | Gatling EL treats `.` and other punctuation in names as attribute access, so the name was made safe everywhere it is used. |
| TestPlan > Shoppers (ThreadGroup) > Coupons (CSVDataSet) | CSV column `coupon.code` must be named `coupon_code` in the file's header row | Gatling takes variable names from the header row, and the converter renamed this variable. |
| TestPlan > Shoppers (ThreadGroup) > Coupons (CSVDataSet) | Column names come from the file's header row | JMeter skipped the first line and used variableNames=coupon.code; Gatling uses the header row itself, so its names must match. |
| TestPlan > Shoppers (ThreadGroup) > Coupons (CSVDataSet) | Sharing mode thread not converted | Gatling feeders are shared by all users of the simulation (JMeter's "All threads"). |
| TestPlan > Shoppers (ThreadGroup) > Coupons (CSVDataSet) | File encoding ISO-8859-1 not converted | Gatling reads feeder files as UTF-8 unless configured otherwise. |
| TestPlan > Shoppers (ThreadGroup) > Coupons (CSVDataSet) | CSV file referenced by path `/data/feeds/coupons.tsv` | Gatling looks for the file on its classpath (the resources folder) first. Copy it there and use a relative name, or keep it at this path. |
| TestPlan > Shoppers (ThreadGroup) > Exhausting List (CSVDataSet) | Column names come from the file's header row | JMeter skipped the first line and used variableNames=ticket; Gatling uses the header row itself, so its names must match. |
| TestPlan > Shoppers (ThreadGroup) > Exhausting List (CSVDataSet) | Recycle on EOF off → queue() | When the file runs out, Gatling stops the whole simulation; JMeter kept going with &lt;EOF> as the value. |
| TestPlan > Shoppers (ThreadGroup) > Exhausting List (CSVDataSet) | Sharing mode group not converted | Gatling feeders are shared by all users of the simulation (JMeter's "All threads"). |
| TestPlan > Shoppers (ThreadGroup) > Shipping (GenericController) > Addresses (CSVDataSet) | JMeter property `addressFile` read as Java system property `addressFile` | Pass `-DaddressFile=...` to Gatling instead of `-JaddressFile=...` to JMeter. Default when unset: `addresses.csv`. |
| TestPlan > Shoppers (ThreadGroup) > Shipping (GenericController) > Addresses (CSVDataSet) | Column names come from the file's header row | JMeter skipped the first line and used variableNames=street;city; Gatling uses the header row itself, so its names must match. |
| TestPlan > Shoppers (ThreadGroup) | Closed model (4 threads, ramp-up 2 s) → open model rampUsers ... during | Each Gatling user runs the scenario once and exits, while JMeter keeps its threads looping. Concurrency matches only while every user is still in its loop. |
| TestPlan > Admins (ThreadGroup) | Closed model (1 threads, ramp-up 1 s) → open model rampUsers ... during | Each Gatling user runs the scenario once and exits, while JMeter keeps its threads looping. Concurrency matches only while every user is still in its loop. |
| TestPlan > Shoppers (ThreadGroup) > GET /cart/add (HTTPSamplerProxy) | Variable `username` is used but no converted element sets it | It is probably set by a script, a JMeter built-in or an unconverted element, unless it is a column of a CSV file with a header row. Gatling fails the request when the attribute is missing. |

### Ignored elements

None.

