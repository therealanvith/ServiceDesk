-- ==============================================================================
-- Flyway Database Migration: V3 Create Incidents, Comments and Audit Logs
-- Project: ServiceDesk
-- Description: Central incident aggregate, SLA monitoring, discussions, and audit ledger
-- ==============================================================================

CREATE TABLE incidents (
    id UUID PRIMARY KEY,
    incident_number VARCHAR(20) NOT NULL UNIQUE,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    category VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL,
    priority VARCHAR(30) NOT NULL,
    impact VARCHAR(30) NOT NULL,
    urgency VARCHAR(30) NOT NULL,
    reporter_id UUID NOT NULL REFERENCES users(id),
    assignee_id UUID REFERENCES users(id),
    response_deadline TIMESTAMPTZ NOT NULL,
    resolution_deadline TIMESTAMPTZ NOT NULL,
    paused_at TIMESTAMPTZ,
    paused_duration_seconds BIGINT NOT NULL DEFAULT 0,
    sla_status VARCHAR(30) NOT NULL DEFAULT 'WITHIN_SLA',
    resolution_notes TEXT,
    resolved_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_incident_category CHECK (category IN ('NETWORK', 'HARDWARE', 'SOFTWARE', 'ACCESS')),
    CONSTRAINT chk_incident_status CHECK (status IN ('REPORTED', 'ASSIGNED', 'IN_PROGRESS', 'PENDING_INFO', 'RESOLVED', 'CLOSED', 'CANCELLED')),
    CONSTRAINT chk_incident_priority CHECK (priority IN ('P1_CRITICAL', 'P2_HIGH', 'P3_MEDIUM', 'P4_LOW')),
    CONSTRAINT chk_incident_impact CHECK (impact IN ('HIGH', 'MEDIUM', 'LOW')),
    CONSTRAINT chk_incident_urgency CHECK (urgency IN ('HIGH', 'MEDIUM', 'LOW')),
    CONSTRAINT chk_incident_sla_status CHECK (sla_status IN ('WITHIN_SLA', 'WARNING_BREACH_IMMINENT', 'BREACHED'))
);

CREATE TABLE incident_comments (
    id UUID PRIMARY KEY,
    incident_id UUID NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES users(id),
    comment_text TEXT NOT NULL,
    is_internal BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE incident_audit_logs (
    id UUID PRIMARY KEY,
    incident_id UUID NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    actor_id UUID REFERENCES users(id),
    action VARCHAR(50) NOT NULL,
    old_value VARCHAR(100),
    new_value VARCHAR(100),
    remarks TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Optimized Performance Indexes
CREATE INDEX idx_incidents_status_prio ON incidents(status, priority);
CREATE INDEX idx_incidents_assignee ON incidents(assignee_id);
CREATE INDEX idx_incidents_reporter ON incidents(reporter_id);
CREATE INDEX idx_incidents_sla ON incidents(sla_status, resolution_deadline);
CREATE INDEX idx_incidents_category ON incidents(category);
CREATE INDEX idx_comments_incident ON incident_comments(incident_id, created_at ASC);
CREATE INDEX idx_audit_incident_time ON incident_audit_logs(incident_id, created_at ASC);
