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
    /** false면 ai-service가 초안 기능을 켜지 않은 것처럼 404로 답한다 */
    private static final AtomicBoolean DRAFT_ENABLED = new AtomicBoolean(true);
    private static final List<String> REQUEST_ID_HEADERS = new CopyOnWriteArrayList<>();

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
        registry.add("nineouting.minutes.draft.source-check-interval", () -> "200ms");
        registry.add("nineouting.minutes.draft.source-wait", () -> "3s");
    }

    private Long writer;
    private Long minutesId;
    private Long meetingId;

    @BeforeEach
    void setUp() throws Exception {
        SOURCE_READY.set(true);
        DRAFT_ENABLED.set(true);
        writer = newId();
        Long attendee = newId();
        Long folderId = folder(newId(), "기획", writer, attendee);
        meetingId = newId();
        minutesId = finishMeeting(meetingId, folderId, "주간 회의", LocalDateTime.of(2026, 10, 29, 14, 0), writer, writer, attendee);
    }

    @Test
    void 양식을_처음_고르면_빈_칸을_AI가_채우게_요청하고_결과로_빈_칸만_채운다() throws Exception {
        // 고르는 응답에 이미 기다리는 중이 실려 화면이 초안을 기다린다 (요청은 저장이 끝난 뒤)
        call(writer, put("/api/minutes/" + minutesId + "/template"), "{\"templateId\":" + DEFAULT_TEMPLATE_ID + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.draftStatus").value("WAITING_SOURCE"));

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
        assertThat(JsonPath.<String>read(request, "$.transcripts[0].text")).isEqualTo("테라폼으로 올리죠");
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
    void 만드는_중에는_다시_요청하지_못하고_실패하면_다시_요청할_수_있다() throws Exception {
        call(writer, post("/api/minutes/" + minutesId + "/draft"), null)
                .andExpect(jsonPath("$.code").value("MINUTES_TEMPLATE_REQUIRED"));
        call(writer, put("/api/minutes/" + minutesId + "/template"), "{\"templateId\":" + DEFAULT_TEMPLATE_ID + "}")
                .andExpect(status().isOk());
        String requestId = JsonPath.read(awaitDraftRequest(), "$.requestId");
        call(writer, post("/api/minutes/" + minutesId + "/draft"), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DRAFT_IN_PROGRESS"));

        callback("{\"requestId\":\"%s\",\"status\":\"FAILED\",\"error\":{\"code\":\"LLM_TIMEOUT\",\"message\":\"시간 초과\"}}"
                .formatted(requestId));
        call(writer, get("/api/minutes/" + minutesId), null)
                .andExpect(jsonPath("$.data.draftStatus").value("FAILED"))
                .andExpect(jsonPath("$.data.draftError").value("LLM_TIMEOUT"));

        int before = DRAFT_REQUESTS.size();
        call(newId(), post("/api/minutes/" + minutesId + "/draft"), null).andExpect(status().isForbidden());
        call(writer, post("/api/minutes/" + minutesId + "/draft"), null)
                .andExpect(jsonPath("$.data.draftStatus").value("REQUESTED"));
        assertThat(DRAFT_REQUESTS).hasSize(before + 1);
    }

    @Test
    void 오늘_요약이_없으면_기다렸다가_오면_자동으로_요청한다() throws Exception {
        SOURCE_READY.set(false);
        call(writer, put("/api/minutes/" + minutesId + "/template"), "{\"templateId\":" + DEFAULT_TEMPLATE_ID + "}")
                .andExpect(status().isOk());
        awaitDraftStatus("WAITING_SOURCE");
        call(writer, post("/api/minutes/" + minutesId + "/draft"), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DRAFT_IN_PROGRESS"));

        SOURCE_READY.set(true);
        awaitDraftRequest();
        awaitDraftStatus("REQUESTED");
    }

    @Test
    void 오늘_요약이_한도_안에_오지_않으면_실패로_두고_다시_요청할_수_있다() throws Exception {
        SOURCE_READY.set(false);
        call(writer, put("/api/minutes/" + minutesId + "/template"), "{\"templateId\":" + DEFAULT_TEMPLATE_ID + "}")
                .andExpect(status().isOk());
        awaitDraftStatus("FAILED");
        call(writer, get("/api/minutes/" + minutesId), null)
                .andExpect(jsonPath("$.data.draftError").value("DRAFT_SOURCE_TIMEOUT"));

        call(writer, post("/api/minutes/" + minutesId + "/draft"), null)
                .andExpect(jsonPath("$.data.draftStatus").value("WAITING_SOURCE"));
    }

    @Test
    void 빈_표_칸도_AI가_채우게_요청하고_결과의_마크다운_표를_저장_형식으로_바꾼다() throws Exception {
        String body = """
                {"name":"표 양식","items":[{"title":"할 일","description":"담당과 기한","settings":{"recordType":"TABLE","table":{"cols":3}}},
                 {"title":"메모","settings":{"recordType":"FREE"}}]}""";
        Long templateId = id(call(writer, post("/api/minutes-templates"), body), "$.data.templateId");
        call(writer, put("/api/minutes/" + minutesId + "/template"), "{\"templateId\":%d}".formatted(templateId))
                .andExpect(status().isOk());

        String request = awaitDraftRequest();
        assertThat(JsonPath.<List<String>>read(request, "$.template.fields[*].type")).containsExactly("table", "text");
        assertThat(JsonPath.<List<Boolean>>read(request, "$.template.fields[*].aiFill")).containsExactly(true, true);
        assertThat(JsonPath.<Integer>read(request, "$.template.fields[0].columns")).isEqualTo(3);
        String requestId = JsonPath.read(request, "$.requestId");
        assertThat(REQUEST_ID_HEADERS).contains(requestId);

        callback("""
                {"requestId":"%s","status":"SUCCEEDED","header":null,"footer":null,"unplaced":[],"unplacedLines":[],
                 "sections":[{"title":"할 일","type":"table","aiFilled":true,"columns":["할 일","담당","기한"],
                   "content":"| 할 일 | 담당 | 기한 |\\n|---|---|---|\\n| QA 일정표 공유 | 김철수 | 수요일까지 |","evidence":[]},
                  {"title":"메모","type":"text","aiFilled":true,"content":"해당 내용 없음","evidence":[]}]}"""
                .formatted(requestId));

        call(writer, get("/api/minutes/" + minutesId), null)
                .andExpect(jsonPath("$.data.draftStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.sections[0].content").value(String.join("\n",
                        String.join("\t", "할 일", "담당", "기한"), String.join("\t", "QA 일정표 공유", "김철수", "수요일까지"))));
    }

    @Test
    void ai_service에_초안_기능이_없으면_입력_오류가_아니라_쓸_수_없음으로_답한다() throws Exception {
        DRAFT_ENABLED.set(false);
        call(writer, put("/api/minutes/" + minutesId + "/template"), "{\"templateId\":" + DEFAULT_TEMPLATE_ID + "}")
                .andExpect(status().isOk());
        awaitDraftStatus("FAILED");
        call(writer, get("/api/minutes/" + minutesId), null)
                .andExpect(jsonPath("$.data.draftError").value("DRAFT_UNAVAILABLE"));
        call(writer, post("/api/minutes/" + minutesId + "/draft"), null)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DRAFT_UNAVAILABLE"));
    }

    @Test
    void 초안을_만든_뒤_양식을_바꾸면_이전_줄로_다시_배치하고_결과로_칸을_바꾼다() throws Exception {
        String requestId = firstDraft();
        callback("""
                {"requestId":"%s","status":"SUCCEEDED","header":null,"footer":null,"unplaced":[],"unplacedLines":[],
                 "sections":[{"title":"안건별 논의","type":"text","aiFilled":true,"content":"해당 내용 없음","evidence":[]},
                  {"title":"결정 사항","type":"text","aiFilled":true,"content":"- 배포 일정: 30일 배포","evidence":[
                    {"sentence":"배포 일정: 30일 배포","transcriptSeqs":[1],"edited":false,"needsCheck":false,
                     "sources":[{"agendaId":101,"kind":"decision","id":null,"decisionPointId":7}]}]},
                  {"title":"미결 안건","type":"text","aiFilled":true,"content":"해당 내용 없음","evidence":[]},
                  {"title":"할 일","type":"text","aiFilled":true,"content":"- QA 일정표 공유 (김철수, 수요일까지)","evidence":[
                    {"sentence":"QA 일정표 공유 (김철수, 수요일까지)","transcriptSeqs":[2],"edited":false,"needsCheck":false,
                     "sources":[{"agendaId":101,"kind":"todo","id":"k-10","decisionPointId":null}]}]}]}"""
                .formatted(requestId));
        int before = DRAFT_REQUESTS.size();

        Long templateId = id(call(writer, post("/api/minutes-templates"), """
                {"name":"새 양식","items":[{"title":"결정 사항","settings":{"recordType":"FREE"}},
                 {"title":"메모","settings":{"recordType":"FREE"}}]}"""), "$.data.templateId");
        // 바꾸는 응답에 다시 배치 중이 실려 화면이 결과를 기다린다
        call(writer, put("/api/minutes/" + minutesId + "/template"), "{\"templateId\":%d}".formatted(templateId))
                .andExpect(jsonPath("$.data.draftStatus").value("REQUESTED"))
                .andExpect(jsonPath("$.data.sections[0].content").value("- 배포 일정: 30일 배포"));

        String relayout = awaitDraftRequest(before);
        assertThat(JsonPath.<List<Boolean>>read(relayout, "$.template.fields[*].aiFill")).containsExactly(true, true);
        assertThat(JsonPath.<List<String>>read(relayout, "$.previousLines[*].sentence"))
                .containsExactly("배포 일정: 30일 배포", "QA 일정표 공유 (김철수, 수요일까지)");
        assertThat(JsonPath.<List<String>>read(relayout, "$.previousLines[*].title")).containsExactly("결정 사항", "할 일");
        assertThat(JsonPath.<List<Boolean>>read(relayout, "$.previousLines[*].draftEdited")).containsOnly(false);
        assertThat(JsonPath.<Integer>read(relayout, "$.previousLines[0].sources[0].decisionPointId")).isEqualTo(7);
        String relayoutId = JsonPath.read(relayout, "$.requestId");

        callback("""
                {"requestId":"%s","status":"SUCCEEDED","header":null,"footer":null,"unplaced":[],
                 "sections":[{"title":"결정 사항","type":"text","aiFilled":true,"content":"- 배포 일정: 30일 배포","evidence":[
                    {"sentence":"배포 일정: 30일 배포","transcriptSeqs":[1],"edited":false,"needsCheck":false,"draftEdited":false,
                     "sources":[{"agendaId":101,"kind":"decision","id":null,"decisionPointId":7}]}]},
                  {"title":"메모","type":"text","aiFilled":true,"content":"해당 내용 없음","evidence":[]}],
                 "unplacedLines":[{"title":"할 일","sentence":"QA 일정표 공유 (김철수, 수요일까지)","transcriptSeqs":[2],"edited":false,
                   "needsCheck":false,"sources":[{"agendaId":101,"kind":"todo","id":"k-10","decisionPointId":null}],"draftEdited":false}]}"""
                .formatted(relayoutId));

        // 맞는 칸이 없는 줄은 잃지 않게 마지막 글 칸에 붙인다
        call(writer, get("/api/minutes/" + minutesId), null)
                .andExpect(jsonPath("$.data.draftStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.sections[0].content").value("- 배포 일정: 30일 배포"))
                .andExpect(jsonPath("$.data.sections[1].content").value("- QA 일정표 공유 (김철수, 수요일까지)"));
    }

    @Test
    void 다시_배치_중에_작성자가_저장했으면_빈_칸만_채운다() throws Exception {
        String requestId = firstDraft();
        callback("""
                {"requestId":"%s","status":"SUCCEEDED","header":null,"footer":null,"unplaced":[],"unplacedLines":[],
                 "sections":[{"title":"결정 사항","type":"text","aiFilled":true,"content":"- 배포 일정: 30일 배포","evidence":[
                    {"sentence":"배포 일정: 30일 배포","transcriptSeqs":[1],"edited":false,"needsCheck":false,
                     "sources":[{"agendaId":101,"kind":"decision","id":null,"decisionPointId":7}]}]}]}"""
                .formatted(requestId));
        int before = DRAFT_REQUESTS.size();
        Long templateId = id(call(writer, post("/api/minutes-templates"), """
                {"name":"새 양식","items":[{"title":"결정 사항","settings":{"recordType":"FREE"}},
                 {"title":"메모","settings":{"recordType":"FREE"}}]}"""), "$.data.templateId");
        String detail = call(writer, put("/api/minutes/" + minutesId + "/template"), "{\"templateId\":%d}".formatted(templateId))
                .andReturn().getResponse().getContentAsString();
        String relayoutId = JsonPath.read(awaitDraftRequest(before), "$.requestId");

        List<Integer> sectionIds = JsonPath.read(detail, "$.data.sections[*].sectionId");
        call(writer, put("/api/minutes/" + minutesId + "/sections"), """
                {"version":%d,"sections":[{"sectionId":%d,"title":"결정 사항","content":"내가 고친 결정"},
                 {"sectionId":%d,"title":"메모","content":""}]}"""
                .formatted(JsonPath.<Integer>read(detail, "$.data.version"), sectionIds.get(0), sectionIds.get(1)))
                .andExpect(status().isOk());

        callback("""
                {"requestId":"%s","status":"SUCCEEDED","header":null,"footer":null,"unplaced":[],"unplacedLines":[],
                 "sections":[{"title":"결정 사항","type":"text","aiFilled":true,"content":"- 배포 일정: 30일 배포","evidence":[]},
                  {"title":"메모","type":"text","aiFilled":true,"content":"- AI 메모","evidence":[]}]}"""
                .formatted(relayoutId));

        call(writer, get("/api/minutes/" + minutesId), null)
                .andExpect(jsonPath("$.data.sections[0].content").value("내가 고친 결정"))
                .andExpect(jsonPath("$.data.sections[1].content").value("- AI 메모"));
    }

    /** 기본 양식을 골라 첫 초안을 요청하게 하고 요청 번호를 돌려준다 */
    private String firstDraft() throws Exception {
        call(writer, put("/api/minutes/" + minutesId + "/template"), "{\"templateId\":" + DEFAULT_TEMPLATE_ID + "}")
                .andExpect(status().isOk());
        return JsonPath.read(awaitDraftRequest(), "$.requestId");
    }

    private void awaitDraftStatus(String expected) throws Exception {
        long deadline = System.nanoTime() + 10_000_000_000L;
        String actual = null;
        while (System.nanoTime() < deadline) {
            String detail = call(writer, get("/api/minutes/" + minutesId), null).andReturn().getResponse().getContentAsString();
            actual = JsonPath.read(detail, "$.data.draftStatus");
            if (expected.equals(actual)) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("초안 상태가 " + expected + "가 되지 않았습니다 (지금 " + actual + ")");
    }

    private void callback(String body) throws Exception {
        mockMvc.perform(post("/internal/minutes/" + minutesId + "/draft-result")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNoContent());
    }

    private String awaitDraftRequest() throws InterruptedException {
        return awaitDraftRequest(0);
    }

    /** from번째 뒤로 온 이 회의록의 초안 요청 */
    private String awaitDraftRequest(int from) throws InterruptedException {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (System.nanoTime() < deadline) {
            for (String body : DRAFT_REQUESTS.subList(Math.min(from, DRAFT_REQUESTS.size()), DRAFT_REQUESTS.size())) {
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
                       "summary":[],"opinions":[],"todos":[]}]},
                     "transcripts":[{"seq":1,"text":"테라폼으로 올리죠","lowConfidence":false}]}}""".formatted(meetingId, SOURCE_READY.get()));
            return;
        }
        if (path.equals("/internal/minutes/drafts")) {
            if (!DRAFT_ENABLED.get()) {
                respond(exchange, 404, "{\"detail\":\"Not Found\"}");
                return;
            }
            REQUEST_ID_HEADERS.add(String.valueOf(exchange.getRequestHeaders().getFirst("X-Request-Id")));
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
