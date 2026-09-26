-- Audit history in the style of the ISMS: one revision per transaction, one <table>_aud row per change.
CREATE TABLE revision (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    created_at timestamptz NOT NULL,
    created_by text        NOT NULL
);

CREATE TABLE app_user_aud (
    rev             bigint NOT NULL REFERENCES revision (id),
    revtype         text   NOT NULL,
    id              uuid   NOT NULL,
    name            text,
    email           text,
    role            app_role,
    active          boolean,
    tags            text[],
    settings        jsonb,
    organization_id uuid,
    created_at      timestamptz,
    created_by      text,
    updated_at      timestamptz,
    updated_by      text,
    deleted_at      timestamptz,
    version         bigint,
    PRIMARY KEY (rev, id)
);

CREATE TABLE asset_aud (
    rev             bigint NOT NULL REFERENCES revision (id),
    revtype         text   NOT NULL,
    id              bigint NOT NULL,
    owner_id        uuid,
    name            text,
    classification  text,
    value           numeric(12, 2),
    organization_id uuid,
    created_at      timestamptz,
    created_by      text,
    updated_at      timestamptz,
    updated_by      text,
    deleted_at      timestamptz,
    version         bigint,
    PRIMARY KEY (rev, id)
);
