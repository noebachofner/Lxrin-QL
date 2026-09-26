package com.example.blog;

import ch.lxrin.ql.QL;
import ch.lxrin.ql.dsl.Select;

import static ch.lxrin.ql.QL.*;
import static com.example.blog.db.Tables.*;

/** Queries against the generated tables. */
public final class BlogQueries {

    private BlogQueries() {
    }

    /** Titles of published posts reviewed by an author. */
    public static Select<String> reviewedBy(AuthorId reviewer) {
        return QL.createContribution(String.class, POSTS, (c, b) -> c
                .select(col(POSTS.TITLE))
                .join(AUTHORS).onKey(POSTS.FK_REVIEWER)
                .where(and(
                        eq(POSTS.REVIEWER_ID, reviewer),
                        eq(POSTS.STATUS, Status.PUBLISHED))));
    }
}
