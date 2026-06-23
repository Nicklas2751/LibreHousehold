CREATE SCHEMA IF NOT EXISTS usersettings;

CREATE TABLE usersettings.user_preferences (
    member_id UUID PRIMARY KEY,
    theme     TEXT,
    language  TEXT
);
