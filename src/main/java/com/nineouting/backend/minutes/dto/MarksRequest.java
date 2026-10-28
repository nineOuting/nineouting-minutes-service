package com.nineouting.backend.minutes.dto;

/** 즐겨찾기·중요 표시 (REQ-056 레거시). 개인 표시 */
public record MarksRequest(boolean favorite, boolean important) {
}
