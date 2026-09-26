package ch.lxrin.ql.spi;

import ch.lxrin.ql.dsl.Condition;
import ch.lxrin.ql.dsl.Values;
import ch.lxrin.ql.schema.Column;
import ch.lxrin.ql.schema.Table;
import ch.lxrin.ql.statement.DeleteStatement;
import ch.lxrin.ql.statement.DmlStatement;
import ch.lxrin.ql.statement.UpdateStatement;
import ch.lxrin.ql.types.SqlTypes;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Soft delete with a nullable {@code timestamptz} column such as
 * {@code deleted_at}: every read and write of a table that has the column
 * only sees rows where it is {@code NULL}, and {@code DELETE} becomes
 * {@code UPDATE … SET deleted_at = now}.
 *
 * <p>Listeners see the resulting {@code UPDATE} with
 * {@code originalKind() == DELETE}. To see or really delete soft-deleted
 * rows, bypass the policy: {@code ctx.bypassing(SoftDeletePolicy.class)}.</p>
 */
public class SoftDeletePolicy implements TablePolicy {

    private final String column;
    private final Clock clock;

    /**
     * @param column the column name, e.g. {@code deleted_at}
     * @param clock  the clock for the deletion time
     */
    public SoftDeletePolicy(String column, Clock clock) {
        this.column = Objects.requireNonNull(column, "column");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public boolean appliesTo(Table<?> table) {
        return table.column(column).filter(c -> c.type().javaType() == Instant.class).isPresent();
    }

    @Override
    public Condition filter(Table<?> table, PolicyContext context) {
        return table.column(column, SqlTypes.TIMESTAMPTZ).isNull();
    }

    @Override
    public DmlStatement onDelete(DeleteStatement statement, PolicyContext context) {
        UpdateStatement update = statement.toUpdate();
        Column<Instant> c = statement.table().column(column, SqlTypes.TIMESTAMPTZ);
        update.set(c, Values.param(clock.instant(), SqlTypes.TIMESTAMPTZ), true);
        return update;
    }
}
