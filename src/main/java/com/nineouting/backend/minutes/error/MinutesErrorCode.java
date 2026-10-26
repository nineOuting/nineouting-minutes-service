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
    TEMPLATE_NOT_EDITABLE(HttpStatus.FORBIDDEN, "기본 양식은 고칠 수 없습니다");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
