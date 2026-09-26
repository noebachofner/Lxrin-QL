package ch.lxrin.ql.error;

/**
 * Base class of all exceptions thrown by LxrinQL. Unchecked.
 *
 * <p>Exceptions raised while executing a statement carry the SQL, the bind
 * values (sensitive ones redacted) and the SQLSTATE.</p>
 */
public class LxrinQlException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Creates an exception with a message. */
    public LxrinQlException(String message) {
        super(message);
    }

    /** Creates an exception with a message and a cause. */
    public LxrinQlException(String message, Throwable cause) {
        super(message, cause);
    }
}
