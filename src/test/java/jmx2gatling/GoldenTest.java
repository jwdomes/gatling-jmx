package jmx2gatling;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Converts every fixture and compares the whole output tree (simulation, body files, report) with
 * the committed golden copy. Run with {@code -Dgolden.update=true} to regenerate the goldens.
 */
class GoldenTest {

    static final String PACKAGE = "golden";
    private static final boolean UPDATE = Boolean.getBoolean("golden.update");

    @TempDir
    Path temp;

    static List<String> fixtures() {
        return Fixtures.all().stream().map(Fixtures::baseName).toList();
    }

    /** Converts one fixture with the paths as a user would type them, so the report is machine-independent. */
    static Path convert(String fixture, Path outDir) {
        String input = "src/test/resources/fixtures/" + fixture + ".jmx";
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int exit = new Main(new PrintStream(stdout, true, StandardCharsets.UTF_8), new PrintStream(stderr, true, StandardCharsets.UTF_8))
            .run(new String[] {"convert", input, "--out", outDir.toString(), "--package", PACKAGE, "--no-timestamp"});
        assertEquals(Main.OK, exit, () -> "convert failed: " + stderr.toString(StandardCharsets.UTF_8));
        return outDir;
    }

    @ParameterizedTest
    @MethodSource("fixtures")
    void outputMatchesGolden(String fixture) throws IOException {
        Path actual = convert(fixture, temp.resolve(fixture));
        Path golden = Fixtures.goldenSourceDir().resolve(fixture);
        if (UPDATE) {
            deleteRecursively(golden);
            copyRecursively(actual, golden);
            return;
        }
        assertEquals(files(golden), files(actual), "Set of output files differs from golden " + golden);
        for (String file : files(golden)) {
            assertEquals(Files.readString(golden.resolve(file)), Files.readString(actual.resolve(file)),
                "Output differs from golden: " + fixture + "/" + file + " (rerun with -Dgolden.update=true if intended)");
        }
    }

    static List<String> files(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile).map(p -> root.relativize(p).toString().replace('\\', '/')).sorted().toList();
        }
    }

    private static void copyRecursively(Path from, Path to) throws IOException {
        for (String file : files(from)) {
            Path target = to.resolve(file);
            Files.createDirectories(target.getParent());
            Files.copy(from.resolve(file), target);
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }
}
