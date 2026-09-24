package ch.lxrin.ql.statement;

import ch.lxrin.ql.schema.Table;

import java.util.List;

/**
 * A row-locking clause such as {@code FOR UPDATE OF t SKIP LOCKED}.
 *
 * @param strength   {@code UPDATE}, {@code NO KEY UPDATE}, {@code SHARE} or {@code KEY SHARE}
 * @param of         the locked tables, or an empty list for all
 * @param waitPolicy {@code null}, {@code NOWAIT} or {@code SKIP LOCKED}
 */
public record Lock(String strength, List<Table<?>> of, String waitPolicy) {

    /** Creates the clause; {@code of} is copied. */
    public Lock {
        of = List.copyOf(of);
    }
}
