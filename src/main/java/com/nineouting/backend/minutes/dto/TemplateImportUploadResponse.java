package com.nineouting.backend.minutes.dto;

/**
 * REQ-195 양식 가져오기 파일 업로드 주소. 브라우저는 uploadUrl로 PUT(Content-Type은 요청한 값 그대로)한 뒤
 * POST /api/minutes-templates/import에 fileKey를 보낸다
 */
public record TemplateImportUploadResponse(String fileKey, String uploadUrl, String contentType) {
}
