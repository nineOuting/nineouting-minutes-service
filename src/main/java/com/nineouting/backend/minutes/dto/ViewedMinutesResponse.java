package com.nineouting.backend.minutes.dto;

import java.time.LocalDateTime;

/** 아카이브의 최근 본 회의록 (REQ-159) */
public record ViewedMinutesResponse(Long minutesId, Long meetingId, String title, LocalDateTime meetingStartAt,
                                    LocalDateTime viewedAt) {
}
