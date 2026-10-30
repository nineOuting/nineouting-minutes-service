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

    /** 202면 ACCEPTED. 400 등 4xx는 다시 보내도 같아 REJECTED, 503·연결 실패는 UNAVAILABLE */
    public Result requestDraft(String payloadJson) {
        try {
            aiService.post().uri("/internal/minutes/drafts").contentType(MediaType.APPLICATION_JSON)
                    .body(payloadJson).retrieve().toBodilessEntity();
            return Result.ACCEPTED;
        } catch (HttpClientErrorException e) {
            log.error("ai-service가 초안 요청을 거절했습니다: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            return Result.REJECTED;
        } catch (RestClientException e) {
            log.warn("ai-service에 초안을 요청하지 못했습니다: {}", e.toString());
            return Result.UNAVAILABLE;
        }
    }
}
