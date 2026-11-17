package com.nineouting.backend.minutes;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** REQ-199 워크스페이스 기본 양식으로 정한 개인 양식은 그 워크스페이스 멤버 모두 쓴다 (2026-11-17) */
class SharedTemplateTest extends ApiTestSupport {

    @Test
    void 워크스페이스에_공유한_양식은_그_멤버가_목록에서_보고_회의록에_쓴다() throws Exception {
        Long owner = newId();
        Long member = newId();
        Long outsider = newId();
        Long workspaceId = newId();
        Long folderId = folder(workspaceId, "기획", owner, member);
        folder(newId(), "다른 곳", outsider);
        Long templateId = id(call(owner, post("/api/minutes-templates"),
                "{\"name\":\"주간 회의록\",\"items\":[{\"title\":\"안건표\",\"settings\":{\"recordType\":\"TABLE\"}}]}"),
                "$.data.templateId");
        String share = "/api/minutes-templates/" + templateId + "/workspace-shares/" + workspaceId;

        call(member, get("/api/minutes-templates"), null)
                .andExpect(jsonPath("$.data[*].templateId", not(hasItem(templateId.intValue()))));
        call(member, put(share), null).andExpect(status().isNotFound());
        call(owner, put("/api/minutes-templates/" + templateId + "/workspace-shares/" + newId()), null)
                .andExpect(status().isForbidden());
        call(owner, put(share), null).andExpect(status().isOk());

        call(member, get("/api/minutes-templates"), null)
                .andExpect(jsonPath("$.data[*].templateId", hasItem(templateId.intValue())))
                .andExpect(jsonPath("$.data[?(@.templateId == " + templateId + ")].shared", hasItem(true)));
        call(outsider, get("/api/minutes-templates"), null)
                .andExpect(jsonPath("$.data[*].templateId", not(hasItem(templateId.intValue()))));

        Long minutesId = finishMeeting(newId(), folderId, "회의", LocalDateTime.of(2026, 10, 20, 10, 0), member, owner);
        call(member, put("/api/minutes/" + minutesId + "/template"), "{\"templateId\":%d}".formatted(templateId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.template.name").value("주간 회의록"))
                .andExpect(jsonPath("$.data.template.items[0].settings.recordType").value("TABLE"));
        // 고치는 건 만든 사람만
        call(member, put("/api/minutes-templates/" + templateId), "{\"name\":\"바꿈\",\"items\":[{\"title\":\"요약\"}]}")
                .andExpect(status().isNotFound());

        call(owner, delete(share), null).andExpect(status().isOk());
        call(member, put("/api/minutes/" + minutesId + "/template"), "{\"templateId\":%d}".formatted(templateId))
                .andExpect(status().isNotFound());
        // 공유를 거둬도 이미 쓴 회의록은 같은 모양으로 그린다
        call(owner, get("/api/minutes/" + minutesId), null)
                .andExpect(jsonPath("$.data.template.items[0].settings.recordType").value("TABLE"));
    }
}
