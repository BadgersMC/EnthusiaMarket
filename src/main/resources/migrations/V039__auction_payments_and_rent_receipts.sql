ALTER TABLE rent_ledger ADD COLUMN covered_from BIGINT;
ALTER TABLE rent_ledger ADD COLUMN covered_until BIGINT;
CREATE TABLE auction_payments (
    id VARCHAR(36) PRIMARY KEY,
    auction_id VARCHAR(128) NOT NULL,
    purpose VARCHAR(24) NOT NULL,
    payer_type VARCHAR(12) NOT NULL,
    recipient VARCHAR(128) NOT NULL,
    amount BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    next_attempt BIGINT NOT NULL
);
CREATE INDEX idx_auction_payments_pending ON auction_payments(status, next_attempt);
