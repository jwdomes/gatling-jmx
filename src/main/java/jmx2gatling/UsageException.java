package jmx2gatling;

/** The command line is wrong; the message is shown together with the usage text. */
final class UsageException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    UsageException(String message) {
        super(message);
    }
}
