package com.nineouting.backend.minutes.dto;

import com.nineouting.backend.minutes.domain.MinutesStatus;
import java.time.LocalDateTime;

/** 아카이브 목록 항목 (REQ-053). 작성 중·검토 중·확정 모두. confirmedAt은 확정 전이면 null, favorite·important는 내 표시 */
public record ArchiveItemResponse(Long minutesId, Long meetingId, String title, LocalDateTime meetingStartAt,
                                  String location, MinutesStatus status, LocalDateTime confirmedAt, boolean favorite,
                                  boolean important) {
}
