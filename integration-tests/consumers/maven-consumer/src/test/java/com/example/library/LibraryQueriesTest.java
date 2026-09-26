package com.example.library;

import com.example.library.db.LibGenre;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LibraryQueriesTest {

    @Test
    void rendersTypedSql() {
        assertEquals("SELECT lib_books.title FROM lib_books WHERE lib_books.genre = ? ORDER BY lib_books.title ASC",
                LibraryQueries.titles(LibGenre.NON_FICTION).render().sql());
        assertEquals("non-fiction", LibGenre.NON_FICTION.label());
    }
}
