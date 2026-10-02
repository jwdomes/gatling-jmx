package jmx2gatling.convert;

import java.util.List;
import java.util.Set;

/** Everything {@link JavaWriter} needs to write one simulation class. */
record SimulationModel(
    String packageName,
    String className,
    String sourceFileName,
    /** null with --no-timestamp */
    String generatedOn,
    int todoCount,
    List<Constants.Constant> constants,
    List<Field> feeders,
    List<String> protocolComments,
    List<String> protocolCalls,
    List<ChainField> chains,
    List<ScenarioField> scenarios,
    List<Population> populations,
    String maxDuration,
    Set<Conversion.Helper> helpers,
    boolean usesPattern) {

    record Field(List<String> comments, String type, String name, String expr) {
    }

    record ChainField(List<String> comments, String name, List<Step> steps) {
    }

    record ScenarioField(List<String> comments, String name, String displayName, List<Step> steps) {
    }

    /** An injected scenario and the populations that start when it has finished. */
    record Population(List<String> comments, String expr, List<Population> andThen) {
    }
}
