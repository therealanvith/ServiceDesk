-- Demo accounts are identified by role rather than made-up people, and carry no fake email addresses.
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;

UPDATE users SET username = 'emp',       full_name = 'Employee',            email = NULL
  WHERE id = '11111111-1111-1111-1111-111111111111';
UPDATE users SET username = 'tech_net',  full_name = 'Network Technician',  email = NULL
  WHERE id = '22222222-2222-2222-2222-222222222222';
UPDATE users SET username = 'tech_hw',   full_name = 'Hardware Technician', email = NULL
  WHERE id = '33333333-3333-3333-3333-333333333333';
UPDATE users SET username = 'tech_lead', full_name = 'Lead Technician',     email = NULL
  WHERE id = '44444444-4444-4444-4444-444444444444';
UPDATE users SET username = 'admin',     full_name = 'Administrator',       email = NULL
  WHERE id = '55555555-5555-5555-5555-555555555555';
