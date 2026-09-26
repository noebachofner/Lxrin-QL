-- Who-columns holding the user's UUID, filled by ColumnConventions.createdBy/updatedBy from the security context.
CREATE TABLE document (
    id         uuid PRIMARY KEY,
    title      text        NOT NULL,
    created_by uuid,
    updated_by uuid
);
