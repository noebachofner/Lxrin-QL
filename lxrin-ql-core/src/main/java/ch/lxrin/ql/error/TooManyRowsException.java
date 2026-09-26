package ch.lxrin.ql.error;

/** {@code fetchOne()} or {@code findOne(..)} found more than one row. */
public class TooManyRowsException extends LxrinQlException {

    private static final long serialVersionUID = 1L;

    /** Creates the exception. */
    public TooManyRowsException(String message) {
        super(message);
    }
}
