package com.nineouting.backend.minutes.dto;

import java.time.LocalDateTime;

/** 수정 기록 (REQ-104, 111) */
public record RevisionResponse(int version, PersonResponse editor, LocalDateTime editedAt, boolean afterConfirm) {
}
