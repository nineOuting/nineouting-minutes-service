package com.nineouting.backend.minutes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nineouting.backend.minutes.client.TemplateImportClient;
import com.nineouting.backend.minutes.client.TemplateImportClient.Result;
import com.nineouting.backend.minutes.domain.TemplateImport;
import com.nineouting.backend.minutes.repository.TemplateImportRepository;
import com.nineouting.backend.minutes.service.BackgroundStorage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/** REQ-195 양식 가져오기: 실제 LocalStack S3에 올리고, ai-service 답은 가짜로 둔다. 접수 뒤 상태를 다시 묻는다 */
class TemplateImportTest extends ApiTestSupport {

    private static final byte[] PDF = "%PDF-1.7 test".getBytes();
    private static final String SUCCEEDED = """
            {"requestId":"r","status":"SUCCEEDED",
             "fields":[{"title":"논의 내용","type":"free","description":"※ 안건별로 적는다","aiFill":true},
                       {"title":"결정 사항","type":"list","aiFill":true},
                       {"title":"확인 사항","type":"checklist","aiFill":true},
                       {"title":"할 일","type":"table","columns":12,"aiFill":true}],
             "header":"나인아웃팅 회의록","footer":"- 2 -","warnings":[{"code":"CHECKLIST_NO_BOX","title":"확인 사항"}]}""";
    private static final long POLL_LIMIT_MS = 10_000;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @MockitoBean
    private TemplateImportClient templateImportClient;

    @Autowired
    private BackgroundStorage storage;

    @Autowired
    private TemplateImportRepository importRepository;

    @Test
    void 접수하면_뒤에서_AI가_읽고_새_개인_양식으로_저장하며_파일은_지운다() throws Exception {
        Long me = newId();
        given(templateImportClient.importTemplate(anyString(), anyString()))
                .willReturn(new Result(Result.Status.SUCCEEDED, JsonMapper.builder().build().readTree(SUCCEEDED), null));
        String key = upload(me, "application/pdf");

        String done = finished(me, start(me, key, "주간 회의록"));
        assertThat((String) JsonPath.read(done, "$.data.status")).isEqualTo("SUCCEEDED");
        Integer templateId = JsonPath.read(done, "$.data.templateId");
        call(me, get("/api/minutes-templates"), null)
                .andExpect(jsonPath("$.data[?(@.templateId == %d)].name".formatted(templateId)).value("주간 회의록"));
        String templates = call(me, get("/api/minutes-templates"), null).andReturn().getResponse().getContentAsString();
        String base = "$.data[?(@.templateId == %d)]".formatted(templateId);
        assertThat(JsonPath.<java.util.List<Object>>read(templates, base + ".items[*].settings.recordType"))
                .containsExactly("FREE", "LIST", "CHECKLIST", "TABLE");
        // 표 열은 양식 편집 화면 상한 10으로 자른다
        assertThat(JsonPath.<java.util.List<Object>>read(templates, base + ".items[3].settings.table.cols")).containsExactly(10);
        assertThat(JsonPath.<java.util.List<Object>>read(templates, base + ".items[0].description"))
                .containsExactly("※ 안건별로 적는다");
        assertThat(JsonPath.<java.util.List<Object>>read(templates, base + ".layout.header.text")).containsExactly("나인아웃팅 회의록");
        assertThat(JsonPath.<java.util.List<Object>>read(templates, base + ".layout.footer.text")).containsExactly("- 2 -");
        assertThat(storage.size(key)).isEmpty();
    }

    @Test
    void AI가_읽지_못하면_이유별_오류와_문구로_답하고_파일은_지운다() throws Exception {
        Long me = newId();
        expectFailure(me, new Result(Result.Status.FAILED, null, "unsupported_format"), "TEMPLATE_IMPORT_UNSUPPORTED");
        expectFailure(me, new Result(Result.Status.FAILED, null, "encrypted"), "TEMPLATE_IMPORT_ENCRYPTED");
        expectFailure(me, new Result(Result.Status.FAILED, null, "too_large"), "TEMPLATE_IMPORT_TOO_LARGE");
        expectFailure(me, new Result(Result.Status.FAILED, null, "no_text"), "TEMPLATE_IMPORT_NO_TEXT");
        expectFailure(me, new Result(Result.Status.FAILED, null, "NO_FIELDS"), "TEMPLATE_IMPORT_FAILED");
        expectFailure(me, new Result(Result.Status.FAILED, null, "LLM_TIMEOUT"), "TEMPLATE_IMPORT_UNAVAILABLE");
        expectFailure(me, new Result(Result.Status.UNAVAILABLE, null, null), "TEMPLATE_IMPORT_UNAVAILABLE");
    }

