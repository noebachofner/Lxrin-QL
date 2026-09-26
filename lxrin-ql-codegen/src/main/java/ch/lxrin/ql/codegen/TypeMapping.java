package ch.lxrin.ql.codegen;

import ch.lxrin.ql.codegen.SchemaModel.ColumnModel;
import ch.lxrin.ql.codegen.SchemaModel.EnumModel;
import ch.lxrin.ql.codegen.SchemaModel.TableModel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Maps PostgreSQL column types to Java types, {@code DataType} expressions and column classes. */
final class TypeMapping {

    /**
     * How a column is declared in generated code.
     *
     * @param javaType  fully qualified Java type of the values
     * @param dataType  Java expression of the {@code DataType} (fully qualified names are marked with {@code #…#})
     * @param factory   the {@code Table} factory method ({@code stringColumn}, ..., or {@code column})
     * @param fieldType fully qualified column class, generic argument appended where needed
     * @param typeArg   the generic argument of the column class, or {@code null}
     */
    record Mapped(String javaType, String dataType, String factory, String fieldType, String typeArg) {
    }

    private static final String SQL_TYPES = "#ch.lxrin.ql.types.SqlTypes#";
    private static final String SCHEMA = "ch.lxrin.ql.schema.";

    private record Base(String javaType, String constant, String kind) {
    }

    private static final Map<String, Base> BASE = new HashMap<>();

    static {
        base("int2", "java.lang.Short", "INT2", "number");
        base("int4", "java.lang.Integer", "INT4", "number");
        base("int8", "java.lang.Long", "INT8", "number");
        base("numeric", "java.math.BigDecimal", "NUMERIC", "number");
        base("float4", "java.lang.Float", "FLOAT4", "number");
        base("float8", "java.lang.Double", "FLOAT8", "number");
        base("text", "java.lang.String", "TEXT", "string");
        base("name", "java.lang.String", "TEXT", "string");
        base("varchar", "java.lang.String", "VARCHAR", "string");
        base("bpchar", "java.lang.String", "CHAR", "string");
        base("citext", "java.lang.String", "CITEXT", "string");
        base("bool", "java.lang.Boolean", "BOOL", "boolean");
        base("uuid", "java.util.UUID", "UUID", "other");
        base("date", "java.time.LocalDate", "DATE", "temporal");
        base("time", "java.time.LocalTime", "TIME", "temporal");
        base("timestamp", "java.time.LocalDateTime", "TIMESTAMP", "temporal");
        base("timestamptz", "java.time.Instant", "TIMESTAMPTZ", "temporal");
        base("interval", "java.time.Duration", "INTERVAL", "other");
        base("json", "java.lang.String", "JSON", "json");
        base("jsonb", "java.lang.String", "JSONB", "json");
        base("bytea", "byte[]", "BYTEA", "other");
        base("tsvector", "java.lang.String", "TSVECTOR", "tsvector");
        base("tsquery", "java.lang.String", "TSQUERY", "other");
        base("daterange", "java.lang.String", "DATERANGE", "range");
        base("tsrange", "java.lang.String", "TSRANGE", "range");
        base("tstzrange", "java.lang.String", "TSTZRANGE", "range");
        base("int4range", "java.lang.String", "INT4RANGE", "range");
        base("int8range", "java.lang.String", "INT8RANGE", "range");
        base("numrange", "java.lang.String", "NUMRANGE", "range");
    }

    private static void base(String sql, String java, String constant, String kind) {
        BASE.put(sql, new Base(java, constant, kind));
    }

    private final CodegenConfig config;
    private final Map<String, EnumModel> enums = new HashMap<>();
    private final Map<String, String> enumClasses = new HashMap<>();

    TypeMapping(CodegenConfig config, List<EnumModel> enumModels, NamingStrategy naming) {
        this.config = config;
        for (EnumModel e : enumModels) {
            String key = e.schema() + "." + e.name();
            enums.put(key, e);
            String mapped = config.enumMappings().get(e.name());
            if (mapped == null) mapped = config.enumMappings().get(key);
            enumClasses.put(key, mapped != null ? mapped : config.packageName() + "." + naming.enumName(e));
        }
    }

