package com.nineouting.backend.minutes.dto;

import com.nineouting.backend.minutes.domain.ReviewStatus;
import java.time.LocalDateTime;

/** 홈의 검토 대기 회의록 (REQ-021 레거시, 023). myStatus는 미확인 또는 수정 요청 */
public record ReviewRequestItemResponse(Long minutesId, Long meetingId, Long workspaceId, Long folderId, String title,
                                        LocalDateTime meetingStartAt, ReviewStatus myStatus) {
}
