package com.nineouting.backend.minutes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * 양식 항목 (REQ-120).
 *
 * @param settings 화면 표시 설정 (TemplateSettings 참고). 없으면 기본값
 */
public record TemplateItemRequest(
        @NotBlank(message = "항목 제목을 입력해 주세요")
        @Size(max = 100, message = "항목 제목은 100자 이하로 입력해 주세요")
        String title,
        @Size(max = 200, message = "항목 설명은 200자 이하로 입력해 주세요")
        String description,
        Map<String, Object> settings
) {
    public TemplateItemRequest {
        title = title == null ? null : title.trim();
        description = description == null ? "" : description.trim();
        settings = settings == null ? Map.of() : settings;
    }
}
