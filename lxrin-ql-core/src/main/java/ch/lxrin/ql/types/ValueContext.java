package ch.lxrin.ql.types;

import java.sql.Connection;

/**
 * What a {@link DataType} may need while it binds or reads a value: the JDBC
 * connection (for SQL arrays) and the JSON codec of the query context.
 */
public interface ValueContext {

    /** Returns the connection the statement runs on. */
    Connection connection();

    /**
     * Returns the JSON codec of the query context.
     *
     * @throws IllegalStateException if no codec is configured
     */
    JsonCodec jsonCodec();
}
