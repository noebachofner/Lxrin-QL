package ch.lxrin.ql.error;

/** A statement cannot be built or rendered, e.g. an {@code UPDATE} without {@code WHERE}. */
public class InvalidStatementException extends LxrinQlException {

    private static final long serialVersionUID = 1L;

    /** Creates the exception. */
    public InvalidStatementException(String message, Throwable cause) {
        super(message, cause);
    }
}
