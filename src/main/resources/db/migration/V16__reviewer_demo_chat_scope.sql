ALTER TABLE chat_messages ADD COLUMN demo_scope VARCHAR(36) NOT NULL DEFAULT 'REAL';
ALTER TABLE chat_usage ADD COLUMN demo_scope VARCHAR(36) NOT NULL DEFAULT 'REAL';
CREATE INDEX idx_chat_messages_demo_scope ON chat_messages(demo_scope);
CREATE INDEX idx_chat_usage_demo_scope ON chat_usage(demo_scope);
-- Legacy messages have no provenance. Quarantine messages since the active demo started.
UPDATE chat_messages m JOIN reviewer_demo_sessions s ON m.user_id=s.user_id
SET m.demo_scope=s.id WHERE m.created_at>=s.created_at;
-- Cached responses have no creation timestamp; invalidate payloads, retain billing ledger.
UPDATE chat_usage u JOIN reviewer_demo_sessions s ON u.user_id=s.user_id SET u.response=NULL;

ALTER TABLE chat_messages ADD COLUMN source_room_ids TEXT NULL;
