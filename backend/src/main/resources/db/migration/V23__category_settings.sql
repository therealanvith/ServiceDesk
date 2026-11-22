-- Which incident categories reporters can choose. The codes mirror the Category enum;
-- an administrator can switch one off (existing tickets keep it).
CREATE TABLE category_settings (
    code VARCHAR(30) PRIMARY KEY,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO category_settings (code, active) VALUES
('NETWORK', TRUE), ('HARDWARE', TRUE), ('SOFTWARE', TRUE), ('ACCESS', TRUE),
('DATABASE', TRUE), ('CLOUD', TRUE), ('SECURITY', TRUE), ('APPLICATION', TRUE),
('STORAGE', TRUE);
