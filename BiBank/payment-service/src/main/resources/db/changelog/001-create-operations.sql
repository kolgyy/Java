--liquibase formatted sql

--changeset bibank:001
CREATE TABLE operations
(
    id                  UUID PRIMARY KEY,
    type                VARCHAR(30)    NOT NULL,
    status              VARCHAR(20)    NOT NULL,
    initiator_id        BIGINT         NOT NULL,
    idempotency_key     VARCHAR(100),
    from_account_id     UUID,
    from_account_number VARCHAR(20),
    from_owner_id       BIGINT,
    from_owner_name     VARCHAR(255),
    to_account_id       UUID,
    to_account_number   VARCHAR(20),
    to_owner_id         BIGINT,
    to_owner_name       VARCHAR(255),
    amount              NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    currency            VARCHAR(3)     NOT NULL,
    credited_amount     NUMERIC(19, 2) NOT NULL CHECK (credited_amount > 0),
    credited_currency   VARCHAR(3)     NOT NULL,
    exchange_rate       NUMERIC(19, 8),
    description         VARCHAR(255),
    external_ref        VARCHAR(100),
    failure_code        VARCHAR(50),
    failure_reason      VARCHAR(500),
    attempts            INT            NOT NULL DEFAULT 0,
    version             BIGINT         NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    completed_at        TIMESTAMPTZ,
    CONSTRAINT chk_operations_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ux_operations_idempotency UNIQUE (initiator_id, idempotency_key)
);
CREATE INDEX idx_operations_from_owner ON operations (from_owner_id, created_at DESC);
CREATE INDEX idx_operations_to_owner ON operations (to_owner_id, created_at DESC);
CREATE INDEX idx_operations_pending ON operations (updated_at) WHERE status = 'PENDING';
--rollback DROP TABLE operations;
