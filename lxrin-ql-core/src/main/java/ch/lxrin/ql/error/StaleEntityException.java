package ch.lxrin.ql.error;

/**
 * An update or delete of an entity matched no row: the row was deleted
 * meanwhile, never existed, or is hidden by a table policy.
 */
public class StaleEntityException extends LxrinQlException {

    private static final long serialVersionUID = 1L;

    /** Creates the exception. */
    public StaleEntityException(String message) {
        super(message);
    }
}
