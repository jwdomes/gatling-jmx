package jmx2gatling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainTest {

    private static final String FIXTURES = "src/test/resources/fixtures";

    @TempDir
    Path temp;

    private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

    private int run(String... args) {
        return new Main(new PrintStream(stdout, true, StandardCharsets.UTF_8), new PrintStream(stderr, true, StandardCharsets.UTF_8)).run(args);
    }

    private String out() {
        return stdout.toString(StandardCharsets.UTF_8);
    }

    @Test
    void strictFailsOnlyWhenTodosWereEmitted() {
        assertEquals(Main.OK, run("convert", FIXTURES + "/01-basic-get-post.jmx", "--out", temp.resolve("a").toString(), "--package", "p", "--strict"));
        assertEquals(Main.TODOS_IN_STRICT_MODE, run("convert", FIXTURES + "/13-unsupported-elements.jmx", "--out", temp.resolve("b").toString(), "--package", "p", "--strict"));
        assertEquals(Main.OK, run("convert", FIXTURES + "/13-unsupported-elements.jmx", "--out", temp.resolve("c").toString(), "--package", "p"));
    }

    @Test
    void convertsADirectoryRecursivelyIntoOneReport() throws IOException {
        Path nested = temp.resolve("in/deeper");
        Files.createDirectories(nested);
        Files.copy(Path.of(FIXTURES, "01-basic-get-post.jmx"), temp.resolve("in/first.jmx"));
        Files.copy(Path.of(FIXTURES, "02-nested-controllers.jmx"), nested.resolve("second.JMX"));
        Path out = temp.resolve("out");
        assertEquals(Main.OK, run("convert", temp.resolve("in").toString(), "--out", out.toString(), "--package", "com.example.load", "--no-timestamp"));
        assertTrue(Files.exists(out.resolve("java/com/example/load/FirstSimulation.java")));
        assertTrue(Files.exists(out.resolve("java/com/example/load/SecondSimulation.java")));
        String report = Files.readString(out.resolve("conversion-report.md"));
        assertTrue(report.contains("→ FirstSimulation") && report.contains("→ SecondSimulation"), report);
        assertFalse(report.contains("Generated on"), "--no-timestamp");
    }

    @Test
    void sameFileNameInTwoDirectoriesGetsDistinctClasses() throws IOException {
        Files.createDirectories(temp.resolve("in/a"));
        Files.createDirectories(temp.resolve("in/b"));
        Files.copy(Path.of(FIXTURES, "01-basic-get-post.jmx"), temp.resolve("in/a/plan.jmx"));
        Files.copy(Path.of(FIXTURES, "01-basic-get-post.jmx"), temp.resolve("in/b/plan.jmx"));
        Path out = temp.resolve("out");
        assertEquals(Main.OK, run("convert", temp.resolve("in").toString(), "--out", out.toString(), "--package", "p"));
        assertTrue(Files.exists(out.resolve("java/p/PlanSimulation.java")));
        assertTrue(Files.exists(out.resolve("java/p/Plan2Simulation.java")));
        String second = Files.readString(out.resolve("java/p/Plan2Simulation.java"));
        assertTrue(second.contains("public class Plan2Simulation "));
        // Each class gets its own body folder, so one plan's bodies never overwrite the other's.
        assertTrue(second.contains("ElFileBody(\"bodies/plan2/post-api-orders.json\")"), second);
        assertTrue(Files.exists(out.resolve("resources/bodies/plan/post-api-orders.json")));
        assertTrue(Files.exists(out.resolve("resources/bodies/plan2/post-api-orders.json")));
    }

    @Test
    void bodiesDirTakesEveryBody() throws IOException {
        Path out = temp.resolve("out");
        Path bodies = temp.resolve("bodies-root");
        assertEquals(Main.OK, run("convert", FIXTURES + "/01-basic-get-post.jmx", "--out", out.toString(), "--package", "p", "--bodies-dir", bodies.toString()));
        assertTrue(Files.exists(bodies.resolve("bodies/jmx01-basic-get-post/post-api-cart.json")), "short body also externalized");
        assertTrue(Files.exists(bodies.resolve("bodies/jmx01-basic-get-post/post-api-orders.json")));
        assertFalse(Files.exists(out.resolve("resources")));
        String java = Files.readString(out.resolve("java/p/Jmx01BasicGetPostSimulation.java"));
        assertTrue(java.contains("ElFileBody(\"bodies/jmx01-basic-get-post/post-api-cart.json\")"), java);
        assertFalse(java.contains("StringBody("), java);
    }

    @Test
    void timestampAppearsUnlessSuppressed() throws IOException {
        Path out = temp.resolve("out");
        assertEquals(Main.OK, run("convert", FIXTURES + "/01-basic-get-post.jmx", "--out", out.toString(), "--package", "p"));
        assertTrue(Files.readString(out.resolve("java/p/Jmx01BasicGetPostSimulation.java")).matches("(?s).*from 01-basic-get-post\\.jmx on \\d{4}-\\d{2}-\\d{2}\\..*"));
        assertTrue(Files.readString(out.resolve("conversion-report.md")).contains("Generated on "));
    }

    @Test
    void unreadableFilesAreReportedAndFailTheRun() throws IOException {
        Path in = temp.resolve("in");
        Files.createDirectories(in);
        Files.writeString(in.resolve("broken.jmx"), "<jmeterTestPlan><hashTree>");
        Files.copy(Path.of(FIXTURES, "01-basic-get-post.jmx"), in.resolve("good.jmx"));
        Path out = temp.resolve("out");
        assertEquals(Main.FAILED, run("convert", in.toString(), "--out", out.toString(), "--package", "p"));
        assertTrue(Files.exists(out.resolve("java/p/GoodSimulation.java")), "other files are still converted");
        String report = Files.readString(out.resolve("conversion-report.md"));
        assertTrue(report.contains("Files that could not be converted") && report.contains("broken.jmx"), report);
    }

    @Test
    void usageErrors() {
        assertEquals(Main.FAILED, run());
        assertEquals(Main.FAILED, run("convert", FIXTURES, "--package", "p"));
        assertEquals(Main.FAILED, run("convert", FIXTURES, "--out", temp.toString(), "--package", "not a package"));
        assertEquals(Main.FAILED, run("inventory", "does-not-exist.jmx"));
        assertEquals(Main.FAILED, run("inventory", FIXTURES, "--bogus"));
        assertEquals(Main.FAILED, run("frobnicate"));
        assertEquals(Main.OK, run("--help"));
    }

    @Test
    void inventoryPrintsMarkdownOrJson() {
        assertEquals(Main.OK, run("inventory", FIXTURES));
        assertTrue(out().startsWith("# jmx2gatling inventory"), out());
        stdout.reset();
        assertEquals(Main.OK, run("inventory", FIXTURES, "--json"));
        assertTrue(out().startsWith("{\n  \"filesScanned\": " + Fixtures.all().size() + ","), out());
    }
}
