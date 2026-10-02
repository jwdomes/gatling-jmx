package jmx2gatling;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** Locates the synthetic JMX fixtures and their golden outputs in the test resources. */
public final class Fixtures {

    private Fixtures() {
    }

    public static Path fixturesDir() {
        return resource("fixtures");
    }

    /** {@code src/test/resources/golden}, in the source tree (not the build copy) so updates are committed. */
    public static Path goldenSourceDir() {
        return Path.of("src", "test", "resources", "golden").toAbsolutePath();
    }

    public static List<Path> all() {
        try (Stream<Path> files = Files.list(fixturesDir())) {
            return files.filter(f -> f.toString().endsWith(".jmx")).sorted().toList();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String baseName(Path fixture) {
        String name = fixture.getFileName().toString();
        return name.substring(0, name.length() - ".jmx".length());
    }

    private static Path resource(String name) {
        try {
            return Path.of(Fixtures.class.getClassLoader().getResource(name).toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
