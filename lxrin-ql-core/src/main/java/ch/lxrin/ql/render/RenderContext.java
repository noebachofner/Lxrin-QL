package ch.lxrin.ql.render;

import ch.lxrin.ql.dsl.Condition;
import ch.lxrin.ql.schema.Table;
import ch.lxrin.ql.types.DataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Collects SQL text and typed bind parameters while a statement is rendered.
 *
 * <p>A new context is used for every render. Besides the text it carries a
 * few flags that change how parts render themselves, for example whether
 * column references are qualified with their table.</p>
 */
public final class RenderContext {

    /** Supplies the extra conditions table policies add for a table reference. */
    @FunctionalInterface
    public interface TableFilter {
        /** Returns the conditions for {@code table}, or an empty list. */
        List<Condition> filterFor(Table<?> table);
    }

    private final StringBuilder sql = new StringBuilder();
    private final List<Bind<?>> binds = new ArrayList<>();
    private final TableFilter tableFilter;
    private boolean qualifyColumns = true;
    private boolean declareAliases;

    /** Creates a context without table filters. */
    public RenderContext() {
        this(table -> List.of());
    }

    /** Creates a context that applies the given table filter to every table reference. */
    public RenderContext(TableFilter tableFilter) {
        this.tableFilter = Objects.requireNonNull(tableFilter, "tableFilter");
    }

    // -------------------------------------------------------------------------
    // Text
    // -------------------------------------------------------------------------

    /** Appends SQL text. Only library parts and {@code Sql.raw} call this. */
    public RenderContext append(String fragment) {
        sql.append(fragment);
        return this;
    }

    /** Appends a single character. */
    public RenderContext append(char c) {
        sql.append(c);
        return this;
    }

    /** Appends an identifier, quoted if necessary. */
    public RenderContext identifier(String name) {
        sql.append(Identifiers.quote(name));
        return this;
    }

    /** Renders a part. */
    public RenderContext visit(QueryPart part) {
        Objects.requireNonNull(part, "query part must not be null").render(this);
        return this;
    }

    /** Renders several parts with a separator. */
    public RenderContext visitAll(Collection<? extends QueryPart> parts, String separator) {
        boolean first = true;
        for (QueryPart p : parts) {
            if (!first) sql.append(separator);
            visit(p);
            first = false;
        }
        return this;
    }

    // -------------------------------------------------------------------------
    // Binds
    // -------------------------------------------------------------------------

    /** Registers a bind parameter and appends its {@code ?} placeholder. */
    public <T> RenderContext bind(DataType<T> type, T value) {
        binds.add(new Bind<>(type, value));
        sql.append('?');
        return this;
    }

    // -------------------------------------------------------------------------
    // Flags
    // -------------------------------------------------------------------------

    /** Returns {@code true} if column references are written as {@code table.column}. */
    public boolean qualifyColumns() {
        return qualifyColumns;
    }

    /** Renders {@code part} with column qualification switched on or off (e.g. in {@code INSERT} column lists). */
    public RenderContext withQualification(boolean qualify, QueryPart part) {
        boolean previous = qualifyColumns;
        qualifyColumns = qualify;
        try {
            return visit(part);
        } finally {
            qualifyColumns = previous;
        }
    }

    /** Returns {@code true} while a select list or {@code RETURNING} list is rendered, where aliases are declared. */
    public boolean declareAliases() {
        return declareAliases;
    }

    /** Renders {@code parts} as a select list: aliased fields render as {@code expr AS alias}. */
    public RenderContext declaring(Collection<? extends QueryPart> parts, String separator) {
        boolean previous = declareAliases;
        boolean previousQualify = qualifyColumns;
        try {
            boolean first = true;
            for (QueryPart p : parts) {
                if (!first) sql.append(separator);
                declareAliases = true;
                visit(p);
                first = false;
            }
            return this;
        } finally {
            declareAliases = previous;
            qualifyColumns = previousQualify;
        }
    }

    /** Renders a nested part (for example the body of an aliased field) outside the alias declaration mode. */
    public RenderContext nested(QueryPart part) {
        boolean previous = declareAliases;
        declareAliases = false;
        try {
            return visit(part);
        } finally {
            declareAliases = previous;
        }
    }

    /** Returns the conditions table policies add for a table reference. */
    public List<Condition> policyFilter(Table<?> table) {
        return tableFilter.filterFor(table);
    }

    // -------------------------------------------------------------------------
    // Result
    // -------------------------------------------------------------------------

    /** Returns the SQL rendered so far. */
    public String sql() {
        return sql.toString();
    }

    /** Returns the binds collected so far. */
    public List<Bind<?>> binds() {
        return List.copyOf(binds);
    }

    /** Returns the SQL and the binds. */
    public RenderedSql result() {
        return new RenderedSql(sql(), binds);
    }
}
