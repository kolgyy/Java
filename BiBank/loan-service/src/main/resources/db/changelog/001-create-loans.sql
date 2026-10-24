--liquibase formatted sql

--changeset bibank:001
CREATE TABLE loan_products
(
    id                 UUID PRIMARY KEY,
    code               VARCHAR(50)    NOT NULL UNIQUE,
    name               VARCHAR(255)   NOT NULL,
    description        VARCHAR(1000),
    currency           VARCHAR(3)     NOT NULL,
    annual_rate        NUMERIC(6, 3)  NOT NULL CHECK (annual_rate >= 0),
    min_amount         NUMERIC(19, 2) NOT NULL,
    max_amount         NUMERIC(19, 2) NOT NULL,
    min_term_months    INT            NOT NULL,
    max_term_months    INT            NOT NULL,
    auto_approve_limit NUMERIC(19, 2) NOT NULL DEFAULT 0,
    active             BOOLEAN        NOT NULL DEFAULT true,
    CHECK (min_amount > 0 AND max_amount >= min_amount),
    CHECK (min_term_months > 0 AND max_term_months >= min_term_months)
);

CREATE TABLE loan_applications
(
    id              UUID PRIMARY KEY,
    user_id         BIGINT         NOT NULL,
    product_id      UUID           NOT NULL REFERENCES loan_products (id),
    account_id      UUID           NOT NULL,
    account_number  VARCHAR(20),
    amount          NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    currency        VARCHAR(3)     NOT NULL,
    term_months     INT            NOT NULL CHECK (term_months > 0),
    monthly_income  NUMERIC(19, 2),
    purpose         VARCHAR(255),
    status          VARCHAR(20)    NOT NULL,
    decision_reason VARCHAR(500),
    decided_by      BIGINT,
    decided_at      TIMESTAMPTZ,
    loan_id         UUID,
    version         BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT chk_application_status CHECK (status IN ('SUBMITTED', 'APPROVED', 'REJECTED', 'CANCELLED'))
);
CREATE INDEX idx_applications_user ON loan_applications (user_id, created_at DESC);
CREATE INDEX idx_applications_status ON loan_applications (status);

CREATE TABLE loans
(
    id                        UUID PRIMARY KEY,
    user_id                   BIGINT         NOT NULL,
    application_id            UUID           NOT NULL UNIQUE REFERENCES loan_applications (id),
    product_name              VARCHAR(255)   NOT NULL,
    account_id                UUID           NOT NULL,
    currency                  VARCHAR(3)     NOT NULL,
    principal                 NUMERIC(19, 2) NOT NULL CHECK (principal > 0),
    annual_rate               NUMERIC(6, 3)  NOT NULL,
    term_months               INT            NOT NULL,
    monthly_payment           NUMERIC(19, 2) NOT NULL,
    outstanding_principal     NUMERIC(19, 2) NOT NULL CHECK (outstanding_principal >= 0),
    status                    VARCHAR(30)    NOT NULL,
    disbursement_operation_id UUID           NOT NULL UNIQUE,
    issued_at                 TIMESTAMPTZ,
    closed_at                 TIMESTAMPTZ,
    first_payment_date        DATE           NOT NULL,
    version                   BIGINT         NOT NULL DEFAULT 0,
    created_at                TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT chk_loan_status CHECK (status IN ('PENDING_DISBURSEMENT', 'ACTIVE', 'CLOSED', 'CANCELLED'))
);
CREATE INDEX idx_loans_user ON loans (user_id);

CREATE TABLE loan_schedule
(
    id                  UUID PRIMARY KEY,
    loan_id             UUID           NOT NULL REFERENCES loans (id) ON DELETE CASCADE,
    installment_number  INT            NOT NULL,
    due_date            DATE           NOT NULL,
    payment_amount      NUMERIC(19, 2) NOT NULL,
    principal_part      NUMERIC(19, 2) NOT NULL,
    interest_part       NUMERIC(19, 2) NOT NULL,
    remaining_principal NUMERIC(19, 2) NOT NULL,
    status              VARCHAR(20)    NOT NULL,
    paid_at             TIMESTAMPTZ,
    CONSTRAINT ux_schedule_installment UNIQUE (loan_id, installment_number)
);

CREATE TABLE loan_payments
(
    id             UUID PRIMARY KEY,
    loan_id        UUID           NOT NULL REFERENCES loans (id),
    account_id     UUID           NOT NULL,
    operation_id   UUID           NOT NULL UNIQUE,
    mode           VARCHAR(10)    NOT NULL,
    amount         NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    principal_part NUMERIC(19, 2) NOT NULL,
    status         VARCHAR(20)    NOT NULL,
    failure_reason VARCHAR(500),
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    completed_at   TIMESTAMPTZ
);
-- не более одного незавершённого платежа по кредиту одновременно
CREATE UNIQUE INDEX ux_loan_payments_pending ON loan_payments (loan_id) WHERE status = 'PENDING';
--rollback DROP TABLE loan_payments; DROP TABLE loan_schedule; DROP TABLE loans; DROP TABLE loan_applications; DROP TABLE loan_products;

