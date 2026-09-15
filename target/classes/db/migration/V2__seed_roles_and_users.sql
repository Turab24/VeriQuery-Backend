-- =====================================================================
-- Seed roles and demo accounts.
-- Passwords are BCrypt hashes (strength 10) of the values documented in
-- the README. Change or delete these accounts before any real deployment.
-- =====================================================================

INSERT INTO roles (name, description) VALUES
    ('ROLE_ADMIN', 'Full platform administration: users, documents, statistics'),
    ('ROLE_USER',  'Knowledge base consumer: search, chat, conversation history')
ON CONFLICT (name) DO NOTHING;

-- admin@enterpriseai.local / Admin@12345
INSERT INTO users (email, password_hash, full_name, organization, job_title, enabled)
VALUES ('admin@enterpriseai.local',
        '$2a$10$tZ1Wobf7BegeAubhCrOVvuap7lTqgNw7UWdc/sB4WYs1h..vmmC46',
        'Platform Administrator', 'EnterpriseAI Hub', 'Administrator', TRUE)
ON CONFLICT (email) DO NOTHING;

-- analyst@enterpriseai.local / User@12345
INSERT INTO users (email, password_hash, full_name, organization, job_title, enabled)
VALUES ('analyst@enterpriseai.local',
        '$2a$10$nSg1872hQW9LnxLvqh/FgOe5r/tgmeZ4AgOz1iedYtbp7Cuu4km2e',
        'Support Analyst', 'EnterpriseAI Hub', 'Customer Support Analyst', TRUE)
ON CONFLICT (email) DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
         CROSS JOIN roles r
WHERE u.email = 'admin@enterpriseai.local'
  AND r.name IN ('ROLE_ADMIN', 'ROLE_USER')
ON CONFLICT DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
         CROSS JOIN roles r
WHERE u.email = 'analyst@enterpriseai.local'
  AND r.name = 'ROLE_USER'
ON CONFLICT DO NOTHING;
