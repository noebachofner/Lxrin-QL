package ch.lxrin.ql.statement;

import ch.lxrin.ql.dsl.Condition;
import ch.lxrin.ql.schema.Column;
import ch.lxrin.ql.schema.Table;

import java.util.List;

/**
 * A join of a {@code SELECT}.
 *
 * @param type    the join type
 * @param table   the joined table
 * @param on      the join condition, or {@code null} with {@code USING}, {@code CROSS} and {@code NATURAL}
 * @param using   the {@code USING} columns, or an empty list
 */
public record Join(Type type, Table<?> table, Condition on, List<Column<?>> using) {

    /** Join types. */
    public enum Type {
        /** {@code JOIN} */
        INNER("JOIN"),
        /** {@code LEFT JOIN} */
        LEFT("LEFT JOIN"),
        /** {@code RIGHT JOIN} */
        RIGHT("RIGHT JOIN"),
        /** {@code FULL JOIN} */
        FULL("FULL JOIN"),
        /** {@code CROSS JOIN} */
        CROSS("CROSS JOIN"),
        /** {@code NATURAL JOIN} */
        NATURAL("NATURAL JOIN");

        private final String sql;

        Type(String sql) {
            this.sql = sql;
        }

        /** Returns the SQL keyword(s). */
        public String sql() {
            return sql;
        }
    }

    /** Creates a join; {@code using} is copied. */
    public Join {
        using = List.copyOf(using);
    }
}
