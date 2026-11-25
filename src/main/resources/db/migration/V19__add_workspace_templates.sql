-- REQ-213 워크스페이스 양식함 (2026-11-25): 워크스페이스 소유자가 기본 양식으로 정한 양식을 워크스페이스 사본으로 보관한다.
-- workspace_id가 있으면 그 워크스페이스의 양식 (owner_user_id는 비운다). 멤버 모두 쓰고 아무도 고치지 않는다
ALTER TABLE templates ADD COLUMN workspace_id BIGINT;
CREATE INDEX idx_templates_workspace ON templates (workspace_id) WHERE workspace_id IS NOT NULL;
