package com.nineouting.backend.minutes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nineouting.backend.minutes.domain.RagStatus;
import com.nineouting.backend.minutes.repository.MinutesRepository;
import com.nineouting.backend.minutes.service.MinutesPurger;
import com.nineouting.backend.minutes.service.MinutesRagSync;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/** REQ-185 작성 완료 회의록을 챗봇 검색용 저장소에 넣기 (docs/services/rag-chatbot.md 3장). ai-service는 가짜 서버 */
class MinutesRagTest extends ApiTestSupport {

    private static final HttpServer SERVER;
    private static final List<String> REQUESTS = new CopyOnWriteArrayList<>();
    /** true면 RAG가 꺼진 ai-service처럼 404로 답한다 */
    private static final AtomicBoolean RAG_OFF = new AtomicBoolean(false);
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 12, 10, 0);

    static {
        try {
            SERVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        SERVER.createContext("/", MinutesRagTest::handle);
        SERVER.start();
    }

    @DynamicPropertySource
    static void aiService(DynamicPropertyRegistry registry) {
        String url = "http://127.0.0.1:" + SERVER.getAddress().getPort();
        registry.add("nineouting.minutes.draft.ai-service-url", () -> url);
        registry.add("nineouting.minutes.draft.meeting-service-url", () -> url);
        registry.add("nineouting.minutes.draft.callback-base-url", () -> "http://minutes-service:8084");
        // 테스트가 직접 run()을 부른다
        registry.add("nineouting.minutes.rag.send-interval", () -> "1h");
    }

    @Autowired
    MinutesRagSync minutesRagSync;

    @Autowired
    MinutesRepository minutesRepository;

    @Autowired
    MinutesPurger minutesPurger;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Test
    void 작성_완료한_회의록을_넣고_그_뒤_저장할_때마다_새_버전으로_다시_넣는다() throws Exception {
        Long writer = newId();
        Long folderId = folder(newId(), "기획", writer);
        Long minutesId = finishMeeting(newId(), folderId, "주간 회의", START, writer, writer);
        writeContent(minutesId, writer, "배포 일정 논의", "- 배포 일정: 30일 배포");

        // 작성 중인 회의록은 넣지 않는다
        minutesRagSync.run();
        assertThat(ingests(minutesId)).isEmpty();

        call(writer, post("/api/minutes/" + minutesId + "/complete"), null).andExpect(status().isOk());
        minutesRagSync.run();
        String first = ingests(minutesId).getLast();
        String firstVersion = JsonPath.read(first, "$.docVersion");
        assertThat(JsonPath.<String>read(first, "$.sourceType")).isEqualTo("minutes");
        assertThat(JsonPath.<String>read(first, "$.title")).isEqualTo("주간 회의");
        assertThat(JsonPath.<String>read(first, "$.minutes.formatVersion")).isEqualTo("1.0");
        assertThat(JsonPath.<String>read(first, "$.minutes.meetingStartAt")).isEqualTo("2026-11-12T10:00");
        assertThat(JsonPath.<List<String>>read(first, "$.minutes.sections[*].title"))
                .containsExactly("안건별 논의", "결정 사항", "미결 안건", "할 일");
        assertThat(JsonPath.<String>read(first, "$.minutes.sections[1].content")).isEqualTo("- 배포 일정: 30일 배포");
        assertThat(JsonPath.<String>read(first, "$.callbackUrl")).isEqualTo("http://minutes-service:8084/internal/rag/ingest-result");
        assertThat(minutesRepository.findById(minutesId).orElseThrow().getRagStatus()).isEqualTo(RagStatus.ACCEPTED);

        callback(minutesId, firstVersion, "ready");
        assertThat(minutesRepository.findById(minutesId).orElseThrow().getRagStatus()).isEqualTo(RagStatus.READY);

        // 작성 완료 뒤 저장하면 새 버전으로 다시 넣는다. 늦게 온 옛 버전 결과는 무시한다
        saveFirstSection(minutesId, writer, "고친 논의");
        assertThat(minutesRepository.findById(minutesId).orElseThrow().getRagStatus()).isEqualTo(RagStatus.PENDING);
        minutesRagSync.run();
        String second = ingests(minutesId).getLast();
        assertThat(Integer.parseInt(JsonPath.read(second, "$.docVersion"))).isGreaterThan(Integer.parseInt(firstVersion));
        assertThat(JsonPath.<String>read(second, "$.minutes.sections[0].content")).isEqualTo("고친 논의");
        callback(minutesId, firstVersion, "ready");
        assertThat(minutesRepository.findById(minutesId).orElseThrow().getRagStatus()).isEqualTo(RagStatus.ACCEPTED);
    }

    @Test
    void ai_service에_RAG가_꺼져_있으면_실패로_굳히지_않고_켜진_뒤_보낸다() throws Exception {
        Long writer = newId();
        Long folderId = folder(newId(), "기획", writer);
        RAG_OFF.set(true);
        try {
            Long minutesId = confirmed(folderId, writer, START);
            for (int i = 0; i < 6; i++) {
                transactionTemplate.executeWithoutResult(status -> minutesRepository.findById(minutesId).orElseThrow()
                        .ragRetryLater("unavailable", LocalDateTime.now().minusSeconds(1)));
                minutesRagSync.run();
            }
            assertThat(minutesRepository.findById(minutesId).orElseThrow().getRagStatus()).isEqualTo(RagStatus.PENDING);

            RAG_OFF.set(false);
            transactionTemplate.executeWithoutResult(status -> minutesRepository.findById(minutesId).orElseThrow()
                    .ragRetryLater("unavailable", LocalDateTime.now().minusSeconds(1)));
            minutesRagSync.run();
            assertThat(minutesRepository.findById(minutesId).orElseThrow().getRagStatus()).isEqualTo(RagStatus.ACCEPTED);
        } finally {
            RAG_OFF.set(false);
        }
    }

    @Test
    void 챗봇이_찾을_회의록은_그_폴더의_작성_완료_회의록만_최근_회의부터_준다() throws Exception {
        Long writer = newId();
        Long folderId = folder(newId(), "기획", writer);
        Long otherFolder = folder(newId(), "다른 폴더", writer);
        Long older = confirmed(folderId, writer, START.minusDays(7));
        Long newer = confirmed(folderId, writer, START);
        Long writing = finishMeeting(newId(), folderId, "작성 중", START.plusDays(1), writer, writer);
        Long trashed = confirmed(folderId, writer, START.plusDays(2));
        transactionTemplate.executeWithoutResult(status ->
                minutesRepository.findById(trashed).orElseThrow().trash(LocalDateTime.now()));
        confirmed(otherFolder, writer, START);

        mockMvc.perform(get("/internal/minutes/rag-docs").param("folderId", folderId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0]").value(newer))
                .andExpect(jsonPath("$[1]").value(older));
        assertThat(writing).isNotNull();
    }

    @Test
    void 영구_삭제하면_검색용_저장소에서도_지운다() throws Exception {
        Long writer = newId();
        Long folderId = folder(newId(), "기획", writer);
        Long minutesId = confirmed(folderId, writer, START);

        transactionTemplate.executeWithoutResult(status -> minutesPurger.purge(List.of(minutesId)));

        assertThat(REQUESTS).contains("DELETE /internal/rag/documents/minutes/" + minutesId);
    }

    private Long confirmed(Long folderId, Long writer, LocalDateTime startAt) throws Exception {
        Long minutesId = finishMeeting(newId(), folderId, "회의 " + startAt, startAt, writer, writer);
        writeContent(minutesId, writer, "논의");
        call(writer, post("/api/minutes/" + minutesId + "/complete"), null).andExpect(status().isOk());
        return minutesId;
    }

    private void saveFirstSection(Long minutesId, Long writer, String content) throws Exception {
        String detail = call(writer, get("/api/minutes/" + minutesId), null).andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(detail, "$.data.sections[*].sectionId");
        List<String> titles = JsonPath.read(detail, "$.data.sections[*].title");
        List<String> contents = JsonPath.read(detail, "$.data.sections[*].content");
        int version = JsonPath.read(detail, "$.data.version");
        StringBuilder sections = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            sections.append(i == 0 ? "" : ",").append("{\"sectionId\":%d,\"title\":\"%s\",\"content\":\"%s\"}"
                    .formatted(ids.get(i), titles.get(i), i == 0 ? content : contents.get(i)));
        }
        call(writer, put("/api/minutes/" + minutesId + "/sections"),
                "{\"version\":%d,\"sections\":[%s]}".formatted(version, sections)).andExpect(status().isOk());
    }

    private void callback(Long minutesId, String version, String status) throws Exception {
        mockMvc.perform(post("/internal/rag/ingest-result").contentType(MediaType.APPLICATION_JSON).content("""
                        {"sourceType":"minutes","docId":%d,"docVersion":"%s","status":"%s","errorCode":null,"chunkCount":3}"""
                        .formatted(minutesId, version, status)))
                .andExpect(status().isNoContent());
    }

    private static List<String> ingests(Long minutesId) {
        return REQUESTS.stream()
                .filter(request -> request.startsWith("POST /internal/rag/documents\n"))
                .map(request -> request.substring(request.indexOf('\n') + 1))
                .filter(body -> ((Number) JsonPath.read(body, "$.docId")).longValue() == minutesId)
                .toList();
    }

    private static void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String method = exchange.getRequestMethod();
        if (RAG_OFF.get() && path.startsWith("/internal/rag/")) {
            respond(exchange, 404, "{\"detail\":\"Not Found\"}");
        } else if (path.equals("/internal/rag/documents")) {
            REQUESTS.add(method + " " + path + "\n" + body);
            respond(exchange, 202, "{\"status\":\"accepted\"}");
        } else if (path.startsWith("/internal/rag/documents/")) {
            REQUESTS.add(method + " " + path);
            respond(exchange, 204, "");
        } else {
            respond(exchange, 404, "{\"error\":\"NOT_FOUND\"}");
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }
}
