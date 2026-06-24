CREATE SCHEMA IF NOT EXISTS auth;

CREATE TABLE auth.account
(
    id            UUID    PRIMARY KEY,
    email         VARCHAR NOT NULL UNIQUE,
    password_hash VARCHAR
);

CREATE TABLE auth.federated_identity
(
    id           UUID    PRIMARY KEY,
    account_id   UUID    NOT NULL REFERENCES auth.account (id),
    provider     VARCHAR NOT NULL,
    provider_sub VARCHAR NOT NULL,
    UNIQUE (provider, provider_sub)
);
