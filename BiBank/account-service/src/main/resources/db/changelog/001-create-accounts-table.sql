--liquibase formatted sql

--changeset bibank:001
CREATE TABLE accounts
(
    id         UUID PRIMARY KEY,
    number     VARCHAR(20)    NOT NULL UNIQUE,
    owner_id   BIGINT         NOT NULL,
    owner_name VARCHAR(255),
    name       VARCHAR(100)   NOT NULL,
    currency   VARCHAR(3)     NOT NULL,
    type       VARCHAR(20)    NOT NULL,
    status     VARCHAR(20)    NOT NULL,
    balance    NUMERIC(19, 2) NOT NULL DEFAULT 0,
    version    BIGINT         NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT chk_accounts_currency CHECK (currency IN ('RUB', 'USD', 'EUR')),
    CONSTRAINT chk_accounts_type CHECK (type IN ('MAIN', 'SAVINGS', 'SYSTEM')),
    CONSTRAINT chk_accounts_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    -- последний рубеж защиты от отрицательного баланса клиентских счетов
    CONSTRAINT chk_accounts_balance CHECK (type = 'SYSTEM' OR balance >= 0)
);
CREATE INDEX idx_accounts_owner ON accounts (owner_id);
--rollback DROP TABLE accounts;

--changeset bibank:002
CREATE TABLE applied_operations
(
    operation_id UUID PRIMARY KEY,
    type         VARCHAR(50) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ledger_entries
(
    id            BIGSERIAL PRIMARY KEY,
    operation_id  UUID           NOT NULL REFERENCES applied_operations (operation_id),
    account_id    UUID           NOT NULL REFERENCES accounts (id),
    amount        NUMERIC(19, 2) NOT NULL,
    balance_after NUMERIC(19, 2) NOT NULL,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ux_ledger_operation_account UNIQUE (operation_id, account_id)
);
CREATE INDEX idx_ledger_account ON ledger_entries (account_id, created_at);
--rollback DROP TABLE ledger_entries; DROP TABLE applied_operations;

--changeset bibank:003
--comment: технические счета банка — контрагенты для пополнений, кредитов, вкладов и конвертации
INSERT INTO accounts (id, number, owner_id, owner_name, name, currency, type, status, balance)
VALUES ('00000000-0000-0000-0000-000000000643', '30101810000000000001', 0, 'BiBank', 'Казначейство RUB', 'RUB', 'SYSTEM', 'ACTIVE', 0),
       ('00000000-0000-0000-0000-000000000840', '30101840000000000001', 0, 'BiBank', 'Казначейство USD', 'USD', 'SYSTEM', 'ACTIVE', 0),
       ('00000000-0000-0000-0000-000000000978', '30101978000000000001', 0, 'BiBank', 'Казначейство EUR', 'EUR', 'SYSTEM', 'ACTIVE', 0);
