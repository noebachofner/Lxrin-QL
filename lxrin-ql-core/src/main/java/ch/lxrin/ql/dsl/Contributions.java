package ch.lxrin.ql.dsl;

import ch.lxrin.ql.runtime.QueryContext;
import ch.lxrin.ql.schema.Column;
import ch.lxrin.ql.schema.PrimaryKey;
import ch.lxrin.ql.schema.Table;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * The {@code createContribution} / {@code createInsert} / {@code createUpdate}
 * / {@code createDelete} / {@code createUpsert} style: a thin layer over the
 * builders of {@link Statements}, with the builder as {@code c} and the explicit bind
 * helper {@link Binds} as {@code b}. Statements run through the same pipeline
 * (policies, conventions, listeners, observers). Use the methods of
 * {@code QL}, {@link Dsl} or {@link QueryContext}; this class holds the shared implementation.
 */
public final class Contributions {

    private Contributions() {}

    /** Builds a query whose rows are mapped into {@code type}; see {@link SelectScope}. */
    public static <T> Select<T> select(QueryContext context, Class<T> type, Table<?> table,
                                       BiFunction<SelectScope<T>, Binds, ? extends Select<T>> body) {
        if (body == null) throw new IllegalArgumentException("body must not be null");
        Select<T> select = body.apply(new SelectScope<>(context, type, table), Binds.INSTANCE);
        if (select == null) throw new IllegalArgumentException("the body must return the query, e.g. (c, b) -> c.select(..).where(..)");
        return select;
    }

    /** Builds an {@code INSERT}. */
    public static <R> Insert<R> insert(QueryContext context, Table<R> table, BiConsumer<? super Insert<R>, Binds> body) {
        Insert<R> insert = new Insert<>(context, table);
        body.accept(insert, Binds.INSTANCE);
        return insert;
    }

    /** Builds an {@code UPDATE}; it is rejected at execution without a condition unless {@code c.all()} is called. */
    public static <R> Update<R> update(QueryContext context, Table<R> table, BiConsumer<? super Update<R>, Binds> body) {
        Update<R> update = new Update<>(context, table);
        body.accept(update, Binds.INSTANCE);
        return update;
    }

    /** Builds a {@code DELETE}; it is rejected at execution without a condition unless {@code c.all()} is called. */
    public static <R> Delete<R> delete(QueryContext context, Table<R> table, BiConsumer<? super Delete<R>, Binds> body) {
        Delete<R> delete = new Delete<>(context, table);
        body.accept(delete, Binds.INSTANCE);
        return delete;
    }

    /**
     * Builds an upsert: an {@code INSERT … ON CONFLICT}. If the body does not
     * call {@code onConflict(..)}, the conflict target is the primary key and
     * every other set column is updated from {@code EXCLUDED}.
     */
    public static <R> Insert<R> upsert(QueryContext context, Table<R> table, BiConsumer<? super Insert<R>, Binds> body) {
        Insert<R> insert = insert(context, table, body);
        if (!insert.dmlStatement().onConflict()) {
            PrimaryKey<?> pk = table.primaryKey().orElseThrow(() -> new IllegalArgumentException(
                    table.qualifiedName() + " has no primary key; call onConflict(..) in the body"));
            List<Column<?>> key = pk.columns();
            Set<Column<?>> columns = new LinkedHashSet<>();
            for (Map<Column<?>, Field<?>> row : insert.dmlStatement().rows()) columns.addAll(row.keySet());
            List<Column<?>> updates = new ArrayList<>();
            for (Column<?> c : columns) if (!key.contains(c)) updates.add(c);
            insert.onConflict(key.toArray(new Column<?>[0]));
            if (updates.isEmpty()) insert.doNothing();
            else insert.doUpdateSetExcluded(updates.toArray(new Column<?>[0]));
        }
        return insert;
    }
}
