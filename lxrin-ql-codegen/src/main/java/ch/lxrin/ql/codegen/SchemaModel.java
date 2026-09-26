package ch.lxrin.ql.codegen;

import java.util.List;

/**
 * The part of a PostgreSQL schema the generator needs.
 *
 * @param tables the tables and views
 * @param enums  the enum types
 */
public record SchemaModel(List<TableModel> tables, List<EnumModel> enums) {

    /**
     * A table, view, materialized view or partitioned table.
     *
     * @param schema      the schema
     * @param name        the name
     * @param kind        {@code r}, {@code v}, {@code m} or {@code p} (pg_class.relkind)
     * @param comment     the comment, or {@code null}
     * @param columns     the columns in order
     * @param primaryKey  the primary key, or {@code null}
     * @param uniqueKeys  unique constraints and unique indexes
     * @param foreignKeys foreign keys
     */
    public record TableModel(String schema, String name, String kind, String comment, List<ColumnModel> columns,
                             KeyModel primaryKey, List<KeyModel> uniqueKeys, List<ForeignKeyModel> foreignKeys) {

        /** Returns {@code true} for views and materialized views. */
        public boolean view() {
            return "v".equals(kind) || "m".equals(kind);
        }
    }

    /**
     * A column.
     *
     * @param name          the name
     * @param type          the SQL type name ({@code int4}, {@code _text}, an enum or domain name)
     * @param typeSchema    the schema of the type
     * @param typeKind      pg_type.typtype: {@code b} base, {@code e} enum, {@code d} domain, ...
     * @param elementType   for arrays: the element type name, else {@code null}
     * @param elementKind   for arrays: the element's typtype, else {@code null}
     * @param elementSchema for arrays: the element's schema, else {@code null}
     * @param notNull       {@code NOT NULL}
     * @param hasDefault    has a {@code DEFAULT}
     * @param identity      {@code a} (always), {@code d} (by default) or empty
     * @param generated     {@code s} for a stored generated column, else empty
     * @param comment       the comment, or {@code null}
     * @param sequence      the owned sequence (serial or identity), or {@code null}
     */
    public record ColumnModel(String name, String type, String typeSchema, String typeKind, String elementType, String elementKind,
                              String elementSchema, boolean notNull, boolean hasDefault, String identity, String generated,
                              String comment, String sequence) {

        /** Returns {@code true} for array columns. */
        public boolean array() {
            return elementType != null;
        }
    }

    /**
     * A primary or unique key.
     *
     * @param name    the constraint or index name
     * @param columns the column names in order
     */
    public record KeyModel(String name, List<String> columns) {
    }

    /**
     * A foreign key.
     *
     * @param name              the constraint name
     * @param columns           the column names
     * @param referencedSchema  the referenced table's schema
     * @param referencedTable   the referenced table
     * @param referencedColumns the referenced column names
     */
    public record ForeignKeyModel(String name, List<String> columns, String referencedSchema, String referencedTable,
                                  List<String> referencedColumns) {
    }

    /**
     * An enum type.
     *
     * @param schema the schema
     * @param name   the type name
     * @param labels the labels in sort order
     */
    public record EnumModel(String schema, String name, List<String> labels) {
    }
}
