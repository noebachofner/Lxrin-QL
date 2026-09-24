package ch.lxrin.ql.spi;

import ch.lxrin.ql.dsl.Field;
import ch.lxrin.ql.dsl.Values;
import ch.lxrin.ql.schema.Column;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Fills a column automatically on insert and/or update, for example
 * {@code created_at}, {@code created_by}, {@code updated_at} and
 * {@code updated_by}. Create instances with {@link ColumnConventions}.
 *
 * <p>A convention does not replace a value the caller set explicitly unless
 * it is {@link #forced()}. An update that changes nothing does not run, so
 * it does not touch {@code updated_at} either.</p>
 *
 * @param <T> the column's Java type
 */
public final class ColumnConvention<T> {

    /** When the convention applies. */
    public enum When {
        /** On {@code INSERT}. */
        INSERT,
        /** On {@code UPDATE}. */
        UPDATE,
        /** On {@code INSERT} and {@code UPDATE}. */
        INSERT_AND_UPDATE
    }

    private final String description;
    private final Predicate<Column<?>> matcher;
    private final Class<T> type;
    private final When when;
    private final boolean forced;
    private final Function<WriteContext, T> value;
    private final Function<WriteContext, Field<T>> expression;

    ColumnConvention(String description, Predicate<Column<?>> matcher, Class<T> type, When when, boolean forced,
                     Function<WriteContext, T> value, Function<WriteContext, Field<T>> expression) {
        this.description = description;
        this.matcher = Objects.requireNonNull(matcher, "matcher");
        this.type = Objects.requireNonNull(type, "type");
        this.when = Objects.requireNonNull(when, "when");
        this.forced = forced;
        if ((value == null) == (expression == null)) throw new IllegalArgumentException("either a value or an expression");
        this.value = value;
        this.expression = expression;
    }

    /** Returns {@code true} if the convention applies to {@code column}. */
    public boolean appliesTo(Column<?> column) {
        return matcher.test(column);
    }

    /** Returns {@code true} if the convention applies on insert. */
    public boolean onInsert() {
        return when != When.UPDATE;
    }

    /** Returns {@code true} if the convention applies on update. */
    public boolean onUpdate() {
        return when != When.INSERT;
    }

    /** Returns {@code true} if the convention replaces values set by the caller. */
    public boolean isForced() {
        return forced;
    }

    /** Returns a copy that replaces values set by the caller. */
    public ColumnConvention<T> forced() {
        return new ColumnConvention<>(description, matcher, type, when, true, value, expression);
    }

    /** Returns a copy that only applies to columns that also match {@code filter}, e.g. of one schema. */
    public ColumnConvention<T> where(Predicate<Column<?>> filter) {
        return new ColumnConvention<>(description, matcher.and(filter), type, when, forced, value, expression);
    }

    /**
     * Returns the value for {@code column}.
     *
     * @throws IllegalStateException if the column's Java type differs from the convention's
     */
    public Field<T> valueFor(Column<?> column, WriteContext context) {
        if (!column.type().javaType().equals(type)) {
            throw new IllegalStateException("column convention " + description + " produces " + type.getName()
                    + " but column " + column.table().qualifiedName() + "." + column.name() + " is "
                    + column.type().javaType().getName());
        }
        if (expression != null) return expression.apply(context);
        return Values.param(value.apply(context), typed(column).type());
    }

    /** Returns the column's typed reference after the type check of {@link #valueFor}. */
    @SuppressWarnings("unchecked")
    public Column<T> typed(Column<?> column) {
        return (Column<T>) column;
    }

    @Override
    public String toString() {
        return "ColumnConvention[" + description + ", " + when + (forced ? ", forced" : "") + "]";
    }
}
