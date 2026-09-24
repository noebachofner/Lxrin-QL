package ch.lxrin.ql.spring;

import ch.lxrin.ql.runtime.ConnectionProvider;
import org.springframework.jdbc.datasource.DataSourceUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Objects;

/**
 * Gets connections through {@link DataSourceUtils}, so LxrinQL statements
 * join Spring-managed transactions ({@code @Transactional}, {@code TransactionTemplate}).
 */
public final class SpringConnectionProvider implements ConnectionProvider {

    private final DataSource dataSource;

    /** Creates the provider for a data source. */
    public SpringConnectionProvider(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Connection acquire() {
        return DataSourceUtils.getConnection(dataSource);
    }

    @Override
    public void release(Connection connection) {
        DataSourceUtils.releaseConnection(connection, dataSource);
    }
}
