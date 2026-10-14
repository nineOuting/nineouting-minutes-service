package com.nineouting.backend.minutes.domain;

public enum MinutesStatus {
    /** 작성 중: 회의 마무리 후 만들어진 상태 */
    WRITING,
    /** 검토 중 (REQ-095) */
    REVIEWING,
    /** 확정 (REQ-099). 아카이브에 보인다 */
    CONFIRMED
}
