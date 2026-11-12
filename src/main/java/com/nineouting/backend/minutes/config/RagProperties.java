package com.nineouting.backend.minutes.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * REQ-185 작성 완료 회의록을 챗봇 검색용 저장소에 넣기. ai-service 주소와 콜백 주소는 초안 설정(nineouting.minutes.draft)을 같이 쓴다.
 *
 * @param sendInterval    보낼 회의록을 찾는 주기
 * @param maxAttempts     다시 보내는 횟수 상한. 넘으면 실패로 둔다
 * @param retryDelay      처음 다시 보낼 때 기다리는 시간. 실패할 때마다 두 배
 * @param acceptedTimeout 접수 뒤 이 시간 안에 콜백이 없으면 상태 조회로 확인한다
 */
@ConfigurationProperties(prefix = "nineouting.minutes.rag")
public record RagProperties(Duration sendInterval, int maxAttempts, Duration retryDelay, Duration acceptedTimeout) {
}
