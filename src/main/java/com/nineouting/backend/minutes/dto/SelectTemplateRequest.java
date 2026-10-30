package com.nineouting.backend.minutes.dto;

import jakarta.validation.constraints.NotNull;

/** 양식 선택 (REQ-100, 레거시) */
public record SelectTemplateRequest(@NotNull(message = "양식을 선택해 주세요") Long templateId) {
}
