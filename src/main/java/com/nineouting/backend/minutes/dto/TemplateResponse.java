package com.nineouting.backend.minutes.dto;

import java.util.List;

/** 회의록 양식 (REQ-107). isDefault면 고칠 수 없다 */
public record TemplateResponse(Long templateId, String name, boolean isDefault, List<String> items) {
}
