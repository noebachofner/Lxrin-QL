package com.example.blog;

import com.example.blog.db.Post;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.example.blog.db.Tables.POSTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlogQueriesTest {

    @Test
    void rendersTypedSql() {
        assertEquals("SELECT blog_posts.title FROM blog_posts JOIN blog_authors ON blog_posts.reviewer_id = blog_authors.id "
                        + "WHERE (blog_posts.reviewer_id = ? AND blog_posts.status = ?)",
                BlogQueries.reviewedBy(new AuthorId(UUID.randomUUID())).render().sql());
    }

    @Test
    void foreignKeysAreNamedAfterTheirColumnsOrTheConfiguration() {
        assertEquals("blog_posts_author_id_fkey", POSTS.FK_AUTHOR_ID.name());
        assertEquals("blog_posts_reviewer_id_fkey", POSTS.FK_REVIEWER.name());
    }

    @Test
    void entitiesAreGenerated() {
        Post post = new Post();
        post.setTitle("Hello");
        assertTrue(post.isNew());
    }
}
