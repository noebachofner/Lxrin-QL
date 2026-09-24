package ch.lxrin.ql.runtime;

import ch.lxrin.ql.types.JsonCodec;
import ch.lxrin.ql.types.ValueContext;

import java.sql.Connection;

/** The {@link ValueContext} of one statement execution. */
final class ExecValueContext implements ValueContext {

    private final Connection connection;
    private final JsonCodec codec;

    ExecValueContext(Connection connection, JsonCodec codec) {
        this.connection = connection;
        this.codec = codec;
    }

    @Override
    public Connection connection() {
        return connection;
    }

    @Override
    public JsonCodec jsonCodec() {
        if (codec == null) {
            throw new IllegalStateException("no JsonCodec configured: use QueryContext.builder().jsonCodec(..) "
                    + "(lxrin-ql-spring configures a Jackson codec automatically)");
        }
        return codec;
    }
}
