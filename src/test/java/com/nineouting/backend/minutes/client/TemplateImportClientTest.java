package com.nineouting.backend.minutes.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServiceUnavailable;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.nineouting.backend.minutes.client.TemplateImportClient.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** REQ-195 ai-service 양식 가져오기 호출 (template-import-contract 3.1) */
class TemplateImportClientTest {

    private static final String URL = "http://ai/internal/minutes/template-imports";

    private MockRestServiceServer server;
    private TemplateImportClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new TemplateImportClient(builder.build(), true);
    }

    @Test
    void 성공하면_본문을_그대로_돌려주고_요청_번호를_헤더와_본문에_싣는다() {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Request-Id", "template-import-1"))
                .andExpect(jsonPath("$.requestId").value("template-import-1"))
                .andExpect(jsonPath("$.fileUrl").value("https://file"))
                .andRespond(withSuccess("""
                        {"status":"SUCCEEDED","fields":[{"title":"논의 내용","type":"free"}],"header":"머리"}""",
                        MediaType.APPLICATION_JSON));

        Result result = client.importTemplate("https://file", "template-import-1");

        assertThat(result.status()).isEqualTo(Result.Status.SUCCEEDED);
        assertThat(result.body().path("header").asString()).isEqualTo("머리");
        server.verify();
    }

    @Test
    void 읽기_실패는_오류_코드를_돌려준다() {
        server.expect(requestTo(URL)).andRespond(withSuccess("""
                {"status":"FAILED","errorCode":"no_text"}""", MediaType.APPLICATION_JSON));

        Result result = client.importTemplate("https://file", "r");

        assertThat(result.status()).isEqualTo(Result.Status.FAILED);
        assertThat(result.errorCode()).isEqualTo("no_text");
    }

    @Test
    void 바쁘거나_준비되지_않으면_UNAVAILABLE이고_요청을_거절하면_실패다() {
        server.expect(requestTo(URL)).andRespond(withServiceUnavailable());
        assertThat(client.importTemplate("https://file", "r").status()).isEqualTo(Result.Status.UNAVAILABLE);

        server.reset();
        server.expect(requestTo(URL)).andRespond(withBadRequest());
        Result rejected = client.importTemplate("https://file", "r");
        assertThat(rejected.status()).isEqualTo(Result.Status.FAILED);
        assertThat(rejected.errorCode()).isEqualTo("internal_error");
    }

    @Test
    void ai_service_주소가_없으면_부르지_않고_UNAVAILABLE이다() {
        TemplateImportClient disabled = new TemplateImportClient(RestClient.create(), false);

        assertThat(disabled.importTemplate("https://file", "r").status()).isEqualTo(Result.Status.UNAVAILABLE);
    }
}
