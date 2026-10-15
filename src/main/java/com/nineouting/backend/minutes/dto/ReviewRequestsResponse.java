package com.nineouting.backend.minutes.dto;

import java.util.List;

/** 홈의 검토 대기 회의록 (REQ-021~023). 회의 날짜가 오래된 순 */
public record ReviewRequestsResponse(int count, List<ReviewRequestItemResponse> items) {
}
