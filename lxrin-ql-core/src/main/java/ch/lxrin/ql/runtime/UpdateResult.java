package ch.lxrin.ql.runtime;

import java.util.List;

/**
 * The result of a data-modifying statement.
 *
 * @param count the number of affected rows
 * @param rows  the {@code RETURNING} rows (empty without {@code RETURNING})
 */
public record UpdateResult(long count, List<Object[]> rows) {
}
