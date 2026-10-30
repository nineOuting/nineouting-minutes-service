package com.nineouting.backend.minutes.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 * 양식 저장·편집 (REQ-120~121). 항목 순서 = 배열 순서.
 *
 * @param layout 레이아웃 설정 (TemplateSettings 참고). 없으면 기본값
 */
public record TemplateRequest(
        @NotBlank(message = "양식 이름을 입력해 주세요")
        @Size(max = 50, message = "양식 이름은 50자 이하로 입력해 주세요")
        String name,
        @Size(max = 100, message = "설명은 100자 이하로 입력해 주세요")
        String description,
        @NotEmpty(message = "항목을 1개 이상 추가해 주세요")
        List<@Valid TemplateItemRequest> items,
        Map<String, Object> layout
) {
    public TemplateRequest {
        name = name == null ? null : name.trim();
        description = description == null ? "" : description.trim();
        layout = layout == null ? Map.of() : layout;
    }
}
