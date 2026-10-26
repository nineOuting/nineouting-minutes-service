-- REQ-146 (2026-10-26 아카이브 디자인): 최근 본 회의록. 사람마다 회의록당 1줄, 볼 때마다 시각을 고친다
CREATE TABLE minutes_views
(
    id         BIGINT       GENERATED ALWAYS AS IDENTITY,
    user_id    BIGINT       NOT NULL,
    minutes_id BIGINT       NOT NULL,
    viewed_at  TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_minutes_views UNIQUE (user_id, minutes_id)
);
CREATE INDEX idx_minutes_views_recent ON minutes_views (user_id, viewed_at);
