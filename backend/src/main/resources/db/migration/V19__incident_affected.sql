-- People who said "this affects me too" on an open issue, instead of filing a duplicate ticket.
CREATE TABLE incident_affected (
    incident_id UUID NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (incident_id, user_id)
);

CREATE INDEX idx_incident_affected_user ON incident_affected(user_id);
