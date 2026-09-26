package ch.lxrin.ql.dsl;

import ch.lxrin.ql.error.NoRowsException;
import ch.lxrin.ql.error.TooManyRowsException;
import ch.lxrin.ql.render.RenderContext;
import ch.lxrin.ql.render.RenderedSql;
import ch.lxrin.ql.runtime.QueryContext;
import ch.lxrin.ql.statement.DmlStatement;
import ch.lxrin.ql.statement.Statement;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * An {@code INSERT}, {@code UPDATE} or {@code DELETE} with {@code RETURNING},
 * ready to run. It can also be the body of a data-modifying CTE.
 *
 * @param <X> the type of one returned row
 */
public final class Returning<X> implements CteSource {

    private final AbstractDml<?, ?> dml;
    private final List<Field<?>> fields;
    private final Function<Object[], X> mapper;

    Returning(AbstractDml<?, ?> dml, List<Field<?>> fields, Function<Object[], X> mapper) {
        this.dml = dml;
        this.fields = List.copyOf(fields);
        this.mapper = mapper;
    }

    /** Runs the statement and returns all returned rows. */
    public List<X> fetch() {
        return dml.context().executeDml(dml.dmlStatement(), mapper, QueryContext.ExecOptions.dsl()).rows();
    }

    /** Runs the statement and maps all returned rows. */
    public <Y> List<Y> fetch(Function<? super X, ? extends Y> rowMapper) {
        Function<Object[], Y> mapping = v -> rowMapper.apply(mapper.apply(v));
        return dml.context().executeDml(dml.dmlStatement(), mapping, QueryContext.ExecOptions.dsl()).rows();
    }

    /**
     * Runs the statement and returns the single returned row, e.g. a generated id.
     *
     * @throws NoRowsException      if no row was returned
     * @throws TooManyRowsException if several rows were returned
     */
    public X fetchOne() {
        List<X> rows = fetch();
        if (rows.isEmpty()) throw new NoRowsException("the statement returned no row");
        if (rows.size() > 1) throw new TooManyRowsException("the statement returned " + rows.size() + " rows, expected one");
        return rows.get(0);
    }

    /** Runs the statement and returns the returned row, if any (e.g. after {@code ON CONFLICT DO NOTHING}). */
    public Optional<X> fetchOptional() {
        List<X> rows = fetch();
        if (rows.size() > 1) throw new TooManyRowsException("the statement returned " + rows.size() + " rows, expected at most one");
        return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(rows.get(0));
    }

    @Override
    public Statement statement() {
        return dml.dmlStatement();
    }

    @Override
    public List<Field<?>> fields() {
        return fields;
    }

    /** Renders the statement without table policies. */
    public RenderedSql render() {
        RenderContext ctx = new RenderContext();
        dml.dmlStatement().render(ctx);
        return ctx.result();
    }

    @Override
    public String toString() {
        return render().toString();
    }

    DmlStatement model() {
        return dml.dmlStatement();
    }
}
