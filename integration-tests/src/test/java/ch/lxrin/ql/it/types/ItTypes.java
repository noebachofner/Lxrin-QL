package ch.lxrin.ql.it.types;

import ch.lxrin.ql.types.DataType;
import ch.lxrin.ql.types.SqlTypes;

/** Custom types of the integration test schema. */
public final class ItTypes {

    /** {@code uuid} ↔ {@link UserId}. */
    public static final DataType<UserId> USER_ID = SqlTypes.UUID.map(UserId.class, UserId::new, UserId::value);

    private ItTypes() {}
}
