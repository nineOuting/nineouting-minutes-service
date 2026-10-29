-- REQ-101~103 (ai-service docs/api/minutes-draft-contract.md): AI 회의록 초안 요청 상태
-- draft_status: REQUESTED(만드는 중, 화면은 "준비 중") / SUCCEEDED / FAILED. 요청한 적 없으면 NULL
ALTER TABLE minutes ADD COLUMN draft_status VARCHAR(20) NULL;
ALTER TABLE minutes ADD COLUMN draft_request_id VARCHAR(36) NULL;
ALTER TABLE minutes ADD COLUMN draft_error VARCHAR(100) NULL;
ALTER TABLE minutes ADD COLUMN draft_requested_at TIMESTAMP(6) NULL;
