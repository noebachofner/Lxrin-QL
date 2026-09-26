package ch.lxrin.ql.error;

/**
 * A sort parameter from outside (e.g. {@code ?sort=name,desc}) names a key
 * that is not in the whitelist or has an invalid direction. Map it to
 * HTTP 400.
 */
public class InvalidSortException extends LxrinQlException {

    private static final long serialVersionUID = 1L;

    /** Creates the exception. */
    public InvalidSortException(String message) {
        super(message);
    }
}
