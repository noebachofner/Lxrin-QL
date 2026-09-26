package ch.lxrin.ql.dsl;

import ch.lxrin.ql.runtime.QueryContext;
import ch.lxrin.ql.schema.Column;
import ch.lxrin.ql.schema.Table;
import ch.lxrin.ql.schema.UniqueKey;
import ch.lxrin.ql.statement.InsertStatement;

import java.util.Arrays;

/**
 * Clauses shared by all {@code INSERT} builders: {@code ON CONFLICT} and
 * {@code OVERRIDING}.
 *
 * @param <R> the table's row type
 * @param <S> the concrete builder type
 */
public abstract class AbstractInsert<R, S extends AbstractInsert<R, S>> extends AbstractDml<R, S> {

    final InsertStatement statement;

    AbstractInsert(QueryContext context, Table<R> table) {
        super(context, table);
        this.statement = new InsertStatement(table);
    }

    @Override
    public InsertStatement dmlStatement() {
        return statement;
    }

    /** {@code OVERRIDING SYSTEM VALUE} – write explicit values into {@code GENERATED ALWAYS} identity columns. */
    public S overridingSystemValue() {
        statement.overriding(InsertStatement.Overriding.SYSTEM_VALUE);
        return self();
    }

    /** {@code OVERRIDING USER VALUE} */
    public S overridingUserValue() {
        statement.overriding(InsertStatement.Overriding.USER_VALUE);
        return self();
    }

    /** {@code ON CONFLICT (columns)} – continue with {@link #doNothing()} or {@code doUpdateSet..}. */
    public S onConflict(Column<?>... columns) {
        statement.onConflict(Arrays.asList(columns));
        return self();
    }

    /** {@code ON CONFLICT ON CONSTRAINT name} for a generated unique key. */
    public S onConflictOnConstraint(UniqueKey key) {
        statement.onConflictOnConstraint(key.name());
        return self();
    }

    /** Adds an index predicate to the conflict target, for partial unique indexes. */
    public S onConflictWhere(Condition condition) {
        statement.addConflictWhere(AbstractSelect.requireCondition(condition));
        return self();
    }

    /** {@code ON CONFLICT DO NOTHING}; without {@code onConflict(..)} any conflict is ignored. */
    public S doNothing() {
        statement.doNothing();
        return self();
    }

    /** {@code DO UPDATE SET column = ?} */
    public <T> S doUpdateSet(Column<T> column, T value) {
        return doUpdateSet(column, Values.param(value, column.type()));
    }

    /** {@code DO UPDATE SET column = expr} */
    public <T> S doUpdateSet(Column<T> column, Field<T> value) {
        requireConflict();
        statement.updates().put(column, require(value));
        return self();
    }

    /** {@code DO UPDATE SET col = EXCLUDED.col} for each column – the classic upsert. */
    public S doUpdateSetExcluded(Column<?>... columns) {
        requireConflict();
        for (Column<?> c : columns) statement.updates().put(c, excluded(c));
        return self();
    }

    /** {@code DO UPDATE SET ... WHERE condition} */
    public S doUpdateWhere(Condition condition) {
        requireConflict();
        statement.addUpdateWhere(AbstractSelect.requireCondition(condition));
        return self();
    }

    /** Returns {@code EXCLUDED.column} as a typed field, e.g. for {@code doUpdateSet(c, excluded(c).plus(c))}. */
    public static <T> Field<T> excluded(Column<T> column) {
        return Fields.of(column.type(), InsertStatement.excluded(column));
    }

    private void requireConflict() {
        if (!statement.onConflict()) throw new IllegalStateException("call onConflict(..) first");
    }

    void requireEmpty() {
        if (!statement.rows().isEmpty() || statement.source() != null) {
            throw new IllegalStateException("columns(..) cannot be combined with set(..)");
        }
    }
}
