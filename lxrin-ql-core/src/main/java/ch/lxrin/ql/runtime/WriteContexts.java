package ch.lxrin.ql.runtime;

import ch.lxrin.ql.dsl.Condition;
import ch.lxrin.ql.dsl.Field;
import ch.lxrin.ql.dsl.Values;
import ch.lxrin.ql.error.StatementRejectedException;
import ch.lxrin.ql.schema.Column;
import ch.lxrin.ql.schema.Table;
import ch.lxrin.ql.spi.AffectedRow;
import ch.lxrin.ql.spi.DeleteContext;
import ch.lxrin.ql.spi.EntityWrite;
import ch.lxrin.ql.spi.InsertContext;
import ch.lxrin.ql.spi.Origin;
import ch.lxrin.ql.spi.StatementListener;
import ch.lxrin.ql.spi.TruncateContext;
import ch.lxrin.ql.spi.TruncateResult;
import ch.lxrin.ql.spi.UpdateContext;
import ch.lxrin.ql.spi.WriteContext;
import ch.lxrin.ql.spi.WriteResult;
import ch.lxrin.ql.statement.DeleteStatement;
import ch.lxrin.ql.statement.DmlStatement;
import ch.lxrin.ql.statement.InsertStatement;
import ch.lxrin.ql.statement.StatementKind;
import ch.lxrin.ql.statement.TruncateStatement;
import ch.lxrin.ql.statement.UpdateStatement;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Implementations of the contexts handed to policies and listeners. */
final class WriteContexts {

    private WriteContexts() {}

    abstract static class Base implements WriteContext {
        final QueryContext ctx;
        final DmlStatement statement;
        final Origin origin;
        final List<EntityWrite> entityWrites;
        final Set<Column<?>> requested = new LinkedHashSet<>();

        Base(QueryContext ctx, DmlStatement statement, Origin origin, List<EntityWrite> entityWrites) {
            this.ctx = ctx;
            this.statement = statement;
            this.origin = origin;
            this.entityWrites = entityWrites;
        }

        @Override
        public Table<?> table() {
            return statement.table();
        }

        @Override
        public StatementKind kind() {
            return statement.kind();
        }

        @Override
        public Origin origin() {
            return origin;
        }

        @Override
        public List<EntityWrite> entityWrites() {
            return entityWrites;
        }

        @Override
        public void requestReturning(List<? extends Column<?>> columns) {
            for (Column<?> c : columns) {
                if (!c.table().sameTable(statement.table())) {
                    throw new IllegalArgumentException("column " + c + " does not belong to " + statement.table().qualifiedName());
                }
                requested.add(statement.table().column(c.name()).orElseThrow());
            }
        }

        @Override
        public void reject(String reason) {
            throw new StatementRejectedException(reason);
        }

        @Override
        public QueryContext queryContext() {
            return ctx;
        }
    }

    static final class Insert extends Base implements InsertContext {
        Insert(QueryContext ctx, InsertStatement statement, Origin origin, List<EntityWrite> entityWrites) {
            super(ctx, statement, origin, entityWrites);
        }

        @Override
        public InsertStatement statement() {
            return (InsertStatement) statement;
        }

        @Override
        public <T> void setValue(Column<T> column, T value) {
            statement().setValue(own(column), Values.param(value, column.type()), false);
        }

        @Override
        public <T> void setValue(Column<T> column, Field<T> value) {
            statement().setValue(own(column), value, false);
        }

        @Override
        public <T> void forceValue(Column<T> column, T value) {
            statement().setValue(own(column), Values.param(value, column.type()), true);
        }
    }

    static final class Update extends Base implements UpdateContext {
        Update(QueryContext ctx, UpdateStatement statement, Origin origin, List<EntityWrite> entityWrites) {
            super(ctx, statement, origin, entityWrites);
        }

        @Override
        public UpdateStatement statement() {
            return (UpdateStatement) statement;
        }

        @Override
        public <T> void set(Column<T> column, T value) {
            statement().set(own(column), Values.param(value, column.type()), false);
        }

