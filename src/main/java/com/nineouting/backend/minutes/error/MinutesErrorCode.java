package com.nineouting.backend.minutes.error;

import com.nineouting.backend.common.web.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * minutes-service 에러 코드 (docs/services/minutes-service.md 4.5).
 * 다른 서비스 연결 실패(503)는 client-common의 ClientErrorCode를 쓴다.
 */
@Getter
@RequiredArgsConstructor
public enum MinutesErrorCode implements ErrorCode {

    MINUTES_NOT_READY(HttpStatus.NOT_FOUND, "회의록을 준비하고 있습니다. 잠시 후 다시 시도해 주세요"),
    FOLDER_ACCESS_DENIED(HttpStatus.FORBIDDEN, "폴더에 접근할 권한이 없습니다"),
    NOT_MINUTES_WRITER(HttpStatus.FORBIDDEN, "회의록 작성자만 할 수 있습니다"),
    NOT_MINUTES_REVIEWER(HttpStatus.FORBIDDEN, "검토 대상 참석자만 할 수 있습니다"),
    MINUTES_NOT_WRITING(HttpStatus.CONFLICT, "작성 중인 회의록만 양식을 바꿀 수 있습니다"),
    MINUTES_NOT_REVIEWING(HttpStatus.CONFLICT, "검토 중인 회의록이 아닙니다"),
    MINUTES_ALREADY_CONFIRMED(HttpStatus.CONFLICT, "이미 확정된 회의록입니다"),
    MINUTES_VERSION_CONFLICT(HttpStatus.CONFLICT, "다른 곳에서 먼저 저장했습니다. 새로고침 후 다시 저장해 주세요"),
    TEMPLATE_NOT_EDITABLE(HttpStatus.FORBIDDEN, "기본 양식은 고칠 수 없습니다"),
    TEMPLATE_BACKGROUND_INVALID(HttpStatus.BAD_REQUEST, "배경 사진은 5MB 이하의 PNG, JPEG, WebP만 올릴 수 있습니다"),
    TEMPLATE_BACKGROUND_NOT_UPLOADED(HttpStatus.CONFLICT, "배경 사진이 아직 올라가지 않았습니다. 다시 올려 주세요"),
    // REQ-195 양식 가져오기. 문구는 임시 (2026-11-24, 기획에서 정하는 중)
    TEMPLATE_IMPORT_INVALID(HttpStatus.BAD_REQUEST, "PDF나 Word 파일만 20MB까지 올릴 수 있어요"),
    TEMPLATE_IMPORT_NOT_UPLOADED(HttpStatus.CONFLICT, "파일이 아직 올라가지 않았어요. 다시 올려 주세요"),
    TEMPLATE_IMPORT_UNSUPPORTED(HttpStatus.UNPROCESSABLE_CONTENT, "PDF나 Word 파일만 가져올 수 있어요"),
    TEMPLATE_IMPORT_ENCRYPTED(HttpStatus.UNPROCESSABLE_CONTENT, "암호가 걸린 파일은 가져올 수 없어요"),
    TEMPLATE_IMPORT_TOO_LARGE(HttpStatus.UNPROCESSABLE_CONTENT, "20MB, 10쪽까지만 가져올 수 있어요"),
    TEMPLATE_IMPORT_NO_TEXT(HttpStatus.UNPROCESSABLE_CONTENT, "글자를 읽을 수 없는 파일이에요 (스캔한 PDF 등)"),
    TEMPLATE_IMPORT_FAILED(HttpStatus.UNPROCESSABLE_CONTENT, "양식을 분석하지 못했어요. 다른 파일로 다시 시도해 주세요"),
    TEMPLATE_IMPORT_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "지금은 양식을 분석할 수 없어요. 잠시 후 다시 시도해 주세요"),
    MINUTES_TEMPLATE_REQUIRED(HttpStatus.CONFLICT, "양식을 먼저 골라 주세요"),
    DRAFT_SOURCE_NOT_READY(HttpStatus.CONFLICT, "오늘 회의 요약이 아직 준비되지 않았습니다. 잠시 후 다시 시도해 주세요"),
    DRAFT_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "지금은 AI 초안을 만들 수 없습니다. 잠시 후 다시 시도해 주세요"),
    DRAFT_IN_PROGRESS(HttpStatus.CONFLICT, "AI 초안을 만드는 중입니다. 잠시 후 다시 확인해 주세요");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
