package ch.lxrin.ql.error;

/** A listener or table policy rejected a statement, e.g. {@code TRUNCATE} of an audited table. */
public class StatementRejectedException extends LxrinQlException {

    private static final long serialVersionUID = 1L;

    /** Creates the exception. */
    public StatementRejectedException(String message) {
        super(message);
    }
}