        @Override
        public <T> void set(Column<T> column, Field<T> value) {
            statement().set(own(column), value, false);
        }

        @Override
        public void addCondition(Condition condition) {
            statement().addWhere(condition);
        }
    }

    static final class Delete extends Base implements DeleteContext {
        Delete(QueryContext ctx, DeleteStatement statement, Origin origin, List<EntityWrite> entityWrites) {
            super(ctx, statement, origin, entityWrites);
        }

        @Override
        public DeleteStatement statement() {
            return (DeleteStatement) statement;
        }

        @Override
        public void addCondition(Condition condition) {
            statement().addWhere(condition);
        }
    }

    /** Resolves a column of the canonical table to the statement's (possibly aliased) table. */
    @SuppressWarnings("unchecked")
    static <T> Column<T> own(Column<T> column) {
        return column;
    }

    static final class Truncate implements TruncateContext {
        final QueryContext ctx;
        final TruncateStatement statement;
        final Origin origin;
        boolean capture;

        Truncate(QueryContext ctx, TruncateStatement statement, Origin origin) {
            this.ctx = ctx;
            this.statement = statement;
            this.origin = origin;
        }

        @Override
        public TruncateStatement statement() {
            return statement;
        }

        @Override
        public List<Table<?>> tables() {
            return statement.tables();
        }

        @Override
        public Origin origin() {
            return origin;
        }

        @Override
        public void reject(String reason) {
            throw new StatementRejectedException(reason);
        }

        @Override
        public void captureRowsBeforeTruncate() {
            capture = true;
        }

        @Override
        public QueryContext queryContext() {
            return ctx;
        }
    }

    static final class Result implements WriteResult {
        private final Base write;
        private final long rowCount;
        private final List<AffectedRow> rows;
        private final boolean upsert;
        private final StatementListener listener;

        Result(Base write, long rowCount, List<AffectedRow> rows, boolean upsert, StatementListener listener) {
            this.write = write;
            this.rowCount = rowCount;
            this.rows = rows;
            this.upsert = upsert;
            this.listener = listener;
        }

        Result forListener(StatementListener l) {
            return new Result(write, rowCount, rows, upsert, l);
        }

        @Override
        public Table<?> table() {
            return write.table();
        }

        @Override
        public StatementKind kind() {
            return write.statement.kind();
        }

        @Override
        public StatementKind originalKind() {
            return write.statement instanceof UpdateStatement ? ((UpdateStatement) write.statement).originalKind() : kind();
        }

        @Override
        public long rowCount() {
            return rowCount;
        }

        @Override
        public List<AffectedRow> affectedRows() {
            return rows;
        }

        @Override
        public boolean wasUpsert() {
            return upsert;
        }

        @Override
        public List<EntityWrite> entityWrites() {
            return write.entityWrites;
        }

        @Override
        public Origin origin() {
            return write.origin;
        }

        @Override
        public QueryContext dsl() {
            return write.ctx.withOrigin(Origin.listener(listener == null ? "listener" : listener.getClass().getSimpleName()));
        }

        @Override
        public TransactionScope transaction() {
            return write.ctx.transactions.current().orElseThrow(() ->
                    new IllegalStateException("no active transaction"));
        }
    }

    static final class TruncateDone implements TruncateResult {
        private final Truncate truncate;
        private final Map<Table<?>, List<AffectedRow>> captured;

        TruncateDone(Truncate truncate, Map<Table<?>, List<AffectedRow>> captured) {
            this.truncate = truncate;
            this.captured = captured;
        }

        @Override
        public List<Table<?>> tables() {
            return truncate.tables();
        }

        @Override
        public Map<Table<?>, List<AffectedRow>> capturedRows() {
            return captured;
        }

        @Override
        public QueryContext dsl() {
            return truncate.ctx.withOrigin(Origin.listener("truncate"));
        }

        @Override
        public TransactionScope transaction() {
            return truncate.ctx.transactions.current().orElseThrow(() -> new IllegalStateException("no active transaction"));
        }
    }

    static List<Column<?>> list(Set<Column<?>> set) {
        return new ArrayList<>(set);
    }
}
