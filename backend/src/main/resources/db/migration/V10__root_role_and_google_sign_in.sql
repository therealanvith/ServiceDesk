-- Root superuser role, and accounts that sign in with Google instead of a password.
ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_user_role;
ALTER TABLE users ADD CONSTRAINT chk_user_role
    CHECK (role IN ('EMPLOYEE', 'TECHNICIAN', 'ADMINISTRATOR', 'ROOT'));

-- Google-only accounts have no local password.
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

-- Stable Google account id ("sub" claim); emails can change, this cannot.
ALTER TABLE users ADD COLUMN IF NOT EXISTS google_sub VARCHAR(255);
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_google_sub ON users(google_sub) WHERE google_sub IS NOT NULL;

-- Only one root account may exist.
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_single_root ON users(role) WHERE role = 'ROOT';
