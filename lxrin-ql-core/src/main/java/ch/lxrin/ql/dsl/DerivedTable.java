package ch.lxrin.ql.dsl;

import ch.lxrin.ql.render.RenderContext;
import ch.lxrin.ql.schema.Column;
import ch.lxrin.ql.schema.Table;
import ch.lxrin.ql.statement.Statement;

import java.util.List;

/**
 * A sub-query in {@code FROM}: {@code (SELECT ...) AS alias}. Created with
 * {@code query.asTable("alias")}; its columns are accessed with {@link #field(Field)}.
 */
public final class DerivedTable extends Table<Row> {

    private final Statement body;
    private final List<Field<?>> sourceFields;
    private final boolean lateral;

    DerivedTable(String alias, Statement body, List<Field<?>> fields, boolean lateral) {
        super(null, alias, null);
        this.body = body;
        this.sourceFields = DerivedColumns.require(fields, "derived table " + alias);
        this.lateral = lateral;
        for (Field<?> f : sourceFields) column(f.name(), f.type(), 0);
    }

    private DerivedTable(String alias, Statement body) {
        super(null, alias, null);
        this.body = body;
        this.sourceFields = List.of();
        this.lateral = false;
    }

    /** Wraps a statement without exposing columns, e.g. for {@code SELECT count(*) FROM (query) AS q}. */
    static DerivedTable wrap(Statement body, String alias) {
        return new DerivedTable(alias, body);
    }

    /** Returns this table's column for a field of its query (matched by output name). */
    public <T> Column<T> field(Field<T> queryField) {
        if (queryField.name() == null) throw new IllegalArgumentException("field has no name: " + queryField);
        return column(queryField.name(), queryField.type());
    }

    /** Returns a {@code LATERAL} copy, which may reference columns of preceding tables. */
    public DerivedTable lateral() {
        return new DerivedTable(name(), body, sourceFields, true);
    }

    @Override
    public boolean supportsPolicies() {
        return false;
    }

    @Override
    public DerivedTable as(String alias) {
        return new DerivedTable(alias, body, sourceFields, lateral);
    }

    @Override
    public Row mapRow(Row row) {
        return row;
    }

    @Override
    public void render(RenderContext ctx) {
        if (lateral) ctx.append("LATERAL ");
        ctx.append('(');
        body.render(ctx);
        ctx.append(") AS ").identifier(name());
    }

    @Override
    public boolean equals(Object o) {
        return this == o;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }
}
