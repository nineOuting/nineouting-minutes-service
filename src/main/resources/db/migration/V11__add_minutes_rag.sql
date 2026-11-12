-- REQ-185 챗봇이 찾는 작성 완료 회의록을 ai-service 검색용 저장소에 넣은 상태 (docs/services/rag-chatbot.md 3장).
-- 보관함 표를 따로 두지 않고 이 열로 다시 보낸다. 작성 완료 때, 그 뒤 저장할 때마다 PENDING이 된다.
-- rag_status: PENDING(보낼 차례) / ACCEPTED(접수, 콜백 기다림) / READY(검색 가능) / FAILED(rag_error_code)
-- rag_version: 마지막으로 보낸 content_version (콜백의 docVersion과 비교한다)
ALTER TABLE minutes
    ADD COLUMN rag_status          VARCHAR(20),
    ADD COLUMN rag_version         INT,
    ADD COLUMN rag_error_code      VARCHAR(40),
    ADD COLUMN rag_attempts        INT NOT NULL DEFAULT 0,
    ADD COLUMN rag_next_attempt_at TIMESTAMP(6),
    ADD COLUMN rag_sent_at         TIMESTAMP(6);

-- 이미 작성 완료된 회의록도 배포 뒤 보낸다
UPDATE minutes SET rag_status = 'PENDING' WHERE status = 'CONFIRMED';

CREATE INDEX idx_minutes_rag ON minutes (rag_status, rag_next_attempt_at);
