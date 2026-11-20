-- Whether the assistant's "Try this first" steps solved a problem without a ticket being filed.
CREATE TABLE self_help_feedback (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    helped BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_self_help_feedback_time ON self_help_feedback(created_at);
