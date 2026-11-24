-- REQ-195 양식 가져오기 진행 상태. 접수하면 PENDING, AI 결과에 따라 SUCCEEDED(만든 양식 template_id) 또는 FAILED(error_code)
CREATE TABLE template_imports
(
    id          BIGINT       GENERATED ALWAYS AS IDENTITY,
    user_id     BIGINT       NOT NULL,
    file_key    VARCHAR(255) NOT NULL,
    name        VARCHAR(50)  NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    template_id BIGINT,
    error_code  VARCHAR(50),
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_template_imports_user ON template_imports (user_id);
