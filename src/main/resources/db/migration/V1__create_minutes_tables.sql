-- 시각은 모두 한국 시간
CREATE TABLE minutes
(
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    meeting_id           BIGINT       NOT NULL,
    workspace_id         BIGINT       NOT NULL,
    folder_id            BIGINT       NOT NULL,
    title                VARCHAR(100) NOT NULL,
    meeting_start_at     DATETIME(6)  NOT NULL,
    writer_user_id       BIGINT       NOT NULL,
    status               VARCHAR(20)  NOT NULL,
    template_id          BIGINT       NULL,
    content_version      INT          NOT NULL,
    review_round         INT          NOT NULL,
    confirmed_at         DATETIME(6)  NULL,
    confirmed_by_user_id BIGINT       NULL,
    search_text          MEDIUMTEXT   NOT NULL,
    created_at           DATETIME(6)  NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_minutes_meeting UNIQUE (meeting_id),
    INDEX idx_minutes_folder_status (folder_id, status, meeting_start_at),
    -- 아카이브 검색 (REQ-052). ngram: 한국어를 2글자 단위로 잘라 찾는다
    FULLTEXT INDEX ft_minutes_search (search_text) WITH PARSER ngram
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE minutes_attendees
(
    id         BIGINT NOT NULL AUTO_INCREMENT,
    minutes_id BIGINT NOT NULL,
    user_id    BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_minutes_attendees UNIQUE (minutes_id, user_id),
    INDEX idx_minutes_attendees_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE minutes_sections
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    minutes_id BIGINT       NOT NULL,
    position   INT          NOT NULL,
    title      VARCHAR(100) NOT NULL,
    content    MEDIUMTEXT   NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_minutes_sections_minutes (minutes_id, position)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 저장할 때마다 전체 내용(snapshot JSON)과 수정한 사람·시각 (REQ-093, 109)
CREATE TABLE minutes_revisions
(
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    minutes_id     BIGINT      NOT NULL,
    version        INT         NOT NULL,
    editor_user_id BIGINT      NOT NULL,
    edited_at      DATETIME(6) NOT NULL,
    snapshot       MEDIUMTEXT  NOT NULL,
    after_confirm  BOOLEAN     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_minutes_revisions UNIQUE (minutes_id, version)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE minutes_reviews
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    minutes_id BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    status     VARCHAR(20) NOT NULL,
    decided_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_minutes_reviews UNIQUE (minutes_id, user_id),
    INDEX idx_minutes_reviews_user (user_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE minutes_comments
(
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    minutes_id     BIGINT        NOT NULL,
    section_id     BIGINT        NOT NULL,
    quote          VARCHAR(1000) NOT NULL,
    body           VARCHAR(1000) NOT NULL,
    author_user_id BIGINT        NOT NULL,
    created_at     DATETIME(6)   NOT NULL,
    resolved_at    DATETIME(6)   NULL,
    PRIMARY KEY (id),
    INDEX idx_minutes_comments_minutes (minutes_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE minutes_tags
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    minutes_id BIGINT      NOT NULL,
    tag        VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_minutes_tags UNIQUE (minutes_id, tag),
    INDEX idx_minutes_tags_tag (tag)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 즐겨찾기·중요 표시는 개인 표시 (REQ-054)
CREATE TABLE minutes_marks
(
    id         BIGINT  NOT NULL AUTO_INCREMENT,
    minutes_id BIGINT  NOT NULL,
    user_id    BIGINT  NOT NULL,
    favorite   BOOLEAN NOT NULL,
    important  BOOLEAN NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_minutes_marks UNIQUE (minutes_id, user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- owner_user_id가 없으면 기본 양식 (누구나 고를 수 있고 고칠 수 없음)
CREATE TABLE templates
(
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    owner_user_id BIGINT      NULL,
    name          VARCHAR(50) NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_templates_owner (owner_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE template_items
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    template_id BIGINT       NOT NULL,
    position    INT          NOT NULL,
    title       VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_template_items_template (template_id, position)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 기본 양식 (docs/services/minutes-service.md 8장 결정 5)
INSERT INTO templates (id, owner_user_id, name, created_at, updated_at)
VALUES (1, NULL, '기본 양식', NOW(6), NOW(6));
INSERT INTO template_items (template_id, position, title)
VALUES (1, 0, '안건별 논의'),
       (1, 1, '결정 사항'),
       (1, 2, '미결 안건'),
       (1, 3, '할 일');
