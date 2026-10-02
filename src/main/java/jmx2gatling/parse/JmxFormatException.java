package jmx2gatling.parse;

/** The file is not a JMX test plan the parser can read. */
public final class JmxFormatException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public JmxFormatException(String message) {
        super(message);
    }
}
