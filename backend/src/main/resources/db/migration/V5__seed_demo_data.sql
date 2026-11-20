-- ==============================================================================
-- Flyway Database Migration: V5 Seed Demo Data
-- Project: ServiceDesk
-- Description: Pre-seeded users, skills, and historical incidents for evaluation
-- All default user accounts have password: password123
-- ==============================================================================

-- 1. Insert Pre-seeded Demo Users (BCrypt hash for 'password123')
INSERT INTO users (id, username, password_hash, full_name, email, role, is_active, created_at, updated_at) VALUES
('11111111-1111-1111-1111-111111111111', 'employee1', '$2a$10$eACCYoNO38qC46qx2Gs95uMTq3QJHWNaQdA8LypG9z42d/iqW2Kgy', 'Prof. Alan Turing', 'aturing@campus.edu', 'EMPLOYEE', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('22222222-2222-2222-2222-222222222222', 'tech_network', '$2a$10$eACCYoNO38qC46qx2Gs95uMTq3QJHWNaQdA8LypG9z42d/iqW2Kgy', 'Alice Smith (Network Specialist)', 'alice@servicedesk.edu', 'TECHNICIAN', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('33333333-3333-3333-3333-333333333333', 'tech_hardware', '$2a$10$eACCYoNO38qC46qx2Gs95uMTq3QJHWNaQdA8LypG9z42d/iqW2Kgy', 'Bob Jones (Hardware Tech)', 'bob@servicedesk.edu', 'TECHNICIAN', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('44444444-4444-4444-4444-444444444444', 'tech_lead', '$2a$10$eACCYoNO38qC46qx2Gs95uMTq3QJHWNaQdA8LypG9z42d/iqW2Kgy', 'Charlie Brown (Lead Systems Tech)', 'charlie@servicedesk.edu', 'TECHNICIAN', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('55555555-5555-5555-5555-555555555555', 'admin', '$2a$10$eACCYoNO38qC46qx2Gs95uMTq3QJHWNaQdA8LypG9z42d/iqW2Kgy', 'System Administrator', 'admin@servicedesk.edu', 'ADMINISTRATOR', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 2. Insert Technician Skills
INSERT INTO user_skills (id, user_id, category) VALUES
('a1111111-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222', 'NETWORK'),
('a1111111-0000-0000-0000-000000000002', '33333333-3333-3333-3333-333333333333', 'HARDWARE'),
('a1111111-0000-0000-0000-000000000003', '44444444-4444-4444-4444-444444444444', 'NETWORK'),
('a1111111-0000-0000-0000-000000000004', '44444444-4444-4444-4444-444444444444', 'HARDWARE'),
('a1111111-0000-0000-0000-000000000005', '44444444-4444-4444-4444-444444444444', 'SOFTWARE'),
('a1111111-0000-0000-0000-000000000006', '44444444-4444-4444-4444-444444444444', 'ACCESS');

-- 3. Historical Resolved Ticket (for Jaccard Similarity Detection Demo)
INSERT INTO incidents (
    id, incident_number, title, description, category, status, priority, impact, urgency,
    reporter_id, assignee_id, response_deadline, resolution_deadline, paused_duration_seconds,
    sla_status, resolution_notes, resolved_at, closed_at, created_at, updated_at
) VALUES (
    'e7b0a880-92a0-4b89-a5e4-cb911a3b0842',
    'INC-0842',
    'Lab 304 switch port trunking issue after reboot',
    'Workstations in Lab 304 could not reach gateway after campus maintenance.',
    'NETWORK',
    'CLOSED',
    'P1_CRITICAL',
    'HIGH',
    'HIGH',
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    CURRENT_TIMESTAMP - INTERVAL '2 days',
    CURRENT_TIMESTAMP - INTERVAL '2 days' + INTERVAL '2 hours',
    0,
    'WITHIN_SLA',
    'VLAN 40 configuration re-applied on switch SW-C304 port 24 and DHCP relay restarted.',
    CURRENT_TIMESTAMP - INTERVAL '47 hours',
    CURRENT_TIMESTAMP - INTERVAL '46 hours',
    CURRENT_TIMESTAMP - INTERVAL '2 days',
    CURRENT_TIMESTAMP - INTERVAL '46 hours'
);

INSERT INTO incident_audit_logs (id, incident_id, actor_id, action, old_value, new_value, remarks, created_at) VALUES
('b1111111-0000-0000-0000-000000000001', 'e7b0a880-92a0-4b89-a5e4-cb911a3b0842', '11111111-1111-1111-1111-111111111111', 'CREATED', NULL, 'REPORTED', 'Ticket initially logged by employee', CURRENT_TIMESTAMP - INTERVAL '2 days'),
('b1111111-0000-0000-0000-000000000002', 'e7b0a880-92a0-4b89-a5e4-cb911a3b0842', '22222222-2222-2222-2222-222222222222', 'STATUS_CHANGED', 'REPORTED', 'RESOLVED', 'Resolution applied', CURRENT_TIMESTAMP - INTERVAL '47 hours'),
('b1111111-0000-0000-0000-000000000003', 'e7b0a880-92a0-4b89-a5e4-cb911a3b0842', '11111111-1111-1111-1111-111111111111', 'STATUS_CHANGED', 'RESOLVED', 'CLOSED', 'Reporter verified solution', CURRENT_TIMESTAMP - INTERVAL '46 hours');
