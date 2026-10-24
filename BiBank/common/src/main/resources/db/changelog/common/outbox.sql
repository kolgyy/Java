--liquibase formatted sql

--changeset bibank:outbox-1
CREATE TABLE outbox_events
(
    id           UUID PRIMARY KEY,
    topic        VARCHAR(100) NOT NULL,
    event_key    VARCHAR(100),
    event_type   VARCHAR(100) NOT NULL,
    payload      TEXT         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,
    attempts     INT          NOT NULL DEFAULT 0,
    last_error   TEXT
);
CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;
--rollback DROP TABLE outbox_events;
