-- Optimistic locking: concurrent edits to the same incident fail loudly instead of overwriting each other.
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
