package com.nineouting.backend.minutes.client;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/**
 * ai-service RAG 호출: 회의록 넣기, 상태 조회, 지우기 (RAG 명세 2026-11-12, minutes-draft-contract.md 4장).
 * 서버 간 인증은 지금 없다 (내부망만). 400, 409는 Rejected(다시 보내도 같음), 408, 429, 5xx, 연결 실패는 Unavailable(다시 보냄).
 */
public class RagClient {

    public static final String SOURCE_TYPE = "minutes";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String PREFIX = "/internal/rag";

    private final RestClient restClient;

    public RagClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /** 문서 넣기. 응답의 status(accepted, ignored)를 돌려준다 */
    public String ingest(Object body) {
        JsonNode response = call(() -> restClient.post().uri(PREFIX + "/documents")
                .header(REQUEST_ID_HEADER, UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class));
        return response == null ? "accepted" : response.path("status").asString("accepted");
    }

    /** 상태 조회. 넣은 적 없으면 빈 값 */
    public Optional<JsonNode> status(Long minutesId) {
        try {
            return Optional.ofNullable(call(() -> restClient.get()
                    .uri(PREFIX + "/documents/{sourceType}/{docId}", SOURCE_TYPE, minutesId)
                    .header(REQUEST_ID_HEADER, UUID.randomUUID().toString())
                    .retrieve()
                    .body(JsonNode.class)));
        } catch (Rejected e) {
            if (e.getCause() instanceof HttpClientErrorException.NotFound) {
                return Optional.empty();
            }
            throw e;
        }
    }

    /** 문서 지우기 (없는 문서여도 204) */
    public void delete(Long minutesId) {
        call(() -> restClient.delete()
                .uri(PREFIX + "/documents/{sourceType}/{docId}", SOURCE_TYPE, minutesId)
                .header(REQUEST_ID_HEADER, UUID.randomUUID().toString())
                .retrieve()
                .toBodilessEntity());
    }

    private static <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (HttpClientErrorException e) {
            int status = e.getStatusCode().value();
            String message = "ai-service RAG가 거절했습니다: " + e.getStatusCode() + " " + errorOf(e);
            if (status == HttpStatus.REQUEST_TIMEOUT.value() || status == HttpStatus.TOO_MANY_REQUESTS.value()) {
                throw new Unavailable(message, e);
            }
            throw new Rejected(message, e);
        } catch (RestClientException e) {
            throw new Unavailable("ai-service RAG 호출 실패: " + e.getMessage(), e);
        }
    }

    /** 오류 본문의 error 코드만 (detail에는 회의록 글이 들어갈 수 있어 남기지 않는다) */
    private static String errorOf(HttpClientErrorException e) {
        try {
            JsonNode body = e.getResponseBodyAs(JsonNode.class);
            return body == null ? "" : body.path("error").asString("");
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    /** 잠시 뒤 다시 보내면 되는 실패 */
    public static class Unavailable extends RuntimeException {
        public Unavailable(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** 다시 보내도 결과가 같은 거절 */
    public static class Rejected extends Unavailable {
        public Rejected(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
