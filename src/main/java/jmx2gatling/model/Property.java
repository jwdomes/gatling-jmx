package jmx2gatling.model;

import java.util.List;

/**
 * A JMeter test-element property as serialized in a JMX file.
 *
 * <p>{@code stringProp}, {@code boolProp}, {@code intProp}, {@code longProp} (and the rarer
 * {@code floatProp}/{@code doubleProp}) are all {@link Value}s that keep their raw text; typed
 * reading happens in {@link Props}.
 */
public sealed interface Property permits Property.Value, Property.Element, Property.Collection {

    String name();

    /** A scalar property. {@code type} is the XML tag, for example {@code stringProp}. */
    record Value(String name, String type, String text) implements Property {
    }

    /** An {@code elementProp}: a nested test element such as an HTTP argument or a header. */
    record Element(String name, String elementType, String testClass, Props props) implements Property {
    }

    /** A {@code collectionProp}: an ordered list of properties. */
    record Collection(String name, List<Property> items) implements Property {
    }
}
