package ch.lxrin.ql.dsl;

import ch.lxrin.ql.statement.Statement;

import java.util.List;

/**
 * Something that can be the body of a common table expression or a derived
 * table: a {@code SELECT}, or an {@code INSERT}/{@code UPDATE}/{@code DELETE}
 * with {@code RETURNING}.
 */
public interface CteSource {

    /** Returns the statement model. */
    Statement statement();

    /** Returns the output fields (select list or {@code RETURNING} list). */
    List<Field<?>> fields();
}
