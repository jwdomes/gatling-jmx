package jmx2gatling.convert;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jmx2gatling.model.JmxElement;
import jmx2gatling.model.Props;

/** CSV Data Set Config → a {@code csv(...)} feeder field, shared by every scenario that reads it. */
final class CsvFeeders {

    private final Conversion c;
    private final StaticValues statics;
    private final Map<JmxElement, String> fieldByElement = new IdentityHashMap<>();
    private final List<SimulationModel.Field> fields = new ArrayList<>();

    CsvFeeders(Conversion c, StaticValues statics) {
        this.c = c;
        this.statics = statics;
    }

    List<SimulationModel.Field> fields() {
        return List.copyOf(fields);
    }

    /** The feed step for {@code csv}; comments carry any TODO about it. */
    Step feed(JmxElement csv) {
        c.appliedElements.add(csv);
        String existing = fieldByElement.get(csv);
        if (existing != null) {
            return new Step.Action(List.of("CSV Data Set: " + ChainConverter.stepPath(csv)), GatlingDsl.feed(existing));
        }
        Props p = csv.props();
        List<String> comments = new ArrayList<>(List.of(csv.path()));
        String fileName = p.string("filename").trim();
        Optional<String> file = statics.literal(fileName);
        Optional<String> fileExpr = statics.string(fileName, csv);
        if (fileExpr.isEmpty()) {
            comments.addAll(c.todo(csv, "CSV file name `" + fileName + "` depends on runtime variables; set the real path", fileName));
        }
        String delimiterText = p.string("delimiter", ",");
        String delimiter = delimiterText.equals("\\t") ? "\t" : delimiterText.isEmpty() ? "," : delimiterText;
        char separator = ',';
        if (delimiter.length() == 1) {
            separator = delimiter.charAt(0);
        } else {
            comments.addAll(c.todo(csv, "Delimiter `" + delimiterText + "` is longer than one character", delimiterText));
        }

        String variableNames = p.string("variableNames").trim();
        if (variableNames.isEmpty()) {
            c.feedsColumnsFromFileHeader = true;
        } else {
            List<String> names = new ArrayList<>();
            for (String name : variableNames.split(java.util.regex.Pattern.quote(delimiter), -1)) {
                names.add(name.trim());
                c.defineVariable(name.trim());
                String attribute = c.attribute(name.trim(), csv);
                if (!attribute.equals(name.trim())) {
                    c.approximate(csv, "CSV column `" + name.trim() + "` must be named `" + attribute + "` in the file's header row",
                        "Gatling takes variable names from the header row, and the converter renamed this variable.");
                }
            }
            if (p.bool("ignoreFirstLine", false)) {
                c.approximate(csv, "Column names come from the file's header row",
                    "JMeter skipped the first line and used variableNames=" + variableNames + "; Gatling uses the header row itself, so its names must match.");
            } else {
                comments.addAll(c.todo(csv, "The CSV file has no header row; Gatling needs one. Add this first line: "
                    + String.join(String.valueOf(separator), names), ""));
            }
        }

        boolean recycle = p.bool("recycle", true);
        boolean stopThread = p.bool("stopThread", false);
        String strategy;
        if (recycle) {
            strategy = GatlingDsl.FEEDER_CIRCULAR;
        } else {
            strategy = GatlingDsl.FEEDER_QUEUE;
            c.approximate(csv, "Recycle on EOF off → queue()", stopThread
                ? "When the file runs out, Gatling stops the whole simulation; JMeter stopped only the thread."
                : "When the file runs out, Gatling stops the whole simulation; JMeter kept going with <EOF> as the value.");
        }
        String shareMode = p.string("shareMode", "shareMode.all").trim();
        if (!shareMode.isEmpty() && !shareMode.equals("shareMode.all")) {
            c.approximate(csv, "Sharing mode " + shareMode.replace("shareMode.", "") + " not converted",
                "Gatling feeders are shared by all users of the simulation (JMeter's \"All threads\").");
        }
        String encoding = p.string("fileEncoding").trim();
        if (!encoding.isEmpty() && !encoding.equalsIgnoreCase("UTF-8")) {
            c.approximate(csv, "File encoding " + encoding + " not converted", "Gatling reads feeder files as UTF-8 unless configured otherwise.");
        }
        if (file.isPresent() && (file.get().startsWith("/") || file.get().matches("[A-Za-z]:.*") || file.get().contains(".."))) {
            c.approximate(csv, "CSV file referenced by path `" + file.get() + "`",
                "Gatling looks for the file on its classpath (the resources folder) first. Copy it there and use a relative name, or keep it at this path.");
        }

        String base = csv.name().isBlank() ? fileName : csv.name();
        String name = c.identifiers.field(base.toLowerCase(java.util.Locale.ROOT).endsWith("feeder") ? base : base + " feeder", "feeder");
        fields.add(new SimulationModel.Field(comments, GatlingDsl.FEEDER_TYPE, name,
            GatlingDsl.csvFeeder(fileExpr.orElse(JavaText.quote(fileName)), separator) + strategy));
        fieldByElement.put(csv, name);
        return new Step.Action(List.of("CSV Data Set: " + ChainConverter.stepPath(csv)), GatlingDsl.feed(name));
    }
}
