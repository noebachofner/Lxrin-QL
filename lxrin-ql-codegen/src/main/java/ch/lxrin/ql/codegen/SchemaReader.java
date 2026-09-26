package ch.lxrin.ql.codegen;

import ch.lxrin.ql.codegen.SchemaModel.ColumnModel;
import ch.lxrin.ql.codegen.SchemaModel.EnumModel;
import ch.lxrin.ql.codegen.SchemaModel.ForeignKeyModel;
import ch.lxrin.ql.codegen.SchemaModel.KeyModel;
import ch.lxrin.ql.codegen.SchemaModel.TableModel;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Reads tables, columns, keys and enum types from {@code pg_catalog}. */
public final class SchemaReader {

    private static final String TABLES = "SELECT n.nspname, c.relname, c.relkind, obj_description(c.oid, 'pg_class'), c.oid"
            + " FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace"
            + " WHERE c.relkind IN ('r', 'v', 'm', 'p') AND NOT c.relispartition AND n.nspname = ANY (?)"
            + " ORDER BY n.nspname, c.relname";

    private static final String COLUMNS = "SELECT a.attname, t.typname, tn.nspname, t.typtype, et.typname, et.typtype, etn.nspname,"
            + " bt.typname, a.attnotnull, a.atthasdef, a.attidentity, a.attgenerated, col_description(a.attrelid, a.attnum),"
            + " CASE WHEN c.relkind IN ('r', 'p') THEN pg_get_serial_sequence(quote_ident(n.nspname) || '.' || quote_ident(c.relname),"
            + "   a.attname) END"
            + " FROM pg_attribute a JOIN pg_class c ON c.oid = a.attrelid JOIN pg_namespace n ON n.oid = c.relnamespace"
            + " JOIN pg_type t ON t.oid = a.atttypid JOIN pg_namespace tn ON tn.oid = t.typnamespace"
            + " LEFT JOIN pg_type et ON et.oid = t.typelem AND t.typcategory = 'A'"
            + " LEFT JOIN pg_namespace etn ON etn.oid = et.typnamespace"
            + " LEFT JOIN pg_type bt ON bt.oid = t.typbasetype AND t.typtype = 'd'"
            + " WHERE a.attrelid = ? AND a.attnum > 0 AND NOT a.attisdropped ORDER BY a.attnum";

    private static final String CONSTRAINTS = "SELECT con.conname, con.contype,"
            + " ARRAY(SELECT a.attname FROM unnest(con.conkey) WITH ORDINALITY k(attnum, ord)"
            + "   JOIN pg_attribute a ON a.attrelid = con.conrelid AND a.attnum = k.attnum ORDER BY k.ord),"
            + " fn.nspname, fc.relname,"
            + " ARRAY(SELECT a.attname FROM unnest(con.confkey) WITH ORDINALITY k(attnum, ord)"
            + "   JOIN pg_attribute a ON a.attrelid = con.confrelid AND a.attnum = k.attnum ORDER BY k.ord)"
            + " FROM pg_constraint con LEFT JOIN pg_class fc ON fc.oid = con.confrelid"
            + " LEFT JOIN pg_namespace fn ON fn.oid = fc.relnamespace"
            + " WHERE con.conrelid = ? AND con.contype IN ('p', 'u', 'f') ORDER BY con.conname";

    private static final String UNIQUE_INDEXES = "SELECT ic.relname,"
            + " ARRAY(SELECT a.attname FROM unnest(i.indkey::int2[]) WITH ORDINALITY k(attnum, ord)"
            + "   JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = k.attnum ORDER BY k.ord)"
            + " FROM pg_index i JOIN pg_class ic ON ic.oid = i.indexrelid"
            + " WHERE i.indrelid = ? AND i.indisunique AND NOT i.indisprimary AND i.indexprs IS NULL"
            + " AND NOT EXISTS (SELECT 1 FROM pg_constraint con WHERE con.conindid = i.indexrelid)"
            + " ORDER BY ic.relname";

    private static final String ENUMS = "SELECT n.nspname, t.typname, array_agg(e.enumlabel ORDER BY e.enumsortorder)"
            + " FROM pg_type t JOIN pg_enum e ON e.enumtypid = t.oid JOIN pg_namespace n ON n.oid = t.typnamespace"
            + " WHERE n.nspname NOT IN ('pg_catalog', 'information_schema') GROUP BY n.nspname, t.typname ORDER BY 1, 2";

    private SchemaReader() {}

    /** Reads the given schemas; tables are filtered with the configuration's include/exclude rules. */
    public static SchemaModel read(Connection con, CodegenConfig config) throws SQLException {
        List<TableModel> tables = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(TABLES)) {
            ps.setArray(1, con.createArrayOf("text", config.schemas().toArray()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString(2);
                    if (!config.included(name)) continue;
                    long oid = rs.getLong(5);
                    tables.add(readTable(con, rs.getString(1), name, rs.getString(3), rs.getString(4), oid));
                }
            }
        }
        List<EnumModel> enums = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(ENUMS); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) enums.add(new EnumModel(rs.getString(1), rs.getString(2), strings(rs.getArray(3))));
        }
        return new SchemaModel(tables, enums);
    }

    private static TableModel readTable(Connection con, String schema, String name, String kind, String comment, long oid)
            throws SQLException {
        List<ColumnModel> columns = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(COLUMNS)) {
            ps.setLong(1, oid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String type = rs.getString(2);
                    String typeKind = rs.getString(4);
                    String domainBase = rs.getString(8);
                    if ("d".equals(typeKind) && domainBase != null) {
                        type = domainBase;
                        typeKind = "b";
                    }
                    columns.add(new ColumnModel(rs.getString(1), type, rs.getString(3), typeKind, rs.getString(5), rs.getString(6),
                            rs.getString(7), rs.getBoolean(9), rs.getBoolean(10), rs.getString(11), rs.getString(12),
                            rs.getString(13), rs.getString(14)));
                }
            }
        }
        KeyModel primaryKey = null;
        List<KeyModel> uniques = new ArrayList<>();
        List<ForeignKeyModel> foreignKeys = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(CONSTRAINTS)) {
            ps.setLong(1, oid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String conName = rs.getString(1);
                    List<String> cols = strings(rs.getArray(3));
                    switch (rs.getString(2)) {
                        case "p":
                            primaryKey = new KeyModel(conName, cols);
                            break;
                        case "u":
                            uniques.add(new KeyModel(conName, cols));
                            break;
                        default:
                            foreignKeys.add(new ForeignKeyModel(conName, cols, rs.getString(4), rs.getString(5), strings(rs.getArray(6))));
                    }
                }
            }
        }
        try (PreparedStatement ps = con.prepareStatement(UNIQUE_INDEXES)) {
            ps.setLong(1, oid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) uniques.add(new KeyModel(rs.getString(1), strings(rs.getArray(2))));
            }
        }
        return new TableModel(schema, name, kind, comment, columns, primaryKey, uniques, foreignKeys);
    }

    private static List<String> strings(Array array) throws SQLException {
        if (array == null) return List.of();
        return new ArrayList<>(Arrays.asList((String[]) array.getArray()));
    }
}
