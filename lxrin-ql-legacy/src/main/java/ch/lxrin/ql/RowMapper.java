package ch.lxrin.ql;

/**
 * Maps a single result row (an {@code Object[]} column array) to a typed
 * domain object.
 *
 * <pre>{@code
 * RowMapper<MyBean> mapper = row -> {
 *     MyBean b = new MyBean();
 *     b.setId((Long) row[0]);
 *     b.setName((String) row[1]);
 *     return b;
 * };
 * }</pre>
 *
 * @param <T> the target type
 */
@FunctionalInterface
public interface RowMapper<T> {

    /**
     * Maps a result row to {@code T}.
     *
     * @param row column values for the current row (order matches the
     *            {@code SELECT} columns)
     * @return mapped object
     */
    T map(Object[] row);
}
