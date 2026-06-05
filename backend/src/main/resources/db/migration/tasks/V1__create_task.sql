CREATE SCHEMA IF NOT EXISTS tasks;

CREATE TABLE tasks.task
(
    id                  UUID    PRIMARY KEY,
    household_id        UUID    NOT NULL,
    assigned_to         UUID,
    title               TEXT    NOT NULL,
    description         TEXT,
    due_date            DATE    NOT NULL,
    done                DATE,
    recurring           BOOLEAN NOT NULL DEFAULT FALSE,
    recurrence_unit     TEXT,
    recurrence_interval INTEGER
);
