package com.nineouting.backend.minutes.dto;

import java.util.List;

/**
 * @param page 0부터 시작
 */
public record ArchivePageResponse(List<ArchiveItemResponse> items, int page, int size, long totalCount, boolean hasNext) {
}
