-- REQ-123 회의록 양식 즐겨찾기. 개인 표시라 사용자마다 따로 둔다. 양식을 지우면 함께 지운다
CREATE TABLE template_favorites
(
    id          BIGINT    GENERATED ALWAYS AS IDENTITY,
    template_id BIGINT    NOT NULL REFERENCES templates (id) ON DELETE CASCADE,
    user_id     BIGINT    NOT NULL,
    created_at  TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_template_favorites UNIQUE (template_id, user_id)
);
