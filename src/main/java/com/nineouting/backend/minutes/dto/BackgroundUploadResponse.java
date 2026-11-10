package com.nineouting.backend.minutes.dto;

/**
 * REQ-157 배경 이미지 업로드 주소. 브라우저는 uploadUrl로 PUT(Content-Type은 요청한 값 그대로)한 뒤
 * 양식을 저장할 때 layout.backgroundImageKey에 imageKey를 넣는다
 */
public record BackgroundUploadResponse(String imageKey, String uploadUrl, String contentType) {
}
