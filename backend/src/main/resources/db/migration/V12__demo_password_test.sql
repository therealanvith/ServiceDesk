-- Demo accounts now use the password "test" (BCrypt, cost 10). Root is not touched: its
-- password comes from ROOT_PASSWORD. Deactivate these accounts in any real deployment.
UPDATE users
SET password_hash = '$2a$10$tXI8yRbP2jlSVR2ifwM6d.XnjTYLNAcIxBlEkyxMjLD.r.kE6DTe6',
    updated_at = CURRENT_TIMESTAMP
WHERE id IN (
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    '33333333-3333-3333-3333-333333333333',
    '44444444-4444-4444-4444-444444444444',
    '55555555-5555-5555-5555-555555555555'
);
