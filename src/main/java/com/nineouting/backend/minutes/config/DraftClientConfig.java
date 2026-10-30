package com.nineouting.backend.minutes.config;

import com.nineouting.backend.minutes.client.DraftClient;
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
}
