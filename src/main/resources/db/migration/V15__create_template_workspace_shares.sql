-- REQ-199 워크스페이스 회의록 기본 양식 (2026-11-17 결정): 소유자의 개인 양식을 기본 양식으로 정하면 그 워크스페이스 멤버 모두 쓸 수 있다
CREATE TABLE template_workspace_shares (
    id           BIGSERIAL PRIMARY KEY,
    template_id  BIGINT    NOT NULL,
    workspace_id BIGINT    NOT NULL,
    created_at   TIMESTAMP NOT NULL,
    CONSTRAINT uk_template_workspace_shares UNIQUE (template_id, workspace_id)
);
CREATE INDEX idx_template_workspace_shares_workspace ON template_workspace_shares (workspace_id);
