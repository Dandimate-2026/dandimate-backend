-- Payment audit survives room deletion. No card data or provider secrets are stored.
CREATE TABLE payment_orders (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    room_id VARCHAR(36) NULL,
    from_plan VARCHAR(10) NOT NULL,
    target_plan VARCHAR(10) NOT NULL,
    amount INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    payment_key VARCHAR(200) NULL,
    room_request VARCHAR(4000) NULL,
    created_at DATETIME(6) NOT NULL,
    approved_at DATETIME(6) NULL,
    UNIQUE KEY uk_payment_key (payment_key),
    KEY idx_payment_room_status (room_id, status),
    KEY idx_payment_user_created (user_id, created_at),
    CONSTRAINT chk_payment_amount CHECK (amount > 0)
);
