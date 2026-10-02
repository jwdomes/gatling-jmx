package jmx2gatling.convert;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The scheme://host:port combinations requests go to. The most used one becomes the
 * protocol's base URL; every other one gets its own overridable constant.
 */
final class Origins {

    static final class Origin {
        final String key;
        /** Java expression for the default value, for example {@code "https://api.example.com"}. */
        final String defaultExpr;
        int uses;
        boolean primary;
        String constant;

        Origin(String key, String defaultExpr) {
            this.key = key;
            this.defaultExpr = defaultExpr;
        }
    }

    /** A request URL: {@code el} is relative to {@code origin}, or a full URL when there is no origin. */
    record Url(Origin origin, String el) {

        String javaExpr() {
            if (origin == null || origin.primary) {
                return JavaText.quote(el);
            }
            return origin.constant + " + " + JavaText.quote(el);
        }
    }

    private final Map<String, Origin> byKey = new LinkedHashMap<>();
    private Origin primary;

    Url url(String key, String defaultExpr, String pathEl) {
        Origin origin = byKey.computeIfAbsent(key, k -> new Origin(k, defaultExpr));
        origin.uses++;
        return new Url(origin, pathEl);
    }

    /** Picks the most used origin (first seen on ties) as the base URL and names the constants. */
    void decide(Constants constants) {
        for (Origin o : byKey.values()) {
            if (primary == null || o.uses > primary.uses) {
                primary = o;
            }
        }
        if (primary == null) {
            return;
        }
        primary.primary = true;
        primary.constant = constants.add("origin:" + primary.key, "BASE_URL", "String",
            "System.getProperty(\"baseUrl\", " + primary.defaultExpr + ")", "Override with -DbaseUrl=...");
        int n = 2;
        for (Origin o : byKey.values()) {
            if (o != primary) {
                String property = "baseUrl" + n;
                o.constant = constants.add("origin:" + o.key, "BASE_URL_" + n, "String",
                    "System.getProperty(" + JavaText.quote(property) + ", " + o.defaultExpr + ")",
                    "Another host the plan calls; override with -D" + property + "=...");
                n++;
            }
        }
    }

    Origin primary() {
        return primary;
    }

    List<Origin> secondary() {
        List<Origin> result = new ArrayList<>();
        for (Origin o : byKey.values()) {
            if (o != primary) {
                result.add(o);
            }
        }
        return result;
    }
}
