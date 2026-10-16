-- 시각은 모두 한국 시간
-- 아카이브 검색(REQ-052)은 글자 3개 단위 인덱스(pg_trgm)로 한국어 부분 일치를 찾는다
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE minutes
(
    id                   BIGINT       GENERATED ALWAYS AS IDENTITY,
    meeting_id           BIGINT       NOT NULL,
    workspace_id         BIGINT       NOT NULL,
    folder_id            BIGINT       NOT NULL,
    title                VARCHAR(100) NOT NULL,
    meeting_start_at     TIMESTAMP(6) NOT NULL,
    writer_user_id       BIGINT       NOT NULL,
    status               VARCHAR(20)  NOT NULL,
    template_id          BIGINT       NULL,
    content_version      INT          NOT NULL,
    review_round         INT          NOT NULL,
    confirmed_at         TIMESTAMP(6) NULL,
    confirmed_by_user_id BIGINT       NULL,
    search_text          TEXT         NOT NULL,
    created_at           TIMESTAMP(6) NOT NULL,
    updated_at           TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_minutes_meeting UNIQUE (meeting_id)
);
CREATE INDEX idx_minutes_folder_status ON minutes (folder_id, status, meeting_start_at);
CREATE INDEX idx_minutes_search ON minutes USING gin (search_text gin_trgm_ops);

CREATE TABLE minutes_attendees
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY,
    minutes_id BIGINT NOT NULL,
    user_id    BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_minutes_attendees UNIQUE (minutes_id, user_id)
);
CREATE INDEX idx_minutes_attendees_user ON minutes_attendees (user_id);

CREATE TABLE minutes_sections
(
    id         BIGINT       GENERATED ALWAYS AS IDENTITY,
    minutes_id BIGINT       NOT NULL,
    position   INT          NOT NULL,
    title      VARCHAR(100) NOT NULL,
    content    TEXT         NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_minutes_sections_minutes ON minutes_sections (minutes_id, position);

-- 저장할 때마다 전체 내용(snapshot JSON)과 수정한 사람·시각 (REQ-093, 109)
CREATE TABLE minutes_revisions
(
    id             BIGINT      GENERATED ALWAYS AS IDENTITY,
    minutes_id     BIGINT      NOT NULL,
    version        INT         NOT NULL,
    editor_user_id BIGINT      NOT NULL,
    edited_at      TIMESTAMP(6) NOT NULL,
    snapshot       TEXT        NOT NULL,
    after_confirm  BOOLEAN     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_minutes_revisions UNIQUE (minutes_id, version)
);

CREATE TABLE minutes_reviews
(
    id         BIGINT      GENERATED ALWAYS AS IDENTITY,
    minutes_id BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    status     VARCHAR(20) NOT NULL,
    decided_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_minutes_reviews UNIQUE (minutes_id, user_id)
);
CREATE INDEX idx_minutes_reviews_user ON minutes_reviews (user_id, status);

CREATE TABLE minutes_comments
(
    id             BIGINT        GENERATED ALWAYS AS IDENTITY,
    minutes_id     BIGINT        NOT NULL,
    section_id     BIGINT        NOT NULL,
    quote          VARCHAR(1000) NOT NULL,
    body           VARCHAR(1000) NOT NULL,
    author_user_id BIGINT        NOT NULL,
    created_at     TIMESTAMP(6)  NOT NULL,
    resolved_at    TIMESTAMP(6)  NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_minutes_comments_minutes ON minutes_comments (minutes_id);

CREATE TABLE minutes_tags
(
    id         BIGINT      GENERATED ALWAYS AS IDENTITY,
    minutes_id BIGINT      NOT NULL,
    tag        VARCHAR(20) NOT NULL,
    PRIMARY KEY (id)
);
-- 태그는 대소문자를 구분하지 않는다
CREATE UNIQUE INDEX uk_minutes_tags ON minutes_tags (minutes_id, lower(tag));
CREATE INDEX idx_minutes_tags_tag ON minutes_tags (lower(tag));

-- 즐겨찾기·중요 표시는 개인 표시 (REQ-054)
CREATE TABLE minutes_marks
(
    id         BIGINT  GENERATED ALWAYS AS IDENTITY,
    minutes_id BIGINT  NOT NULL,
    user_id    BIGINT  NOT NULL,
    favorite   BOOLEAN NOT NULL,
    important  BOOLEAN NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_minutes_marks UNIQUE (minutes_id, user_id)
);

-- owner_user_id가 없으면 기본 양식 (누구나 고를 수 있고 고칠 수 없음)
CREATE TABLE templates
(
    id            BIGINT      GENERATED ALWAYS AS IDENTITY,
    owner_user_id BIGINT      NULL,
    name          VARCHAR(50) NOT NULL,
    created_at    TIMESTAMP(6) NOT NULL,
    updated_at    TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_templates_owner ON templates (owner_user_id);

CREATE TABLE template_items
(
    id          BIGINT       GENERATED ALWAYS AS IDENTITY,
    template_id BIGINT       NOT NULL,
    position    INT          NOT NULL,
    title       VARCHAR(100) NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_template_items_template ON template_items (template_id, position);

-- 기본 양식 (docs/services/minutes-service.md 8장 결정 5). 처음 넣는 행이라 id는 1이다
INSERT INTO templates (owner_user_id, name, created_at, updated_at)
VALUES (NULL, '기본 양식', LOCALTIMESTAMP, LOCALTIMESTAMP);
INSERT INTO template_items (template_id, position, title)
VALUES (1, 0, '안건별 논의'),
       (1, 1, '결정 사항'),
       (1, 2, '미결 안건'),
       (1, 3, '할 일');
