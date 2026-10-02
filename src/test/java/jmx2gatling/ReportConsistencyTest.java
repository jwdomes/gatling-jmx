package jmx2gatling;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Every TODO marker in the code has a report row with the same number, and the other way round. */
class ReportConsistencyTest {

    private static final Pattern CODE_MARKER = Pattern.compile("// TODO\\(jmx2gatling\\) #(\\d+) ");
    private static final Pattern REPORT_ROW = Pattern.compile("(?m)^\\| #(\\d+) \\|");
    private static final Pattern JAVADOC_COUNT = Pattern.compile("conversion-report\\.md \\((\\d+) TODOs?\\)");

    @TempDir
    Path temp;

    static Iterable<String> fixtures() {
        return GoldenTest.fixtures();
    }

    @ParameterizedTest
    @MethodSource("fixtures")
    void todoMarkersMatchReportRows(String fixture) throws IOException {
        Path out = GoldenTest.convert(fixture, temp.resolve(fixture));
        String report = Files.readString(out.resolve("conversion-report.md"));
        Set<Integer> inCode = new TreeSet<>();
        String javadocCount = null;
        for (String file : GoldenTest.files(out)) {
            if (file.endsWith(".java")) {
                String java = Files.readString(out.resolve(file));
                collect(CODE_MARKER.matcher(java), inCode);
                Matcher m = JAVADOC_COUNT.matcher(java);
                javadocCount = m.find() ? m.group(1) : null;
            }
        }
        Set<Integer> inReport = new TreeSet<>();
        collect(REPORT_ROW.matcher(report), inReport);
        assertEquals(inReport, inCode, "TODO ids in code vs report for " + fixture);
        assertEquals(String.valueOf(inReport.size()), javadocCount, "TODO count in the class comment");
        Set<Integer> oneToN = new TreeSet<>();
        for (int id = 1; id <= inReport.size(); id++) {
            oneToN.add(id);
        }
        assertEquals(oneToN, inReport, "TODO ids are numbered 1..N without gaps");
    }

    private static void collect(Matcher m, Set<Integer> out) {
        while (m.find()) {
            out.add(Integer.parseInt(m.group(1)));
        }
    }
}
