package com.nineouting.backend.minutes.config;

import com.nineouting.backend.minutes.client.DraftClient;
import com.nineouting.backend.minutes.client.RagClient;
import com.nineouting.backend.minutes.client.TemplateImportClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class DraftClientConfig {

    /** 초안 요청이 느려도 회의록 작성 화면이 막히지 않게 타임아웃을 둔다 */
    @Bean
    DraftClient draftClient(RestClient.Builder builder, DraftProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        RestClient.Builder meeting = builder.clone().requestFactory(requestFactory);
        if (properties.meetingServiceUrl() != null && !properties.meetingServiceUrl().isBlank()) {
            meeting.baseUrl(properties.meetingServiceUrl());
        }
        RestClient.Builder ai = builder.clone().requestFactory(requestFactory);
        if (properties.enabled()) {
            ai.baseUrl(properties.aiServiceUrl());
        }
        return new DraftClient(meeting.build(), ai.build());
    }

    /** REQ-185 회의록 넣기. 초안과 같은 ai-service 주소, 타임아웃 (넣기는 바로 접수로 답한다) */
    @Bean
    RagClient ragClient(RestClient.Builder builder, DraftProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        RestClient.Builder ai = builder.clone().requestFactory(requestFactory);
        if (properties.enabled()) {
            ai.baseUrl(properties.aiServiceUrl());
        }
        return new RagClient(ai.build());
    }

    /** REQ-195 양식 가져오기. 같은 ai-service 주소, 답이 최대 약 2분 걸려 응답 대기 시간을 따로 둔다 */
    @Bean
    TemplateImportClient templateImportClient(RestClient.Builder builder, DraftProperties properties,
                                              TemplateImportProperties importProperties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(importProperties.readTimeout());
        RestClient.Builder ai = builder.clone().requestFactory(requestFactory);
        if (properties.enabled()) {
            ai.baseUrl(properties.aiServiceUrl());
        }
        return new TemplateImportClient(ai.build(), properties.enabled());
    }
}
