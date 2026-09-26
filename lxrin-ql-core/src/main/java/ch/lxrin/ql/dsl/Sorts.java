package ch.lxrin.ql.dsl;

import ch.lxrin.ql.error.InvalidSortException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns sort parameters of a request into {@code ORDER BY} items, strictly
 * through a whitelist:
 *
 * <pre>{@code
 * static final Map<String, Field<?>> SORTABLE = Map.of(
 *         "username", USERS.NAME,
 *         "created", USERS.CREATED_AT);
 *
 * selectFrom(USERS).orderBy(Sorts.from(sortParam, SORTABLE)).fetch();   // "username,desc"
 * }</pre>
 *
 * <p>A parameter is {@code key}, {@code key,asc} or {@code key,desc};
 * several are separated by {@code ;} or given as a collection (e.g. the
 * repeated {@code sort} parameters of Spring). Keys are matched exactly.
 * Unknown keys and directions are rejected with an {@link InvalidSortException};
 * nothing from the parameter is ever written into the SQL. A {@code null} or
 * blank parameter means "no sort" and returns an empty list.</p>
 */
public final class Sorts {

    private static final int MAX_ECHO = 40;

    private Sorts() {}

    /** Parses one parameter, possibly with several {@code ;}-separated items. */
    public static List<SortField<?>> from(String param, Map<String, ? extends Field<?>> whitelist) {
        if (param == null || param.isBlank()) return List.of();
        return from(List.of(param.split(";", -1)), whitelist);
    }

    /** Parses one parameter; if it is {@code null} or blank the defaults are returned. */
    public static List<SortField<?>> from(String param, Map<String, ? extends Field<?>> whitelist, SortField<?>... defaults) {
        List<SortField<?>> parsed = from(param, whitelist);
        return parsed.isEmpty() ? List.of(defaults) : parsed;
    }

    /** Parses several parameters, each {@code key[,asc|desc]}. */
    public static List<SortField<?>> from(Collection<String> params, Map<String, ? extends Field<?>> whitelist) {
        if (whitelist == null) throw new IllegalArgumentException("whitelist must not be null");
        List<SortField<?>> result = new ArrayList<>();
        if (params == null) return result;
        for (String p : params) {
            if (p == null || p.isBlank()) {
                if (params.size() > 1) throw new InvalidSortException("empty sort item");
                continue;
            }
            String[] parts = p.trim().split(",", -1);
            if (parts.length > 2) throw new InvalidSortException("invalid sort item: " + echo(p));
            String key = parts[0].trim();
            Field<?> field = whitelist.get(key);
            if (field == null) throw new InvalidSortException("unknown sort key: " + echo(key));
            boolean ascending = true;
            if (parts.length == 2) {
                String dir = parts[1].trim().toLowerCase(Locale.ROOT);
                if (dir.equals("desc")) ascending = false;
                else if (!dir.equals("asc")) throw new InvalidSortException("invalid sort direction: " + echo(parts[1]));
            }
            result.add(ascending ? field.asc() : field.desc());
        }
        return List.copyOf(result);
    }

    private static String echo(String text) {
        String t = text.length() > MAX_ECHO ? text.substring(0, MAX_ECHO) + "…" : text;
        StringBuilder sb = new StringBuilder("'");
        for (char c : t.toCharArray()) sb.append(Character.isISOControl(c) ? '?' : c);
        return sb.append('\'').toString();
    }
}
