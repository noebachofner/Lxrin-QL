package ch.lxrin.ql.dsl;

import ch.lxrin.ql.render.QueryPart;

/**
 * A sub-query that returns one column of type {@code T}, for
 * {@code IN (SELECT ...)}, {@code EXISTS} and scalar sub-queries.
 * Single-column selects ({@code select(field)}) implement it.
 *
 * @param <T> the column type
 */
public interface Subquery<T> extends QueryPart {

    /** Returns the sub-query as a scalar expression: {@code (SELECT ...)}. */
    Field<T> asField();
}
