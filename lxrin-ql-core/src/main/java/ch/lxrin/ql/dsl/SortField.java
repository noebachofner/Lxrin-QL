package ch.lxrin.ql.dsl;

import ch.lxrin.ql.render.QueryPart;
import ch.lxrin.ql.render.RenderContext;

import java.util.Objects;

/**
 * An {@code ORDER BY} item: {@code field ASC|DESC [NULLS FIRST|LAST]}.
 *
 * @param <T> the type of the sorted field
 */
public final class SortField<T> implements QueryPart {

    private final Field<T> field;
    private final boolean ascending;
    private final Boolean nullsFirst;

    SortField(Field<T> field, boolean ascending, Boolean nullsFirst) {
        this.field = Objects.requireNonNull(field, "field");
        this.ascending = ascending;
        this.nullsFirst = nullsFirst;
    }

    /** Returns the sorted field. */
    public Field<T> field() {
        return field;
    }

    /** Returns {@code true} for {@code ASC}. */
    public boolean ascending() {
        return ascending;
    }

    /** Returns {@code TRUE} for {@code NULLS FIRST}, {@code FALSE} for {@code NULLS LAST}, {@code null} for the default. */
    public Boolean nullOrdering() {
        return nullsFirst;
    }

    /** Adds {@code NULLS FIRST}. */
    public SortField<T> nullsFirst() {
        return new SortField<>(field, ascending, Boolean.TRUE);
    }

    /** Adds {@code NULLS LAST}. */
    public SortField<T> nullsLast() {
        return new SortField<>(field, ascending, Boolean.FALSE);
    }

    @Override
    public void render(RenderContext ctx) {
        ctx.visit(field).append(ascending ? " ASC" : " DESC");
        if (nullsFirst != null) ctx.append(nullsFirst ? " NULLS FIRST" : " NULLS LAST");
    }
}
