package com.nineouting.backend.minutes.dto;

/** 회의록 아카이브 폴더 탭의 회의록 수 */
public record ArchiveFolderCountResponse(Long folderId, long minutesCount) {
}
