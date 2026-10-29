package com.nineouting.backend.minutes.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 회의록 초안 (REQ-101~103, ai-service docs/api/minutes-draft-contract.md).
 *
 * @param aiServiceUrl      ai-service 주소. 비우면 초안을 요청하지 않는다
 * @param meetingServiceUrl 초안 재료(안건·오늘 회의 요약)를 받을 meeting-service 주소
 * @param callbackBaseUrl   ai-service가 결과를 보낼 이 서비스 주소 (ai-service MINUTES_CALLBACK_ALLOWED_HOSTS에 있어야 한다)
 */
@ConfigurationProperties(prefix = "nineouting.minutes.draft")
public record DraftProperties(String aiServiceUrl, String meetingServiceUrl, String callbackBaseUrl,
                              Duration connectTimeout, Duration readTimeout) {

    public DraftProperties {
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(10) : readTimeout;
    }

    public boolean enabled() {
        return aiServiceUrl != null && !aiServiceUrl.isBlank();
    }
}
