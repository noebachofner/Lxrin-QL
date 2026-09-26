package ch.lxrin.ql.codegen;

import org.testcontainers.DockerClientFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * Where the generator reads the schema from: a database (a disposable PostgreSQL with the
 * migrations, or an existing one) or a checked-in {@link SchemaSnapshot}. Used by the
 * Gradle and Maven plugins and the command line, so all three behave the same.
 */
public final class SchemaSources {

    /** The {@code schemaSource} setting. */
    public enum Source {
        /** The database if a JDBC URL is set or Docker is available, otherwise the snapshot. */
        AUTO,
        /** Always the database; fails without Docker (unless a JDBC URL is set). */
        DATABASE,
        /** Always the snapshot; no database and no Docker needed. */
        SNAPSHOT;

        /** Parses {@code auto}, {@code database} or {@code snapshot} (any case). */
        public static Source parse(String value) {
            if (value == null || value.isBlank()) return AUTO;
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("schemaSource must be auto, database or snapshot, not " + value, e);
            }
        }
    }

    /** Where messages go: the build tool's log. */
    public interface Log {
        /** An informational message. */
        void info(String message);

        /** A warning. */
        void warn(String message);
    }

    /**
     * The database settings.
     *
     * @param image            the Docker image of the disposable database
     * @param flywayMigrations Flyway migration directories
     * @param sqlScripts       SQL scripts or directories applied after Flyway
     * @param jdbcUrl          an existing database instead of a container, or {@code null}
     * @param user             its user
     * @param password         its password
     */
    public record Database(String image, List<Path> flywayMigrations, List<Path> sqlScripts, String jdbcUrl, String user,
                           String password) {

        /** Normalises {@code null} lists. */
        public Database {
            flywayMigrations = flywayMigrations == null ? List.of() : List.copyOf(flywayMigrations);
            sqlScripts = sqlScripts == null ? List.of() : List.copyOf(sqlScripts);
        }

        /** A disposable database with migrations. */
        public static Database container(String image, List<Path> flywayMigrations, List<Path> sqlScripts) {
            return new Database(image, flywayMigrations, sqlScripts, null, null, null);
        }

        boolean existing() {
            return jdbcUrl != null && !jdbcUrl.isBlank();
        }

        DatabaseProvisioner provision() {
            if (existing()) return DatabaseProvisioner.jdbc(jdbcUrl, user, password);
            return DatabaseProvisioner.testcontainer(image == null ? "postgres:17-alpine" : image, flywayMigrations, sqlScripts);
        }

        /** Returns the hash of the migrations and scripts. */
        String migrationsHash() {
            List<Path> roots = new ArrayList<>(flywayMigrations);
            roots.addAll(sqlScripts);
            return SchemaSnapshot.hash(roots);
        }

        String describe() {
            return existing() ? "the database " + jdbcUrl : "a disposable PostgreSQL (" + image + ") with the migrations";
        }
    }

    private final BooleanSupplier docker;

    /** Uses Testcontainers to find out whether Docker is available. */
    public SchemaSources() {
        this(SchemaSources::dockerAvailable);
    }

    SchemaSources(BooleanSupplier docker) {
        this.docker = docker;
    }

    /** Returns {@code true} if Testcontainers can reach a Docker daemon. */
    public static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable e) {
            return false;
        }
    }

    /** Generates the sources from the configured source. */
    public CodeGenerator.Result generate(CodegenConfig config, Source source, Database database, Path snapshot, Log log)
            throws SQLException {
        SchemaModel model = load(config.validate(), source, database, snapshot, log);
        return CodeGenerator.generate(model, config, new DefaultNamingStrategy(config));
    }

    /** Reads the schema model from the configured source. */
    public SchemaModel load(CodegenConfig config, Source source, Database database, Path snapshot, Log log) throws SQLException {
        boolean snapshotExists = snapshot != null && Files.isRegularFile(snapshot);
        switch (source) {
            case DATABASE:
                log.info("LxrinQL: reading the schema from " + database.describe());
                return fromDatabase(config, database);
            case SNAPSHOT:
                if (!snapshotExists) {
                    throw new IllegalStateException("schemaSource is snapshot, but the snapshot " + snapshot
                            + " does not exist; create it with lxrinQlSnapshot (Gradle), lxrin-ql:snapshot (Maven) or --write-snapshot");
                }
                return fromSnapshot(config, database, snapshot, "schemaSource is snapshot", log);
            default:
                if (database.existing()) {
                    log.info("LxrinQL: reading the schema from " + database.describe());
                    return fromDatabase(config, database);
                }
                if (docker.getAsBoolean()) {
                    log.info("LxrinQL: Docker is available, reading the schema from " + database.describe());
                    return fromDatabase(config, database);
                }
                if (snapshotExists) return fromSnapshot(config, database, snapshot, "Docker is not available", log);
                throw new IllegalStateException("LxrinQL: Docker is not available and there is no schema snapshot"
                        + (snapshot == null ? "" : " at " + snapshot) + ". Start Docker, set the JDBC URL of an existing database,"
                        + " or create a snapshot on a machine with Docker (lxrinQlSnapshot, lxrin-ql:snapshot or --write-snapshot)"
                        + " and commit it.");
        }
    }

    private SchemaModel fromSnapshot(CodegenConfig config, Database database, Path snapshot, String reason, Log log) {
        SchemaSnapshot s = SchemaSnapshot.read(snapshot);
        log.info("LxrinQL: " + reason + ", generating from the schema snapshot " + snapshot);
        if (!database.existing() && !s.migrations().equals(database.migrationsHash())) {
            log.warn("LxrinQL: the migrations changed since the snapshot " + snapshot + " was written; the generated code may"
                    + " be out of date. Run lxrinQlSnapshot (Gradle), lxrin-ql:snapshot (Maven) or --write-snapshot with Docker.");
        }
        return s.select(config);
    }

    private static SchemaModel fromDatabase(CodegenConfig config, Database database) throws SQLException {
        try (DatabaseProvisioner db = database.provision(); Connection con = db.connect()) {
            return SchemaReader.read(con, config);
        }
    }

    /** Reads the schema from the database and returns it as a snapshot. */
    public SchemaSnapshot snapshot(CodegenConfig config, Database database) throws SQLException {
        return new SchemaSnapshot(fromDatabase(config, database), database.migrationsHash());
    }

    /** Reads the schema from the database and writes the snapshot file. */
    public void writeSnapshot(CodegenConfig config, Database database, Path file, Log log) throws SQLException {
        log.info("LxrinQL: reading the schema from " + database.describe());
        SchemaSnapshot s = snapshot(config, database);
        s.write(file);
        log.info("LxrinQL: wrote the schema snapshot " + file + " (" + s.model().tables().size() + " tables, "
                + s.model().enums().size() + " enum types)");
    }

    /**
     * Checks that the snapshot matches the migrations. With a database (Docker or a JDBC URL)
     * the schema is read and the complete snapshot text compared; without one only the
     * migrations hash is compared, and the log says so.
     *
     * @return {@code true} if the snapshot is up to date
     */
    public boolean checkSnapshot(CodegenConfig config, Database database, Path file, Log log) throws SQLException {
        if (file == null || !Files.isRegularFile(file)) {
            log.warn("LxrinQL: the schema snapshot " + file + " does not exist");
            return false;
        }
        String existing = SchemaSnapshot.read(file).toJson();
        if (!database.existing() && !docker.getAsBoolean()) {
            boolean same = SchemaSnapshot.read(file).migrations().equals(database.migrationsHash());
            log.warn("LxrinQL: Docker is not available, so only the migrations hash of " + file + " was compared");
            if (!same) log.warn("LxrinQL: the schema snapshot " + file + " is out of date: the migrations changed");
            return same;
        }
        log.info("LxrinQL: reading the schema from " + database.describe());
        String actual = snapshot(config, database).toJson();
        if (actual.equals(existing)) {
            log.info("LxrinQL: the schema snapshot " + file + " is up to date");
            return true;
        }
        log.warn("LxrinQL: the schema snapshot " + file + " is out of date; " + firstDifference(existing, actual)
                + ". Run lxrinQlSnapshot (Gradle), lxrin-ql:snapshot (Maven) or --write-snapshot and commit the file.");
        return false;
    }

    static String firstDifference(String expected, String actual) {
        String[] a = expected.split("\n", -1);
        String[] b = actual.split("\n", -1);
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            String x = i < a.length ? a[i].trim() : "<end of file>";
            String y = i < b.length ? b[i].trim() : "<end of file>";
            if (!x.equals(y)) return "line " + (i + 1) + " is " + x + " but the migrations give " + y;
        }
        return "the files differ";
    }
}
