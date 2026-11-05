CREATE TABLE household
(
    id    UUID PRIMARY KEY,
    name  TEXT NOT NULL,
    image TEXT
);

CREATE TABLE member
(
    id           UUID PRIMARY KEY,
    name         TEXT NOT NULL,
    email        TEXT NOT NULL UNIQUE,
    avatar       TEXT,
    household_id UUID NOT NULL REFERENCES household (id),
    is_admin     BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE invite
(
    id           BIGSERIAL PRIMARY KEY,
    household_id UUID NOT NULL REFERENCES household (id),
    token        UUID NOT NULL UNIQUE,
    valid_until  DATE NOT NULL
);

CREATE TABLE account
(
    member_id                            UUID PRIMARY KEY REFERENCES member (id) ON DELETE CASCADE,
    password_hash                        TEXT NULL, -- NULL for accounts that sign in via social login only
    email_verified                       BOOLEAN NOT NULL DEFAULT FALSE,
    registered_at                        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    verification_deletion_warning_sent_at TIMESTAMP WITH TIME ZONE NULL,
    locked_until                         TIMESTAMP WITH TIME ZONE NULL,
    failed_login_attempts                INT NOT NULL DEFAULT 0
);

CREATE TABLE account_token
(
    id          BIGSERIAL PRIMARY KEY,
    member_id   UUID NOT NULL REFERENCES member (id) ON DELETE CASCADE,
    token       UUID NOT NULL UNIQUE,
    purpose     TEXT NOT NULL, -- 'EMAIL_VERIFICATION' | 'PASSWORD_RESET'
    valid_until TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE account_identity
(
    id               BIGSERIAL PRIMARY KEY,
    member_id        UUID NOT NULL REFERENCES member (id) ON DELETE CASCADE,
    provider_id      TEXT NOT NULL,
    external_subject TEXT NOT NULL,
    linked_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (provider_id, external_subject)
);
