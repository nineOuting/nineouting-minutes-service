package com.nineouting.backend.minutes.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/**
 * AI 회의록 초안에 쓰는 서비스 간 호출. meeting-service에서 재료를 받고, ai-service에 요청한다
 * (ai-service docs/api/minutes-draft-contract.md 2장). 결과는 콜백으로 온다.
 */
@Slf4j
public class DraftClient {

    public enum Result { ACCEPTED, REJECTED, UNAVAILABLE }

    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    private final RestClient meetingService;
    private final RestClient aiService;

    public DraftClient(RestClient meetingService, RestClient aiService) {
        this.meetingService = meetingService;
        this.aiService = aiService;
    }

    /** {meetingId, ready, agendas[...], summary{agendas[...]}}. 연결할 수 없으면 null */
    public JsonNode draftSource(Long meetingId) {
        try {
            JsonNode body = meetingService.get().uri("/internal/meetings/{meetingId}/draft-source", meetingId)
                    .retrieve().body(JsonNode.class);
            return body == null ? null : body.path("data");
        } catch (RestClientException e) {
            log.warn("meeting-service에서 초안 재료를 받지 못했습니다 (meetingId={}): {}", meetingId, e.toString());
            return null;
        }
    }

    /**
     * 202면 ACCEPTED. 400 등 4xx는 다시 보내도 같아 REJECTED, 503·연결 실패는 UNAVAILABLE.
     * 404는 ai-service가 초안 기능을 켜지 않은 것이라(명세 2.2) UNAVAILABLE로 본다.
     * X-Request-Id에 요청 번호를 실어 두 서비스 로그를 잇는다 (명세 1장 공통 규칙)
     */
    public Result requestDraft(String payloadJson, String requestId) {
        try {
            aiService.post().uri("/internal/minutes/drafts").contentType(MediaType.APPLICATION_JSON)
                    .header(REQUEST_ID_HEADER, requestId).body(payloadJson).retrieve().toBodilessEntity();
            return Result.ACCEPTED;
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("ai-service에 초안 기능이 켜져 있지 않습니다 (requestId={})", requestId);
            return Result.UNAVAILABLE;
        } catch (HttpClientErrorException e) {
            log.error("ai-service가 초안 요청을 거절했습니다: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            return Result.REJECTED;
        } catch (RestClientException e) {
            log.warn("ai-service에 초안을 요청하지 못했습니다: {}", e.toString());
            return Result.UNAVAILABLE;
        }
    }
}
