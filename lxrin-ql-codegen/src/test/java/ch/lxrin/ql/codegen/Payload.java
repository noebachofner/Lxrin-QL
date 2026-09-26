package ch.lxrin.ql.codegen;

import ch.lxrin.ql.types.DataType;
import ch.lxrin.ql.types.SqlTypes;

/**
 * A forced type used by the golden schema.
 *
 * @param json the JSON text
 */
public record Payload(String json) {

    /** The SQL type. */
    public static final DataType<Payload> TYPE = SqlTypes.JSONB.map(Payload.class, Payload::new, Payload::json);
}