--changeset bibank:002
CREATE TABLE deposit_products
(
    id               UUID PRIMARY KEY,
    code             VARCHAR(50)    NOT NULL UNIQUE,
    name             VARCHAR(255)   NOT NULL,
    description      VARCHAR(1000),
    currency         VARCHAR(3)     NOT NULL,
    annual_rate      NUMERIC(6, 3)  NOT NULL CHECK (annual_rate >= 0),
    early_close_rate NUMERIC(6, 3)  NOT NULL CHECK (early_close_rate >= 0),
    min_amount       NUMERIC(19, 2) NOT NULL,
    max_amount       NUMERIC(19, 2) NOT NULL,
    min_term_months  INT            NOT NULL,
    max_term_months  INT            NOT NULL,
    capitalization   BOOLEAN        NOT NULL,
    active           BOOLEAN        NOT NULL DEFAULT true
);

CREATE TABLE deposits
(
    id                 UUID PRIMARY KEY,
    user_id            BIGINT         NOT NULL,
    product_id         UUID           NOT NULL REFERENCES deposit_products (id),
    product_name       VARCHAR(255)   NOT NULL,
    account_id         UUID           NOT NULL,
    currency           VARCHAR(3)     NOT NULL,
    principal          NUMERIC(19, 2) NOT NULL CHECK (principal > 0),
    annual_rate        NUMERIC(6, 3)  NOT NULL,
    early_close_rate   NUMERIC(6, 3)  NOT NULL,
    capitalization     BOOLEAN        NOT NULL,
    term_months        INT            NOT NULL,
    open_date          DATE           NOT NULL,
    maturity_date      DATE           NOT NULL,
    accrued_through    DATE           NOT NULL,
    accrued_interest   NUMERIC(19, 2) NOT NULL DEFAULT 0,
    status             VARCHAR(20)    NOT NULL,
    open_operation_id  UUID           NOT NULL UNIQUE,
    close_operation_id UUID UNIQUE,
    payout_amount      NUMERIC(19, 2),
    payout_interest    NUMERIC(19, 2),
    early_closed       BOOLEAN        NOT NULL DEFAULT false,
    failure_reason     VARCHAR(500),
    version            BIGINT         NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    closed_at          TIMESTAMPTZ,
    CONSTRAINT chk_deposit_status CHECK (status IN ('OPENING', 'ACTIVE', 'CLOSING', 'CLOSED', 'FAILED'))
);
CREATE INDEX idx_deposits_user ON deposits (user_id);
CREATE INDEX idx_deposits_status ON deposits (status);

CREATE TABLE interest_accruals
(
    id           UUID PRIMARY KEY,
    deposit_id   UUID           NOT NULL REFERENCES deposits (id),
    accrual_date DATE           NOT NULL,
    base         NUMERIC(19, 2) NOT NULL,
    amount       NUMERIC(19, 2) NOT NULL,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ux_accrual_day UNIQUE (deposit_id, accrual_date)
);
--rollback DROP TABLE interest_accruals; DROP TABLE deposits; DROP TABLE deposit_products;

--changeset bibank:003
--comment: витрина продуктов
INSERT INTO loan_products (id, code, name, description, currency, annual_rate, min_amount, max_amount,
                           min_term_months, max_term_months, auto_approve_limit)
VALUES ('11111111-0000-0000-0000-000000000001', 'CONSUMER', 'Потребительский',
        'Кредит на любые цели без залога и поручителей', 'RUB', 17.900, 10000, 3000000, 6, 60, 300000),
       ('11111111-0000-0000-0000-000000000002', 'AUTO', 'Автокредит',
        'Покупка нового или подержанного автомобиля', 'RUB', 12.500, 100000, 5000000, 12, 84, 0),
       ('11111111-0000-0000-0000-000000000003', 'MORTGAGE', 'Ипотека «Первый дом»',
        'Длительный кредит на покупку жилья, рассматривается индивидуально', 'RUB', 9.900, 500000, 20000000, 36, 360, 0),
       ('11111111-0000-0000-0000-000000000004', 'USD_CASH', 'Валютный кредит',
        'Кредит в долларах США для валютных расходов', 'USD', 8.500, 500, 50000, 6, 36, 5000);

INSERT INTO deposit_products (id, code, name, description, currency, annual_rate, early_close_rate, min_amount,
                              max_amount, min_term_months, max_term_months, capitalization)
VALUES ('22222222-0000-0000-0000-000000000001', 'SAVE_START', 'Накопительный старт',
        'Ежедневная капитализация процентов', 'RUB', 16.000, 0.100, 10000, 5000000, 3, 12, true),
       ('22222222-0000-0000-0000-000000000002', 'CLASSIC', 'Классический',
        'Максимальная ставка, проценты выплачиваются в конце срока', 'RUB', 18.000, 1.000, 50000, 10000000, 6, 24, false),
       ('22222222-0000-0000-0000-000000000003', 'USD_SAVE', 'Валютный',
        'Сбережения в долларах США', 'USD', 3.500, 0.100, 100, 1000000, 6, 24, true);
