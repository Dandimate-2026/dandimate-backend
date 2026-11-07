ALTER TABLE photos
    ADD COLUMN admin_area_code VARCHAR(10) NULL AFTER city;

ALTER TABLE notifications
    ADD COLUMN message VARCHAR(300) NULL AFTER resource_id,
    ADD COLUMN scheduled_for DATETIME(6) NULL AFTER message;

