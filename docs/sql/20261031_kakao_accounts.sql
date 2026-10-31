-- MySQL 8.4: 기존 스키마에 검토 후 1회 적용. 자동 실행 스크립트가 아니다.
-- 기존 익명 참가자를 임의 계정에 연결하거나 삭제하지 않는다.
CREATE TABLE users (
    id VARCHAR(36) NOT NULL,
    provider VARCHAR(20) NOT NULL,
    provider_user_id VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_provider_subject UNIQUE (provider, provider_user_id)
);
ALTER TABLE members ADD COLUMN user_id VARCHAR(36) NULL;
ALTER TABLE members ADD CONSTRAINT uk_members_room_user UNIQUE (room_id, user_id);
ALTER TABLE members ADD CONSTRAINT fk_members_user FOREIGN KEY (user_id) REFERENCES users(id);
