package com.nineouting.backend.minutes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/** REQ-157 양식 배경 이미지: 실제 LocalStack S3에 올리고 양식에 저장한 뒤 보여 준다 */
class TemplateBackgroundTest extends ApiTestSupport {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void 올린_배경_이미지를_양식에_저장하면_보여_줄_주소가_붙는다() throws Exception {
        Long me = newId();
        var prepared = call(me, post("/api/minutes-templates/background-upload-url"),
                "{\"contentType\":\"image/png\",\"size\":%d}".formatted(PNG.length))
                .andExpect(status().isOk());
        String key = data(prepared, "$.data.imageKey");
        assertThat(key).startsWith("template-backgrounds/" + me + "/");

        // 올리기 전에 저장하면 거절한다
        call(me, post("/api/minutes-templates"), templateJson(key))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TEMPLATE_BACKGROUND_NOT_UPLOADED"));

        HttpResponse<Void> put = httpClient.send(HttpRequest.newBuilder(URI.create(data(prepared, "$.data.uploadUrl")))
                .header("Content-Type", "image/png")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(PNG)).build(), HttpResponse.BodyHandlers.discarding());
        assertThat(put.statusCode()).isEqualTo(200);

        call(me, post("/api/minutes-templates"), templateJson(key))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.layout.backgroundImageKey").value(key))
                .andExpect(jsonPath("$.data.layout.backgroundImageUrl").isNotEmpty());
        String url = data(call(me, get("/api/minutes-templates"), null), "$.data[1].layout.backgroundImageUrl");
        HttpResponse<byte[]> image = httpClient.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(image.body()).isEqualTo(PNG);
    }

    @Test
    void 이미지가_아니거나_5MB를_넘거나_엉뚱한_위치면_거절한다() throws Exception {
        Long me = newId();
        call(me, post("/api/minutes-templates/background-upload-url"), "{\"contentType\":\"application/pdf\",\"size\":10}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEMPLATE_BACKGROUND_INVALID"));
        call(me, post("/api/minutes-templates/background-upload-url"),
                "{\"contentType\":\"image/png\",\"size\":%d}".formatted(5L * 1024 * 1024 + 1))
                .andExpect(status().isBadRequest());
        call(me, post("/api/minutes-templates"), templateJson("meeting-materials/1/secret"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEMPLATE_BACKGROUND_INVALID"));
    }

    private static String data(ResultActions result, String path) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
    }

    private static String templateJson(String backgroundKey) {
        return """
                {"name":"배경 양식","items":[{"title":"요약"}],"layout":{"pageNumber":true,"backgroundImageKey":"%s"}}"""
                .formatted(backgroundKey);
    }
}
