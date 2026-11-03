-- 그룹 삭제를 soft delete 로 바꾼다. 생성·삭제 수를 통계로 다시 셀 수 있게 한다.
ALTER TABLE share_group ADD COLUMN deleted_at TIMESTAMP(6) WITH TIME ZONE;
