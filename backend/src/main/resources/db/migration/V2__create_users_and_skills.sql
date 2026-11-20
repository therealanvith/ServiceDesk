-- ==============================================================================
-- Flyway Database Migration: V2 Create Users and Skills
-- Project: ServiceDesk
-- Description: Core user authentication accounts, roles, and technician skills
-- ==============================================================================

CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    role VARCHAR(30) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_user_role CHECK (role IN ('EMPLOYEE', 'TECHNICIAN', 'ADMINISTRATOR'))
);

CREATE TABLE user_skills (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category VARCHAR(50) NOT NULL,
    CONSTRAINT chk_skill_category CHECK (category IN ('NETWORK', 'HARDWARE', 'SOFTWARE', 'ACCESS')),
    CONSTRAINT uq_user_skill UNIQUE (user_id, category)
);

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_user_skills_user ON user_skills(user_id);
