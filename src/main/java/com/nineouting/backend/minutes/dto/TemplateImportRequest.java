package com.nineouting.backend.minutes.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * REQ-195 올린 양식 파일을 읽어 새 개인 양식으로 저장한다.
 *
 * @param name 새 양식 이름 (보통 파일 이름에서 확장자를 뺀 것). 50자를 넘으면 자른다
 */
public record TemplateImportRequest(
        @NotBlank(message = "파일 위치가 없습니다")
        String fileKey,
        @NotBlank(message = "양식 이름을 입력해 주세요")
        String name
) {
    public TemplateImportRequest {
        name = name == null ? null : name.trim();
    }
}
