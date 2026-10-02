package jmx2gatling;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import jmx2gatling.convert.Naming;
import jmx2gatling.convert.SimulationConverter;
import jmx2gatling.inventory.Inventory;
import jmx2gatling.parse.JmxFormatException;
import jmx2gatling.parse.JmxParser;
import jmx2gatling.report.ReportWriter;

/** Command-line entry point. */
public final class Main {

    static final int OK = 0;
    static final int FAILED = 1;
    static final int TODOS_IN_STRICT_MODE = 2;

    private static final String USAGE = String.join("\n",
        "Usage:",
        "  java -jar jmx2gatling.jar inventory <file-or-dir>... [--json]",
        "  java -jar jmx2gatling.jar convert   <file-or-dir>... --out <dir> --package <pkg>",
        "                                      [--bodies-dir <dir>] [--strict] [--no-timestamp]",
        "",
        "inventory  Counts element types and JMeter functions with their support status.",
        "           The output contains no names, URLs, values or scripts, so it is safe to share.",
        "convert    Writes one Gatling simulation per .jmx file plus conversion-report.md.",
        "           --out <dir>         output root (java/, resources/ and the report go here)",
        "           --package <pkg>     Java package for the generated simulations",
        "           --bodies-dir <dir>  write every request body to files under <dir>",
        "                               (default: only long bodies, under <out>/resources)",
        "           --strict            exit with code 2 if any TODO was emitted",
        "           --no-timestamp      omit the generation date, for byte-identical output",
        "");

    private final PrintStream out;
    private final PrintStream err;

    Main(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
    }

    public static void main(String[] args) {
        System.exit(new Main(System.out, System.err).run(args));
    }

    int run(String[] args) {
        if (args.length == 0 || args[0].equals("--help") || args[0].equals("-h")) {
            out.print(USAGE);
            return args.length == 0 ? FAILED : OK;
        }
        try {
            switch (args[0]) {
                case "inventory":
                    return inventory(CommandLine.parse(args, 1, List.of("--json"), List.of()));
                case "convert":
                    return convert(CommandLine.parse(args, 1, List.of("--strict", "--no-timestamp"), List.of("--out", "--package", "--bodies-dir")));
                default:
                    throw new UsageException("Unknown command: " + args[0]);
            }
        } catch (UsageException e) {
            err.println("Error: " + e.getMessage());
            err.println();
            err.print(USAGE);
            return FAILED;
        } catch (IOException e) {
            err.println("Error: " + e.getMessage());
            return FAILED;
        }
    }

    private int inventory(CommandLine cmd) throws IOException {
        Inventory inventory = new Inventory();
        JmxParser parser = new JmxParser();
        for (Path file : jmxFiles(cmd.paths())) {
            try {
                inventory.add(parser.parse(file));
            } catch (JmxFormatException | IOException e) {
                // stderr only: the inventory itself must not contain file names.
                err.println("Skipped " + file + ": " + e.getMessage());
                inventory.addFailure();
            }
        }
        out.print(cmd.flag("--json") ? inventory.toJson() : inventory.toMarkdown());
        return OK;
    }

    private int convert(CommandLine cmd) throws IOException {
        Path outDir = Path.of(cmd.required("--out"));
        String packageName = cmd.required("--package");
        if (!packageName.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*")) {
            throw new UsageException("Not a valid Java package name: " + packageName);
        }
        Optional<String> bodiesDir = cmd.option("--bodies-dir");
        Path resourcesDir = bodiesDir.map(Path::of).orElse(outDir.resolve("resources"));
        Path javaDir = outDir.resolve("java").resolve(packageName.replace('.', '/'));
        String generatedOn = cmd.flag("--no-timestamp") ? null : LocalDate.now().toString();
        SimulationConverter.Options options = new SimulationConverter.Options(packageName, bodiesDir.isPresent(), generatedOn);

        List<ReportWriter.FileReport> reports = new ArrayList<>();
        List<ReportWriter.Failure> failures = new ArrayList<>();
        Set<String> classNames = new HashSet<>();
        int todos = 0;
        for (Path file : jmxFiles(cmd.paths())) {
            String fileName = file.getFileName().toString();
            String className = uniqueClassName(fileName, classNames);
            SimulationConverter.Result result;
            try {
                result = new SimulationConverter().convert(new JmxParser().parse(file), fileName, className, options);
            } catch (JmxFormatException | IOException e) {
                err.println("Could not convert " + file + ": " + e.getMessage());
                failures.add(new ReportWriter.Failure(file.toString(), e.getMessage()));
                continue;
            } catch (RuntimeException e) {
                // A converter bug: keep converting the other files, but show the full stack trace.
                err.println("Internal error converting " + file + ":");
                e.printStackTrace(err);
                failures.add(new ReportWriter.Failure(file.toString(), "Internal converter error: " + e));
                continue;
            }
            List<String> outputs = new ArrayList<>();
            Path javaFile = javaDir.resolve(className + ".java");
            write(javaFile, result.javaSource());
            outputs.add(relative(outDir, javaFile));
            for (Map.Entry<String, String> body : result.bodyFiles().entrySet()) {
                Path bodyFile = resourcesDir.resolve(body.getKey());
                write(bodyFile, body.getValue());
                outputs.add(relative(outDir, bodyFile));
            }
            reports.add(new ReportWriter.FileReport(file.toString(), className, outputs, result.findings()));
            todos += result.findings().todos().size();
            out.println("Converted " + file + " -> " + relative(outDir, javaFile) + " (" + result.findings().todos().size() + " TODOs)");
        }
        Path report = outDir.resolve("conversion-report.md");
        write(report, new ReportWriter().write(reports, failures, generatedOn, SimulationConverter.GATLING_VERSION));
        out.println("Report: " + report);
        if (!failures.isEmpty()) {
            return FAILED;
        }
        if (cmd.flag("--strict") && todos > 0) {
            err.println("--strict: " + todos + " TODO(s) emitted");
            return TODOS_IN_STRICT_MODE;
        }
        return OK;
    }

    private static String uniqueClassName(String fileName, Set<String> used) {
        String base = Naming.pascalCase(fileName.replaceAll("(?i)\\.jmx$", "")) + "Simulation";
        String name = base;
        for (int i = 2; !used.add(name); i++) {
            name = base.substring(0, base.length() - "Simulation".length()) + i + "Simulation";
        }
        return name;
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static String relative(Path base, Path file) {
        Path b = base.toAbsolutePath().normalize();
        Path f = file.toAbsolutePath().normalize();
        return (f.startsWith(b) ? b.relativize(f) : f).toString().replace('\\', '/');
    }

    /** All .jmx files under the given paths, recursively, in a stable order. */
    static List<Path> jmxFiles(List<String> paths) throws IOException {
        if (paths.isEmpty()) {
            throw new UsageException("No input files or directories given");
        }
        List<Path> result = new ArrayList<>();
        for (String p : paths) {
            Path path = Path.of(p);
            if (Files.isDirectory(path)) {
                try (Stream<Path> walk = Files.walk(path)) {
                    walk.filter(f -> Files.isRegularFile(f) && f.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".jmx"))
                        .sorted()
                        .forEach(result::add);
                }
            } else if (Files.isRegularFile(path)) {
                result.add(path);
            } else {
                throw new UsageException("No such file or directory: " + p);
            }
        }
        return result;
    }
}
