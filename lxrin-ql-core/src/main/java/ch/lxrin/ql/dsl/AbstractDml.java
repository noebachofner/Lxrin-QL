package ch.lxrin.ql.dsl;

import ch.lxrin.ql.render.RenderContext;
import ch.lxrin.ql.render.RenderedSql;
import ch.lxrin.ql.runtime.QueryContext;
import ch.lxrin.ql.schema.Table;
import ch.lxrin.ql.statement.DmlStatement;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Common part of the {@code INSERT}, {@code UPDATE} and {@code DELETE} builders.
 *
 * @param <R> the table's row type
 * @param <S> the concrete builder type
 */
public abstract class AbstractDml<R, S extends AbstractDml<R, S>> extends DmlReturningBase {

    private QueryContext context;
    private final Table<R> table;

    AbstractDml(QueryContext context, Table<R> table) {
        if (table == null) throw new IllegalArgumentException("table must not be null");
        this.context = context;
        this.table = table;
    }

    /** Returns the statement model. */
    public abstract DmlStatement dmlStatement();

    @SuppressWarnings("unchecked")
    final S self() {
        return (S) this;
    }

    /** Returns the target table. */
    public Table<R> table() {
        return table;
    }

    /** Attaches the builder to a query context. */
    public S attach(QueryContext queryContext) {
        this.context = queryContext;
        return self();
    }

    /** Adds common table expressions. */
    public S with(Cte... ctes) {
        dmlStatement().with().add(false, ctes);
        return self();
    }

    QueryContext context() {
        return context != null ? context : QueryContext.getDefault();
    }

    /** Returns the attached context or {@code null}, without resolving the default. */
    QueryContext attachedContext() {
        return context;
    }

    /** Executes the statement and returns the number of affected rows. */
    public long execute() {
        return context().executeDml(dmlStatement(), v -> v, QueryContext.ExecOptions.dsl()).count();
    }

    /** {@code RETURNING} all columns of the table; rows are the table's row records. */
    public Returning<R> returningAll() {
        List<Field<?>> columns = new ArrayList<>(table.columns());
        Row.Shape shape = new Row.Shape(columns);
        return returningFields(columns, v -> table.mapRow(shape.row(v)));
    }

    @Override
    <X> Returning<X> returningFields(List<Field<?>> fields, Function<Object[], X> mapper) {
        for (Field<?> f : fields) if (f == null) throw new IllegalArgumentException("returning fields must not be null");
        dmlStatement().returning().clear();
        dmlStatement().returning().addAll(fields);
        return new Returning<>(this, fields, mapper);
    }

    /** Renders the statement without table policies, for logs and tests. */
    public RenderedSql render() {
        RenderContext ctx = new RenderContext();
        dmlStatement().render(ctx);
        return ctx.result();
    }

    @Override
    public String toString() {
        return render().toString();
    }

    static <T> Field<T> require(Field<T> value) {
        if (value == null) throw new IllegalArgumentException("value must not be null; use Values.nullValue(type) for NULL");
        return value;
    }
}
