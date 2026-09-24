package ch.lxrin.ql.codegen;

import org.flywaydb.core.Flyway;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Provides the database to read the schema from: a disposable PostgreSQL
 * container with migrations applied (Flyway or plain SQL scripts), or an
 * existing database.
 */
public final class DatabaseProvisioner implements AutoCloseable {

    private final PostgreSQLContainer container;
    private final String url;
    private final String user;
    private final String password;

    private DatabaseProvisioner(PostgreSQLContainer container, String url, String user, String password) {
        this.container = container;
        this.url = url;
        this.user = user;
        this.password = password;
    }

    /**
     * Starts a PostgreSQL container and applies the migrations.
     *
     * @param image             the Docker image, e.g. {@code postgres:17-alpine}
     * @param flywayLocations   directories with Flyway migrations ({@code V1__init.sql}, ...), may be empty
     * @param sqlScripts        plain SQL files or directories (applied in name order after Flyway), may be empty
     */
    public static DatabaseProvisioner testcontainer(String image, List<Path> flywayLocations, List<Path> sqlScripts) {
        PostgreSQLContainer container = new PostgreSQLContainer(image);
        container.start();
        DatabaseProvisioner provisioner = new DatabaseProvisioner(container, container.getJdbcUrl(), container.getUsername(),
                container.getPassword());
        try {
            provisioner.migrate(flywayLocations, sqlScripts);
        } catch (RuntimeException e) {
            provisioner.close();
            throw e;
        }
        return provisioner;
    }

    /** Uses an existing database; nothing is migrated. */
    public static DatabaseProvisioner jdbc(String url, String user, String password) {
        return new DatabaseProvisioner(null, url, user, password);
    }

    /** Opens a connection. */
    public Connection connect() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    private void migrate(List<Path> flywayLocations, List<Path> sqlScripts) {
        List<String> locations = new ArrayList<>();
        for (Path p : flywayLocations) if (Files.isDirectory(p)) locations.add("filesystem:" + p.toAbsolutePath());
        if (!locations.isEmpty()) {
            Flyway.configure().dataSource(url, user, password).locations(locations.toArray(new String[0])).load().migrate();
        }
        List<Path> files = new ArrayList<>();
        for (Path p : sqlScripts) {
            if (Files.isDirectory(p)) {
                try (Stream<Path> s = Files.list(p)) {
                    s.filter(f -> f.toString().endsWith(".sql")).sorted().forEach(files::add);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            } else if (Files.exists(p)) {
                files.add(p);
            }
        }
        if (files.isEmpty()) return;
        try (Connection con = connect(); Statement s = con.createStatement()) {
            for (Path f : files) s.execute(Files.readString(f));
        } catch (SQLException | IOException e) {
            throw new IllegalStateException("cannot apply SQL scripts: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        if (container != null) container.stop();
    }
}
