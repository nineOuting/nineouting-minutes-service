package com.nineouting.backend.minutes.dto;

import java.time.LocalDateTime;

/** 아카이브의 최근 본 회의록 (REQ-159). 검색창 아래 "폴더 · 언제 봤어요"에 폴더 이름을 쓴다 */
public record ViewedMinutesResponse(Long minutesId, Long meetingId, String title, LocalDateTime meetingStartAt,
                                    LocalDateTime viewedAt, Long folderId, String folderName) {
}
