package ch.lxrin.ql.query;

import ch.lxrin.ql.RowMapper;
import ch.lxrin.ql.exec.ResultMapping;
import ch.lxrin.ql.expr.RenderContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A {@code SELECT} statement whose rows are mapped to {@code T}.
 *
 * <pre>{@code
 * import static ch.lxrin.ql.LxrinQL.*;
 *
 * PersonTable p = new PersonTable();
 *
 * List<PersonDto> people = createContribution(PersonDto.class)
 *     .select(p.personNr, p.firstName, p.lastName)
 *     .from(p)
 *     .where(eq(p.status, val("ACTIVE")), ge(p.age, 18))
 *     .orderBy(p.lastName.asc())
 *     .multiple();
 * }</pre>
 *
 * <h2>Mapping</h2>
 * Without {@link #mapWith(RowMapper)} rows are mapped by {@link ResultMapping}:
 * {@code Object[]} returns the raw row, simple types (numbers, strings,
 * dates, ...) take the first column, records are built from the columns in
 * order and other classes are treated as beans whose properties match the
 * select item names.
 *
 * @param <T> the element type returned by {@link #single()} / {@link #multiple()}
 */
public class SelectQuery<T> extends AbstractSelect<SelectQuery<T>> {

    private final Class<T> elementType;
    private RowMapper<T> rowMapper;

    /**
     * Creates an empty query; usually obtained through
     * {@code LxrinQL.createContribution(..)} or {@code LxrinQL.select(..)}.
     *
     * @param elementType result element type
     */
    public SelectQuery(Class<T> elementType) {
        if (elementType == null) throw new IllegalArgumentException("elementType must not be null");
        this.elementType = elementType;
    }

    /** Sets a custom mapper that converts each result row to {@code T}. */
    public SelectQuery<T> mapWith(RowMapper<T> rowMapper) {
        this.rowMapper = rowMapper;
        return this;
    }

    // -------------------------------------------------------------------------
    // Terminal operations
    // -------------------------------------------------------------------------

    /** Executes the query and returns the first row, or {@code null} if there is none. */
    public T single() {
        List<T> rows = fetch();
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Executes the query and returns the first row as an {@link Optional}. */
    public Optional<T> optional() {
        return Optional.ofNullable(single());
    }

    /** Executes the query and returns all rows (never {@code null}). */
    public List<T> multiple() {
        return fetch();
    }

    /** Executes {@code SELECT count(*) FROM (this query) q} and returns the number of rows. */
    public long fetchCount() {
        RenderContext ctx = new RenderContext();
        ctx.append("SELECT count(*) FROM ");
        render(ctx);
        ctx.append(" q");
        Object[][] rows = resolveExecutor().select(ctx.sql(), toBindMap(ctx));
        Object value = rows == null || rows.length == 0 ? null : rows[0][0];
        return value == null ? 0L : ((Number) value).longValue();
    }

    /** Executes {@code SELECT EXISTS (this query)}. */
    public boolean fetchExists() {
        RenderContext ctx = new RenderContext();
        ctx.append("SELECT EXISTS ");
        render(ctx);
        Object[][] rows = resolveExecutor().select(ctx.sql(), toBindMap(ctx));
        return rows != null && rows.length > 0 && Boolean.TRUE.equals(rows[0][0]);
    }

    private List<T> fetch() {
        RenderedSql rendered = build();
        Object[][] rows = resolveExecutor().select(rendered.sql(), rendered.binds());
        List<T> result = new ArrayList<>();
        if (rows == null) return result;
        RowMapper<T> mapper = rowMapper != null ? rowMapper : ResultMapping.forType(elementType, getSelectNames());
        for (Object[] row : rows) {
            result.add(mapper.map(row));
        }
        return result;
    }

    /** Returns the configured result element type. */
    public Class<T> getElementType() {
        return elementType;
    }
}
