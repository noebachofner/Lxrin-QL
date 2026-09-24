package ch.lxrin.ql.runtime;

import ch.lxrin.ql.render.Bind;
import ch.lxrin.ql.render.RenderedSql;
import ch.lxrin.ql.types.DataType;
import ch.lxrin.ql.types.ValueContext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/** Executes statements over JDBC, binding and reading values with their {@link DataType}s. */
public class JdbcExecutor implements SqlExecutor {

    @Override
    public ResultCursor query(ValueContext context, RenderedSql sql, List<DataType<?>> resultTypes, int fetchSize)
            throws SQLException {
        Connection con = context.connection();
        PreparedStatement ps = con.prepareStatement(sql.sql());
        try {
            bindAll(context, ps, sql.binds());
            if (fetchSize > 0) ps.setFetchSize(fetchSize);
            ResultSet rs = ps.executeQuery();
            return new ResultCursor() {
                @Override
                public Object[] next() throws SQLException {
                    return rs.next() ? readRow(context, rs, resultTypes) : null;
                }

                @Override
                public void close() throws SQLException {
                    try {
                        rs.close();
                    } finally {
                        ps.close();
                    }
                }
            };
        } catch (SQLException | RuntimeException e) {
            ps.close();
            throw e;
        }
    }

    @Override
    public UpdateResult update(ValueContext context, RenderedSql sql, List<DataType<?>> returningTypes) throws SQLException {
        try (PreparedStatement ps = context.connection().prepareStatement(sql.sql())) {
            bindAll(context, ps, sql.binds());
            if (returningTypes.isEmpty()) {
                if (!ps.execute()) return new UpdateResult(ps.getLargeUpdateCount(), List.of());
                long rows = 0;
                try (ResultSet rs = ps.getResultSet()) {
                    while (rs.next()) rows++;
                }
                return new UpdateResult(rows, List.of());
            }
            List<Object[]> rows = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rows.add(readRow(context, rs, returningTypes));
            }
            return new UpdateResult(rows.size(), rows);
        }
    }

    @Override
    public BatchResult batch(ValueContext context, String sql, List<List<Bind<?>>> parameterSets,
                             List<String> returningColumns, List<DataType<?>> returningTypes) throws SQLException {
        Connection con = context.connection();
        try (PreparedStatement ps = returningColumns.isEmpty()
                ? con.prepareStatement(sql)
                : con.prepareStatement(sql, returningColumns.toArray(new String[0]))) {
            for (List<Bind<?>> binds : parameterSets) {
                bindAll(context, ps, binds);
                ps.addBatch();
            }
            long[] counts = ps.executeLargeBatch();
            List<Object[]> rows = new ArrayList<>();
            if (!returningColumns.isEmpty()) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    while (rs.next()) rows.add(readRow(context, rs, returningTypes));
                }
            }
            return new BatchResult(counts, rows);
        }
    }

    /** Binds all values; override to customise binding. */
    protected void bindAll(ValueContext context, PreparedStatement ps, List<Bind<?>> binds) throws SQLException {
        for (int i = 0; i < binds.size(); i++) bind(context, ps, i + 1, binds.get(i));
    }

    private static <T> void bind(ValueContext context, PreparedStatement ps, int index, Bind<T> bind) throws SQLException {
        if (bind.value() == null) {
            ps.setNull(index, Types.OTHER);
        } else {
            bind.type().access().set(context, ps, index, bind.value());
        }
    }

    /** Reads one row with the given types. */
    protected Object[] readRow(ValueContext context, ResultSet rs, List<DataType<?>> types) throws SQLException {
        Object[] row = new Object[types.size()];
        for (int i = 0; i < row.length; i++) row[i] = types.get(i).access().get(context, rs, i + 1);
        return row;
    }
}
