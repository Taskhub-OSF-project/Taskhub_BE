-- V12: Xóa bảng giao dịch MoMo và tạo bảng lưu trữ log webhook từ SePay (VietQR)
DROP TABLE IF EXISTS momo_transactions CASCADE;

CREATE TABLE IF NOT EXISTS sepay_webhook_logs (
    id                  BIGSERIAL PRIMARY KEY,
    gateway             VARCHAR(50),             -- Ngân hàng (MBBank, VCB...)
    transaction_date    VARCHAR(50),             -- Thời gian giao dịch từ Webhook
    account_number      VARCHAR(50),             -- Số tài khoản nhận
    sub_account         VARCHAR(50),             -- Tài khoản phụ (nếu có)
    amount_in           NUMERIC(15,0) DEFAULT 0, -- Số tiền ghi có (Nạp)
    amount_out          NUMERIC(15,0) DEFAULT 0, -- Số tiền ghi nợ (Rút)
    accumulated         NUMERIC(15,0),           -- Số dư sau giao dịch
    code                VARCHAR(100),            -- Mã chuyển khoản / Nội dung rút gọn
    transaction_content TEXT,                    -- Nội dung chuyển khoản gốc
    reference_number    VARCHAR(100) UNIQUE,     -- Mã tham chiếu ngân hàng (chống trùng webhook)
    body_json           TEXT,                    -- Toàn bộ raw JSON từ webhook để đối soát
    status              VARCHAR(30) NOT NULL DEFAULT 'RECEIVED', -- RECEIVED | PROCESSED | IGNORED | ERROR
    error_message       VARCHAR(512),            -- Ghi nhận lỗi nếu có
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sepay_ref_num ON sepay_webhook_logs(reference_number);
CREATE INDEX IF NOT EXISTS idx_sepay_code ON sepay_webhook_logs(code);
CREATE INDEX IF NOT EXISTS idx_sepay_status ON sepay_webhook_logs(status);
