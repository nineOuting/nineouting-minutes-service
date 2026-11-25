package com.nineouting.backend.minutes.client;

import com.nineouting.backend.common.client.ClientProperties;
import com.nineouting.backend.common.web.error.BusinessException;
import com.nineouting.backend.common.web.error.CommonErrorCode;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * REQ-213 workspace-service에 워크스페이스 기본 양식을 정해 달라고 한다 (/internal, 소유자 확인은 workspace-service가 한다).
 * 소유자가 아니면 403을 그대로 돌려주고, 나머지 실패는 잠시 뒤 다시 하라는 오류로 바꾼다
 */
@Component
public class WorkspaceDefaultTemplateClient {

    private final RestClient restClient;

    public WorkspaceDefaultTemplateClient(RestClient.Builder builder, ClientProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = builder.clone().requestFactory(requestFactory).baseUrl(properties.workspaceServiceUrl()).build();
    }

    public void change(Long workspaceId, Long userId, Long templateId) {
        try {
            restClient.put()
                    .uri("/internal/workspaces/{workspaceId}/default-template", workspaceId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("userId", userId, "templateId", templateId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException error) {
            if (error.getStatusCode().isSameCodeAs(HttpStatus.FORBIDDEN)) {
                throw new BusinessException(CommonErrorCode.FORBIDDEN);
            }
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }
    }
}
