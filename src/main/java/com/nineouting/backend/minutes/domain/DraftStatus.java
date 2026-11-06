package com.nineouting.backend.minutes.domain;

/** AI 회의록 초안 상태 (REQ-101~103, REQ-098 "만들어지기 전에는 준비 중") */
public enum DraftStatus {
    /** 오늘 회의 요약(todaySummary final)이 오기를 기다리는 중. 오면 자동으로 요청한다 (화면은 "준비 중") */
    WAITING_SOURCE,
    /** ai-service가 만드는 중 */
    REQUESTED,
    /** 비어 있던 칸에 초안을 채웠다 */
    SUCCEEDED,
    /** 만들지 못했다 (draftError). 다시 요청할 수 있다 */
    FAILED
}
