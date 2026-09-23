package ch.lxrin.ql.exec;

import ch.lxrin.ql.bind.BindMap;

import javax.sql.DataSource;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * {@link SqlExecutor} based on plain JDBC. Works with any framework that
 * gives you a {@link DataSource} or a {@link Connection} (Spring, Jakarta EE,
 * Quarkus, Micronaut, HikariCP, ...).
 *
 * <pre>{@code
 * // one connection per statement (auto-commit)
 * LxrinQL.setDefaultExecutor(new JdbcSqlExecutor(dataSource));
 *
 * // inside your own transaction
 * try (Connection con = dataSource.getConnection()) {
 *     con.setAutoCommit(false);
 *     SqlExecutor tx = JdbcSqlExecutor.forConnection(con);
 *     insertInto(o).set(o.total, val(total)).executor(tx).execute();
 *     update(s).set(s.stock, s.stock.minus(1)).where(eq(s.id, val(id))).executor(tx).execute();
 *     con.commit();
 * }
 * }</pre>
 *
 * <p>Value handling: Java arrays are sent as SQL arrays, {@link Instant} as
 * {@code timestamptz}, enums by name. Result values are converted to
 * {@code java.time} types ({@code date} &rarr; {@link LocalDate},
 * {@code timestamp} &rarr; {@link LocalDateTime}, {@code timestamptz} &rarr;
 * {@link OffsetDateTime}), SQL arrays to Java arrays and {@code json}/{@code jsonb}
 * to {@code String}.</p>
 */
public class JdbcSqlExecutor implements SqlExecutor {

    /** Supplies and releases connections. */
    public interface ConnectionProvider {
        /** Returns a connection for one statement. */
        Connection acquire() throws SQLException;

        /** Called after the statement; closes or returns the connection. */
        void release(Connection connection) throws SQLException;
    }

    private final ConnectionProvider connections;

    /** Uses a new connection from the data source for every statement and closes it afterwards. */
    public JdbcSqlExecutor(DataSource dataSource) {
        this(new ConnectionProvider() {
            @Override
            public Connection acquire() throws SQLException {
                return dataSource.getConnection();
            }

            @Override
            public void release(Connection connection) throws SQLException {
                connection.close();
            }
        });
    }

    /** Uses a custom connection provider (e.g. one bound to the current transaction). */
    public JdbcSqlExecutor(ConnectionProvider connections) {
        if (connections == null) throw new IllegalArgumentException("connections must not be null");
        this.connections = connections;
    }

    /** Uses the given connection for every statement; it is never closed by the executor. */
    public static JdbcSqlExecutor forConnection(Connection connection) {
        return new JdbcSqlExecutor(new ConnectionProvider() {
            @Override
            public Connection acquire() {
                return connection;
            }

            @Override
            public void release(Connection c) {
                // owned by the caller
            }
        });
    }

    @Override
    public Object[][] select(String sql, BindMap binds) {
        NamedParameterSql parsed = NamedParameterSql.parse(sql, binds.asMap());
        return withConnection(sql, con -> {
            try (PreparedStatement ps = con.prepareStatement(parsed.sql())) {
                setParameters(con, ps, parsed.values());
                try (ResultSet rs = ps.executeQuery()) {
                    return readAll(rs);
                }
            }
        });
    }

    @Override
    public int execute(String sql, BindMap binds) {
        NamedParameterSql parsed = NamedParameterSql.parse(sql, binds.asMap());
        return withConnection(sql, con -> {
            try (PreparedStatement ps = con.prepareStatement(parsed.sql())) {
                setParameters(con, ps, parsed.values());
                return ps.executeUpdate();
            }
        });
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private interface SqlWork<R> {
        R run(Connection connection) throws SQLException;
    }

    private <R> R withConnection(String sql, SqlWork<R> work) {
        Connection con = null;
        try {
            con = connections.acquire();
            return work.run(con);
        } catch (SQLException e) {
            throw new SqlExecutionException("SQL failed: " + e.getMessage() + "\nSQL: " + sql, e);
        } finally {
            if (con != null) {
                try {
                    connections.release(con);
                } catch (SQLException ignored) {
                    // releasing must not hide the original result or error
                }
            }
        }
    }

    /** Binds the values to the statement; override to customise type handling. */
    protected void setParameters(Connection con, PreparedStatement ps, List<Object> values) throws SQLException {
        for (int i = 0; i < values.size(); i++) {
            ps.setObject(i + 1, toJdbcValue(con, values.get(i)));
        }
    }

    /** Converts a Java value into something the JDBC driver accepts. */
    protected Object toJdbcValue(Connection con, Object value) throws SQLException {
        if (value == null) return null;
        if (value instanceof Enum) return ((Enum<?>) value).name();
        if (value instanceof Instant) return OffsetDateTime.ofInstant((Instant) value, ZoneOffset.UTC);
        if (value instanceof java.util.Date && !(value instanceof java.sql.Date)
                && !(value instanceof Timestamp) && !(value instanceof java.sql.Time)) {
            return new Timestamp(((java.util.Date) value).getTime());
        }
        if (value.getClass().isArray() && !(value instanceof byte[])) {
            int n = Array.getLength(value);
            Object[] boxed = new Object[n];
            for (int i = 0; i < n; i++) boxed[i] = Array.get(value, i);
            return con.createArrayOf(sqlArrayType(value.getClass().getComponentType()), boxed);
        }
        return value;
    }

    private static String sqlArrayType(Class<?> type) {
        if (type == String.class || type.isEnum()) return "text";
        if (type == Long.class || type == long.class) return "int8";
        if (type == Integer.class || type == int.class) return "int4";
        if (type == Short.class || type == short.class) return "int2";
        if (type == Double.class || type == double.class) return "float8";
        if (type == Float.class || type == float.class) return "float4";
        if (type == Boolean.class || type == boolean.class) return "bool";
        if (type == BigDecimal.class) return "numeric";
        if (type == UUID.class) return "uuid";
        if (type == LocalDate.class) return "date";
        if (type == LocalDateTime.class) return "timestamp";
        if (type == OffsetDateTime.class || type == Instant.class) return "timestamptz";
        return "text";
    }

    private static Object[][] readAll(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columns = meta.getColumnCount();
        String[] typeNames = new String[columns];
        for (int c = 0; c < columns; c++) typeNames[c] = meta.getColumnTypeName(c + 1);
        List<Object[]> rows = new ArrayList<>();
        while (rs.next()) {
            Object[] row = new Object[columns];
            for (int c = 0; c < columns; c++) row[c] = readValue(rs, c + 1, typeNames[c]);
            rows.add(row);
        }
        return rows.toArray(new Object[0][]);
    }

    private static Object readValue(ResultSet rs, int index, String typeName) throws SQLException {
        String type = typeName == null ? "" : typeName.toLowerCase();
        switch (type) {
            case "json":
            case "jsonb":
                return rs.getString(index);
            case "timestamptz":
                return rs.getObject(index, OffsetDateTime.class);
            default:
                break;
        }
        Object value = rs.getObject(index);
        if (value instanceof java.sql.Array) {
            java.sql.Array array = (java.sql.Array) value;
            try {
                return array.getArray();
            } finally {
                array.free();
            }
        }
        if (value instanceof java.sql.Date) return ((java.sql.Date) value).toLocalDate();
        if (value instanceof Timestamp) return ((Timestamp) value).toLocalDateTime();
        if (value instanceof java.sql.Time) return ((java.sql.Time) value).toLocalTime();
        if (value != null && "interval".equals(type)) return value.toString();
        return value;
    }
}
