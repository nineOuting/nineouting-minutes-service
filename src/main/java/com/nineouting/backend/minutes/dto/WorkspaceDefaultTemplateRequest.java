package com.nineouting.backend.minutes.dto;

import jakarta.validation.constraints.NotNull;

/** REQ-213 워크스페이스 기본 양식으로 정할 양식 (공용 기본 양식, 내 양식, 이 워크스페이스 사본) */
public record WorkspaceDefaultTemplateRequest(@NotNull(message = "양식을 골라 주세요") Long templateId) {
}
