-- Acknowledge step, technician capacity limits and the escalation chain.
ALTER TABLE incidents DROP CONSTRAINT chk_incident_status;
ALTER TABLE incidents ADD CONSTRAINT chk_incident_status CHECK (status IN
    ('REPORTED', 'ASSIGNED', 'ACKNOWLEDGED', 'IN_PROGRESS', 'PENDING_INFO', 'RESOLVED', 'CLOSED', 'CANCELLED'));

ALTER TABLE incidents ADD COLUMN escalation_level VARCHAR(20) NOT NULL DEFAULT 'NONE';
ALTER TABLE incidents ADD CONSTRAINT chk_incident_escalation_level
    CHECK (escalation_level IN ('NONE', 'TEAM_LEAD', 'ADMINISTRATOR', 'IT_MANAGER'));
-- Tickets escalated under the old single-step rule went to administrators.
UPDATE incidents SET escalation_level = 'ADMINISTRATOR' WHERE escalated_at IS NOT NULL;

ALTER TABLE users ADD COLUMN max_active_tickets INT NOT NULL DEFAULT 5;
ALTER TABLE users ADD CONSTRAINT chk_user_max_active_tickets CHECK (max_active_tickets BETWEEN 1 AND 50);
ALTER TABLE users ADD COLUMN is_team_lead BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE users SET is_team_lead = TRUE WHERE username = 'tech_lead';
