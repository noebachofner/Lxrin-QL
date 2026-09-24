package ch.lxrin.ql.exec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Converts SQL with named placeholders ({@code :name}) into JDBC SQL with
 * {@code ?} placeholders plus the ordered parameter values.
 *
 * <p>The parser understands PostgreSQL syntax well enough to leave these
 * untouched: string literals ({@code '...'}, {@code E'...'}), quoted
 * identifiers ({@code "..."}), dollar-quoted strings ({@code $$...$$},
 * {@code $tag$...$tag$}), comments ({@code --} and {@code /* *}{@code /})
 * and the cast operator {@code ::}. A literal {@code ?} (e.g. a jsonb
 * operator) is escaped as {@code ??} for the PostgreSQL JDBC driver.</p>
 *
 * <p>A {@link Collection} value is expanded to {@code ?, ?, ?} so it can be
 * used inside {@code IN (:ids)}.</p>
 */
public final class NamedParameterSql {

    private final String jdbcSql;
    private final List<Object> values;

    private NamedParameterSql(String jdbcSql, List<Object> values) {
        this.jdbcSql = jdbcSql;
        this.values = values;
    }

    /** Returns the SQL with {@code ?} placeholders. */
    public String sql() {
        return jdbcSql;
    }

    /** Returns the parameter values in placeholder order. */
    public List<Object> values() {
        return values;
    }

    /**
     * Parses {@code sql} and resolves each {@code :name} from {@code binds}.
     *
     * @throws IllegalArgumentException if a placeholder has no value
     */
    public static NamedParameterSql parse(String sql, Map<String, Object> binds) {
        StringBuilder out = new StringBuilder(sql.length());
        List<Object> values = new ArrayList<>();
        int n = sql.length();
        int i = 0;
        while (i < n) {
            char c = sql.charAt(i);
            if (c == '\'' ) {
                int end = skipQuoted(sql, i, '\'');
                out.append(sql, i, end);
                i = end;
            } else if (c == '"') {
                int end = skipQuoted(sql, i, '"');
                out.append(sql, i, end);
                i = end;
            } else if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
                int end = sql.indexOf('\n', i);
                end = end < 0 ? n : end;
                out.append(sql, i, end);
                i = end;
            } else if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                int end = sql.indexOf("*/", i + 2);
                end = end < 0 ? n : end + 2;
                out.append(sql, i, end);
                i = end;
            } else if (c == '$' && isDollarQuoteStart(sql, i)) {
                int tagEnd = sql.indexOf('$', i + 1);
                String tag = sql.substring(i, tagEnd + 1);
                int close = sql.indexOf(tag, tagEnd + 1);
                int end = close < 0 ? n : close + tag.length();
                out.append(sql, i, end);
                i = end;
            } else if (c == ':' && i + 1 < n && sql.charAt(i + 1) == ':') {
                out.append("::");
                i += 2;
            } else if (c == ':' && i + 1 < n && Character.isJavaIdentifierStart(sql.charAt(i + 1))) {
                int end = i + 1;
                while (end < n && (Character.isJavaIdentifierPart(sql.charAt(end)) || sql.charAt(end) == '.')) end++;
                String name = sql.substring(i + 1, end);
                if (!binds.containsKey(name)) {
                    throw new IllegalArgumentException("no value bound for placeholder :" + name);
                }
                Object value = binds.get(name);
                if (value instanceof Collection) {
                    Collection<?> col = (Collection<?>) value;
                    if (col.isEmpty()) {
                        out.append("NULL");
                    } else {
                        boolean first = true;
                        for (Object v : col) {
                            if (!first) out.append(", ");
                            out.append('?');
                            values.add(v);
                            first = false;
                        }
                    }
                } else {
                    out.append('?');
                    values.add(value);
                }
                i = end;
            } else if (c == '?') {
                out.append("??");
                i++;
            } else {
                out.append(c);
                i++;
            }
        }
        return new NamedParameterSql(out.toString(), values);
    }

    private static int skipQuoted(String sql, int start, char quote) {
        int i = start + 1;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (c == quote) {
                if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) {
                    i += 2;              // doubled quote
                    continue;
                }
                return i + 1;
            }
            if (c == '\\' && quote == '\'' && start > 0 && Character.toUpperCase(sql.charAt(start - 1)) == 'E') {
                i += 2;                  // backslash escape in E'...'
                continue;
            }
            i++;
        }
        return sql.length();
    }

    private static boolean isDollarQuoteStart(String sql, int i) {
        if (i > 0 && (Character.isJavaIdentifierPart(sql.charAt(i - 1)))) return false;   // e.g. identifier$1
        int j = i + 1;
        while (j < sql.length() && (Character.isLetterOrDigit(sql.charAt(j)) || sql.charAt(j) == '_')) {
            if (j == i + 1 && Character.isDigit(sql.charAt(j))) return false;              // $1 positional
            j++;
        }
        return j < sql.length() && sql.charAt(j) == '$';
    }
}
