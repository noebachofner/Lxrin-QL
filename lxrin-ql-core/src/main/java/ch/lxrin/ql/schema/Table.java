package ch.lxrin.ql.schema;

import ch.lxrin.ql.dsl.Fields;
import ch.lxrin.ql.dsl.Row;
import ch.lxrin.ql.render.Identifiers;
import ch.lxrin.ql.render.QueryPart;
import ch.lxrin.ql.render.RenderContext;
import ch.lxrin.ql.types.DataType;
import ch.lxrin.ql.types.Kind;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * A table (or view) with typed columns and keys. Table classes are generated
 * from the database schema; each instance may carry an alias
 * ({@code USERS.as("u")}).
 *
 * @param <R> the row record type returned by {@code selectFrom(table)}
 */
public abstract class Table<R> implements QueryPart {

    private final String schema;
    private final String name;
    private final String alias;
    private final List<Column<?>> columns = new ArrayList<>();
    private final List<UniqueKey> uniqueKeys = new ArrayList<>();
    private final List<ForeignKey> foreignKeys = new ArrayList<>();
    private PrimaryKey<?> primaryKey;

    /**
     * @param schema the schema, or {@code null} to use the search path
     * @param name   the table name
     * @param alias  the alias, or {@code null}
     */
    protected Table(String schema, String name, String alias) {
        this.schema = schema == null ? null : Identifiers.requireName(schema);
        this.name = Identifiers.requireName(name);
        this.alias = alias == null ? null : Identifiers.requireName(alias);
    }

    // -------------------------------------------------------------------------
    // Metadata
    // -------------------------------------------------------------------------

    /** Returns the schema, or {@code null}. */
    public String schema() {
        return schema;
    }

    /** Returns the table name. */
    public String name() {
        return name;
    }

    /** Returns the alias, if any. */
    public Optional<String> alias() {
        return Optional.ofNullable(alias);
    }

    /** Returns the name used to qualify columns: the alias, or else the table name. */
    public String qualifier() {
        return alias != null ? alias : name;
    }

    /** Returns {@code schema.name} or {@code name}, for messages. */
    public String qualifiedName() {
        return schema == null ? name : schema + "." + name;
    }

    /** Returns all columns in table order. */
    public List<Column<?>> columns() {
        return Collections.unmodifiableList(columns);
    }

    /** Returns the column with the given SQL name. This is a metadata lookup, not SQL. */
    public Optional<Column<?>> column(String columnName) {
        for (Column<?> c : columns) {
            if (c.name().equals(columnName)) return Optional.of(c);
        }
        return Optional.empty();
    }

    /**
     * Returns the column with the given name and checks its Java type. Used by
     * generic code such as policies ({@code table.column("deleted_at", SqlTypes.TIMESTAMPTZ)}).
     *
     * @throws IllegalArgumentException if there is no such column or its type differs
     */
    @SuppressWarnings("unchecked")
    public <T> Column<T> column(String columnName, DataType<T> type) {
        Column<?> c = column(columnName).orElseThrow(() ->
                new IllegalArgumentException("table " + qualifiedName() + " has no column " + columnName));
        if (!c.type().javaType().equals(type.javaType())) {
            throw new IllegalArgumentException("column " + qualifiedName() + "." + columnName + " is of type "
                    + c.type() + ", not " + type);
        }
        return (Column<T>) c;
    }

    /** Returns the primary key, if any. */
    public Optional<PrimaryKey<?>> primaryKey() {
        return Optional.ofNullable(primaryKey);
    }

    /** Returns the unique constraints and unique indexes. */
    public List<UniqueKey> uniqueKeys() {
        return Collections.unmodifiableList(uniqueKeys);
    }

    /** Returns the foreign keys of this table. */
    public List<ForeignKey> foreignKeys() {
        return Collections.unmodifiableList(foreignKeys);
    }

    /** Returns {@code true} for views and other tables that cannot be written. */
    public boolean readOnly() {
        return false;
    }

    /** Returns {@code true} if table policies apply to references of this table. */
    public boolean supportsPolicies() {
        return true;
    }

    /** Returns a copy of this table with an alias. */
    public abstract Table<R> as(String alias);

    /** Maps a result row that contains all columns to the row record. */
    public abstract R mapRow(Row row);

