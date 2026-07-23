-- V10: MoMo payment gateway transactions table
CREATE TABLE IF NOT EXISTS momo_transactions (
    id            BIGSERIAL PRIMARY KEY,
    order_id      VARCHAR(50)  NOT NULL UNIQUE,
    user_id       BIGINT       NOT NULL REFERENCES users(id),
    type          VARCHAR(20)  NOT NULL,   -- DEPOSIT | WITHDRAWAL
    status        VARCHAR(20)  NOT NULL,   -- PENDING | SUCCESS | FAILED | CANCELLED
    amount        NUMERIC(15,0) NOT NULL,
    momo_trans_id VARCHAR(100),
    pay_url       VARCHAR(512),
    deeplink      VARCHAR(512),
    qr_code_url   VARCHAR(512),
    phone         VARCHAR(15),
    error_message VARCHAR(512),
    result_code   INT,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_momo_tx_user_id  ON momo_transactions(user_id);
CREATE INDEX IF NOT EXISTS idx_momo_tx_order_id ON momo_transactions(order_id);
