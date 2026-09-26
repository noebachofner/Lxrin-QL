package com.example.blog;

import ch.lxrin.ql.types.DataType;
import ch.lxrin.ql.types.SqlTypes;

/** The data types of the forced types. */
public final class BlogTypes {

    /** {@code uuid} as {@link AuthorId}. */
    public static final DataType<AuthorId> AUTHOR_ID = SqlTypes.UUID.map(AuthorId.class, AuthorId::new, AuthorId::value);

    private BlogTypes() {
    }
}
