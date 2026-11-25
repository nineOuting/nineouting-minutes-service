package com.nineouting.backend.minutes;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nineouting.backend.common.web.error.BusinessException;
import com.nineouting.backend.common.web.error.CommonErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** REQ-213 워크스페이스 양식함: 소유자가 정한 양식을 워크스페이스 사본으로 만들어 기본 양식으로 쓴다 (2026-11-25) */
class WorkspaceTemplateTest extends ApiTestSupport {

    @Test
    void 기본_양식으로_정하면_사본이_생기고_원본을_지워도_멤버가_쓴다() throws Exception {
        Long owner = newId();
        Long member = newId();
        Long outsider = newId();
        Long workspaceId = newId();
        Long folderId = folder(workspaceId, "기획", owner, member);
        folder(newId(), "다른 곳", outsider);
        Long original = id(call(owner, post("/api/minutes-templates"),
                "{\"name\":\"주간 회의록\",\"items\":[{\"title\":\"안건표\",\"settings\":{\"recordType\":\"TABLE\"}}]}"),
                "$.data.templateId");
        String path = "/api/minutes-templates/workspaces/" + workspaceId + "/default";

        Long copy = id(call(owner, put(path), "{\"templateId\":%d}".formatted(original))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("주간 회의록"))
                .andExpect(jsonPath("$.data.shared").value(true))
                .andExpect(jsonPath("$.data.workspaceId").value(workspaceId.intValue())), "$.data.templateId");
        verify(workspaceDefaultTemplateClient).change(workspaceId, owner, copy);

        call(member, get("/api/minutes-templates"), null)
                .andExpect(jsonPath("$.data[*].templateId", hasItem(copy.intValue())))
                .andExpect(jsonPath("$.data[*].templateId", not(hasItem(original.intValue()))));
        call(outsider, get("/api/minutes-templates"), null)
                .andExpect(jsonPath("$.data[*].templateId", not(hasItem(copy.intValue()))));

        // 원본을 고치거나 지워도 사본은 그대로
        call(owner, put("/api/minutes-templates/" + original), "{\"name\":\"바꿈\",\"items\":[{\"title\":\"요약\"}]}")
                .andExpect(status().isOk());
        call(owner, delete("/api/minutes-templates/" + original), null).andExpect(status().isOk());
        Long minutesId = finishMeeting(newId(), folderId, "회의", LocalDateTime.of(2026, 10, 20, 10, 0), member, owner);
        call(member, put("/api/minutes/" + minutesId + "/template"), "{\"templateId\":%d}".formatted(copy))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.template.name").value("주간 회의록"))
                .andExpect(jsonPath("$.data.template.items[0].settings.recordType").value("TABLE"));

        // 사본은 아무도 고치지 않는다
        call(owner, put("/api/minutes-templates/" + copy), "{\"name\":\"바꿈\",\"items\":[{\"title\":\"요약\"}]}")
                .andExpect(status().isNotFound());
    }

    @Test
    void 소유자가_아니면_사본도_남지_않는다() throws Exception {
        Long owner = newId();
        Long member = newId();
        Long workspaceId = newId();
        folder(workspaceId, "기획", owner, member);
        Long mine = id(call(member, post("/api/minutes-templates"), "{\"name\":\"내 양식\",\"items\":[{\"title\":\"요약\"}]}"),
                "$.data.templateId");
        willThrow(new BusinessException(CommonErrorCode.FORBIDDEN))
                .given(workspaceDefaultTemplateClient).change(eq(workspaceId), eq(member), anyLong());

        call(member, put("/api/minutes-templates/workspaces/" + workspaceId + "/default"), "{\"templateId\":%d}".formatted(mine))
                .andExpect(status().isForbidden());
        call(owner, get("/api/minutes-templates"), null)
                .andExpect(jsonPath("$.data[?(@.name == '내 양식')]").isEmpty());
        // 멤버가 아닌 워크스페이스에는 정할 수 없다
        call(owner, put("/api/minutes-templates/workspaces/" + newId() + "/default"), "{\"templateId\":1}")
                .andExpect(status().isForbidden());
    }
}
