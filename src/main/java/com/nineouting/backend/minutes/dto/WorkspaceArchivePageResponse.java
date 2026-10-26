package com.nineouting.backend.minutes.dto;

import java.util.List;

/**
 * @param page 0부터 시작
 */
public record WorkspaceArchivePageResponse(List<WorkspaceArchiveItemResponse> items, int page, int size, long totalCount,
                                           boolean hasNext) {
}
