CREATE TABLE reviewer_demo_sessions (
    id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    host_room_id VARCHAR(36) NOT NULL,
    member_room_id VARCHAR(36) NOT NULL,
    completed_room_id VARCHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_reviewer_demo_sessions_user (user_id)
);
