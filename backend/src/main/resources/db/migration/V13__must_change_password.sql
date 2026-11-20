-- Accounts created by an administrator get a temporary password that must be replaced on first
-- sign-in; the API refuses other requests until it is.
ALTER TABLE users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
