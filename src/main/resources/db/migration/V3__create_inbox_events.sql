-- nineouting-common inbox 모듈의 이벤트 저장 테이블 (inbox/inbox_events.sql과 같다)
CREATE TABLE inbox_events
(
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    event_id        VARCHAR(36)  NOT NULL,
    topic           VARCHAR(200) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         TEXT         NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    attempts        INT          NOT NULL,
    next_attempt_at DATETIME(6)  NOT NULL,
    last_error      VARCHAR(500) NULL,
    received_at     DATETIME(6)  NOT NULL,
    processed_at    DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_inbox_events_event_id UNIQUE (event_id),
    INDEX idx_inbox_events_status_next_attempt (status, next_attempt_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
