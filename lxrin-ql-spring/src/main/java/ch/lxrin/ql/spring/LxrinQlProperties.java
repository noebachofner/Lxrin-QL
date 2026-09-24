package ch.lxrin.ql.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Configuration properties {@code lxrin.ql.*}. */
@ConfigurationProperties("lxrin.ql")
public class LxrinQlProperties {

    /** Name of the optimistic-locking version column, e.g. {@code version}; none if empty. */
    private String versionColumn;
    /** Maximum rows per multi-row insert and per JDBC batch. */
    private int batchSize = 1000;
    /** JDBC fetch size of streamed queries. */
    private int fetchSize = 500;
    /** Whether the context becomes the default for the static DSL and BEANS delegates to Spring. */
    private boolean registerDefault = true;
    /** Logging of statements. */
    private final Logging logging = new Logging();

    /** Returns the version column. */
    public String getVersionColumn() {
        return versionColumn;
    }

    /** Sets the version column. */
    public void setVersionColumn(String versionColumn) {
        this.versionColumn = versionColumn;
    }

    /** Returns the batch size. */
    public int getBatchSize() {
        return batchSize;
    }

    /** Sets the batch size. */
    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    /** Returns the fetch size. */
    public int getFetchSize() {
        return fetchSize;
    }

    /** Sets the fetch size. */
    public void setFetchSize(int fetchSize) {
        this.fetchSize = fetchSize;
    }

    /** Returns whether the default context and BEANS delegation are registered. */
    public boolean isRegisterDefault() {
        return registerDefault;
    }

    /** Sets whether the default context and BEANS delegation are registered. */
    public void setRegisterDefault(boolean registerDefault) {
        this.registerDefault = registerDefault;
    }

    /** Returns the logging settings. */
    public Logging getLogging() {
        return logging;
    }

    /** Statement logging through {@code System.Logger} (logger {@code ch.lxrin.ql.sql}). */
    public static class Logging {
        /** Whether statements are logged. */
        private boolean enabled = true;
        /** Statements slower than this are logged as warnings. */
        private Duration slowThreshold = Duration.ofMillis(500);
        /** Whether bind values are logged (sensitive values are always redacted). */
        private boolean binds = false;

        /** Returns whether logging is enabled. */
        public boolean isEnabled() {
            return enabled;
        }

        /** Enables or disables logging. */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /** Returns the slow threshold. */
        public Duration getSlowThreshold() {
            return slowThreshold;
        }

        /** Sets the slow threshold. */
        public void setSlowThreshold(Duration slowThreshold) {
            this.slowThreshold = slowThreshold;
        }

        /** Returns whether binds are logged. */
        public boolean isBinds() {
            return binds;
        }

        /** Sets whether binds are logged. */
        public void setBinds(boolean binds) {
            this.binds = binds;
        }
    }
}
