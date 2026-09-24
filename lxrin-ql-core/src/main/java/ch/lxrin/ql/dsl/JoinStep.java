package ch.lxrin.ql.dsl;

import ch.lxrin.ql.schema.Column;
import ch.lxrin.ql.schema.ForeignKey;
import ch.lxrin.ql.schema.Table;
import ch.lxrin.ql.statement.Join;
import ch.lxrin.ql.statement.SelectStatement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A join waiting for its condition: {@code .join(ORDERS).on(ORDERS.USER_ID.eq(USERS.ID))}.
 *
 * @param <S> the select builder to return to
 */
public final class JoinStep<S> {

    private final S select;
    private final SelectStatement statement;
    private final Join.Type type;
    private final Table<?> table;

    JoinStep(S select, SelectStatement statement, Join.Type type, Table<?> table) {
        if (table == null) throw new IllegalArgumentException("table must not be null");
        this.select = select;
        this.statement = statement;
        this.type = type;
        this.table = table;
    }

    /** {@code ON condition} */
    public S on(Condition condition) {
        statement.joins().add(new Join(type, table, AbstractSelect.requireCondition(condition), List.of()));
        return select;
    }

    /** {@code ON TRUE}, e.g. for {@code LEFT JOIN LATERAL}. */
    public S onTrue() {
        statement.joins().add(new Join(type, table, Condition.noCondition(), List.of()));
        return select;
    }

    /** {@code USING (columns)} */
    public S using(Column<?>... columns) {
        if (columns.length == 0) throw new IllegalArgumentException("USING needs at least one column");
        statement.joins().add(new Join(type, table, null, Arrays.asList(columns)));
        return select;
    }

    /**
     * Joins along a generated foreign key. The key may point from the joined
     * table to an earlier table or the other way round; aliases are respected.
     */
    public S onKey(ForeignKey key) {
        List<Table<?>> earlier = new ArrayList<>(statement.from());
        for (Join j : statement.joins()) earlier.add(j.table());
        Table<?> child;
        Table<?> parent;
        if (key.table().sameTable(table) && findReferenced(earlier, key) != null) {
            child = table;
            parent = findReferenced(earlier, key);
        } else if (key.references(table)) {
            child = find(earlier, key.table());
            parent = table;
        } else {
            throw new IllegalArgumentException("foreign key " + key.name() + " does not connect " + table + " with an earlier table");
        }
        if (child == null) throw new IllegalArgumentException("table " + key.table().qualifiedName() + " is not part of the query");
        List<Condition> parts = new ArrayList<>();
        for (int i = 0; i < key.columns().size(); i++) {
            Column<?> c = child.column(key.columns().get(i).name()).orElseThrow();
            Column<?> p = parent.column(key.referencedColumns().get(i)).orElseThrow(() ->
                    new IllegalArgumentException("referenced column missing in " + table));
            parts.add(equal(c, p));
        }
        return on(Condition.and(parts));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Condition equal(Column<?> a, Column<?> b) {
        if (!a.type().javaType().equals(b.type().javaType())) {
            throw new IllegalArgumentException("foreign key columns " + a + " and " + b + " have different types");
        }
        return ((Column) a).eq((Field) b);
    }

    private static Table<?> findReferenced(List<Table<?>> tables, ForeignKey key) {
        for (Table<?> t : tables) if (key.references(t)) return t;
        return null;
    }

    private static Table<?> find(List<Table<?>> tables, Table<?> like) {
        for (Table<?> t : tables) if (t.sameTable(like)) return t;
        return null;
    }
}
