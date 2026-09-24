package ch.lxrin.ql.statement;

import ch.lxrin.ql.render.QueryPart;
import ch.lxrin.ql.schema.Table;

import java.util.List;

/**
 * A complete statement as an inspectable model. The DSL builds it, table
 * policies, column conventions and listeners read and change it, and it is
 * rendered to SQL only at the very end.
 */
public interface Statement extends QueryPart {

    /** Returns the kind of statement. */
    StatementKind kind();

    /** Returns a deep enough copy that the pipeline can change without affecting the builder. */
    Statement copy();

    /** Returns the tables the statement reads or writes directly (for observers and metrics). */
    List<Table<?>> tables();
}
