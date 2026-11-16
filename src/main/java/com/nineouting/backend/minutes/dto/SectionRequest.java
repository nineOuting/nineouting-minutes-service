package com.nineouting.backend.minutes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param sectionId   기존 칸을 고치면 그 칸 ID, 새 칸이면 null. 요청에 없는 기존 칸은 지운다
 * @param richContent 서식을 담은 HTML (REQ-194). 없으면 null. 서버가 허용한 태그만 남긴다
 */
public record SectionRequest(
        Long sectionId,
        @NotBlank(message = "항목 제목을 입력해 주세요")
        @Size(max = 100, message = "항목 제목은 100자 이하로 입력해 주세요")
        String title,
        @NotNull(message = "내용이 없습니다")
        @Size(max = 20000, message = "항목 내용은 20000자 이하로 입력해 주세요")
        String content,
        @Size(max = 100000, message = "서식을 포함한 항목 내용이 너무 길어요")
        String richContent
) {
    public SectionRequest {
        title = title == null ? null : title.trim();
    }
}
