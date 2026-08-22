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
    member_id     UUID PRIMARY KEY REFERENCES member (id) ON DELETE CASCADE,
    password_hash TEXT NOT NULL
);

-- Spring Authorization Server schema (see JdbcRegisteredClientRepository /
-- JdbcOAuth2AuthorizationService / JdbcOAuth2AuthorizationConsentService), copied verbatim from
-- the official oauth2-*-schema.sql files shipped in the spring-security-oauth2-authorization-server
-- jar, only adapted for PostgreSQL as instructed by the comment header of those files
-- (blob -> text, timestamp -> timestamp with time zone).
CREATE TABLE oauth2_registered_client
(
    id                             VARCHAR(100)                           NOT NULL,
    client_id                      VARCHAR(100)                           NOT NULL,
    client_id_issued_at            TIMESTAMP WITH TIME ZONE DEFAULT NOW()  NOT NULL,
    client_secret                  VARCHAR(200)             DEFAULT NULL,
    client_secret_expires_at       TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    client_name                    VARCHAR(200)                           NOT NULL,
    client_authentication_methods  VARCHAR(1000)                          NOT NULL,
    authorization_grant_types      VARCHAR(1000)                          NOT NULL,
    redirect_uris                  VARCHAR(1000)            DEFAULT NULL,
    post_logout_redirect_uris      VARCHAR(1000)            DEFAULT NULL,
    scopes                         VARCHAR(1000)                          NOT NULL,
    client_settings                VARCHAR(2000)                          NOT NULL,
    token_settings                 VARCHAR(2000)                          NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE oauth2_authorization
(
    id                             VARCHAR(100)                           NOT NULL,
    registered_client_id           VARCHAR(100)                           NOT NULL,
    principal_name                 VARCHAR(200)                           NOT NULL,
    authorization_grant_type       VARCHAR(100)                           NOT NULL,
    authorized_scopes              VARCHAR(1000)            DEFAULT NULL,
    attributes                     TEXT                     DEFAULT NULL,
    state                          VARCHAR(500)             DEFAULT NULL,
    authorization_code_value       TEXT                     DEFAULT NULL,
    authorization_code_issued_at   TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    authorization_code_expires_at  TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    authorization_code_metadata    TEXT                     DEFAULT NULL,
    access_token_value             TEXT                     DEFAULT NULL,
    access_token_issued_at         TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    access_token_expires_at        TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    access_token_metadata          TEXT                     DEFAULT NULL,
    access_token_type              VARCHAR(100)             DEFAULT NULL,
    access_token_scopes            VARCHAR(1000)            DEFAULT NULL,
    oidc_id_token_value            TEXT                     DEFAULT NULL,
    oidc_id_token_issued_at        TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    oidc_id_token_expires_at       TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    oidc_id_token_metadata         TEXT                     DEFAULT NULL,
    refresh_token_value            TEXT                     DEFAULT NULL,
    refresh_token_issued_at        TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    refresh_token_expires_at       TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    refresh_token_metadata         TEXT                     DEFAULT NULL,
    user_code_value                TEXT                     DEFAULT NULL,
    user_code_issued_at            TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    user_code_expires_at           TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    user_code_metadata              TEXT                    DEFAULT NULL,
    device_code_value              TEXT                     DEFAULT NULL,
    device_code_issued_at          TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    device_code_expires_at         TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    device_code_metadata           TEXT                     DEFAULT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE oauth2_authorization_consent
(
    registered_client_id VARCHAR(100)  NOT NULL,
    principal_name        VARCHAR(200) NOT NULL,
    authorities           VARCHAR(1000) NOT NULL,
    PRIMARY KEY (registered_client_id, principal_name)
);
