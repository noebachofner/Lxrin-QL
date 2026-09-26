package ch.lxrin.ql.spi;

import ch.lxrin.ql.render.RenderedSql;
import ch.lxrin.ql.schema.Table;
import ch.lxrin.ql.statement.StatementKind;

import java.util.List;

/**
 * One executed statement, as seen by {@link ExecutionObserver}s.
 *
 * @param kind      the statement kind
 * @param tables    the tables the statement reads or writes directly
 * @param sql       the SQL and its binds; {@link RenderedSql#toString()} redacts sensitive values
 * @param origin    where the statement comes from
 * @param batchSize the number of parameter sets for a JDBC batch, 1 otherwise
 */
public record StatementEvent(StatementKind kind, List<Table<?>> tables, RenderedSql sql, Origin origin, int batchSize) {
}
