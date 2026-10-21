package com.nineouting.backend.minutes.dto;

import java.time.LocalDateTime;

/** 홈의 최근 확정 회의록 (REQ-026~027) */
public record RecentMinutesResponse(Long minutesId, Long meetingId, String title, LocalDateTime meetingStartAt,
                                    LocalDateTime confirmedAt, Long workspaceId, String workspaceName, Long folderId,
                                    String folderName) {
}
