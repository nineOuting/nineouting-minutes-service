package com.nineouting.backend.minutes.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 회의록 양식 (REQ-119). isDefault는 기본 양식 (공용 기본 양식이나 그것을 고친 내 기본 양식, QA 64). 고칠 수 있고 지울 수 없다. favorite은 내가 즐겨찾기했는지 (REQ-123).
 * updatedAt은 마지막으로 고친 시각 (양식 목록 카드의 "수정 날짜", 한국 시간).
 * shared는 다른 사람의 양식이 내 워크스페이스 기본 양식으로 공유된 것 (REQ-199)이나 워크스페이스 양식함 사본. 쓸 수만 있고 고칠 수 없다.
 * workspaceId는 워크스페이스 양식함 사본일 때 그 워크스페이스 (REQ-213), 아니면 null
 */
public record TemplateResponse(Long templateId, String name, String description, boolean isDefault,
                               List<TemplateItemResponse> items, Map<String, Object> layout, boolean favorite,
                               LocalDateTime updatedAt, boolean shared, Long workspaceId) {

    public TemplateResponse asShared() {
        return new TemplateResponse(templateId, name, description, isDefault, items, layout, favorite, updatedAt, true,
                workspaceId);
    }

    public record TemplateItemResponse(String title, String description, Map<String, Object> settings) {
    }
}
