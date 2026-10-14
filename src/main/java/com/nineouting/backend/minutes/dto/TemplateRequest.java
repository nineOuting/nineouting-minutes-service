package com.nineouting.backend.minutes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 양식 저장·편집 (REQ-107, 108). 항목 순서 = 배열 순서 */
public record TemplateRequest(
        @NotBlank(message = "양식 이름을 입력해 주세요")
        @Size(max = 50, message = "양식 이름은 50자 이하로 입력해 주세요")
        String name,
        @NotEmpty(message = "항목을 1개 이상 추가해 주세요")
        List<@NotBlank(message = "항목 제목을 입력해 주세요") @Size(max = 100, message = "항목 제목은 100자 이하로 입력해 주세요") String> items
) {
    public TemplateRequest {
        name = name == null ? null : name.trim();
        items = items == null ? null : items.stream().map(item -> item == null ? null : item.trim()).toList();
    }
}
