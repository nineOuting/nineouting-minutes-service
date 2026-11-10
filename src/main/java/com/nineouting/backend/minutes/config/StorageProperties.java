package com.nineouting.backend.minutes.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 양식 배경 이미지 저장소 (S3, REQ-157). endpoint를 비우면 AWS S3, 채우면 LocalStack 같은 호환 저장소.
 * 접근 키를 비우면 AWS 기본 자격 증명(운영은 Pod Identity)을 쓴다.
 * presignEndpoint는 브라우저에 줄 주소만 다를 때 쓴다 (로컬 통합 환경: 서비스는 localstack:4566, 브라우저는 localhost:4566).
 */
@ConfigurationProperties("nineouting.minutes.storage")
public record StorageProperties(String bucket, String region, String endpoint, String presignEndpoint,
                                String accessKey, String secretKey, Duration uploadUrlTtl, Duration downloadUrlTtl) {

    public boolean hasEndpoint() {
        return endpoint != null && !endpoint.isBlank();
    }

    /** 업로드, 내려받기 주소를 만들 때 쓸 주소. 따로 없으면 endpoint */
    public String presignEndpointOrDefault() {
        return presignEndpoint != null && !presignEndpoint.isBlank() ? presignEndpoint : endpoint;
    }

    public boolean hasStaticCredentials() {
        return accessKey != null && !accessKey.isBlank();
    }
}
