package com.nineouting.backend.minutes.domain;

/** 작성 완료 회의록을 ai-service 검색용 저장소에 넣은 상태 (docs/services/rag-chatbot.md 2장). 작성 중이면 null */
public enum RagStatus {
    /** 보낼 차례 (작성 완료, 작성 완료 뒤 저장, 다시 보내기) */
    PENDING,
    /** ai-service가 접수함. 완료 콜백을 기다린다 */
    ACCEPTED,
    /** 검색 가능 */
    READY,
    /** 실패. 다시 보내지 않는다 (ragErrorCode) */
    FAILED
}
