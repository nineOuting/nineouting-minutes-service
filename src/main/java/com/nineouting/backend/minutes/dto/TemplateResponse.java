package com.nineouting.backend.minutes.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 회의록 양식 (REQ-119). isDefault면 고칠 수 없다. favorite은 내가 즐겨찾기했는지 (REQ-123).
 * updatedAt은 마지막으로 고친 시각 (양식 목록 카드의 "수정 날짜", 한국 시간)
 */
public record TemplateResponse(Long templateId, String name, String description, boolean isDefault,
                               List<TemplateItemResponse> items, Map<String, Object> layout, boolean favorite,
                               LocalDateTime updatedAt) {

    public record TemplateItemResponse(String title, String description, Map<String, Object> settings) {
    }
}
