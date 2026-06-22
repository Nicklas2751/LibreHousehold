CREATE SCHEMA IF NOT EXISTS tasks;

CREATE TABLE tasks.task
(
    id                  UUID    PRIMARY KEY,
    household_id        UUID    NOT NULL,
    assigned_to         UUID,
    title               TEXT    NOT NULL,
    description         TEXT,
    due_date            DATE    NOT NULL,
    recurring           BOOLEAN NOT NULL DEFAULT FALSE,
    recurrence_unit     TEXT,
    recurrence_interval INTEGER
);

CREATE TABLE tasks.task_completion
(
    id        UUID PRIMARY KEY,
    task_id   UUID NOT NULL REFERENCES tasks.task (id) ON DELETE CASCADE,
    done_by   UUID NOT NULL,
    done_date DATE NOT NULL
);

CREATE INDEX idx_task_completion_task_id ON tasks.task_completion (task_id);
