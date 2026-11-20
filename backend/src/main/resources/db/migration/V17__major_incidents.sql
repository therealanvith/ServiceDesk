-- Major incidents: tickets can be linked under a parent; resolving the parent resolves them.
ALTER TABLE incidents ADD COLUMN parent_incident_id UUID REFERENCES incidents(id) ON DELETE SET NULL;
ALTER TABLE incidents ADD CONSTRAINT chk_incident_not_own_parent CHECK (parent_incident_id <> id);
CREATE INDEX idx_incidents_parent ON incidents(parent_incident_id);