    /** Returns the Java class of an enum type, generated or mapped. */
    String enumClass(EnumModel e) {
        return enumClasses.get(e.schema() + "." + e.name());
    }

    /** Returns {@code true} if the enum type is mapped to an existing Java enum. */
    boolean mapped(EnumModel e) {
        return config.enumMappings().containsKey(e.name()) || config.enumMappings().containsKey(e.schema() + "." + e.name());
    }

    /** The SQL name used in casts: schema-qualified unless in the default schema. */
    String enumSqlName(EnumModel e) {
        return e.schema().equals(config.defaultSchema()) ? e.name() : e.schema() + "." + e.name();
    }

    /** The {@code DataType} expression of an enum type. */
    String enumDataType(EnumModel e) {
        String cls = "#" + enumClass(e) + "#";
        if (!mapped(e)) return cls + ".TYPE";
        StringBuilder sb = new StringBuilder(SQL_TYPES + ".pgEnumByName(\"" + escape(enumSqlName(e)) + "\", " + cls + ".class");
        for (String label : e.labels()) sb.append(", \"").append(escape(label)).append('"');
        return sb.append(')').toString();
    }

    Mapped map(TableModel table, ColumnModel column) {
        String sqlType = column.array() ? column.elementType() + "[]" : column.type();
        for (CodegenConfig.ForcedType f : config.forcedTypes()) {
            if (f.matches(table.name(), column.name(), sqlType)) {
                return new Mapped(f.javaType(), "#" + f.dataType().substring(0, f.dataType().lastIndexOf('.')) + "#"
                        + f.dataType().substring(f.dataType().lastIndexOf('.')), "column", SCHEMA + "Column", f.javaType());
            }
        }
        if (column.array()) {
            Mapped element = scalar(column.elementType(), column.elementKind(), column.elementSchema());
            return new Mapped(element.javaType() + "[]", element.dataType() + ".array()", "arrayColumn",
                    SCHEMA + "ArrayColumn", element.javaType());
        }
        return scalar(column.type(), column.typeKind(), column.typeSchema());
    }

    private Mapped scalar(String type, String typeKind, String typeSchema) {
        if ("e".equals(typeKind)) {
            EnumModel e = enums.get(typeSchema + "." + type);
            if (e == null) throw new IllegalStateException("unknown enum type " + typeSchema + "." + type);
            return new Mapped(enumClass(e), enumDataType(e), "otherColumn", SCHEMA + "Column", enumClass(e));
        }
        Base b = BASE.get(type);
        if (b == null) {
            return new Mapped("java.lang.String", SQL_TYPES + ".otherAsText(\"" + escape(type) + "\")", "otherColumn",
                    SCHEMA + "Column", "java.lang.String");
        }
        String dataType = SQL_TYPES + "." + b.constant();
        switch (b.kind()) {
            case "number":
                return new Mapped(b.javaType(), dataType, "numberColumn", SCHEMA + "NumberColumn", b.javaType());
            case "string":
                return new Mapped(b.javaType(), dataType, "stringColumn", SCHEMA + "StringColumn", null);
            case "boolean":
                return new Mapped(b.javaType(), dataType, "booleanColumn", SCHEMA + "BooleanColumn", null);
            case "temporal":
                return new Mapped(b.javaType(), dataType, "temporalColumn", SCHEMA + "TemporalColumn", b.javaType());
            case "range":
                return new Mapped(b.javaType(), dataType, "rangeColumn", SCHEMA + "RangeColumn", null);
            case "tsvector":
                return new Mapped(b.javaType(), dataType, "tsvectorColumn", SCHEMA + "TsVectorColumn", null);
            case "json":
                return new Mapped(b.javaType(), dataType, "jsonColumn", SCHEMA + "JsonColumn", b.javaType());
            default:
                return new Mapped(b.javaType(), dataType, "otherColumn", SCHEMA + "Column", b.javaType());
        }
    }

    static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
