package ch.lxrin.ql.error;

/** A transaction could not be started, committed or rolled back, or was marked rollback-only. */
public class TransactionException extends LxrinQlException {

    private static final long serialVersionUID = 1L;

    /** Creates the exception. */
    public TransactionException(String message, Throwable cause) {
        super(message, cause);
    }
}
