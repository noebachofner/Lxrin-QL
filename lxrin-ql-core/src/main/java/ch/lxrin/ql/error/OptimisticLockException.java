package ch.lxrin.ql.error;

/** The version column of an entity did not match: someone else changed the row meanwhile. */
public class OptimisticLockException extends StaleEntityException {

    private static final long serialVersionUID = 1L;

    /** Creates the exception. */
    public OptimisticLockException(String message) {
        super(message);
    }
}
