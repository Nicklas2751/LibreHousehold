ALTER TABLE account
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN registered_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    ADD COLUMN verification_deletion_warning_sent_at TIMESTAMP WITH TIME ZONE NULL;

CREATE TABLE account_token
(
    id          BIGSERIAL PRIMARY KEY,
    member_id   UUID NOT NULL REFERENCES member (id) ON DELETE CASCADE,
    token       UUID NOT NULL UNIQUE,
    purpose     TEXT NOT NULL, -- 'EMAIL_VERIFICATION' | 'PASSWORD_RESET'
    valid_until TIMESTAMP WITH TIME ZONE NOT NULL
);
