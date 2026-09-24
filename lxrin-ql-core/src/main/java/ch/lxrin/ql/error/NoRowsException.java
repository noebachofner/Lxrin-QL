package ch.lxrin.ql.error;

/** {@code fetchOne()} found no row. */
public class NoRowsException extends LxrinQlException {

    private static final long serialVersionUID = 1L;

    /** Creates the exception. */
    public NoRowsException(String message) {
        super(message);
    }
}
