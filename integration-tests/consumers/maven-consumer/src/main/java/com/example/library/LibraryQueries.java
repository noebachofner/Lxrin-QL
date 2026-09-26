package com.example.library;

import ch.lxrin.ql.dsl.Select1;
import com.example.library.db.LibGenre;

import static ch.lxrin.ql.dsl.Dsl.*;
import static com.example.library.db.Tables.*;

/** Queries against the generated tables. */
public final class LibraryQueries {

    private LibraryQueries() {}

    /** Titles of a genre. */
    public static Select1<String> titles(LibGenre genre) {
        return select(BOOKS.TITLE).from(BOOKS).where(BOOKS.GENRE.eq(genre)).orderBy(BOOKS.TITLE.asc());
    }
}
