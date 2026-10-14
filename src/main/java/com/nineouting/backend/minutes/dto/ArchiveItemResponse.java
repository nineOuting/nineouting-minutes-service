package com.nineouting.backend.minutes.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 아카이브 목록 항목 (REQ-051). favorite·important는 내 표시 */
public record ArchiveItemResponse(Long minutesId, Long meetingId, String title, LocalDateTime meetingStartAt,
                                  LocalDateTime confirmedAt, List<String> tags, boolean favorite, boolean important) {
}
