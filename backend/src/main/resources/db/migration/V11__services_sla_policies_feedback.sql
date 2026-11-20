-- IT service catalogue, configurable SLA targets, wider categories, response/escalation tracking
-- and reporter feedback.

CREATE TABLE it_services (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    criticality VARCHAR(20) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_service_criticality CHECK (criticality IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

-- Starting catalogue; administrators can edit, add or retire services from the Settings page.
INSERT INTO it_services (id, name, description, criticality) VALUES
('5e000000-0000-0000-0000-000000000001', 'Payment Application', 'Fee payments and online transactions', 'CRITICAL'),
('5e000000-0000-0000-0000-000000000002', 'Authentication (SSO)', 'Single sign-on, accounts and passwords', 'CRITICAL'),
('5e000000-0000-0000-0000-000000000003', 'Campus Network', 'Wired network, Wi-Fi and VPN', 'HIGH'),
('5e000000-0000-0000-0000-000000000004', 'Email and Collaboration', 'Mail, calendars and chat', 'HIGH'),
('5e000000-0000-0000-0000-000000000005', 'Database Platform', 'Shared database servers', 'HIGH'),
('5e000000-0000-0000-0000-000000000006', 'Learning Management System', 'Course content and submissions', 'HIGH'),
('5e000000-0000-0000-0000-000000000007', 'Cloud Storage', 'File storage and sharing', 'MEDIUM'),
('5e000000-0000-0000-0000-000000000008', 'End-user Devices', 'Desktops, laptops, printers and projectors', 'MEDIUM');

ALTER TABLE incidents ADD COLUMN service_id UUID REFERENCES it_services(id);
ALTER TABLE incidents ADD COLUMN responded_at TIMESTAMPTZ;
ALTER TABLE incidents ADD COLUMN escalated_at TIMESTAMPTZ;
ALTER TABLE incidents ADD COLUMN feedback_rating SMALLINT;
ALTER TABLE incidents ADD COLUMN feedback_comment TEXT;
ALTER TABLE incidents ADD CONSTRAINT chk_incident_feedback_rating
    CHECK (feedback_rating IS NULL OR feedback_rating BETWEEN 1 AND 5);
CREATE INDEX idx_incidents_service ON incidents(service_id);
CREATE INDEX idx_incidents_created ON incidents(created_at);

ALTER TABLE incidents DROP CONSTRAINT IF EXISTS chk_incident_category;
ALTER TABLE incidents ADD CONSTRAINT chk_incident_category CHECK (category IN
    ('NETWORK', 'HARDWARE', 'SOFTWARE', 'ACCESS', 'DATABASE', 'CLOUD', 'SECURITY', 'APPLICATION', 'STORAGE'));
ALTER TABLE user_skills DROP CONSTRAINT IF EXISTS chk_skill_category;
ALTER TABLE user_skills ADD CONSTRAINT chk_skill_category CHECK (category IN
    ('NETWORK', 'HARDWARE', 'SOFTWARE', 'ACCESS', 'DATABASE', 'CLOUD', 'SECURITY', 'APPLICATION', 'STORAGE'));

CREATE TABLE sla_policies (
    priority VARCHAR(30) PRIMARY KEY,
    response_minutes INTEGER NOT NULL,
    resolution_minutes INTEGER NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_sla_priority CHECK (priority IN ('P1_CRITICAL', 'P2_HIGH', 'P3_MEDIUM', 'P4_LOW')),
    CONSTRAINT chk_sla_positive CHECK (response_minutes > 0 AND resolution_minutes >= response_minutes)
);

INSERT INTO sla_policies (priority, response_minutes, resolution_minutes) VALUES
('P1_CRITICAL', 5, 30),
('P2_HIGH', 15, 120),
('P3_MEDIUM', 60, 480),
('P4_LOW', 240, 1440);
