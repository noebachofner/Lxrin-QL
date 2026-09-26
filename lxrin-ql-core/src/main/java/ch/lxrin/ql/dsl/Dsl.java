package ch.lxrin.ql.dsl;

import ch.lxrin.ql.runtime.QueryContext;
import ch.lxrin.ql.schema.Table;
import ch.lxrin.ql.types.SqlTypes;

import java.util.Arrays;
import java.util.List;

/**
 * The static entry point of the LxrinQL DSL:
 *
 * <pre>{@code
 * import static ch.lxrin.ql.dsl.Dsl.*;
 * import static com.example.db.Tables.*;
 *
 * List<UserSummary> gmail = select(USERS.ID, USERS.NAME, USERS.EMAIL)
 *         .from(USERS)
 *         .where(USERS.EMAIL.endsWith("@gmail.com"))
 *         .orderBy(USERS.NAME.asc())
 *         .fetch(UserSummary::new);
 * }</pre>
 *
 * <p>Statements created here run on {@link QueryContext#getDefault()};
 * statements created with {@code ctx.select(..)} run on {@code ctx}. The
 * function catalog ({@link Functions}), bind parameters and literals
 * ({@link Values}) and the typed {@code select(..)} overloads are inherited.</p>
 */
public final class Dsl extends Functions {

    private Dsl() {}

    // =========================================================================
    // Statements
    // =========================================================================

    /** {@code SELECT <all columns> FROM table}; rows are the table's row records. */
    public static <R> Select<R> selectFrom(Table<R> table) {
        return QueryContext.newSelectFrom(null, table);
    }

    /** {@code SELECT} of a dynamic list of fields; rows are {@link Row}s with typed access. */
    public static Select<Row> select(List<? extends Field<?>> fields) {
        return QueryContext.newDynamicSelect(null, fields);
    }

    /** {@code SELECT count(*)}; add {@code .from(..)}. */
    public static Select1<Long> selectCount() {
        return new Select1<>(null, Fields.number(SqlTypes.INT8, ctx -> ctx.append("count(*)")));
    }

    /** {@code SELECT 1}, e.g. for {@code exists(selectOne().from(..).where(..))}. */
    public static Select1<Integer> selectOne() {
        return new Select1<>(null, inline(1));
    }

    /** Starts an {@code INSERT INTO table}. */
    public static <R> Insert<R> insertInto(Table<R> table) {
        return new Insert<>(null, table);
    }

    /** Starts an {@code UPDATE table}. */
    public static <R> Update<R> update(Table<R> table) {
        return new Update<>(null, table);
    }

    /** Starts a {@code DELETE FROM table}. */
    public static <R> Delete<R> deleteFrom(Table<R> table) {
        return new Delete<>(null, table);
    }

    /** Starts a {@code TRUNCATE}. */
    public static Truncate truncate(Table<?>... tables) {
        return new Truncate(null, Arrays.asList(tables));
    }

    // =========================================================================
    // CTEs, derived and function tables
    // =========================================================================

    /** A common table expression over a query; add it with {@code .with(cte)}. */
    public static Cte cte(String name, CteSource query) {
        return Cte.of(name, query);
    }

    /** A recursive CTE; declare its columns, then set the query with {@code as(..)}. */
    public static Cte recursiveCte(String name) {
        return Cte.recursive(name);
    }

    /** A set-returning function in {@code FROM}: {@code tableOf(generateSeries(..), "n")}. */
    public static <T> FunctionTable<T> tableOf(Field<T> setReturningFunction, String alias) {
        return new FunctionTable<>(setReturningFunction, alias, false);
    }

    /** {@code LATERAL (SELECT ...) AS alias} */
    public static DerivedTable lateral(DerivedTable table) {
        return table.lateral();
    }

    // =========================================================================
    // Windows
    // =========================================================================

    /** {@code (PARTITION BY fields)} */
    public static WindowSpec partitionBy(Field<?>... fields) {
        return WindowSpec.empty().partitionBy(fields);
    }

    /** {@code (ORDER BY ...)} */
    public static WindowSpec orderBy(SortField<?>... sortFields) {
        return WindowSpec.empty().orderBy(sortFields);
    }

    /** An empty window, to be refined. */
    public static WindowSpec window() {
        return WindowSpec.empty();
    }

    /** A named window for the {@code WINDOW} clause. */
    public static WindowDefinition window(String name, WindowSpec spec) {
        return new WindowDefinition(name, spec);
    }

    /** {@code UNBOUNDED PRECEDING} */
    public static FrameBound unboundedPreceding() {
        return FrameBound.unboundedPreceding();
    }

    /** {@code UNBOUNDED FOLLOWING} */
    public static FrameBound unboundedFollowing() {
        return FrameBound.unboundedFollowing();
    }

    /** {@code CURRENT ROW} */
    public static FrameBound currentRow() {
        return FrameBound.currentRow();
    }

    /** {@code n PRECEDING} */
    public static FrameBound preceding(long n) {
        return FrameBound.preceding(n);
    }

    /** {@code n FOLLOWING} */
    public static FrameBound following(long n) {
        return FrameBound.following(n);
    }
}
