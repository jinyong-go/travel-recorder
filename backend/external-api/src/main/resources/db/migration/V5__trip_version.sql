-- 여행 동시 수정을 감지하는 버전 컬럼을 추가한다.
ALTER TABLE trips ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
