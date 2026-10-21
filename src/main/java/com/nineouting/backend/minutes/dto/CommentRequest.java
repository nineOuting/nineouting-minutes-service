package com.nineouting.backend.minutes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 검토 코멘트 (REQ-108): 칸에서 고른 문장(quote)과 의견(body) */
public record CommentRequest(
        @NotNull(message = "칸을 선택해 주세요") Long sectionId,
        @NotBlank(message = "수정할 문장을 선택해 주세요")
        @Size(max = 1000, message = "선택한 문장은 1000자 이하여야 합니다")
        String quote,
        @NotBlank(message = "의견을 입력해 주세요")
        @Size(max = 1000, message = "의견은 1000자 이하로 입력해 주세요")
        String body
) {
}
