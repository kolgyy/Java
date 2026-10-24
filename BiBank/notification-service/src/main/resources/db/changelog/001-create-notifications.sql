--liquibase formatted sql

--changeset bibank:001
CREATE TABLE notifications
(
    id         UUID PRIMARY KEY,
    user_id    BIGINT        NOT NULL,
    event_id   UUID          NOT NULL,
    event_type VARCHAR(100)  NOT NULL,
    level      VARCHAR(20)   NOT NULL,
    title      VARCHAR(255)  NOT NULL,
    message    VARCHAR(1000) NOT NULL,
    is_read    BOOLEAN       NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    read_at    TIMESTAMPTZ,
    CONSTRAINT ux_notifications_event_user UNIQUE (event_id, user_id)
);
CREATE INDEX idx_notifications_user ON notifications (user_id, created_at DESC);
CREATE INDEX idx_notifications_unread ON notifications (user_id) WHERE is_read = false;
--rollback DROP TABLE notifications;
