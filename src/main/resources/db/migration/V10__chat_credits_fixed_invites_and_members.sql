-- Additive migration: keep existing invitation hashes valid.
ALTER TABLE rooms ADD COLUMN encrypted_invite_token VARCHAR(500) NULL,
 ADD COLUMN retained_invite_hash VARCHAR(64) NULL,
 ADD UNIQUE KEY uk_rooms_retained_invite_hash (retained_invite_hash);
ALTER TABLE members ADD COLUMN kicked_at DATETIME(6) NULL;
ALTER TABLE photos ADD COLUMN place_id VARCHAR(100) NULL;
ALTER TABLE payment_orders MODIFY COLUMN from_plan VARCHAR(10) NULL,
 MODIFY COLUMN target_plan VARCHAR(10) NULL,
 ADD COLUMN order_type VARCHAR(16) NOT NULL DEFAULT 'ROOM',
 ADD COLUMN pack_id VARCHAR(16) NULL,
 ADD COLUMN credit_amount INT NOT NULL DEFAULT 0;
CREATE TABLE chat_wallets (
 user_id VARCHAR(36) PRIMARY KEY,
 free_date DATE NULL,
 free_used INT NOT NULL DEFAULT 0,
 credits BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT chk_chat_wallet_balance CHECK (free_used >= 0 AND credits >= 0),
 CONSTRAINT fk_chat_wallet_user FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE TABLE chat_usage (
 id VARCHAR(64) PRIMARY KEY,
 user_id VARCHAR(36) NOT NULL,
 request_hash VARCHAR(64) NOT NULL,
 status VARCHAR(16) NOT NULL,
 free BIT NOT NULL,
 usage_date DATE NULL,
 expires_at DATETIME(6) NULL,
 response LONGTEXT NULL,
 KEY idx_chat_usage_pending (user_id, status, expires_at),
 CONSTRAINT fk_chat_usage_user FOREIGN KEY (user_id) REFERENCES users(id)
);
