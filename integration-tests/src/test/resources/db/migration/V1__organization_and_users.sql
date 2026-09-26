-- An ISMS-like schema used by the integration tests. The code in ch.lxrin.ql.it.db is generated from it.

CREATE TYPE app_role AS ENUM ('admin', 'user', 'auditor');

COMMENT ON TYPE app_role IS 'Roles of application users';

CREATE TABLE app_user (
    id              uuid PRIMARY KEY,
    name            text        NOT NULL,
    email           text        NOT NULL CONSTRAINT app_user_email_key UNIQUE,
    role            app_role    NOT NULL DEFAULT 'user',
    active          boolean     NOT NULL DEFAULT true,
    tags            text[]      NOT NULL DEFAULT '{}',
    settings        jsonb,
    organization_id uuid        NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    created_by      text,
    updated_at      timestamptz,
    updated_by      text,
    deleted_at      timestamptz,
    version         bigint      NOT NULL DEFAULT 0
);

COMMENT ON TABLE app_user IS 'People who can sign in';
COMMENT ON COLUMN app_user.email IS 'Unique e-mail address <used for login>';
