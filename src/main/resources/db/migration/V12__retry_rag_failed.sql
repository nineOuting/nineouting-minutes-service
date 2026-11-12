-- V11 배포 직후 dev ai-service에 RAG가 꺼져 있어(404) 거절로 보고 실패로 굳힌 회의록을 다시 보낸다.
-- 이제 404, 503, 연결 실패는 실패로 굳히지 않는다 (MinutesRagSync)
UPDATE minutes
SET rag_status = 'PENDING', rag_error_code = NULL, rag_attempts = 0, rag_next_attempt_at = NULL
WHERE rag_status = 'FAILED' AND rag_error_code IN ('rejected', 'unavailable');
