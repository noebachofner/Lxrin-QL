package ch.lxrin.ql.dsl;

import java.util.List;
import java.util.Optional;

/**
 * A page of a keyset-paginated query.
 *
 * @param items the rows of this page
 * @param next  the cursor for the next page, or {@code null} on the last page
 * @param <R>   the row type
 */
public record Page<R>(List<R> items, Cursor next) {

    /** Creates a page with an immutable copy of the items. */
    public Page {
        items = List.copyOf(items);
    }

    /** Returns the cursor for the next page, if there is one. */
    public Optional<Cursor> nextCursor() {
        return Optional.ofNullable(next);
    }

    /** Returns {@code true} if another page may follow. */
    public boolean hasNext() {
        return next != null;
    }
}
