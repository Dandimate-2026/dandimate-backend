-- 사진마다 직접 입력한 장소. 좌표가 있는 사진만 지도에 나온다.
ALTER TABLE photos
    ADD COLUMN place_name VARCHAR(60),
    ADD COLUMN address VARCHAR(200),
    ADD COLUMN city VARCHAR(30),
    ADD COLUMN latitude DOUBLE,
    ADD COLUMN longitude DOUBLE;
