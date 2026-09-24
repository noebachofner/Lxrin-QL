package ch.lxrin.ql.it.types;

import java.util.Objects;
import java.util.UUID;

/**
 * A value object for user ids, mapped by a forced type in the code generator.
 *
 * @param value the UUID
 */
public record UserId(UUID value) {

    /** Checks the value. */
    public UserId {
        Objects.requireNonNull(value, "value");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
