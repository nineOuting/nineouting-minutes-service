package com.nineouting.backend.minutes.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * REQ-195 양식 가져오기 (ai-service docs/api/template-import-contract.md 3.1). ai-service 주소는 초안 설정(draft.ai-service-url)을 같이 쓴다.
 *
 * @param readTimeout    AI 서버 응답을 기다리는 한도. 보통 4~25초, 다시 시키면 최대 약 2분이라 그보다 길게 둔다
 * @param fileUrlTtl     AI 서버가 파일을 내려받을 서명 주소의 유효 시간
 * @param maxFileBytes   올릴 수 있는 파일 크기 (AI 서버 상한 20MB와 같다)
 * @param workers        이 서버가 동시에 AI 서버에 맡기는 가져오기 수 (AI 서버 동시 처리 한도 4와 같다)
 * @param pendingTimeout 접수한 뒤 이 시간이 지나도 PENDING이면 실패로 본다 (처리하던 서버가 꺼진 경우)
 */
@ConfigurationProperties(prefix = "nineouting.minutes.template-import")
public record TemplateImportProperties(Duration readTimeout, Duration fileUrlTtl, Long maxFileBytes,
                                       Integer workers, Duration pendingTimeout) {

    public TemplateImportProperties {
        readTimeout = readTimeout == null ? Duration.ofSeconds(150) : readTimeout;
        fileUrlTtl = fileUrlTtl == null ? Duration.ofMinutes(10) : fileUrlTtl;
        maxFileBytes = maxFileBytes == null ? 20L * 1024 * 1024 : maxFileBytes;
        workers = workers == null ? 4 : workers;
        pendingTimeout = pendingTimeout == null ? Duration.ofMinutes(5) : pendingTimeout;
    }
}
