package com.nineouting.backend.minutes.dto;

/**
 * REQ-195 양식 가져오기 진행 상태. 화면은 PENDING이면 잠시 뒤 다시 묻는다.
 *
 * @param status     PENDING, SUCCEEDED, FAILED
 * @param templateId SUCCEEDED일 때 만든 양식
 * @param code       FAILED일 때 이유 (TEMPLATE_IMPORT_UNSUPPORTED 등)
 * @param message    FAILED일 때 화면에 보일 문구 (임시, 기획에서 정하는 중)
 */
public record TemplateImportStatusResponse(Long importId, String status, Long templateId, String code, String message) {
}
