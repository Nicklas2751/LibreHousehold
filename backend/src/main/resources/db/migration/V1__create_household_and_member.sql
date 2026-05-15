CREATE TABLE member
(
    id     UUID PRIMARY KEY,
    name   TEXT NOT NULL,
    email  TEXT NOT NULL UNIQUE,
    avatar TEXT
);

CREATE TABLE household
(
    id       UUID PRIMARY KEY,
    name     TEXT NOT NULL,
    image    TEXT,
    admin_id UUID NOT NULL UNIQUE REFERENCES member (id)
);
