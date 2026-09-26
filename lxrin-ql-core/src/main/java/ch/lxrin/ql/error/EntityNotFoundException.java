package ch.lxrin.ql.error;

/** An entity that was expected to exist was not found, e.g. by {@code getById}. */
public class EntityNotFoundException extends LxrinQlException {

    private static final long serialVersionUID = 1L;

    /** Creates the exception. */
    public EntityNotFoundException(String message) {
        super(message);
    }
}
