package com.nineouting.backend.minutes;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/** Swagger UI·OpenAPI 문서 (공통 모듈 api-docs) */
class ApiDocsTest extends ApiTestSupport {

    @Test
    void API_문서는_로그인_없이_보이고_내부_주소는_빠진다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("minutes-service API"))
                .andExpect(jsonPath("$.paths['/api/minutes/{minutesId}']").exists())
                .andExpect(content().string(not(containsString("\"/internal"))));
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