    @Test
    void PDF_Word가_아니거나_20MB를_넘거나_남의_파일이거나_아직_안_올렸으면_거절한다() throws Exception {
        Long me = newId();
        call(me, post("/api/minutes-templates/import-upload-url"), "{\"contentType\":\"image/png\",\"size\":10}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEMPLATE_IMPORT_INVALID"));
        call(me, post("/api/minutes-templates/import-upload-url"),
                "{\"contentType\":\"application/pdf\",\"size\":%d}".formatted(20L * 1024 * 1024 + 1))
                .andExpect(status().isBadRequest());
        String docx = data(call(me, post("/api/minutes-templates/import-upload-url"),
                "{\"contentType\":\"application/vnd.openxmlformats-officedocument.wordprocessingml.document\",\"size\":10}")
                .andExpect(status().isOk()), "$.data.fileKey");
        assertThat(docx).startsWith("template-imports/" + me + "/").endsWith(".docx");

        Long other = newId();
        String othersKey = upload(other, "application/pdf");
        call(me, post("/api/minutes-templates/import"), "{\"fileKey\":\"%s\",\"name\":\"양식\"}".formatted(othersKey))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEMPLATE_IMPORT_INVALID"));
        call(me, post("/api/minutes-templates/import"), "{\"fileKey\":\"%s\",\"name\":\"양식\"}".formatted(docx))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TEMPLATE_IMPORT_NOT_UPLOADED"));
    }

    @Test
    void 남의_가져오기_상태는_볼_수_없다() throws Exception {
        Long me = newId();
        given(templateImportClient.importTemplate(anyString(), anyString()))
                .willReturn(new Result(Result.Status.FAILED, null, "no_text"));
        Integer importId = start(me, upload(me, "application/pdf"), "양식");
        call(newId(), get("/api/minutes-templates/import/" + importId), null).andExpect(status().isNotFound());
    }

    @Test
    void 처리하던_서버가_꺼져_오래_PENDING이면_조회할_때_실패로_바꾼다() throws Exception {
        Long me = newId();
        TemplateImport stale = importRepository.save(new TemplateImport(me, "template-imports/" + me + "/gone.pdf", "양식",
                LocalDateTime.now().minusMinutes(10)));

        call(me, get("/api/minutes-templates/import/" + stale.getId()), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.code").value("TEMPLATE_IMPORT_UNAVAILABLE"));
    }

    private void expectFailure(Long me, Result result, String code) throws Exception {
        given(templateImportClient.importTemplate(anyString(), anyString())).willReturn(result);
        String key = upload(me, "application/pdf");
        String done = finished(me, start(me, key, "양식"));
        assertThat((String) JsonPath.read(done, "$.data.status")).isEqualTo("FAILED");
        assertThat((String) JsonPath.read(done, "$.data.code")).isEqualTo(code);
        assertThat((String) JsonPath.read(done, "$.data.message")).isNotBlank();
        assertThat(storage.size(key)).isEmpty();
    }

    /** 접수하고 importId를 돌려준다 (202, PENDING 또는 이미 끝남) */
    private Integer start(Long userId, String key, String name) throws Exception {
        ResultActions accepted = call(userId, post("/api/minutes-templates/import"),
                "{\"fileKey\":\"%s\",\"name\":\"%s\"}".formatted(key, name))
                .andExpect(status().isAccepted());
        return JsonPath.read(accepted.andReturn().getResponse().getContentAsString(), "$.data.importId");
    }

    /** PENDING이 끝날 때까지 상태를 다시 묻는다 */
    private String finished(Long userId, Integer importId) throws Exception {
        long deadline = System.currentTimeMillis() + POLL_LIMIT_MS;
        while (true) {
            String body = call(userId, get("/api/minutes-templates/import/" + importId), null)
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            if (!"PENDING".equals(JsonPath.read(body, "$.data.status")) || System.currentTimeMillis() > deadline) {
                return body;
            }
            Thread.sleep(100);
        }
    }

    /** 업로드 주소를 받아 실제로 올리고 fileKey를 돌려준다 */
    private String upload(Long userId, String contentType) throws Exception {
        ResultActions prepared = call(userId, post("/api/minutes-templates/import-upload-url"),
                "{\"contentType\":\"%s\",\"size\":%d}".formatted(contentType, PDF.length))
                .andExpect(status().isOk());
        HttpResponse<Void> put = httpClient.send(HttpRequest.newBuilder(URI.create(data(prepared, "$.data.uploadUrl")))
                .header("Content-Type", contentType)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(PDF)).build(), HttpResponse.BodyHandlers.discarding());
        assertThat(put.statusCode()).isEqualTo(200);
        return data(prepared, "$.data.fileKey");
    }

    private static String data(ResultActions result, String path) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
    }
}
