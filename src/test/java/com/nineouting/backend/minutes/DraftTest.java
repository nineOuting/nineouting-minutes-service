package com.nineouting.backend.minutes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** AI 회의록 초안 (REQ-101~103, ai-service docs/api/minutes-draft-contract.md). meeting·ai-service는 가짜 서버 */
class DraftTest extends ApiTestSupport {

    private static final HttpServer SERVER;
    private static final List<String> DRAFT_REQUESTS = new CopyOnWriteArrayList<>();
    private static final AtomicBoolean SOURCE_READY = new AtomicBoolean(true);

    static {
        try {
            SERVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        SERVER.createContext("/", DraftTest::handle);
        SERVER.start();
    }

    @DynamicPropertySource
    static void draftServices(DynamicPropertyRegistry registry) {
        String url = "http://127.0.0.1:" + SERVER.getAddress().getPort();
        registry.add("nineouting.minutes.draft.ai-service-url", () -> url);
        registry.add("nineouting.minutes.draft.meeting-service-url", () -> url);
        registry.add("nineouting.minutes.draft.callback-base-url", () -> "http://minutes-service:8084");
    }

    private Long writer;
    private Long minutesId;
    private Long meetingId;

    @BeforeEach
    void setUp() throws Exception {
        SOURCE_READY.set(true);
        writer = newId();
        Long attendee = newId();
        Long folderId = folder(newId(), "기획", writer, attendee);
        meetingId = newId();
        minutesId = finishMeeting(meetingId, folderId, "주간 회의", LocalDateTime.of(2026, 10, 29, 14, 0), writer, writer, attendee);
    }

    @Test
    void 양식을_처음_고르면_빈_칸을_AI가_채우게_요청하고_결과로_빈_칸만_채운다() throws Exception {
        call(writer, put("/api/minutes/" + minutesId + "/template"), "{\"templateId\":" + DEFAULT_TEMPLATE_ID + "}")
                .andExpect(status().isOk());

        String request = awaitDraftRequest();
        assertThat(JsonPath.<Integer>read(request, "$.minutesId").longValue()).isEqualTo(minutesId);
        assertThat(JsonPath.<List<String>>read(request, "$.template.fields[*].title"))
                .containsExactly("안건별 논의", "결정 사항", "미결 안건", "할 일");
        assertThat(JsonPath.<List<Boolean>>read(request, "$.template.fields[*].aiFill")).containsOnly(true);
        assertThat(JsonPath.<String>read(request, "$.agendas[0].title")).isEqualTo("배포 일정");
        assertThat(JsonPath.<String>read(request, "$.agendas[0].decisionPoints[0].text")).isEqualTo("배포 날짜");
        assertThat(JsonPath.<String>read(request, "$.template.fields[1].description")).isEqualTo("회의에서 결정된 주요 사항을 정리합니다.");
        assertThat(JsonPath.<List<String>>read(request, "$.template.fields[*].type")).containsOnly("text");
        assertThat(JsonPath.<Object>read(request, "$.template.header")).isNull();
        assertThat(JsonPath.<String>read(request, "$.summary.agendas[0].status")).isEqualTo("DECIDED");
        assertThat(JsonPath.<String>read(request, "$.callbackUrl"))
                .isEqualTo("http://minutes-service:8084/internal/minutes/" + minutesId + "/draft-result");
        assertThat(JsonPath.<List<String>>read(request, "$.attendees[*].name")).contains("user" + writer);
        String requestId = JsonPath.read(request, "$.requestId");
        String detail = call(writer, get("/api/minutes/" + minutesId), null)
                .andExpect(jsonPath("$.data.draftStatus").value("REQUESTED"))
                .andReturn().getResponse().getContentAsString();

        // 그사이 작성자가 첫 칸을 썼다 → 그 칸은 덮어쓰지 않는다
        List<Integer> sectionIds = JsonPath.read(detail, "$.data.sections[*].sectionId");
        int version = JsonPath.read(detail, "$.data.version");
        call(writer, put("/api/minutes/" + minutesId + "/sections"), """
                {"version":%d,"sections":[{"sectionId":%d,"title":"안건별 논의","content":"내가 쓴 글"},
                 {"sectionId":%d,"title":"결정 사항","content":""},{"sectionId":%d,"title":"미결 안건","content":""},
                 {"sectionId":%d,"title":"할 일","content":""}]}"""
                .formatted(version, sectionIds.get(0), sectionIds.get(1), sectionIds.get(2), sectionIds.get(3)))
                .andExpect(status().isOk());

        callback("{\"requestId\":\"다른-요청\",\"status\":\"SUCCEEDED\",\"sections\":[]}");
        callback("""
                {"requestId":"%s","status":"SUCCEEDED","header":null,"footer":null,"unplaced":[],"unplacedLines":[],
                 "sections":[{"title":"안건별 논의","type":"text","aiFilled":true,"content":"AI가 쓴 논의","evidence":[]},
                  {"title":"결정 사항","type":"text","aiFilled":true,"content":"- 배포 일정: 30일 배포","evidence":[]},
                  {"title":"미결 안건","type":"text","aiFilled":true,"content":"","evidence":[]},
                  {"title":"할 일","type":"text","aiFilled":true,"content":"- QA 일정표 공유","evidence":[]}]}"""
                .formatted(requestId));

        call(writer, get("/api/minutes/" + minutesId), null)
                .andExpect(jsonPath("$.data.draftStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.sections[0].content").value("내가 쓴 글"))
                .andExpect(jsonPath("$.data.sections[1].content").value("- 배포 일정: 30일 배포"))
                .andExpect(jsonPath("$.data.sections[2].content").value(""))
                .andExpect(jsonPath("$.data.sections[3].content").value("- QA 일정표 공유"));
    }

    @Test
    void 실패하면_다시_요청할_수_있고_재료가_준비되지_않았으면_알려_준다() throws Exception {
        call(writer, post("/api/minutes/" + minutesId + "/draft"), null)
                .andExpect(jsonPath("$.code").value("MINUTES_TEMPLATE_REQUIRED"));
        SOURCE_READY.set(false);
        call(writer, put("/api/minutes/" + minutesId + "/template"), "{\"templateId\":" + DEFAULT_TEMPLATE_ID + "}")
                .andExpect(status().isOk());
        call(writer, post("/api/minutes/" + minutesId + "/draft"), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DRAFT_SOURCE_NOT_READY"));

        SOURCE_READY.set(true);
        int before = DRAFT_REQUESTS.size();
        call(newId(), post("/api/minutes/" + minutesId + "/draft"), null).andExpect(status().isForbidden());
        call(writer, post("/api/minutes/" + minutesId + "/draft"), null)
                .andExpect(jsonPath("$.data.draftStatus").value("REQUESTED"));
        String requestId = JsonPath.read(DRAFT_REQUESTS.get(before), "$.requestId");
        callback("{\"requestId\":\"%s\",\"status\":\"FAILED\",\"error\":{\"code\":\"LLM_TIMEOUT\",\"message\":\"시간 초과\"}}"
                .formatted(requestId));
        call(writer, get("/api/minutes/" + minutesId), null)
                .andExpect(jsonPath("$.data.draftStatus").value("FAILED"))
                .andExpect(jsonPath("$.data.draftError").value("LLM_TIMEOUT"));
    }

    private void callback(String body) throws Exception {
        mockMvc.perform(post("/internal/minutes/" + minutesId + "/draft-result")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNoContent());
    }

    private String awaitDraftRequest() throws InterruptedException {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (System.nanoTime() < deadline) {
            for (String body : DRAFT_REQUESTS) {
                if (body.contains("\"minutesId\":" + minutesId + ",")) {
                    return body;
                }
            }
            Thread.sleep(100);
        }
        throw new AssertionError("ai-service가 초안 요청을 받지 못했습니다");
    }

    private static void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (path.endsWith("/draft-source")) {
            String meetingId = path.split("/")[3];
            respond(exchange, 200, """
                    {"success":true,"data":{"meetingId":%s,"ready":%s,
                     "agendas":[{"agendaId":101,"title":"배포 일정","decisionPoints":[{"decisionPointId":7,"position":1,"text":"배포 날짜"}]}],
                     "summary":{"agendas":[{"agendaId":101,"status":"DECIDED","statusReason":{"text":"30일","evidenceSeqs":[1]},
                       "summary":[],"opinions":[],"todos":[]}]}}}""".formatted(meetingId, SOURCE_READY.get()));
            return;
        }
        if (path.equals("/internal/minutes/drafts")) {
            DRAFT_REQUESTS.add(body);
            respond(exchange, 202, "{\"requestId\":\"x\",\"status\":\"ACCEPTED\"}");
            return;
        }
        respond(exchange, 404, "{}");
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
