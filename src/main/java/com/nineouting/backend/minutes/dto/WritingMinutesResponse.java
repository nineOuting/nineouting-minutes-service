package com.nineouting.backend.minutes.dto;

import java.time.LocalDateTime;

/** 홈의 작성 중인 회의록 (REQ-142). 내가 작성자인 작성 중 회의록 */
public record WritingMinutesResponse(Long minutesId, Long meetingId, String title, LocalDateTime meetingStartAt,
                                     String location, Long workspaceId, String workspaceName, Long folderId,
                                     String folderName) {
}
