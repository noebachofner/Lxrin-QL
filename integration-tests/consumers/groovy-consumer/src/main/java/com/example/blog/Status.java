package com.example.blog;

/** The status of a post; mapped to the PostgreSQL enum blog_status. */
public enum Status {
    /** Not visible yet. */
    DRAFT,
    /** Visible. */
    PUBLISHED
}
