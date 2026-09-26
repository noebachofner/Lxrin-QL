package ch.lxrin.ql.it.isms;

import ch.lxrin.ql.dsl.Insert;
import ch.lxrin.ql.schema.Column;
import ch.lxrin.ql.schema.Table;
import ch.lxrin.ql.spi.AffectedRow;
import ch.lxrin.ql.spi.DeleteContext;
import ch.lxrin.ql.spi.InsertContext;
import ch.lxrin.ql.spi.StatementListener;
import ch.lxrin.ql.spi.TruncateContext;
import ch.lxrin.ql.spi.UpdateContext;
import ch.lxrin.ql.spi.WriteResult;
import ch.lxrin.ql.statement.StatementKind;
import ch.lxrin.ql.runtime.TransactionScope;

import ch.lxrin.ql.types.SqlTypes;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static ch.lxrin.ql.dsl.AbstractInsert.excluded;
import static ch.lxrin.ql.dsl.Dsl.caseWhen;
import static ch.lxrin.ql.dsl.Dsl.param;
import static ch.lxrin.ql.it.db.Tables.*;

/**
 * The audit history of the ISMS, built only on LxrinQL extension points (it is
 * not part of the library): every change to an audited table writes a row to
 * {@code <table>_aud}, linked to one {@code revision} row per transaction
 * (who, when). Entity saves, {@code saveAll}, bulk DSL updates and deletes,
 * soft deletes and upserts are all covered, because they all go through the
 * statement pipeline.
 */
public final class IsmsAuditListener implements StatementListener {

    /** Audit revision types. */
    public enum RevType {
        /** Added. */
        ADD,
        /** Modified. */
        MOD,
        /** Deleted (hard or soft). */
        DEL
    }

    private static final TransactionScope.Key<Long> REVISION_ID = TransactionScope.key("isms.revision");

    private final Map<Table<?>, Table<?>> auditTables = Map.of(USERS, USER_AUD, ASSET, ASSET_AUD);
    private final Supplier<String> currentUser;
    private final Clock clock;

    /**
     * @param currentUser the user of the current request (e.g. the Keycloak principal)
     * @param clock       the clock for revision timestamps
     */
    public IsmsAuditListener(Supplier<String> currentUser, Clock clock) {
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Override
    public boolean appliesTo(Table<?> table) {
        for (Table<?> audited : auditTables.keySet()) if (audited.sameTable(table)) return true;
        return false;
    }

    @Override
    public void beforeInsert(InsertContext c) {
        c.requestReturning(c.table().columns());
    }

    @Override
    public void beforeUpdate(UpdateContext c) {
        c.requestReturning(c.table().columns());
    }

    @Override
    public void beforeDelete(DeleteContext c) {
        c.requestReturning(c.table().columns());
    }

    @Override
    public void beforeTruncate(TruncateContext c) {
        c.reject("audited tables cannot be truncated");
    }

    @Override
    public void afterInsert(WriteResult r) {
        write(r, RevType.ADD);
    }

    @Override
    public void afterUpdate(WriteResult r) {
        write(r, r.originalKind() == StatementKind.DELETE ? RevType.DEL : RevType.MOD);
    }

    @Override
    public void afterDelete(WriteResult r) {
        write(r, RevType.DEL);
    }

    private void write(WriteResult r, RevType defaultType) {
        if (r.affectedRows().isEmpty()) return;
        long revision = r.transaction().attribute(REVISION_ID, () -> r.dsl().insertInto(REVISION)
                .set(REVISION.CREATED_BY, currentUser.get())
                .set(REVISION.CREATED_AT, clock.instant())
                .returning(REVISION.ID).fetchOne());
        Table<?> audit = auditTableOf(r.table());
        for (AffectedRow row : r.affectedRows()) {
            RevType type = r.wasUpsert() ? (row.inserted() ? RevType.ADD : RevType.MOD) : defaultType;
            Insert<?> insert = r.dsl().insertInto(audit)
                    .setUnchecked(audit.column("rev").orElseThrow(), revision)
                    .setUnchecked(audit.column("revtype").orElseThrow(), type.name());
            List<Column<?>> data = new ArrayList<>();
            for (Map.Entry<Column<?>, Object> value : row.values().entrySet()) {
                Column<?> target = audit.column(value.getKey().name()).orElseThrow();
                insert.setUnchecked(target, convert(value.getKey(), target, value.getValue()));
                if (!target.primaryKey()) data.add(target);
            }
            // a row changed several times in one transaction keeps one audit row with its latest state;
            // ADD stays ADD within the revision, DEL wins
            Column<String> revtype = audit.column("revtype", SqlTypes.TEXT);
            insert.onConflict(audit.primaryKey().orElseThrow().columns().toArray(new Column<?>[0]))
                    .doUpdateSetExcluded(data.toArray(new Column<?>[0]))
                    .doUpdateSet(revtype, caseWhen(excluded(revtype).eq(RevType.DEL.name()), param(RevType.DEL.name()))
                            .otherwise(revtype))
                    .execute();
        }
    }

    private Table<?> auditTableOf(Table<?> table) {
        for (Map.Entry<Table<?>, Table<?>> e : auditTables.entrySet()) if (e.getKey().sameTable(table)) return e.getValue();
        throw new IllegalArgumentException("not audited: " + table);
    }

    /** Copies a value between columns whose Java types may differ (e.g. UserId in the table, uuid in the audit table). */
    @SuppressWarnings("unchecked")
    private static Object convert(Column<?> source, Column<?> target, Object value) {
        if (value == null || source.type().javaType() == target.type().javaType()) return value;
        return target.type().fromRaw(((Column<Object>) source).type().toRaw(value));
    }
}
