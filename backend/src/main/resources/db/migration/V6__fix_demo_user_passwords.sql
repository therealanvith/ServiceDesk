-- ==============================================================================
-- Flyway Database Migration: V6 Fix Demo User Passwords
-- Project: ServiceDesk
-- Description: Updates pre-seeded user accounts with verified BCrypt hash for 'password123'
-- ==============================================================================

UPDATE users
SET password_hash = '$2a$10$shBLxpv2h/YBF/8AH36RwO5uep8WsQJvKSwPevIiQpH9lXPaBPv02',
    updated_at = CURRENT_TIMESTAMP
WHERE username IN ('employee1', 'tech_network', 'tech_hardware', 'tech_lead', 'admin');
