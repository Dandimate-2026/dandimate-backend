-- 방장이 두루마리 완성 시각을 정하기 전에는 마감 시각을 비워 둔다.
ALTER TABLE rooms MODIFY COLUMN deadline_at DATETIME(6) NULL;
