package jmx2gatling.parse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import jmx2gatling.model.JmxElement;
import jmx2gatling.model.Property;
import jmx2gatling.model.Props;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * Reads a JMX file into a {@link JmxElement} tree.
 *
 * <p>In JMX an element's children are not nested inside it: they live in the {@code <hashTree>}
 * that immediately follows the element. Every {@code hashTree} therefore holds a sequence of
 * (element, hashTree) pairs.
 */
public final class JmxParser {

    public JmxElement parse(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return parse(in);
        }
    }

    public JmxElement parse(InputStream in) throws IOException {
        Document doc;
        try {
            doc = newDocumentBuilder().parse(in);
        } catch (SAXException e) {
            throw new JmxFormatException("Not well-formed XML: " + e.getMessage());
        }
        Element root = doc.getDocumentElement();
        if (!"jmeterTestPlan".equals(root.getTagName())) {
            throw new JmxFormatException("Root element is <" + root.getTagName() + ">, expected <jmeterTestPlan>");
        }
        Element topTree = firstChildElement(root, "hashTree");
        if (topTree == null) {
            throw new JmxFormatException("<jmeterTestPlan> has no <hashTree>");
        }
        List<JmxElement> top = readHashTree(topTree, null);
        if (top.size() != 1 || !"TestPlan".equals(top.get(0).testClass())) {
            throw new JmxFormatException("Expected exactly one TestPlan at the top level, found " + top.size() + " element(s)");
        }
        return top.get(0);
    }

    /** Reads the (element, hashTree) pairs of {@code hashTree}, attaching them to {@code owner} when given. */
    private List<JmxElement> readHashTree(Element hashTree, JmxElement owner) {
        List<JmxElement> result = new ArrayList<>();
        JmxElement previous = null;
        for (Element child : childElements(hashTree)) {
            if ("hashTree".equals(child.getTagName())) {
                if (previous == null) {
                    throw new JmxFormatException("<hashTree> without a preceding test element under " + describe(owner));
                }
                readHashTree(child, previous);
                previous = null;
            } else {
                previous = readElement(child);
                if (owner != null) {
                    owner.addChild(previous);
                }
                result.add(previous);
            }
        }
        return result;
    }

    private JmxElement readElement(Element node) {
        String testClass = node.hasAttribute("testclass") ? node.getAttribute("testclass") : node.getTagName();
        return new JmxElement(
            testClass,
            node.getAttribute("guiclass"),
            node.getAttribute("testname"),
            !"false".equals(node.getAttribute("enabled")),
            readProps(node));
    }

    private Props readProps(Element node) {
        Map<String, Property> props = new LinkedHashMap<>();
        for (Element child : childElements(node)) {
            Property p = readProperty(child);
            props.put(p.name(), p);
        }
        return new Props(props);
    }

    private Property readProperty(Element node) {
        String tag = node.getTagName();
        switch (tag) {
            case "elementProp":
                return new Property.Element(
                    node.getAttribute("name"), node.getAttribute("elementType"), node.getAttribute("testclass"), readProps(node));
            case "collectionProp": {
                List<Property> items = new ArrayList<>();
                for (Element child : childElements(node)) {
                    items.add(readProperty(child));
                }
                return new Property.Collection(node.getAttribute("name"), items);
            }
            default:
                // Older JMeter versions wrote some properties as <FloatProperty><name>..</name><value>..</value>.
                Element nameChild = firstChildElement(node, "name");
                if (!node.hasAttribute("name") && nameChild != null) {
                    Element valueChild = firstChildElement(node, "value");
                    return new Property.Value(nameChild.getTextContent(), tag, valueChild == null ? "" : valueChild.getTextContent());
                }
                return new Property.Value(node.getAttribute("name"), tag, node.getTextContent());
        }
    }

    private static String describe(JmxElement owner) {
        return owner == null ? "<jmeterTestPlan>" : owner.path();
    }

    private static DocumentBuilder newDocumentBuilder() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // JMX files never need a DTD or external entities; refusing them blocks XXE attacks.
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("The JDK XML parser does not support secure processing", e);
        }
    }

    private static List<Element> childElements(Element parent) {
        List<Element> result = new ArrayList<>();
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e) {
                result.add(e);
            }
        }
        return result;
    }

    private static Element firstChildElement(Element parent, String tag) {
        for (Element e : childElements(parent)) {
            if (tag.equals(e.getTagName())) {
                return e;
            }
        }
        return null;
    }
}
