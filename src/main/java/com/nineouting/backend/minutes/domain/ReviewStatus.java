package com.nineouting.backend.minutes.domain;

/** 참석자별 검토 현황 (REQ-098) */
public enum ReviewStatus {
    /** 미확인 */
    PENDING,
    /** 동의 */
    APPROVED,
    /** 수정 요청 (코멘트를 남김) */
    CHANGES_REQUESTED
}
