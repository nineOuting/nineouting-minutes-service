-- REQ-166, REQ-143: 회의를 쓰레기통에 넣으면 회의록도 숨긴다 (meeting-service MeetingTrashed).
-- 복구하면 다시 보이고(MeetingRestored), 영구 삭제하면 지운다(MeetingPurged)
ALTER TABLE minutes ADD COLUMN trashed_at TIMESTAMP(6) NULL;
