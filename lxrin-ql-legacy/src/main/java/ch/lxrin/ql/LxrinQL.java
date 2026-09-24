package ch.lxrin.ql;

import ch.lxrin.ql.exec.SqlExecutor;
import ch.lxrin.ql.exec.SqlExecutors;
import ch.lxrin.ql.function.Functions;
import ch.lxrin.ql.query.DeleteQuery;
import ch.lxrin.ql.query.InsertQuery;
import ch.lxrin.ql.query.SelectQuery;
import ch.lxrin.ql.query.TruncateQuery;
import ch.lxrin.ql.query.UpdateQuery;
import ch.lxrin.ql.table.Table;
import ch.lxrin.ql.table.TableDef;

/**
 * Entry point of the <strong>LxrinQL</strong> DSL. One static import gives
 * access to every statement factory, condition, function and helper:
 *
 * <pre>{@code
 * import static ch.lxrin.ql.LxrinQL.*;
 *
 * PersonTable p = new PersonTable();
 *
 * List<PersonDto> adults = createContribution(PersonDto.class)
 *     .select(p.personNr, p.firstName, p.lastName)
 *     .from(p)
 *     .where(eq(p.status, val("ACTIVE")), ge(p.age, 18))
 *     .orderBy(p.lastName.asc())
 *     .multiple();
 *
 * Long id = insertInto(p)
 *     .set(p.firstName, val("Ada"))
 *     .set(p.lastName, val("Lovelace"))
 *     .returning(p.personNr)
 *     .single(Long.class);
 * }</pre>
 *
 * <p>The static methods are inherited from {@link Functions},
 * {@link ch.lxrin.ql.condition.Conditions} and
 * {@link ch.lxrin.ql.expr.Expressions}; this class adds the statement
 * factories and configuration.</p>
 */
public final class LxrinQL extends Functions {

    private LxrinQL() {}

    // =========================================================================
    // SELECT
    // =========================================================================

    /**
     * Starts a {@code SELECT} whose rows are mapped to {@code elementType}.
     * Continue with {@code .select(..)}, {@code .from(..)}, ... and finish with
     * {@code .single()}, {@code .optional()} or {@code .multiple()}.
     */
    public static <T> SelectQuery<T> createContribution(Class<T> elementType) {
        return new SelectQuery<>(elementType);
    }

    /**
     * Same as {@link #createContribution(Class)}; the collection type is only
     * for readability ({@code createContribution(List.class, PersonDto.class)}).
     */
    public static <T> SelectQuery<T> createContribution(@SuppressWarnings("unused") Class<?> collectionType,
                                                        Class<T> elementType) {
        return new SelectQuery<>(elementType);
    }

    /** Alias of {@link #createContribution(Class)}. */
    public static <T> SelectQuery<T> query(Class<T> elementType) {
        return new SelectQuery<>(elementType);
    }

    /** Starts a {@code SELECT items} whose rows are returned as {@code Object[]}. */
    public static SelectQuery<Object[]> select(Object... items) {
        return new SelectQuery<>(Object[].class).select(items);
    }

    /** Starts a {@code SELECT items} whose rows are mapped to {@code elementType}. */
    public static <T> SelectQuery<T> select(Class<T> elementType, Object... items) {
        return new SelectQuery<>(elementType).select(items);
    }

    /** Starts a {@code SELECT DISTINCT items} whose rows are returned as {@code Object[]}. */
    public static SelectQuery<Object[]> selectDistinct(Object... items) {
        return select(items).distinct();
    }

    /** {@code SELECT * FROM table} with rows as {@code Object[]}. */
    public static SelectQuery<Object[]> selectFrom(Object table) {
        return new SelectQuery<>(Object[].class).from(table);
    }

    // =========================================================================
    // Data modification
    // =========================================================================

    /** Starts an {@code INSERT INTO table}. */
    public static InsertQuery insertInto(Object table) {
        return new InsertQuery(table);
    }

    /** Starts an {@code UPDATE table}. */
    public static UpdateQuery update(Object table) {
        return new UpdateQuery(table);
    }

    /** Starts a {@code DELETE FROM table}. */
    public static DeleteQuery deleteFrom(Object table) {
        return new DeleteQuery(table);
    }

    /** Starts a {@code TRUNCATE tables}. */
    public static TruncateQuery truncate(Object... tables) {
        return new TruncateQuery(tables);
    }

    // =========================================================================
    // Tables and configuration
    // =========================================================================

    /** Creates an ad-hoc table reference: {@code table("ADDRESS", "a").col("CITY")}. */
    public static Table table(String tableName, String alias) {
        return new Table(tableName, alias);
    }

    /** Returns the {@code FROM} fragment of a table definition, e.g. {@code "PERSON p"}. */
    public static String fromSql(TableDef table) {
        return table.toFromSql();
    }

    /**
     * Sets the executor used by all statements without an explicit
     * {@code .executor(..)}, e.g. {@code setDefaultExecutor(new JdbcSqlExecutor(dataSource))}.
     */
    public static void setDefaultExecutor(SqlExecutor executor) {
        SqlExecutors.setDefault(executor);
    }
}
