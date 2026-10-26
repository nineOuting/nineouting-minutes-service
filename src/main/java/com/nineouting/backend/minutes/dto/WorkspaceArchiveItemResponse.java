package com.nineouting.backend.minutes.dto;

import com.nineouting.backend.minutes.domain.MinutesStatus;
import java.time.LocalDateTime;
import java.util.List;

/** 워크스페이스 아카이브 목록 항목 (REQ-053). favorite·important는 내 표시 */
public record WorkspaceArchiveItemResponse(Long minutesId, Long meetingId, String title, LocalDateTime meetingStartAt,
                                           String location, MinutesStatus status, Long folderId, String folderName,
                                           List<PersonResponse> attendees, boolean favorite, boolean important) {
}