    /** Returns {@code true} if {@code other} is the same table, ignoring aliases. */
    public boolean sameTable(Table<?> other) {
        return other != null && name.equals(other.name) && Objects.equals(schema, other.schema);
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    /** Renders the table reference for {@code FROM}: {@code [schema.]name [AS alias]}. */
    @Override
    public void render(RenderContext ctx) {
        renderName(ctx);
        if (alias != null) ctx.append(" AS ").identifier(alias);
    }

    /** Renders {@code [schema.]name}. */
    public void renderName(RenderContext ctx) {
        if (schema != null) ctx.identifier(schema).append('.');
        ctx.identifier(name);
    }

    // -------------------------------------------------------------------------
    // Factories for generated code
    // -------------------------------------------------------------------------

    /** Declares a column; the returned class matches the type's kind at runtime. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected <T> Column<T> column(String columnName, DataType<T> type, int flags) {
        Column<?> c;
        Kind kind = type.kind();
        if (kind == Kind.STRING && type.javaType() == String.class) c = new StringColumn(this, columnName, (DataType) type, flags);
        else if (kind == Kind.NUMBER && Number.class.isAssignableFrom(type.javaType())) c = new NumberColumn(this, columnName, (DataType) type, flags);
        else if (kind == Kind.TEMPORAL) c = new TemporalColumn<>(this, columnName, type, flags);
        else if (kind == Kind.BOOLEAN && type.javaType() == Boolean.class) c = new BooleanColumn(this, columnName, (DataType) type, flags);
        else if (kind == Kind.JSON) c = new JsonColumn<>(this, columnName, type, flags);
        else if (kind == Kind.ARRAY) c = new ArrayColumn(this, columnName, type, flags);
        else if (Fields.isRange(type)) c = new RangeColumn(this, columnName, (DataType) type, flags);
        else if (Fields.isTsVector(type)) c = new TsVectorColumn(this, columnName, (DataType) type, flags);
        else c = new Column<>(this, columnName, type, flags);
        return (Column<T>) register(c);
    }

    /** Declares a text column. */
    protected StringColumn stringColumn(String columnName, DataType<String> type, int flags) {
        return register(new StringColumn(this, columnName, type, flags));
    }

    /** Declares a numeric column. */
    protected <N extends Number> NumberColumn<N> numberColumn(String columnName, DataType<N> type, int flags) {
        return register(new NumberColumn<>(this, columnName, type, flags));
    }

    /** Declares a temporal column. */
    protected <T> TemporalColumn<T> temporalColumn(String columnName, DataType<T> type, int flags) {
        return register(new TemporalColumn<>(this, columnName, type, flags));
    }

    /** Declares a boolean column. */
    protected BooleanColumn booleanColumn(String columnName, DataType<Boolean> type, int flags) {
        return register(new BooleanColumn(this, columnName, type, flags));
    }

    /** Declares a JSON column. */
    protected <T> JsonColumn<T> jsonColumn(String columnName, DataType<T> type, int flags) {
        return register(new JsonColumn<>(this, columnName, type, flags));
    }

    /** Declares an array column. */
    protected <E> ArrayColumn<E> arrayColumn(String columnName, DataType<E[]> type, int flags) {
        return register(new ArrayColumn<>(this, columnName, type, flags));
    }

    /** Declares a range column ({@code daterange}, {@code tstzrange}, …). */
    protected RangeColumn rangeColumn(String columnName, DataType<String> type, int flags) {
        return register(new RangeColumn(this, columnName, type, flags));
    }

    /** Declares a {@code tsvector} column. */
    protected TsVectorColumn tsvectorColumn(String columnName, DataType<String> type, int flags) {
        return register(new TsVectorColumn(this, columnName, type, flags));
    }

    /** Declares any other column (UUIDs, enums, value objects, ...). */
    protected <T> Column<T> otherColumn(String columnName, DataType<T> type, int flags) {
        return register(new Column<>(this, columnName, type, flags));
    }

    private <C extends Column<?>> C register(C column) {
        for (Column<?> c : columns) {
            if (c.name().equals(column.name())) throw new IllegalStateException("duplicate column " + column.name());
        }
        columns.add(column);
        return column;
    }

    /** Declares a single-column primary key. */
    protected <K> PrimaryKey<K> primaryKey(String constraintName, Column<K> column, KeyStrategy strategy) {
        return setPrimaryKey(new PrimaryKey<>(constraintName, this, List.of(column), column.type().javaType(), strategy,
                values -> column.type().cast(values[0]), key -> new Object[]{key}));
    }

    /** Declares a composite primary key with a key record type. */
    protected <K> PrimaryKey<K> primaryKey(String constraintName, Class<K> keyType, Function<Object[], K> fromValues,
                                           Function<K, Object[]> toValues, Column<?>... keyColumns) {
        return setPrimaryKey(new PrimaryKey<>(constraintName, this, Arrays.asList(keyColumns), keyType, KeyStrategy.none(),
                fromValues, toValues));
    }

    private <K> PrimaryKey<K> setPrimaryKey(PrimaryKey<K> key) {
        if (primaryKey != null) throw new IllegalStateException("primary key already declared");
        primaryKey = key;
        Constraints.register(key);
        return key;
    }

    /** Declares a unique constraint or unique index. */
    protected UniqueKey uniqueKey(String constraintName, Column<?>... keyColumns) {
        UniqueKey key = new UniqueKey(constraintName, this, Arrays.asList(keyColumns));
        uniqueKeys.add(key);
        Constraints.register(key);
        return key;
    }

    /** Declares a foreign key. */
    protected ForeignKey foreignKey(String constraintName, List<Column<?>> keyColumns, String referencedSchema,
                                    String referencedTable, List<String> referencedColumns) {
        ForeignKey key = new ForeignKey(constraintName, this, keyColumns, referencedSchema, referencedTable, referencedColumns);
        foreignKeys.add(key);
        Constraints.register(key);
        return key;
    }

    // -------------------------------------------------------------------------
    // Object methods
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Table)) return false;
        Table<?> other = (Table<?>) o;
        return name.equals(other.name) && Objects.equals(schema, other.schema) && Objects.equals(alias, other.alias)
                && getClass() == other.getClass();
    }

    @Override
    public int hashCode() {
        return Objects.hash(schema, name, alias);
    }

    @Override
    public String toString() {
        return alias == null ? qualifiedName() : qualifiedName() + " AS " + alias;
    }
}
