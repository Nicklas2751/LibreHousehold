CREATE SCHEMA IF NOT EXISTS expenses;

CREATE TABLE expenses.category (
    id           UUID PRIMARY KEY,
    household_id UUID NOT NULL,
    name         TEXT NOT NULL,
    icon         TEXT
);

CREATE TABLE expenses.expense (
    id           UUID PRIMARY KEY,
    household_id UUID NOT NULL,
    title        TEXT NOT NULL,
    amount       NUMERIC(10,2) NOT NULL,
    paid_by      UUID NOT NULL,
    date         DATE NOT NULL,
    category_id  UUID NOT NULL,
    notes        TEXT
);

CREATE TABLE expenses.expense_split (
    expense_id UUID NOT NULL,
    member_id  UUID NOT NULL
);

CREATE TABLE expenses.reimbursement (
    id           UUID PRIMARY KEY,
    household_id UUID NOT NULL,
    amount       NUMERIC(10,2) NOT NULL,
    creditor_id  UUID NOT NULL,
    debtor_id    UUID NOT NULL,
    status       TEXT NOT NULL DEFAULT 'PENDING',
    notes        TEXT,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW()
);
