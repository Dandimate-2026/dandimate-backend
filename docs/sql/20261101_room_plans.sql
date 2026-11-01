-- MySQL 8.4, 기존 DB에 배포 전 한 번 실행. 실행 전에 백업 및 애플리케이션 쓰기 중단.
-- 신규 DB는 JPA 스키마와 동일하게 생성한다. 자동 실행 스크립트가 아니다.
-- 기존 방: 정원 8 / FREE. 음성 집계는 배포 이후부터 시작(삭제된 과거 초안은 복원 불가).
-- 기존 regeneration_count는 유지. 3회 이상인 Free 방은 추가 재엮기 불가.
ALTER TABLE rooms
    ADD COLUMN capacity INT NOT NULL DEFAULT 8,
    ADD COLUMN plan_type VARCHAR(10) NOT NULL DEFAULT 'FREE',
    ADD COLUMN voice_used_ms BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN voice_reserved_ms BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_rooms_capacity CHECK (capacity BETWEEN 1 AND 8),
    ADD CONSTRAINT ck_rooms_plan CHECK (plan_type IN ('FREE', 'LITE', 'PRO')),
    ADD CONSTRAINT ck_rooms_voice CHECK (voice_used_ms >= 0 AND voice_reserved_ms >= 0);

CREATE TABLE voice_usage (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    room_id VARCHAR(36) NOT NULL,
    member_id VARCHAR(36) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    duration_ms BIGINT NOT NULL,
    status VARCHAR(12) NOT NULL,
    draft_id VARCHAR(36) NULL,
    languages VARCHAR(300) NULL,
    updated_at DATETIME(6) NOT NULL,
    INDEX idx_voice_usage_room (room_id)
);
-- 원문/초안 삭제 후에도 사용 내역을 유지하므로 draft FK는 두지 않는다.
-- 방 삭제 시 애플리케이션이 voice_usage를 함께 삭제한다.

-- 긴 음성의 받아쓰기 원문을 답가로 확정할 수 있도록 확장.
ALTER TABLE replies MODIFY COLUMN transcript VARCHAR(3000) NOT NULL;
