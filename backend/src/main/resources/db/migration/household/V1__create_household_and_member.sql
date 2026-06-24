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
