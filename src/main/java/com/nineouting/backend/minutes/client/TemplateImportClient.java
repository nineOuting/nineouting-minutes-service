package com.nineouting.backend.minutes.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * REQ-195 양식 가져오기. ai-service에 파일 서명 주소를 주면 읽은 항목을 바로 돌려준다 (동기,
 * ai-service docs/api/template-import-contract.md 3.1). 서명 주소와 양식 글은 로그에 남기지 않는다.
 */
@Slf4j
public class TemplateImportClient {

    /** SUCCEEDED면 body에 fields, header, footer. FAILED면 errorCode. UNAVAILABLE은 AI 서버가 바쁘거나 준비되지 않았거나 닿지 않음 */
    public record Result(Status status, JsonNode body, String errorCode) {
        public enum Status { SUCCEEDED, FAILED, UNAVAILABLE }

        static Result unavailable() {
            return new Result(Status.UNAVAILABLE, null, null);
        }
    }

    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    private final RestClient aiService;
    private final boolean enabled;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public TemplateImportClient(RestClient aiService, boolean enabled) {
        this.aiService = aiService;
        this.enabled = enabled;
    }

    public Result importTemplate(String fileUrl, String requestId) {
        if (!enabled) {
            log.warn("ai-service 주소가 없어 양식을 가져오지 못했습니다 (requestId={})", requestId);
            return Result.unavailable();
        }
        try {
            JsonNode body = aiService.post().uri("/internal/minutes/template-imports")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(REQUEST_ID_HEADER, requestId)
                    .body(jsonMapper.createObjectNode().put("requestId", requestId).put("fileUrl", fileUrl))
                    .retrieve().body(JsonNode.class);
            if (body == null) {
                return new Result(Result.Status.FAILED, null, "internal_error");
            }
            if ("SUCCEEDED".equals(body.path("status").asString(""))) {
                return new Result(Result.Status.SUCCEEDED, body, null);
            }
            String errorCode = body.path("errorCode").asString("internal_error");
            log.info("ai-service가 양식을 읽지 못했습니다 (requestId={}, errorCode={})", requestId, errorCode);
            return new Result(Result.Status.FAILED, body, errorCode);
        } catch (HttpClientErrorException e) {
            // 400 INVALID_INPUT: 허용하지 않은 저장소 주소 등 이쪽 설정 문제
            log.error("ai-service가 양식 가져오기 요청을 거절했습니다 (requestId={}): {}", requestId, e.getStatusCode());
            return new Result(Result.Status.FAILED, null, "internal_error");
        } catch (RestClientException e) {
            // 503 BUSY, NOT_READY, 시간 초과, 연결 실패
            log.warn("ai-service에 양식 가져오기를 요청하지 못했습니다 (requestId={}): {}", requestId, e.getClass().getSimpleName());
            return Result.unavailable();
        }
    }
}
