-- V6: operational payment states, reconciliation tracking, coupon-to-payment linkage, audit detail.
-- Additive only: V1-V5 are already applied and must never be edited.

ALTER TABLE payment_record DROP CONSTRAINT payment_record_status_check;
ALTER TABLE payment_record ADD CONSTRAINT payment_record_status_check
    CHECK (status IN ('INITIATED', 'PROCESSING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'EXPIRED',
                      'REFUND_PENDING', 'REFUNDED'));

ALTER TABLE payment_record ADD COLUMN reconciliation_state VARCHAR(24) NOT NULL DEFAULT 'NONE'
    CHECK (reconciliation_state IN ('NONE', 'RETRY_REQUIRED', 'RECONCILIATION_REQUIRED', 'RECONCILED'));
ALTER TABLE payment_record ADD COLUMN failure_reason VARCHAR(200);
ALTER TABLE payment_record ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 1 CHECK (attempt_count >= 1);
ALTER TABLE payment_record ADD COLUMN discount_amount NUMERIC(12, 2) NOT NULL DEFAULT 0
    CHECK (discount_amount >= 0);
ALTER TABLE payment_record ADD COLUMN coupon_id BIGINT REFERENCES coupon(id) ON DELETE RESTRICT;
CREATE INDEX idx_payment_reconciliation ON payment_record(reconciliation_state, created_at)
    WHERE reconciliation_state <> 'NONE';

ALTER TABLE recharge_transaction DROP CONSTRAINT recharge_transaction_status_check;
ALTER TABLE recharge_transaction ADD CONSTRAINT recharge_transaction_status_check
    CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'EXPIRED', 'REVERSED'));

ALTER TABLE coupon_redemption ADD COLUMN payment_id BIGINT REFERENCES payment_record(id) ON DELETE SET NULL;
ALTER TABLE coupon_redemption ADD COLUMN discount_amount NUMERIC(12, 2) CHECK (discount_amount >= 0);

ALTER TABLE audit_event ADD COLUMN details VARCHAR(500);

ALTER TABLE mobile_number ADD COLUMN is_primary BOOLEAN NOT NULL DEFAULT FALSE;
CREATE UNIQUE INDEX uq_mobile_primary_per_customer ON mobile_number(customer_id) WHERE is_primary;

ALTER TABLE customer_profile ADD COLUMN display_language VARCHAR(8) NOT NULL DEFAULT 'en';
