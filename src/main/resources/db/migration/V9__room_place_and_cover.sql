CREATE TABLE room_covers (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    location VARCHAR(1000) NOT NULL,
    content_type VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_room_covers_user FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE INDEX idx_room_covers_user ON room_covers(user_id);
ALTER TABLE rooms ADD COLUMN place_name VARCHAR(200),
    ADD COLUMN place_address VARCHAR(500),
    ADD COLUMN place_latitude DOUBLE,
    ADD COLUMN place_longitude DOUBLE,
    ADD COLUMN cover_photo_id VARCHAR(36);
ALTER TABLE rooms ADD CONSTRAINT fk_rooms_cover FOREIGN KEY (cover_photo_id) REFERENCES room_covers(id);
