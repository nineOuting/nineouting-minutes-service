-- nineouting-common outbox 모듈의 이벤트 저장 테이블 (outbox/outbox_events.sql과 같다)
CREATE TABLE outbox_events
(
    id              BIGINT       GENERATED ALWAYS AS IDENTITY,
    event_id        VARCHAR(36)  NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    destination     VARCHAR(200) NOT NULL,
    message_key     VARCHAR(100) NULL,
    payload         TEXT         NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    attempts        INT          NOT NULL,
    next_attempt_at TIMESTAMP(6) NOT NULL,
    last_error      VARCHAR(500) NULL,
    created_at      TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_outbox_events_event_id UNIQUE (event_id)
);
CREATE INDEX idx_outbox_events_status_next_attempt ON outbox_events (status, next_attempt_at);
