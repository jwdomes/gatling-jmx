package jmx2gatling;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Converting the same input twice gives byte-identical output (with --no-timestamp). */
class DeterminismTest {

    @TempDir
    Path temp;

    @Test
    void twoRunsProduceIdenticalBytes() throws IOException {
        Path first = convertAll(temp.resolve("first"));
        Path second = convertAll(temp.resolve("second"));
        assertEquals(GoldenTest.files(first), GoldenTest.files(second));
        for (String file : GoldenTest.files(first)) {
            assertArrayEquals(Files.readAllBytes(first.resolve(file)), Files.readAllBytes(second.resolve(file)), file);
        }
    }

    private static Path convertAll(Path out) {
        int exit = new Main(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
            new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8))
            .run(new String[] {"convert", "src/test/resources/fixtures", "--out", out.toString(), "--package", "p", "--no-timestamp"});
        assertEquals(Main.OK, exit);
        return out;
    }
}
