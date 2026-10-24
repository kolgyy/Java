--liquibase formatted sql

--changeset bibank:004
ALTER TABLE users ADD COLUMN full_name VARCHAR(255);
UPDATE users SET full_name = split_part(email, '@', 1) WHERE full_name IS NULL;
ALTER TABLE users ALTER COLUMN full_name SET NOT NULL;

ALTER TABLE users ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE users ADD COLUMN last_login_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE users ALTER COLUMN created_at TYPE TIMESTAMPTZ;
ALTER TABLE users ALTER COLUMN created_at SET NOT NULL;

CREATE UNIQUE INDEX ux_users_email_lower ON users (LOWER(email));
ALTER TABLE users ADD CONSTRAINT chk_users_role CHECK (role IN ('ROLE_USER', 'ROLE_ADMIN'));
ALTER TABLE users ADD CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'BLOCKED'));
--rollback ALTER TABLE users DROP COLUMN full_name, DROP COLUMN status, DROP COLUMN last_login_at, DROP COLUMN updated_at;

--changeset bibank:005
--comment: refresh-токены хранятся в Redis (хешированными), таблица больше не используется
DROP TABLE refresh_tokens;
