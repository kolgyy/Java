-- liquibase formatted sql

-- changeset dev:002

ALTER TABLE users
    ADD COLUMN role VARCHAR(50);

UPDATE users
SET role = 'ROLE_USER';

ALTER TABLE users
    ALTER COLUMN role SET NOT NULL;