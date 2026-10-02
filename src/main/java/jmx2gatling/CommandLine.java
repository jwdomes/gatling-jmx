package jmx2gatling;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Positional paths plus boolean flags and single-value options. */
record CommandLine(List<String> paths, Set<String> flags, Map<String, String> options) {

    static CommandLine parse(String[] args, int start, List<String> knownFlags, List<String> knownOptions) {
        List<String> paths = new ArrayList<>();
        Set<String> flags = new HashSet<>();
        Map<String, String> options = new HashMap<>();
        for (int i = start; i < args.length; i++) {
            String arg = args[i];
            if (knownFlags.contains(arg)) {
                flags.add(arg);
            } else if (knownOptions.contains(arg)) {
                if (i + 1 >= args.length) {
                    throw new UsageException(arg + " needs a value");
                }
                options.put(arg, args[++i]);
            } else if (arg.startsWith("--")) {
                throw new UsageException("Unknown option: " + arg);
            } else {
                paths.add(arg);
            }
        }
        return new CommandLine(paths, flags, options);
    }

    boolean flag(String name) {
        return flags.contains(name);
    }

    Optional<String> option(String name) {
        return Optional.ofNullable(options.get(name));
    }

    String required(String name) {
        return option(name).orElseThrow(() -> new UsageException(name + " is required"));
    }
}
