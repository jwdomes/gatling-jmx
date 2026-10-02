package jmx2gatling.convert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

/** Compiles generated literals and comments with javac, so escaping is checked by the compiler itself. */
class JavaTextTest {

    private static final List<String> NASTY = List.of(
        "plain",
        "quote \" backslash \\ tab \t cr \r",
        "\"\"\" triple \"\"\"\" quadruple \"\"",
        "unicode é中 and a fake escape \\u0022 and \\\\u0041",
        "trailing spaces   \nand tab\t\n   \n  leading kept\n",
        "  indented first line\n    deeper\nlast line ends with backslash \\",
        "#{gatling} \\#{escaped}",
        "control \u0001 \u007f end",
        "ends with quote\"");

    @Test
    void quotedLiteralsAndTextBlocksRoundTrip() throws Exception {
        StringBuilder source = new StringBuilder("public class Probe implements java.util.function.Supplier<String[]> {\n");
        source.append("    public String[] get() {\n        return new String[] {\n");
        for (String s : NASTY) {
            source.append("            ").append(JavaText.quote(s)).append(",\n");
            source.append("            ").append(JavaText.textBlock(s, "                ")).append(",\n");
        }
        source.append("        };\n    }\n");
        for (String s : NASTY) {
            source.append("    // ").append(JavaText.comment(s)).append('\n');
        }
        source.append("}\n");

        String[] values = compileAndRun(source.toString());
        for (int i = 0; i < NASTY.size(); i++) {
            assertEquals(NASTY.get(i), values[2 * i], "quoted literal #" + i);
            assertEquals(NASTY.get(i), values[2 * i + 1], "text block #" + i);
        }
    }

    @Test
    void literalsAreAscii() {
        for (String s : NASTY) {
            assertTrue(JavaText.quote(s).chars().allMatch(ch -> ch >= 0x20 && ch < 0x7f), s);
            assertTrue(JavaText.comment(s).chars().allMatch(ch -> ch >= 0x20 && ch < 0x7f), s);
        }
    }

    @SuppressWarnings("unchecked")
    private static String[] compileAndRun(String source) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Map<String, ByteCode> classes = new HashMap<>();
        StandardJavaFileManager standard = compiler.getStandardFileManager(null, null, null);
        ForwardingJavaFileManager<StandardJavaFileManager> manager = new ForwardingJavaFileManager<>(standard) {
            @Override
            public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) {
                ByteCode out = new ByteCode(className);
                classes.put(className, out);
                return out;
            }
        };
        JavaFileObject file = new SimpleJavaFileObject(URI.create("string:///Probe.java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }
        };
        List<String> diagnostics = new ArrayList<>();
        boolean ok = compiler.getTask(null, manager, d -> diagnostics.add(d.toString()), List.of("--release", "17"), null, List.of(file)).call();
        assertTrue(ok, () -> String.join("\n", diagnostics) + "\n" + source);
        ClassLoader loader = new ClassLoader(JavaTextTest.class.getClassLoader()) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                ByteCode b = classes.get(name);
                if (b == null) {
                    throw new ClassNotFoundException(name);
                }
                byte[] bytes = b.bytes.toByteArray();
                return defineClass(name, bytes, 0, bytes.length);
            }
        };
        Object probe = loader.loadClass("Probe").getDeclaredConstructor().newInstance();
        return ((java.util.function.Supplier<String[]>) probe).get();
    }

    private static final class ByteCode extends SimpleJavaFileObject {
        final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();

        ByteCode(String className) {
            super(URI.create("bytes:///" + className.replace('.', '/') + ".class"), Kind.CLASS);
        }

        @Override
        public java.io.OutputStream openOutputStream() throws IOException {
            return bytes;
        }
    }
}
