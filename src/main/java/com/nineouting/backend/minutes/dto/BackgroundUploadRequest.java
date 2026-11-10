package com.nineouting.backend.minutes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/** REQ-157 양식 배경 이미지 업로드 주소 요청 (PNG, JPEG, WebP, 5MB 이하) */
public record BackgroundUploadRequest(
        @NotBlank(message = "파일 형식이 없습니다")
        String contentType,
        @Positive(message = "빈 파일은 올릴 수 없습니다")
        long size
) {
}
