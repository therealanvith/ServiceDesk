-- ==============================================================================
-- ServiceDesk: Seed / Reset Demo Users and Skills
-- Description: Idempotent script to insert or update pre-seeded demo accounts
--              All passwords are set to 'password123'
--
-- How to run:
--   Via Docker Compose:
--     docker compose exec -T postgres psql -U servicedesk_user -d servicedesk < scripts/seed-demo-users.sql
--   Via Local psql:
--     psql -U servicedesk_user -d servicedesk -f scripts/seed-demo-users.sql
-- ==============================================================================

-- 1. Insert or update users with verified BCrypt hash for 'password123'
INSERT INTO users (id, username, password_hash, full_name, email, role, is_active, created_at, updated_at) VALUES
('11111111-1111-1111-1111-111111111111', 'employee1', '$2a$10$shBLxpv2h/YBF/8AH36RwO5uep8WsQJvKSwPevIiQpH9lXPaBPv02', 'Prof. Alan Turing', 'aturing@campus.edu', 'EMPLOYEE', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('22222222-2222-2222-2222-222222222222', 'tech_network', '$2a$10$shBLxpv2h/YBF/8AH36RwO5uep8WsQJvKSwPevIiQpH9lXPaBPv02', 'Alice Smith (Network Specialist)', 'alice@servicedesk.edu', 'TECHNICIAN', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('33333333-3333-3333-3333-333333333333', 'tech_hardware', '$2a$10$shBLxpv2h/YBF/8AH36RwO5uep8WsQJvKSwPevIiQpH9lXPaBPv02', 'Bob Jones (Hardware Tech)', 'bob@servicedesk.edu', 'TECHNICIAN', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('44444444-4444-4444-4444-444444444444', 'tech_lead', '$2a$10$shBLxpv2h/YBF/8AH36RwO5uep8WsQJvKSwPevIiQpH9lXPaBPv02', 'Charlie Brown (Lead Systems Tech)', 'charlie@servicedesk.edu', 'TECHNICIAN', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('55555555-5555-5555-5555-555555555555', 'admin', '$2a$10$shBLxpv2h/YBF/8AH36RwO5uep8WsQJvKSwPevIiQpH9lXPaBPv02', 'System Administrator', 'admin@servicedesk.edu', 'ADMINISTRATOR', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE SET
  password_hash = EXCLUDED.password_hash,
  is_active = TRUE,
  updated_at = CURRENT_TIMESTAMP;

-- 2. Insert technician skills if missing
INSERT INTO user_skills (id, user_id, category) VALUES
('a1111111-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222', 'NETWORK'),
('a1111111-0000-0000-0000-000000000002', '33333333-3333-3333-3333-333333333333', 'HARDWARE'),
('a1111111-0000-0000-0000-000000000003', '44444444-4444-4444-4444-444444444444', 'NETWORK'),
('a1111111-0000-0000-0000-000000000004', '44444444-4444-4444-4444-444444444444', 'HARDWARE'),
('a1111111-0000-0000-0000-000000000005', '44444444-4444-4444-4444-444444444444', 'SOFTWARE'),
('a1111111-0000-0000-0000-000000000006', '44444444-4444-4444-4444-444444444444', 'ACCESS')
ON CONFLICT (id) DO NOTHING;

-- 3. Ensure sequence exists for persistent incident numbers
CREATE SEQUENCE IF NOT EXISTS incident_number_seq START WITH 1001;
SELECT setval(
    'incident_number_seq',
    GREATEST(1000, COALESCE(
        (SELECT MAX(SUBSTRING(incident_number FROM 5)::BIGINT)
         FROM incidents WHERE incident_number ~ '^INC-[0-9]+$'), 1000)),
    true
);
