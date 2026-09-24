package ch.lxrin.ql.statement;

/** The kind of a statement. */
public enum StatementKind {
    /** {@code SELECT} */
    SELECT,
    /** {@code INSERT} (including upserts) */
    INSERT,
    /** {@code UPDATE} */
    UPDATE,
    /** {@code DELETE} */
    DELETE,
    /** {@code TRUNCATE} */
    TRUNCATE,
    /** Anything written with {@code Sql.statement(..)} */
    OTHER
}
