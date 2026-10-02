package jmx2gatling.report;

import java.util.List;

/** Writes {@code conversion-report.md} for one convert run. */
public final class ReportWriter {

    /** One converted input file. {@code outputs} are the files written for it, relative to the output root. */
    public record FileReport(String input, String simulation, List<String> outputs, Findings findings) {
    }

    public record Failure(String input, String error) {
    }

    private final StringBuilder out = new StringBuilder();

    public String write(List<FileReport> files, List<Failure> failures, String generatedOn, String gatlingVersion) {
        out.append("# jmx2gatling conversion report\n\n");
        if (generatedOn != null) {
            out.append("Generated on ").append(generatedOn).append(". ");
        }
        out.append("Target: Gatling ").append(gatlingVersion).append(" Java DSL.\n\n");
        howToRead();
        summary(files, failures);
        for (FileReport file : files) {
            file(file);
        }
        return out.toString();
    }

    private void howToRead() {
        out.append("## How to read this report\n\n");
        out.append("- **TODO**: behavior that was not converted. Each one has a numbered marker in the simulation, ")
            .append("`// TODO(jmx2gatling) #<id>`, with the same number as its row below. Numbers restart for each file. ")
            .append("Generated code always compiles; a TODO is either a no-op, a placeholder value or a missing check.\n");
        out.append("- **Approximation**: converted, but Gatling behaves differently from JMeter in the way described.\n");
        out.append("- **Ignored**: elements with no load-test effect (listeners, debug elements), disabled elements, ")
            .append("and elements JMeter itself never applies.\n");
        out.append("- Every element is identified by its path in the JMeter tree, as `Name (type)` from the test plan down.\n\n");
    }

    private void summary(List<FileReport> files, List<Failure> failures) {
        out.append("## Summary\n\n");
        out.append("| Input file | Simulation | Samplers converted | Samplers not converted | TODOs | Approximations | Ignored |\n");
        out.append("|---|---|---:|---:|---:|---:|---:|\n");
        for (FileReport f : files) {
            Findings x = f.findings();
            out.append("| ").append(cell(f.input())).append(" | ").append(cell(f.simulation())).append(" | ")
                .append(x.samplersConverted()).append(" | ").append(x.samplersNotConverted()).append(" | ")
                .append(x.todos().size()).append(" | ").append(x.approximations().size()).append(" | ")
                .append(x.ignored().size()).append(" |\n");
        }
        out.append('\n');
        if (!failures.isEmpty()) {
            out.append("### Files that could not be converted\n\n");
            out.append("| Input file | Error |\n|---|---|\n");
            for (Failure f : failures) {
                out.append("| ").append(cell(f.input())).append(" | ").append(cell(f.error())).append(" |\n");
            }
            out.append('\n');
        }
    }

    private void file(FileReport f) {
        Findings x = f.findings();
        out.append("## ").append(text(f.input())).append(" → ").append(text(f.simulation())).append("\n\n");
        out.append("- Samplers converted: ").append(x.samplersConverted()).append('\n');
        out.append("- Samplers replaced by a no-op: ").append(x.samplersNotConverted()).append('\n');
        out.append("- TODOs: ").append(x.todos().size()).append('\n');
        out.append("- Approximations: ").append(x.approximations().size()).append('\n');
        out.append("- Disabled or ignored elements: ").append(x.ignored().size()).append('\n');
        out.append("- Files written: ").append(String.join(", ", f.outputs().stream().map(o -> "`" + o + "`").toList())).append("\n\n");

        out.append("### TODOs\n\n");
        if (x.todos().isEmpty()) {
            out.append("None.\n\n");
        } else {
            out.append("| # | Element path | Type | Reason |\n|---|---|---|---|\n");
            for (Findings.Todo t : x.todos()) {
                out.append("| #").append(t.id()).append(" | ").append(cell(t.path())).append(" | ").append(cell(t.type()))
                    .append(" | ").append(cell(t.reason())).append(" |\n");
            }
            out.append('\n');
            for (Findings.Todo t : x.todos()) {
                if (!t.original().isBlank()) {
                    out.append("<details><summary>#").append(t.id()).append(" original content</summary>\n\n");
                    String fence = fence(t.original());
                    out.append(fence).append('\n').append(t.original()).append('\n').append(fence).append("\n\n</details>\n\n");
                }
            }
        }

        out.append("### Approximations\n\n");
        if (x.approximations().isEmpty()) {
            out.append("None.\n\n");
        } else {
            out.append("| Element path | What was done | How it differs from JMeter |\n|---|---|---|\n");
            for (Findings.Approximation a : x.approximations()) {
                out.append("| ").append(cell(a.path())).append(" | ").append(cell(a.what())).append(" | ").append(cell(a.difference())).append(" |\n");
            }
            out.append('\n');
        }

        out.append("### Ignored elements\n\n");
        if (x.ignored().isEmpty()) {
            out.append("None.\n\n");
        } else {
            out.append("| Element path | Type | Why |\n|---|---|---|\n");
            for (Findings.Ignored i : x.ignored()) {
                out.append("| ").append(cell(i.path())).append(" | ").append(cell(i.type())).append(" | ").append(cell(i.reason())).append(" |\n");
            }
            out.append('\n');
        }
    }

    /** A fence longer than any backtick run in the content, so the content cannot close it. */
    private static String fence(String content) {
        int longest = 0;
        int run = 0;
        for (char ch : content.toCharArray()) {
            run = ch == '`' ? run + 1 : 0;
            longest = Math.max(longest, run);
        }
        return "`".repeat(Math.max(3, longest + 1));
    }

    private static String text(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace("\r", " ").replace("\n", " ");
    }

    private static String cell(String s) {
        return text(s).replace("|", "\\|");
    }
}
