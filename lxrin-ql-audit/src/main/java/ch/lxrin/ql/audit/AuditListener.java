package ch.lxrin.ql.audit;

import ch.lxrin.ql.dsl.Field;
import ch.lxrin.ql.dsl.Insert;
import ch.lxrin.ql.dsl.Row;
import ch.lxrin.ql.dsl.Values;
import ch.lxrin.ql.runtime.QueryContext;
import ch.lxrin.ql.runtime.TransactionScope;
import ch.lxrin.ql.schema.AdHocTable;
import ch.lxrin.ql.schema.Column;
import ch.lxrin.ql.schema.PrimaryKey;
import ch.lxrin.ql.schema.Table;
import ch.lxrin.ql.spi.AffectedRow;
import ch.lxrin.ql.spi.DeleteContext;
import ch.lxrin.ql.spi.InsertContext;
import ch.lxrin.ql.spi.StatementListener;
import ch.lxrin.ql.spi.TruncateContext;
import ch.lxrin.ql.spi.UpdateContext;
import ch.lxrin.ql.spi.WriteResult;
import ch.lxrin.ql.statement.StatementKind;
import ch.lxrin.ql.types.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static ch.lxrin.ql.dsl.AbstractInsert.excluded;
import static ch.lxrin.ql.dsl.Functions.caseWhen;

/**
 * Writes the audit history: for every row inserted, updated or deleted in an audited
 * table, the full row goes to {@code <table><suffix>} with the revision number and the
 * revision type (0 insert, 1 update, 2 delete). Each transaction gets one row in the
 * revision table, with the time and the current user. The layout is the one of Hibernate
 * Envers, so Envers can read the history and write to it.
 *
 * <p>Repository writes ({@code save}, {@code saveAll}, {@code delete}, {@code deleteAll}),
 * DSL statements, the {@code createInsert}/{@code createUpdate}/{@code createDelete}/{@code
 * createUpsert} style, upserts and soft deletes are all covered, because they all go
 * through the statement pipeline. The audit rows are written in the same transaction, so
 * a rollback leaves no history. A row changed several times in one transaction keeps one
 * audit row with its latest state; an insert stays an insert and a delete wins.</p>
 *
 * <p>Each audit table needs a primary key on the key columns of its table and the
 * revision column, as Envers creates it. {@code TRUNCATE} of an audited table is rejected.
 * Audit rows are written exactly as they are: column conventions, table policies and the
 * version column do not apply to them.</p>
 */
public final class AuditListener implements StatementListener {

    private static final TransactionScope.Key<Revision> REVISION = TransactionScope.key("lxrin.ql.audit.revision");

    /** The revision of the current transaction, discarded when the savepoint that created it rolls back. */
    private static final class Revision {
        volatile Long id;
    }

    private final AuditSettings settings;
    private final Map<String, AdHocTable> auditTables = new ConcurrentHashMap<>();
    private final AdHocTable revisionTable;

    /** Creates the listener. */
    public AuditListener(AuditSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.revisionTable = new AdHocTable(settings.revisionSchema(), settings.revisionTable(), null);
    }

    /** Returns the settings. */
    public AuditSettings settings() {
        return settings;
    }

    @Override
    public boolean appliesTo(Table<?> table) {
        return settings.audits(table);
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
        c.reject("audited tables cannot be truncated, because the truncated rows would be missing from the history");
    }

    @Override
    public void afterInsert(WriteResult r) {
        write(r, RevisionType.ADD);
    }

    @Override
    public void afterUpdate(WriteResult r) {
        write(r, r.originalKind() == StatementKind.DELETE ? RevisionType.DEL : RevisionType.MOD);
    }

    @Override
    public void afterDelete(WriteResult r) {
        write(r, RevisionType.DEL);
    }

    private void write(WriteResult r, RevisionType defaultType) {
        if (r.affectedRows().isEmpty()) return;
        Table<?> table = r.table();
        PrimaryKey<?> pk = table.primaryKey().orElseThrow(() -> new IllegalStateException(
                "the audited table " + table.qualifiedName() + " has no primary key"));
        QueryContext dsl = r.dsl().derive(b -> b.clearListeners().clearConventions().clearPolicies().versionColumn(null));
        long revision = revision(r, dsl);
        AdHocTable audit = auditTable(table);
        Column<Long> rev = audit.field(settings.revColumn(), SqlTypes.INT8);
        Column<Short> revtype = audit.field(settings.revtypeColumn(), SqlTypes.INT2);
        Set<String> excludedColumns = settings.excludedColumns(table);
        Set<String> keyNames = new java.util.HashSet<>();
        for (Column<?> c : pk.columns()) keyNames.add(c.name());
        Field<Short> deleted = Values.param(RevisionType.DEL.code(), SqlTypes.INT2);
        for (AffectedRow row : r.affectedRows()) {
            RevisionType type = r.wasUpsert() ? (row.inserted() ? RevisionType.ADD : RevisionType.MOD) : defaultType;
            Insert<Row> insert = dsl.insertInto(audit).set(rev, revision).set(revtype, type.code());
            List<Column<?>> key = new ArrayList<>(List.of(rev));
            List<Column<?>> data = new ArrayList<>();
            for (Map.Entry<Column<?>, Object> value : row.values().entrySet()) {
                Column<?> source = value.getKey();
                if (excludedColumns.contains(source.name())) continue;
                Column<?> target = audit.field(source.name(), source.type());
                boolean isKey = keyNames.contains(source.name());
                if (isKey) key.add(target);
                else data.add(target);
                if (type == RevisionType.DEL && !isKey && !settings.storeDataAtDelete()) continue;
                insert.setUnchecked(target, value.getValue());
            }
            insert.onConflict(key.toArray(new Column<?>[0]));
            if (!data.isEmpty()) insert.doUpdateSetExcluded(data.toArray(new Column<?>[0]));
            insert.doUpdateSet(revtype, caseWhen(excluded(revtype).eq(RevisionType.DEL.code()), deleted).otherwise(revtype))
                    .execute();
        }
    }

    private long revision(WriteResult r, QueryContext dsl) {
        TransactionScope scope = r.transaction();
        Revision revision = scope.attribute(REVISION, Revision::new);
        Long id = revision.id;
        if (id == null) {
            id = insertRevision(dsl);
            revision.id = id;
            scope.afterRollback(() -> revision.id = null);
        }
        return id;
    }

    private long insertRevision(QueryContext dsl) {
        Insert<Row> insert = dsl.insertInto(revisionTable)
                .set(revisionTable.field(settings.revisionTimestampColumn(), SqlTypes.TIMESTAMPTZ), settings.clock().instant());
        if (settings.user() != null) setUser(insert, settings.user());
        Long id = insert.returning(revisionTable.field(settings.revisionIdColumn(), SqlTypes.INT8)).fetchOne();
        if (id == null) throw new IllegalStateException("the revision table did not return a revision number");
        return id;
    }

    private <T> void setUser(Insert<Row> insert, AuditUser<T> user) {
        insert.set(revisionTable.field(settings.revisionUserColumn(), user.type()), user.current());
    }

    private AdHocTable auditTable(Table<?> table) {
        return auditTables.computeIfAbsent(table.qualifiedName(), k -> new AdHocTable(table.schema(), table.name() + settings.suffix(), null));
    }
}
