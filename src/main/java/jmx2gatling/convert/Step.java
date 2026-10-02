package jmx2gatling.convert;

import java.util.List;

/**
 * One element of a Gatling chain, before it is written out as Java. Comments are plain text
 * lines (without {@code //}) shown above the step.
 */
sealed interface Step permits Step.Action, Step.Request, Step.Block, Step.Switch, Step.Note {

    List<String> comments();

    /** A single executable expression, for example {@code pause(...)} or a chain field name. */
    record Action(List<String> comments, String code) implements Step {
    }

    record Request(List<String> comments, RequestSpec spec) implements Step {
    }

    /** {@code head(body...)}, for example {@code repeat(3).on(...)}. */
    record Block(List<String> comments, String head, List<Step> body) implements Step {
    }

    /**
     * {@code head(branch, branch, ...)}. A branch with a head renders as {@code head(body...)}
     * (for example {@code percent(50.0).then(...)}); without one, its body is a single executable.
     */
    record Switch(List<String> comments, String head, List<Branch> branches) implements Step {
    }

    record Branch(String head, List<Step> body) {
    }

    /** Comments with no code; they attach to the next step. */
    record Note(List<String> comments) implements Step {
    }
}
