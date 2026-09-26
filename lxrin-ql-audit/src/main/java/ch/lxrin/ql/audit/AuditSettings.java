package ch.lxrin.ql.audit;

import ch.lxrin.ql.schema.Table;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What {@link AuditListener} audits and where it writes. The defaults are the layout of
 * Hibernate Envers with a custom revision entity:
 *
 * <pre>
 * revision     (id bigint identity, revised_at timestamptz, user_id …)
 * app_user_aud (id …, rev bigint REFERENCES revision, revtype smallint, name …, PRIMARY KEY (id, rev))
 * </pre>
 *
 * <pre>{@code
 * AuditSettings settings = AuditSettings.builder()
 *         .tables("app_user", "asset")
 *         .excludeColumns("app_user", "last_seen_at")
 *         .user(AuditUser.of(SqlTypes.UUID, CurrentUser::id))
 *         .build();
 * }</pre>
 */
public final class AuditSettings {

    private final Set<String> tables;
    private final List<Pattern> tablePatterns;
    private final Map<String, Set<String>> excludedColumns;
    private final String suffix;
    private final boolean storeDataAtDelete;
    private final String revisionSchema;
    private final String revisionTable;
    private final String revisionIdColumn;
    private final String revisionTimestampColumn;
    private final String revisionUserColumn;
    private final String revColumn;
    private final String revtypeColumn;
    private final AuditUser<?> user;
    private final Clock clock;

    private AuditSettings(Builder b) {
        tables = Set.copyOf(b.tables);
        tablePatterns = List.copyOf(b.tablePatterns);
        Map<String, Set<String>> excluded = new LinkedHashMap<>();
        b.excludedColumns.forEach((t, c) -> excluded.put(t, Set.copyOf(c)));
        excludedColumns = Map.copyOf(excluded);
        suffix = b.suffix;
        storeDataAtDelete = b.storeDataAtDelete;
        revisionSchema = b.revisionSchema;
        revisionTable = b.revisionTable;
        revisionIdColumn = b.revisionIdColumn;
        revisionTimestampColumn = b.revisionTimestampColumn;
        revisionUserColumn = b.revisionUserColumn;
        revColumn = b.revColumn;
        revtypeColumn = b.revtypeColumn;
        user = b.user;
        clock = b.clock;
    }

    /** Starts the settings. */
    public static Builder builder() {
        return new Builder();
    }

    /** Returns {@code true} if writes to {@code table} are audited. */
    public boolean audits(Table<?> table) {
        String name = table.name();
        if (name.endsWith(suffix)) return false;
        if (name.equals(revisionTable) && Objects.equals(table.schema(), revisionSchema)) return false;
        if (tables.contains(name) || tables.contains(qualified(table))) return true;
        for (Pattern p : tablePatterns) if (p.matcher(name).matches() || p.matcher(qualified(table)).matches()) return true;
        return false;
    }

    /** Returns the columns of {@code table} that are not copied to the audit table. */
    public Set<String> excludedColumns(Table<?> table) {
        Set<String> result = new LinkedHashSet<>(excludedColumns.getOrDefault(table.name(), Set.of()));
        result.addAll(excludedColumns.getOrDefault(qualified(table), Set.of()));
        return result;
    }

    private static String qualified(Table<?> table) {
        return (table.schema() == null ? "public" : table.schema()) + "." + table.name();
    }

    /** Returns the audit table suffix, default {@code _aud}. */
    public String suffix() {
        return suffix;
    }

    /** Returns whether delete rows contain the full row (default {@code false}: only the key, as in Envers). */
    public boolean storeDataAtDelete() {
        return storeDataAtDelete;
    }

    /** Returns the schema of the revision table, or {@code null} for the default schema. */
    public String revisionSchema() {
        return revisionSchema;
    }

    /** Returns the revision table, default {@code revision}. */
    public String revisionTable() {
        return revisionTable;
    }

    /** Returns the revision number column, default {@code id}. */
    public String revisionIdColumn() {
        return revisionIdColumn;
    }

    /** Returns the revision timestamp column, default {@code revised_at}. */
    public String revisionTimestampColumn() {
        return revisionTimestampColumn;
    }

    /** Returns the revision user column, default {@code user_id}. */
    public String revisionUserColumn() {
        return revisionUserColumn;
    }

    /** Returns the revision column of audit tables, default {@code rev}. */
    public String revColumn() {
        return revColumn;
    }

