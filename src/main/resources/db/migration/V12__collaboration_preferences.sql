ALTER TABLE rooms
    ADD COLUMN scroll_template_id VARCHAR(30) NOT NULL DEFAULT 'CLASSIC' AFTER scroll_summary;

ALTER TABLE paragraph_ratings
    ADD COLUMN paragraph_version INT NULL AFTER score;

CREATE TABLE push_subscriptions (
    id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    endpoint VARCHAR(1000) NOT NULL,
    p256dh VARCHAR(255) NOT NULL,
    auth VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_push_subscription_endpoint (endpoint(255)),
    KEY idx_push_subscription_user (user_id)
);

CREATE TABLE user_consents (
    id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    version VARCHAR(50) NOT NULL,
    terms BOOLEAN NOT NULL,
    media_processing BOOLEAN NOT NULL,
    external_ai_processing BOOLEAN NOT NULL,
    consented_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_consent_user_version (user_id, version)
);

CREATE TABLE chat_messages (
    id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    conversation_id VARCHAR(64) NOT NULL,
    role VARCHAR(12) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_chat_messages_conversation (user_id, conversation_id, created_at)
);
