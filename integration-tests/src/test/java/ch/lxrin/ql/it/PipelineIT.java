package ch.lxrin.ql.it;

import ch.lxrin.ql.TestSchema.Role;
import ch.lxrin.ql.dsl.Row;
import ch.lxrin.ql.dsl.Sql;
import ch.lxrin.ql.error.StatementRejectedException;
import ch.lxrin.ql.runtime.QueryContext;
import ch.lxrin.ql.runtime.TransactionScope;
import ch.lxrin.ql.schema.AdHocTable;
import ch.lxrin.ql.schema.Column;
import ch.lxrin.ql.schema.Table;
import ch.lxrin.ql.spi.AffectedRow;
import ch.lxrin.ql.spi.ColumnConventions;
import ch.lxrin.ql.spi.DeleteContext;
import ch.lxrin.ql.spi.InsertContext;
import ch.lxrin.ql.spi.SoftDeletePolicy;
import ch.lxrin.ql.spi.StatementListener;
import ch.lxrin.ql.spi.TruncateContext;
import ch.lxrin.ql.spi.TruncateResult;
import ch.lxrin.ql.spi.UpdateContext;
import ch.lxrin.ql.spi.WriteResult;
import ch.lxrin.ql.types.SqlTypes;
import ch.lxrin.ql.types.UuidV7;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static ch.lxrin.ql.TestSchema.OrdersTable.ORDERS;
import static ch.lxrin.ql.TestSchema.UsersTable.USERS;
import static org.junit.jupiter.api.Assertions.*;

/** Policies, conventions and listeners against PostgreSQL. */
class PipelineIT {

    private static final AdHocTable AUDIT = Sql.table("audit_log");
    private static final Column<String> AUDIT_TABLE = AUDIT.field("table_name", SqlTypes.TEXT);
    private static final Column<String> AUDIT_OP = AUDIT.field("operation", SqlTypes.TEXT);
    private static final Column<String> AUDIT_ROW = AUDIT.field("row_id", SqlTypes.TEXT);
    private static final Column<Long> AUDIT_TX = AUDIT.field("tx", SqlTypes.INT8);

    private final Clock clock = Clock.fixed(Instant.parse("2025-03-01T08:00:00Z"), ZoneOffset.UTC);

    /** Writes one audit row per affected row, in the same transaction as the change. */
    static final class AuditListener implements StatementListener {
        static final TransactionScope.Key<Integer> STATEMENTS = TransactionScope.key("audited statements");
        boolean fail;

        @Override
        public boolean appliesTo(Table<?> table) {
            return table.sameTable(USERS) || table.sameTable(ORDERS);
        }

        @Override
        public void beforeInsert(InsertContext c) {
            c.requestReturning(c.table().primaryKey().orElseThrow().columns());
        }

        @Override
        public void beforeUpdate(UpdateContext c) {
            c.requestReturning(c.table().primaryKey().orElseThrow().columns());
        }

        @Override
        public void beforeDelete(DeleteContext c) {
            c.requestReturning(c.table().primaryKey().orElseThrow().columns());
        }

        @Override
        public void beforeTruncate(TruncateContext c) {
            c.captureRowsBeforeTruncate();
        }

        @Override
        public void afterInsert(WriteResult r) {
            write(r, r.wasUpsert() ? null : "INSERT");
        }

        @Override
        public void afterUpdate(WriteResult r) {
            write(r, r.originalKind().name());
        }

        @Override
        public void afterDelete(WriteResult r) {
            write(r, "DELETE");
        }

        @Override
        public void afterTruncate(TruncateResult r) {
            for (var e : r.capturedRows().entrySet()) {
                for (AffectedRow row : e.getValue()) {
                    Column<?> pk = e.getKey().primaryKey().orElseThrow().columns().get(0);
                    r.dsl().insertInto(AUDIT).set(AUDIT_TABLE, e.getKey().name()).set(AUDIT_OP, "TRUNCATE")
                            .set(AUDIT_ROW, String.valueOf(row.values().get(pk))).execute();
                }
            }
        }

        private void write(WriteResult r, String operation) {
            r.transaction().attribute(STATEMENTS, () -> 0);
            Column<?> pk = r.table().primaryKey().orElseThrow().columns().get(0);
            for (AffectedRow row : r.affectedRows()) {
                String op = operation != null ? operation : (row.inserted() ? "INSERT" : "UPDATE");
                r.dsl().insertInto(AUDIT).set(AUDIT_TABLE, r.table().name()).set(AUDIT_OP, op)
                        .set(AUDIT_ROW, String.valueOf(row.values().get(pk))).execute();
            }
            if (fail) throw new IllegalStateException("audit failed");
        }
    }

    private final AuditListener audit = new AuditListener();
    private QueryContext ctx;
    private UUID ada;

    @BeforeEach
    void setUp() {
        Postgres.resetTestSchema();
        ctx = QueryContext.builder()
                .dataSource(Postgres.dataSource())
                .listener(audit)
                .policy(new SoftDeletePolicy("deleted_at", clock))
                .convention(ColumnConventions.onInsert("status", String.class, c -> "NEW"))
                .versionColumn("version")
                .build();
        ada = UuidV7.generate();
        ctx.insertInto(USERS).set(USERS.ID, ada).set(USERS.NAME, "Ada").set(USERS.EMAIL, "ada@x").execute();
    }

