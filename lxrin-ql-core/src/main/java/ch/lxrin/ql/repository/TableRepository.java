package ch.lxrin.ql.repository;

import ch.lxrin.ql.dsl.Condition;
import ch.lxrin.ql.dsl.Field;
import ch.lxrin.ql.dsl.Functions;
import ch.lxrin.ql.dsl.Page;
import ch.lxrin.ql.dsl.Select;
import ch.lxrin.ql.dsl.SortField;
import ch.lxrin.ql.dsl.Values;
import ch.lxrin.ql.entity.Entity;
import ch.lxrin.ql.entity.EntityAccess;
import ch.lxrin.ql.error.EntityNotFoundException;
import ch.lxrin.ql.error.OptimisticLockException;
import ch.lxrin.ql.error.StaleEntityException;
import ch.lxrin.ql.runtime.Propagation;
import ch.lxrin.ql.runtime.QueryContext;
import ch.lxrin.ql.schema.Column;
import ch.lxrin.ql.schema.KeyStrategy;
import ch.lxrin.ql.schema.PrimaryKey;
import ch.lxrin.ql.schema.Table;
import ch.lxrin.ql.spi.EntityWrite;
import ch.lxrin.ql.spi.Origin;
import ch.lxrin.ql.spi.TablePolicy;
import ch.lxrin.ql.statement.DeleteStatement;
import ch.lxrin.ql.statement.InsertStatement;
import ch.lxrin.ql.statement.UpdateStatement;
import ch.lxrin.ql.types.UuidV7;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The generic part of generated repositories: keys, {@code save},
 * {@code saveAll}, {@code insert}, {@code update}, {@code delete} and finders.
 * Every method runs through the query context's pipeline, so table policies,
 * column conventions, listeners and observers apply to repositories and the
 * DSL alike.
 *
 * <p>Subclasses add custom queries with the typed DSL, using {@link #ctx()}.</p>
 *
 * @param <E> the entity type
 * @param <K> the key type
 * @param <R> the row record type
 */
public abstract class TableRepository<E extends Entity<K>, K, R> {

    private final QueryContext context;
    private final Table<R> table;
    private final PrimaryKey<K> key;
    private final List<Column<?>> columns;

    /**
     * @param context the query context
     * @param table   the table
     */
    @SuppressWarnings("unchecked")
    protected TableRepository(QueryContext context, Table<R> table) {
        if (context == null) throw new IllegalArgumentException("context must not be null");
        this.context = context;
        this.table = table;
        this.key = (PrimaryKey<K>) table.primaryKey().orElseThrow(() ->
                new IllegalArgumentException("table " + table.qualifiedName() + " has no primary key"));
        this.columns = List.copyOf(table.columns());
    }

    /** Creates a new, empty entity. Generated. */
    protected abstract E newEntity();

    /** Returns the table. */
    public Table<R> table() {
        return table;
    }

    /** Returns the query context of this repository, for custom queries. */
    protected QueryContext ctx() {
        return context.withOrigin(Origin.repository(getClass().getSimpleName()));
    }

    /**
     * Returns a copy of this repository in which the given table policies do
     * not apply, e.g. {@code repo.bypassing(SoftDeletePolicy.class).findAll(..)}.
     * The subclass needs a public constructor taking a {@code QueryContext}.
     */
    @SafeVarargs
    @SuppressWarnings({"unchecked", "varargs"})
    public final <S extends TableRepository<E, K, R>> S bypassing(Class<? extends TablePolicy>... policies) {
        try {
            Constructor<?> ctor = getClass().getConstructor(QueryContext.class);
            return (S) ctor.newInstance(context.bypassing(policies));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(getClass().getName() + " needs a public constructor (QueryContext)", e);
        }
    }

    // =========================================================================
    // Keys
    // =========================================================================

    /** Returns a new primary key value: a UUID v7 or {@code nextval} of the key's sequence. */
    public K createKey() {
        KeyStrategy strategy = key.strategy();
        Column<K> column = keyColumn();
        switch (strategy.type()) {
            case UUID_V7:
                return column.type().fromRaw(UuidV7.generate());
            case SEQUENCE:
                return column.type().fromRaw(ctx().select(Functions.nextval(strategy.sequence())).fetchOne());
            case CUSTOM:
                return column.type().cast(strategy.supplier().get());
            default:
                throw new UnsupportedOperationException("table " + table.qualifiedName() + " has no key strategy");
        }
    }

    /** Returns {@code n} new keys; for sequences in one round trip. */
    public List<K> createKeys(int n) {
        List<K> keys = new ArrayList<>(n);
        if (key.strategy().type() == KeyStrategy.Type.SEQUENCE && n > 0) {
            Column<K> column = keyColumn();
            for (Long v : ctx().select(Functions.nextval(key.strategy().sequence()))
                    .from(ch.lxrin.ql.dsl.Dsl.tableOf(Functions.generateSeries(1, n), "g")).fetch()) {
                keys.add(column.type().fromRaw(v));
            }
            return keys;
        }
        for (int i = 0; i < n; i++) keys.add(createKey());
        return keys;
    }

    @SuppressWarnings("unchecked")
    private Column<K> keyColumn() {
        if (key.composite()) throw new UnsupportedOperationException("composite keys cannot be generated");
        return (Column<K>) key.columns().get(0);
    }

    // =========================================================================
    // Finders
    // =========================================================================

    /** Finds an entity by primary key. */
    public Optional<E> findById(K id) {
        return findOne(key.matches(Objects.requireNonNull(id, "id")));
    }

    /**
     * Returns the entity with the given primary key.
     *
     * @throws EntityNotFoundException if there is none
     */
    public E getById(K id) {
        return findById(id).orElseThrow(() -> new EntityNotFoundException(table.name() + " with id " + id + " not found"));
    }

    /** Returns all entities matching {@code where}, ordered by {@code orderBy}. */
    public List<E> findAll(Condition where, SortField<?>... orderBy) {
        return select().where(where).orderBy(orderBy).fetch();
    }

    /** Returns all entities. */
    public List<E> findAll() {
        return findAll(Condition.noCondition());
    }

    /** Returns the entities with the given keys (in no particular order). */
    public List<E> findAllById(Collection<K> ids) {
        if (ids.isEmpty()) return List.of();
        if (key.composite()) {
            List<Condition> any = new ArrayList<>();
            for (K id : ids) any.add(key.matches(id));
            return findAll(Condition.or(any));
        }
        return findAll(keyColumn().in(ids));
    }

    /**
     * Returns the only entity matching {@code where}, if any.
     *
     * @throws ch.lxrin.ql.error.TooManyRowsException if several match
     */
    public Optional<E> findOne(Condition where) {
        return select().where(where).fetchOptional();
    }

    /**
     * Returns a keyset-paginated page of entities.
     *
     * @param cursor  the encoded cursor of the previous page, or {@code null} for the first page
     * @param limit   the page size
     * @param orderBy the sort order; it should end with a unique column such as the primary key
     */
    public Page<E> findPage(Condition where, String cursor, int limit, SortField<?>... orderBy) {
        return select().where(where).orderBy(orderBy).seekAfterCursor(cursor).limit(limit).fetchPage();
    }

    /** Returns {@code true} if a row matches. */
    public boolean exists(Condition where) {
        return ctx().selectOne().from(table).where(where).fetchExists();
    }

    /** Returns the number of matching rows. */
    public long count(Condition where) {
        return ctx().selectCount().from(table).where(where).fetchOne();
    }

    /** Returns the number of rows. */
    public long count() {
        return count(Condition.noCondition());
    }

    /** A select of all columns that maps rows to loaded entities, for custom finders. */
    protected Select<E> select() {
        return new Select<E>(ctx(), columns, this::toEntity).from(table);
    }

    private E toEntity(Object[] values) {
        E e = newEntity();
        EntityAccess.load(e, columns, values);
        return e;
    }

    // =========================================================================
    // Writes
    // =========================================================================

    /**
     * Inserts a new entity or updates a persistent one. Whether the entity is
     * new is decided by its tracked state, not by its id. An update writes only
     * changed columns; if nothing changed, no statement runs. Database-side
     * values (defaults, triggers, conventions) are read back.
     *
     * @return the same entity, now persistent
     */
    public E save(E entity) {
        switch (entity.state()) {
            case NEW:
                return insert(entity);
            case PERSISTENT:
                return entity.isChanged() ? update(entity) : entity;
            default:
                throw new IllegalStateException("cannot save a deleted entity: " + entity);
        }
    }

    /** Inserts the entity, even if it is persistent (all its columns are written then). */
    public E insert(E entity) {
        Map<Column<?>, Field<?>> row = insertValues(entity);
        InsertStatement statement = new InsertStatement(table);
        statement.rows().add(row);
        applyOverriding(statement, row);
        statement.returning().addAll(columns);
        EntityWrite write = new EntityWrite(entity, true, entity.changes());
        restoreOnRollback(EntityAccess.snapshot(entity, columns));
        QueryContext.DmlResult<Object[]> result = ctx().executeDml(statement, v -> v, options("insert", List.of(write)));
        EntityAccess.load(entity, columns, result.rows().get(0));
        return entity;
    }

    /**
     * Updates the entity, even if it is new (all set columns are written then).
     *
     * @throws StaleEntityException     if the row does not exist (anymore) or is hidden by a policy
     * @throws OptimisticLockException  if the version column does not match
     */
    public E update(E entity) {
        Set<Column<?>> changed = entity.isNew() ? EntityAccess.assignedColumns(entity) : entity.changes().keySet();
        if (changed.isEmpty()) return entity;
        UpdateStatement statement = updateStatement(entity, changed);
        EntityWrite write = new EntityWrite(entity, false, entity.changes());
        restoreOnRollback(EntityAccess.snapshot(entity, columns));
        QueryContext.DmlResult<Object[]> result = ctx().executeDml(statement, v -> v, options("update", List.of(write)));
        if (result.rows().isEmpty()) throw stale(entity, "update");
        EntityAccess.load(entity, columns, result.rows().get(0));
        return entity;
    }

    /**
     * Deletes the entity (or soft-deletes it, if a policy says so).
     *
     * @throws StaleEntityException    if the row does not exist (anymore)
     * @throws OptimisticLockException if the version column does not match
     */
    public void delete(E entity) {
        if (entity.isNew()) throw new IllegalStateException("cannot delete a new entity: " + entity);
        if (entity.isDeleted()) return;
        DeleteStatement statement = new DeleteStatement(table);
        statement.addWhere(key.matches(entity.id()));
        versionCondition(entity).ifPresent(statement::addWhere);
        statement.returning().addAll(key.columns());
        restoreOnRollback(EntityAccess.snapshot(entity, columns));
        QueryContext.DmlResult<Object[]> result = ctx().executeDml(statement, v -> v,
                options("delete", List.of(new EntityWrite(entity, false, Map.of()))));
        if (result.rows().isEmpty()) throw stale(entity, "delete");
        EntityAccess.markDeleted(entity);
    }

    /**
     * Deletes the row with the given key.
     *
     * @throws EntityNotFoundException if there is none
     */
    public void deleteById(K id) {
        long count = ctx().deleteFrom(table).where(key.matches(id)).execute();
        if (count == 0) throw new EntityNotFoundException(table.name() + " with id " + id + " not found");
    }

    /** Deletes all matching rows (in bulk, through listeners and policies) and returns their number. */
    public long deleteAll(Condition where) {
        return ctx().deleteFrom(table).where(where).execute();
    }

    /**
     * Saves many entities in one transaction: new ones as multi-row
     * {@code INSERT}s (in chunks), changed persistent ones as JDBC batches of
     * {@code UPDATE}s. Either all rows are saved or none.
     *
     * @throws StaleEntityException    if an update matched no row
     * @throws OptimisticLockException if a version did not match
     */
    public List<E> saveAll(Collection<E> entities) {
        List<E> all = List.copyOf(entities);
        return ctx().transaction(Propagation.REQUIRED, () -> {
            List<E> inserts = new ArrayList<>();
            List<E> updates = new ArrayList<>();
            for (E e : all) {
                if (e.isDeleted()) throw new IllegalStateException("cannot save a deleted entity: " + e);
                if (e.isNew()) inserts.add(e);
                else if (e.isChanged()) updates.add(e);
            }
            insertAll(inserts);
            updateAll(updates);
            return all;
        });
    }

    private void insertAll(List<E> entities) {
        if (entities.isEmpty()) return;
        int maxRows = Math.max(1, Math.min(ctx().batchSize(), 65_535 / Math.max(1, columns.size())));
        for (int from = 0; from < entities.size(); from += maxRows) {
            List<E> chunk = entities.subList(from, Math.min(entities.size(), from + maxRows));
            InsertStatement statement = new InsertStatement(table);
            List<EntityWrite> writes = new ArrayList<>();
            for (E e : chunk) {
                Map<Column<?>, Field<?>> row = insertValues(e);
                statement.rows().add(row);
                applyOverriding(statement, row);
                writes.add(new EntityWrite(e, true, e.changes()));
                restoreOnRollback(EntityAccess.snapshot(e, columns));
            }
            statement.returning().addAll(columns);
            List<Object[]> rows = ctx().executeDml(statement, v -> v, options("saveAll", writes)).rows();
            if (rows.size() != chunk.size()) throw new IllegalStateException("insert returned " + rows.size() + " rows for " + chunk.size());
            Map<Object, Object[]> byKey = new HashMap<>();
            for (Object[] row : rows) byKey.put(keyOf(row), row);
            for (int i = 0; i < chunk.size(); i++) {
                E e = chunk.get(i);
                Object[] row = e.id() != null && byKey.containsKey(e.id()) ? byKey.get(e.id()) : rows.get(i);
                EntityAccess.load(e, columns, row);
            }
        }
    }

    private void updateAll(List<E> entities) {
        if (entities.isEmpty()) return;
        List<UpdateStatement> statements = new ArrayList<>();
        List<Object> keys = new ArrayList<>();
        List<EntityWrite> writes = new ArrayList<>();
        for (E e : entities) {
            UpdateStatement s = updateStatement(e, e.changes().keySet());
            statements.add(s);
            keys.add(e.id());
            writes.add(new EntityWrite(e, false, e.changes()));
            restoreOnRollback(EntityAccess.snapshot(e, columns));
        }
        List<QueryContext.DmlResult<Object[]>> results = ctx().executeBatch(statements, keys, this::keyOf, v -> v,
                options("saveAll", writes));
        for (int i = 0; i < entities.size(); i++) {
            QueryContext.DmlResult<Object[]> r = results.get(i);
            if (r.count() == 0 || r.rows().isEmpty()) throw stale(entities.get(i), "update");
            EntityAccess.load(entities.get(i), columns, r.rows().get(0));
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Map<Column<?>, Field<?>> insertValues(E entity) {
        Set<Column<?>> set = entity.isNew() ? EntityAccess.assignedColumns(entity) : new LinkedHashSet<>(columns);
        Map<Column<?>, Field<?>> row = new java.util.LinkedHashMap<>();
        for (Column<?> c : set) {
            if (c.generated()) continue;
            Object value = EntityAccess.read(entity, c);
            if (!entity.isNew() && value == null && c.hasDefault()) continue;
            row.put(c, param(c, value));
        }
        return row;
    }

    private static void applyOverriding(InsertStatement statement, Map<Column<?>, Field<?>> row) {
        for (Column<?> c : row.keySet()) {
            if (c.identityAlways()) statement.overriding(InsertStatement.Overriding.SYSTEM_VALUE);
        }
    }

    private UpdateStatement updateStatement(E entity, Set<Column<?>> changed) {
        UpdateStatement statement = new UpdateStatement(table);
        for (Column<?> c : changed) {
            if (!c.generated()) setParam(statement, c, EntityAccess.read(entity, c));
        }
        statement.addWhere(key.matches(entity.isNew() ? entity.id() : originalKey(entity)));
        versionCondition(entity).ifPresent(statement::addWhere);
        statement.returning().addAll(columns);
        return statement;
    }

    @SuppressWarnings("unchecked")
    private K originalKey(E entity) {
        if (key.composite()) {
            Object[] values = new Object[key.columns().size()];
            for (int i = 0; i < values.length; i++) values[i] = entity.originalValue(key.columns().get(i));
            return key.keyOf(values);
        }
        return (K) entity.originalValue(key.columns().get(0));
    }

    private Optional<Condition> versionCondition(E entity) {
        Optional<Column<?>> version = versionColumn();
        if (version.isEmpty() || entity.isNew()) return Optional.empty();
        return Optional.of(version.get().eqUnchecked(entity.originalValue(version.get())));
    }

    private Optional<Column<?>> versionColumn() {
        return context.versionColumn().flatMap(table::column);
    }

    private RuntimeException stale(E entity, String operation) {
        if (versionColumn().isPresent()) {
            boolean exists = context.selectOne().from(table).where(key.matches(entity.id())).fetchExists();
            if (exists) {
                return new OptimisticLockException("cannot " + operation + " " + entity + ": it was changed by someone else "
                        + "(version " + entity.originalValue(versionColumn().get()) + " is outdated)");
            }
        }
        return new StaleEntityException("cannot " + operation + " " + entity + ": the row does not exist (anymore)"
                + " or is hidden by a table policy");
    }

    private Object keyOf(Object[] row) {
        Object[] values = new Object[key.columns().size()];
        for (int i = 0; i < values.length; i++) values[i] = row[columns.indexOf(key.columns().get(i))];
        return key.keyOf(values);
    }

    private void restoreOnRollback(EntityAccess.Snapshot snapshot) {
        context.currentTransaction().ifPresent(tx -> tx.afterRollback(snapshot::restore));
    }

    private QueryContext.ExecOptions options(String method, List<EntityWrite> writes) {
        return new QueryContext.ExecOptions(Origin.repository(getClass().getSimpleName() + "." + method), writes);
    }

    @SuppressWarnings("unchecked")
    private static <T> Field<T> param(Column<T> column, Object value) {
        return Values.param(column.type().cast(value), column.type());
    }

    @SuppressWarnings("unchecked")
    private static <T> void setParam(UpdateStatement statement, Column<T> column, Object value) {
        statement.set(column, Values.param(column.type().cast(value), column.type()), true);
    }
}
