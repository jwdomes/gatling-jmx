package jmx2gatling.convert;

import java.util.ArrayList;
import java.util.List;

/** What converting one step's values produced besides the values: comments and steps to run first. */
final class StepNotes {

    final List<String> comments = new ArrayList<>();
    final List<Step> before = new ArrayList<>();
}
