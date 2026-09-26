package ch.lxrin.ql.runtime;

import java.util.List;

/**
 * The result of a JDBC batch.
 *
 * @param counts the affected rows per parameter set ({@code -2} if the driver does not know)
 * @param rows   the returned rows of all statements, in execution order
 */
public record BatchResult(long[] counts, List<Object[]> rows) {
}