    /** Returns the revision type column of audit tables, default {@code revtype}. */
    public String revtypeColumn() {
        return revtypeColumn;
    }

    /** Returns the current user, or {@code null} if the revision stores none. */
    public AuditUser<?> user() {
        return user;
    }

    /** Returns the clock of the revision timestamps. */
    public Clock clock() {
        return clock;
    }

    /** Builds {@link AuditSettings}. */
    public static final class Builder {
        private final Set<String> tables = new LinkedHashSet<>();
        private final List<Pattern> tablePatterns = new ArrayList<>();
        private final Map<String, Set<String>> excludedColumns = new LinkedHashMap<>();
        private String suffix = "_aud";
        private boolean storeDataAtDelete;
        private String revisionSchema;
        private String revisionTable = "revision";
        private String revisionIdColumn = "id";
        private String revisionTimestampColumn = "revised_at";
        private String revisionUserColumn = "user_id";
        private String revColumn = "rev";
        private String revtypeColumn = "revtype";
        private AuditUser<?> user;
        private Clock clock = Clock.systemUTC();

        private Builder() {}

        /** Audits these tables, by name ({@code app_user}) or qualified name ({@code public.app_user}). */
        public Builder tables(String... names) {
            return tables(List.of(names));
        }

        /** Audits these tables. */
        public Builder tables(Collection<String> names) {
            for (String n : names) tables.add(requireName(n, "table"));
            return this;
        }

        /** Audits every table whose name (or qualified name) matches one of these regular expressions. */
        public Builder tablePatterns(String... regexes) {
            return tablePatterns(List.of(regexes));
        }

        /** Audits every table whose name matches one of these regular expressions. */
        public Builder tablePatterns(Collection<String> regexes) {
            for (String r : regexes) tablePatterns.add(Pattern.compile(requireName(r, "table pattern")));
            return this;
        }

        /** Does not copy these columns of {@code table} to its audit table, e.g. {@code last_seen_at}. */
        public Builder excludeColumns(String table, String... columns) {
            return excludeColumns(table, List.of(columns));
        }

        /** Does not copy these columns of {@code table} to its audit table. */
        public Builder excludeColumns(String table, Collection<String> columns) {
            excludedColumns.computeIfAbsent(requireName(table, "table"), k -> new LinkedHashSet<>()).addAll(columns);
            return this;
        }

        /** Sets the audit table suffix (default {@code _aud}). */
        public Builder suffix(String value) {
            suffix = requireName(value, "suffix");
            return this;
        }

        /** Stores the full row in delete rows instead of only the key (default {@code false}). */
        public Builder storeDataAtDelete(boolean value) {
            storeDataAtDelete = value;
            return this;
        }

        /** Sets the schema of the revision table (default: the default schema). */
        public Builder revisionSchema(String value) {
            revisionSchema = value;
            return this;
        }

        /** Sets the revision table (default {@code revision}). */
        public Builder revisionTable(String value) {
            revisionTable = requireName(value, "revision table");
            return this;
        }

        /** Sets the revision number column (default {@code id}); it must be generated by the database. */
        public Builder revisionIdColumn(String value) {
            revisionIdColumn = requireName(value, "revision id column");
            return this;
        }

        /** Sets the revision timestamp column (default {@code revised_at}, {@code timestamptz}). */
        public Builder revisionTimestampColumn(String value) {
            revisionTimestampColumn = requireName(value, "revision timestamp column");
            return this;
        }

        /** Sets the revision user column (default {@code user_id}). */
        public Builder revisionUserColumn(String value) {
            revisionUserColumn = requireName(value, "revision user column");
            return this;
        }

        /** Sets the revision column of the audit tables (default {@code rev}). */
        public Builder revColumn(String value) {
            revColumn = requireName(value, "rev column");
            return this;
        }

        /** Sets the revision type column of the audit tables (default {@code revtype}). */
        public Builder revtypeColumn(String value) {
            revtypeColumn = requireName(value, "revtype column");
            return this;
        }

        /** Sets the current user stored in each revision; without one the user column is not written. */
        public Builder user(AuditUser<?> value) {
            user = value;
            return this;
        }

        /** Sets the clock of the revision timestamps (default UTC system clock). */
        public Builder clock(Clock value) {
            clock = Objects.requireNonNull(value, "clock");
            return this;
        }

        /** Returns the settings. */
        public AuditSettings build() {
            return new AuditSettings(this);
        }

        private static String requireName(String value, String what) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException(what + " must not be empty");
            return value.trim();
        }
    }
}
