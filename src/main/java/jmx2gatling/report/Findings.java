package jmx2gatling.report;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jmx2gatling.inventory.SupportMatrix;
import jmx2gatling.model.JmxElement;

/** Everything about one converted file that the report must show. */
public final class Findings {

    /** {@code original} is the script or expression the TODO is about, or "" when there is none. */
    public record Todo(int id, String path, String type, String reason, String original) {
    }

    public record Approximation(String path, String what, String difference) {
    }

    public record Ignored(String path, String type, String reason) {
    }

    /** {@code first} is true the first time this TODO is referenced from the generated code. */
    public record TodoRef(Todo todo, boolean first) {
    }

    private final List<Todo> todos = new ArrayList<>();
    private final Map<JmxElement, Map<String, Todo>> todosByElement = new IdentityHashMap<>();
    private final Set<Approximation> approximations = new LinkedHashSet<>();
    private final Set<Ignored> ignored = new LinkedHashSet<>();
    private final Map<String, Integer> samplerCounts = new HashMap<>();

    /** Registers a TODO, or returns the existing one when the same element and reason come up again. */
    public TodoRef todo(JmxElement element, String reason, String original) {
        Map<String, Todo> forElement = todosByElement.computeIfAbsent(element, e -> new HashMap<>());
        Todo existing = forElement.get(reason);
        if (existing != null) {
            return new TodoRef(existing, false);
        }
        Todo todo = new Todo(todos.size() + 1, element.path(), SupportMatrix.typeOf(element), reason, original);
        todos.add(todo);
        forElement.put(reason, todo);
        return new TodoRef(todo, true);
    }

    public void approximate(JmxElement element, String what, String difference) {
        approximations.add(new Approximation(element.path(), what, difference));
    }

    public void ignore(JmxElement element, String reason) {
        ignored.add(new Ignored(element.path(), SupportMatrix.typeOf(element), reason));
    }

    public void countSampler(boolean converted) {
        samplerCounts.merge(converted ? "converted" : "notConverted", 1, Integer::sum);
    }

    public List<Todo> todos() {
        return List.copyOf(todos);
    }

    public List<Approximation> approximations() {
        return List.copyOf(approximations);
    }

    public List<Ignored> ignored() {
        return List.copyOf(ignored);
    }

    public int samplersConverted() {
        return samplerCounts.getOrDefault("converted", 0);
    }

    public int samplersNotConverted() {
        return samplerCounts.getOrDefault("notConverted", 0);
    }
}
