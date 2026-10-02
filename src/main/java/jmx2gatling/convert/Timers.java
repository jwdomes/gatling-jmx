package jmx2gatling.convert;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import jmx2gatling.model.JmxElement;

/**
 * Converts the timers in a sampler's scope into one pause before the request. JMeter runs every
 * timer in scope before each sampler and adds their delays together.
 */
final class Timers {

    /** A sum of milliseconds: a literal part plus Java int expressions (property constants). */
    private static final class Millis {
        long literal;
        final List<String> expressions = new ArrayList<>();

        void add(Millis other) {
            literal += other.literal;
            expressions.addAll(other.expressions);
        }

        String java() {
            List<String> terms = new ArrayList<>(expressions);
            if (literal != 0 || terms.isEmpty()) {
                terms.add(literal > Integer.MAX_VALUE ? literal + "L" : String.valueOf(literal));
            }
            return String.join(" + ", terms);
        }

        boolean isZero() {
            return literal == 0 && expressions.isEmpty();
        }

        boolean sameAs(Millis other) {
            return literal == other.literal && expressions.equals(other.expressions);
        }
    }

    private final Conversion c;
    private final StaticValues statics;

    Timers(Conversion c, StaticValues statics) {
        this.c = c;
        this.statics = statics;
    }

    Optional<Step> pause(JmxElement sampler, List<JmxElement> timers, StepNotes notes) {
        if (timers.isEmpty()) {
            return Optional.empty();
        }
        Millis min = new Millis();
        Millis max = new Millis();
        List<String> sources = new ArrayList<>();
        int randomTimers = 0;
        for (JmxElement timer : timers) {
            c.appliedElements.add(timer);
            switch (timer.testClass()) {
                case "ConstantTimer" -> {
                    Optional<Millis> delay = millis(timer, "ConstantTimer.delay", notes);
                    if (delay.isPresent()) {
                        min.add(delay.get());
                        max.add(delay.get());
                        sources.add(timer.name() + " (" + timer.testClass() + ")");
                    }
                }
                case "UniformRandomTimer" -> {
                    Optional<Millis> offset = millis(timer, "ConstantTimer.delay", notes);
                    Optional<Millis> range = millis(timer, "RandomTimer.range", notes);
                    if (offset.isPresent() && range.isPresent()) {
                        min.add(offset.get());
                        max.add(offset.get());
                        max.add(range.get());
                        sources.add(timer.name() + " (" + timer.testClass() + ")");
                        randomTimers++;
                    }
                }
                case "GaussianRandomTimer" -> {
                    Optional<Millis> offset = millis(timer, "ConstantTimer.delay", notes);
                    Optional<Millis> deviation = millis(timer, "RandomTimer.range", notes);
                    if (offset.isPresent() && deviation.isPresent()) {
                        gaussian(offset.get(), deviation.get(), min, max);
                        sources.add(timer.name() + " (" + timer.testClass() + ")");
                        randomTimers++;
                        c.approximate(timer, "Gaussian delay → uniform pause between offset - deviation and offset + deviation",
                            "Gatling has no per-pause normal distribution. The mean is the same; the spread is narrower and never exceeds offset + deviation.");
                    }
                }
                case "ConstantThroughputTimer", "PreciseThroughputTimer", "SyncTimer" ->
                    notes.comments.addAll(c.todo(timer, timer.testClass() + " not converted; the closest Gatling equivalent is throttle(...) on the setUp", ""));
                default -> {
                    if (Scripts.isScripted(timer)) {
                        notes.comments.addAll(c.todo(timer, "Script timer not converted", Scripts.of(timer)));
                    } else {
                        notes.comments.addAll(c.todo(timer, timer.testClass() + " not converted", ""));
                    }
                }
            }
        }
        if (sources.isEmpty() || max.isZero()) {
            return Optional.empty();
        }
        if (randomTimers > 1) {
            c.approximate(sampler, "Several random timers summed into one uniform pause",
                "The sum of several random delays is not uniformly distributed; the pause keeps the same minimum and maximum.");
        }
        String code = min.sameAs(max)
            ? GatlingDsl.pause(GatlingDsl.millis(min.java()))
            : GatlingDsl.pause(GatlingDsl.millis(min.java()), GatlingDsl.millis(max.java()));
        return Optional.of(new Step.Action(List.of("Timers: " + String.join(", ", sources)), code));
    }

    /** JMeter: |N(0,1) * deviation + offset|. Approximated as uniform in [max(0, offset - deviation), offset + deviation]. */
    private static void gaussian(Millis offset, Millis deviation, Millis min, Millis max) {
        if (offset.expressions.isEmpty() && deviation.expressions.isEmpty()) {
            min.literal += Math.max(0, offset.literal - deviation.literal);
        } else {
            min.expressions.add("Math.max(0, " + offset.java() + " - (" + deviation.java() + "))");
        }
        max.add(offset);
        max.add(deviation);
    }

    private Optional<Millis> millis(JmxElement timer, String property, StepNotes notes) {
        String text = timer.props().string(property).trim();
        Millis result = new Millis();
        if (text.isEmpty()) {
            return Optional.of(result);
        }
        if (text.matches("\\d{1,15}(\\.\\d*)?")) {
            result.literal = Math.round(Double.parseDouble(text));
            return Optional.of(result);
        }
        Optional<String> expr = statics.integer(text, timer);
        if (expr.isPresent()) {
            result.expressions.add(expr.get());
            return Optional.of(result);
        }
        notes.comments.addAll(c.todo(timer, "Timer value `" + text + "` depends on runtime variables", text));
        return Optional.empty();
    }
}