    private List<Row> auditRows() {
        return ctx.select(List.of(AUDIT_TABLE, AUDIT_OP, AUDIT_ROW, AUDIT_TX)).from(AUDIT).orderBy(AUDIT.field("id", SqlTypes.INT8).asc()).fetch();
    }

    @Test
    void everyWritePathIsAuditedInTheSameTransaction() {
        UUID alan = UuidV7.generate();
        ctx.insertInto(USERS).columns(USERS.ID, USERS.NAME, USERS.EMAIL).values(alan, "Alan", "alan@x").execute();
        ctx.update(USERS).set(USERS.ROLE, Role.ADMIN).allRows().execute();
        ctx.deleteFrom(USERS).where(USERS.ID.eq(alan)).execute();
        Long orderId = ctx.insertInto(ORDERS).set(ORDERS.USER_ID, ada).set(ORDERS.TOTAL, BigDecimal.TEN)
                .set(ORDERS.ORDERED_ON, LocalDate.now()).returning(ORDERS.ID).fetchOne();
        ctx.bypassing(SoftDeletePolicy.class).deleteFrom(ORDERS).where(ORDERS.ID.eq(orderId)).execute();

        List<Row> rows = auditRows();
        List<String> ops = new ArrayList<>();
        for (Row r : rows) ops.add(r.get(AUDIT_TABLE) + ":" + r.get(AUDIT_OP));
        assertEquals(List.of("users:INSERT", "users:INSERT", "users:UPDATE", "users:UPDATE", "users:DELETE", "orders:INSERT", "orders:DELETE"), ops);
        assertEquals(alan.toString(), rows.get(4).get(AUDIT_ROW));
        // soft delete: the row is hidden but still there
        assertEquals(0L, ctx.selectCount().from(USERS).where(USERS.ID.eq(alan)).fetchOne());
        assertEquals(1L, ctx.bypassing(SoftDeletePolicy.class).selectCount().from(USERS).where(USERS.ID.eq(alan)).fetchOne());
    }

    @Test
    void theChangeAndItsAuditRowShareOneTransaction() {
        UUID id = UuidV7.generate();
        ctx.insertInto(USERS).set(USERS.ID, id).set(USERS.NAME, "T").set(USERS.EMAIL, "t@x").execute();
        long rowTx = ctx.select(Sql.raw("xmin::text::bigint", SqlTypes.INT8)).from(USERS).where(USERS.ID.eq(id)).fetchOne();
        List<Row> rows = auditRows();
        assertEquals(rowTx, (long) rows.get(rows.size() - 1).get(AUDIT_TX));
    }

    @Test
    void aFailingListenerRollsBackTheChange() {
        audit.fail = true;
        UUID id = UuidV7.generate();
        assertThrows(IllegalStateException.class, () -> ctx.insertInto(USERS).set(USERS.ID, id).set(USERS.NAME, "X").set(USERS.EMAIL, "x@x").execute());
        audit.fail = false;
        assertEquals(0L, ctx.selectCount().from(USERS).where(USERS.ID.eq(id)).fetchOne());
        assertEquals(1, auditRows().size());
    }

    @Test
    void upsertsReportInsertOrUpdate() {
        ctx.insertInto(USERS).set(USERS.ID, UuidV7.generate()).set(USERS.NAME, "Ada 2").set(USERS.EMAIL, "ada@x")
                .onConflict(USERS.EMAIL).doUpdateSetExcluded(USERS.NAME).execute();
        ctx.insertInto(USERS).set(USERS.ID, UuidV7.generate()).set(USERS.NAME, "New").set(USERS.EMAIL, "new@x")
                .onConflict(USERS.EMAIL).doUpdateSetExcluded(USERS.NAME).execute();
        List<Row> rows = auditRows();
        assertEquals("UPDATE", rows.get(1).get(AUDIT_OP));
        assertEquals(ada.toString(), rows.get(1).get(AUDIT_ROW));
        assertEquals("INSERT", rows.get(2).get(AUDIT_OP));
    }

    @Test
    void conventionsAndVersionColumn() {
        Long id = ctx.insertInto(ORDERS).set(ORDERS.USER_ID, ada).set(ORDERS.TOTAL, BigDecimal.ONE).set(ORDERS.ORDERED_ON, LocalDate.now())
                .returning(ORDERS.ID).fetchOne();
        assertEquals("NEW", ctx.select(ORDERS.STATUS).from(ORDERS).where(ORDERS.ID.eq(id)).fetchOne());
        ctx.update(USERS).set(USERS.NAME, "Ada L.").where(USERS.ID.eq(ada)).execute();
        ctx.update(USERS).set(USERS.NAME, "Ada Lovelace").where(USERS.ID.eq(ada)).execute();
        assertEquals(2L, ctx.select(USERS.VERSION).from(USERS).where(USERS.ID.eq(ada)).fetchOne());
    }

    @Test
    void truncateCapturesRowsForListeners() {
        QueryContext plain = ctx.derive(b -> {});
        plain.truncate(USERS).cascade().execute();
        List<Row> rows = auditRows();
        assertEquals("TRUNCATE", rows.get(rows.size() - 1).get(AUDIT_OP));
        assertEquals(ada.toString(), rows.get(rows.size() - 1).get(AUDIT_ROW));
        QueryContext rejecting = ctx.derive(b -> b.listener(new StatementListener() {
            @Override
            public void beforeTruncate(TruncateContext c) {
                c.reject("audited tables cannot be truncated");
            }
        }));
        assertThrows(StatementRejectedException.class, () -> rejecting.truncate(ORDERS).execute());
    }
}
