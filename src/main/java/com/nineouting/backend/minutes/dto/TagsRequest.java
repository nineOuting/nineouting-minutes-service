package com.nineouting.backend.minutes.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/** 아카이브 태그 (REQ-053). 최대 10개, 태그마다 20자. 앞뒤 공백·중복은 지운다 */
public record TagsRequest(@NotNull(message = "태그 목록이 없습니다") List<String> tags) {
}
