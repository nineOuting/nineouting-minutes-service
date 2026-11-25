package com.nineouting.backend.minutes.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.nineouting.backend.common.client.ClientProperties;
import com.nineouting.backend.common.web.error.BusinessException;
import com.nineouting.backend.common.web.error.CommonErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** REQ-213 workspace-service에 워크스페이스 기본 양식 정하기 호출 */
class WorkspaceDefaultTemplateClientTest {

    private static final String URL = "http://workspace/internal/workspaces/7/default-template";

    private MockRestServiceServer server;
    private WorkspaceDefaultTemplateClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://workspace");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new WorkspaceDefaultTemplateClient(builder.build());
    }

    @Test
    void 사용자와_양식을_본문에_실어_PUT으로_보낸다() {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.userId").value(3))
                .andExpect(jsonPath("$.templateId").value(11))
                .andRespond(withSuccess());

        assertThatCode(() -> client.change(7L, 3L, 11L)).doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void 소유자가_아니면_FORBIDDEN이다() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> client.change(7L, 3L, 11L))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode())
                                .isEqualTo(CommonErrorCode.FORBIDDEN));
    }

    @Test
    void 나머지_실패는_INTERNAL_ERROR다() {
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.change(7L, 3L, 11L))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode())
                                .isEqualTo(CommonErrorCode.INTERNAL_ERROR));
    }

    @Test
    void 설정의_주소와_시간_제한으로_만든다() {
        ClientProperties properties = new ClientProperties(null, "http://workspace", null, null);

        assertThatCode(() -> new WorkspaceDefaultTemplateClient(RestClient.builder(), properties))
                .doesNotThrowAnyException();
    }
}
