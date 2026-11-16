package com.nineouting.backend.minutes.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * REQ-194 회의록 칸 서식. 끄면 서식을 저장하지도 내려주지도 않아 예전처럼 서식 없는 글만 쓴다 (되돌리기용 스위치).
 * 화면은 회의록 응답의 richText를 보고 서식 막대와 서식 입력 칸을 켜고 끈다.
 *
 * @param enabled 서식 기능을 켤지
 */
@ConfigurationProperties(prefix = "nineouting.minutes.rich-text")
public record RichTextProperties(boolean enabled) {
}
