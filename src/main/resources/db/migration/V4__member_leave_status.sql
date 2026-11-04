-- 참가자 기록과 작성 콘텐츠는 보존하고 활성 참가자만 정원에 포함한다.
ALTER TABLE members
    ADD COLUMN left_at DATETIME(6) NULL,
    ADD COLUMN active_flower_code VARCHAR(20) NULL;
UPDATE members SET active_flower_code = flower_code WHERE left_at IS NULL;
ALTER TABLE members
    DROP INDEX uk_members_room_flower,
    ADD CONSTRAINT uk_members_room_active_flower UNIQUE (room_id, active_flower_code),
    ADD CONSTRAINT ck_members_active_flower CHECK (
        (left_at IS NULL AND active_flower_code IS NOT NULL AND active_flower_code = flower_code)
        OR (left_at IS NOT NULL AND active_flower_code IS NULL)
    ),
    ADD INDEX idx_members_user_active (user_id, left_at),
    ADD INDEX idx_members_room_active (room_id, left_at);
