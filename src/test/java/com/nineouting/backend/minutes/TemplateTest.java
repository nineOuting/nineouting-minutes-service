package com.nineouting.backend.minutes;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nineouting.backend.common.event.meeting.MeetingFinished;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 회의록 양식 관리 (REQ-119~121) */
class TemplateTest extends ApiTestSupport {

    @Test
    void 내_양식을_만들고_고치고_지운다() throws Exception {
        Long me = newId();
        Long other = newId();

        Long templateId = id(call(me, post("/api/minutes-templates"), "{\"name\":\"짧은 양식\",\"items\":[{\"title\":\"요약\"},{\"title\":\"할 일\"}]}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.isDefault").value(false)), "$.data.templateId");

        call(me, get("/api/minutes-templates"), null)
                .andExpect(jsonPath("$.data[*].name", contains("기본 양식", "짧은 양식")))
                .andExpect(jsonPath("$.data[0].isDefault").value(true))
                .andExpect(jsonPath("$.data[1].items[*].title", contains("요약", "할 일")));
        call(other, get("/api/minutes-templates"), null)
                .andExpect(jsonPath("$.data[*].name", contains("기본 양식")));

        call(me, put("/api/minutes-templates/" + templateId), "{\"name\":\"바뀐 양식\",\"items\":[{\"title\":\"할 일\"},{\"title\":\"요약\"},{\"title\":\"메모\"}]}")
                .andExpect(jsonPath("$.data.items[*].title", contains("할 일", "요약", "메모")));
        call(other, put("/api/minutes-templates/" + templateId), "{\"name\":\"남의 것\",\"items\":[{\"title\":\"x\"}]}")
                .andExpect(status().isNotFound());
        call(me, put("/api/minutes-templates/" + DEFAULT_TEMPLATE_ID), "{\"name\":\"기본\",\"items\":[{\"title\":\"x\"}]}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TEMPLATE_NOT_EDITABLE"));
        call(me, post("/api/minutes-templates"), "{\"name\":\"\",\"items\":[]}")
                .andExpect(status().isBadRequest());

        call(me, delete("/api/minutes-templates/" + templateId), null).andExpect(status().isOk());
        call(me, get("/api/minutes-templates"), null).andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void 양식_설명과_항목_설정_레이아웃을_저장한다() throws Exception {
        Long me = newId();
        String body = """
                {"name":"프로젝트 회의록","description":"진행 상황을 정리하는 양식",
                 "items":[{"title":"개요","description":"회의 목적","settings":{"recordType":"LIST","titleSize":18,"color":"#27187d"}},
                          {"title":"안건별 논의","settings":{"recordType":"TABLE","table":{"rows":4,"cols":3}}}],
                 "layout":{"sectionGap":16,"pageNumber":true,"header":{"enabled":true,"text":"SYNC"}}}""";
        Long templateId = id(call(me, post("/api/minutes-templates"), body)
                .andExpect(jsonPath("$.data.description").value("진행 상황을 정리하는 양식"))
                .andExpect(jsonPath("$.data.items[0].settings.recordType").value("LIST"))
                .andExpect(jsonPath("$.data.items[1].description").value("")), "$.data.templateId");

        call(me, get("/api/minutes-templates"), null)
                .andExpect(jsonPath("$.data[0].items[0].description").value("각 안건에 대한 논의 내용을 정리할 수 있어요."))
                .andExpect(jsonPath("$.data[1].templateId").value(templateId))
                .andExpect(jsonPath("$.data[1].items[0].settings.titleSize").value(18))
                .andExpect(jsonPath("$.data[1].items[1].settings.table.cols").value(3))
                .andExpect(jsonPath("$.data[1].layout.header.text").value("SYNC"))
                .andExpect(jsonPath("$.data[1].layout.pageNumber").value(true));
    }

    @Test
    void 내_양식으로_회의록_칸을_만든다() throws Exception {
        Long writer = newId();
        Long templateId = id(call(writer, post("/api/minutes-templates"), "{\"name\":\"짧은 양식\",\"items\":[{\"title\":\"요약\"},{\"title\":\"할 일\"}]}"),
                "$.data.templateId");
        Long minutesId = finishMeeting(newId(), folder(newId(), "기획", writer), "회의", LocalDateTime.of(2026, 10, 20, 10, 0),
                writer, writer);

        call(writer, put("/api/minutes/" + minutesId + "/template"), "{\"templateId\":%d}".formatted(templateId))
                .andExpect(jsonPath("$.data.sections[*].title", contains("요약", "할 일")));
    }

    @Test
    void 양식을_즐겨찾기하면_내_목록에만_표시된다() throws Exception {
        Long me = newId();
        Long other = newId();
        Long mine = id(call(me, post("/api/minutes-templates"), "{\"name\":\"짧은 양식\",\"items\":[{\"title\":\"요약\"}]}"),
                "$.data.templateId");

        // REQ-123 기본 양식도, 내 양식도 즐겨찾기한다. 여러 번 해도 같다
        call(me, put("/api/minutes-templates/" + DEFAULT_TEMPLATE_ID + "/favorite"), null).andExpect(status().isOk());
        call(me, put("/api/minutes-templates/" + mine + "/favorite"), null).andExpect(status().isOk());
        call(me, put("/api/minutes-templates/" + mine + "/favorite"), null).andExpect(status().isOk());
        call(me, get("/api/minutes-templates"), null)
                .andExpect(jsonPath("$.data[*].favorite", contains(true, true)));
        call(other, get("/api/minutes-templates"), null).andExpect(jsonPath("$.data[0].favorite").value(false));
        // 남의 개인 양식은 없는 것처럼
        call(other, put("/api/minutes-templates/" + mine + "/favorite"), null).andExpect(status().isNotFound());

        call(me, delete("/api/minutes-templates/" + DEFAULT_TEMPLATE_ID + "/favorite"), null).andExpect(status().isOk());
        call(me, get("/api/minutes-templates"), null)
                .andExpect(jsonPath("$.data[*].favorite", contains(false, true)));
        // 즐겨찾기한 양식도 지울 수 있다
        call(me, delete("/api/minutes-templates/" + mine), null).andExpect(status().isOk());
    }

    @Test
    void 대기_화면에서_고른_양식으로_회의록을_만든다() throws Exception {
        Long writer = newId();
        Long attendee = newId();
        Long folderId = folder(newId(), "기획", writer, attendee);
        Long mine = id(call(writer, post("/api/minutes-templates"), "{\"name\":\"짧은 양식\",\"items\":[{\"title\":\"요약\"},{\"title\":\"할 일\"}]}"),
                "$.data.templateId");
        Long othersTemplate = id(call(attendee, post("/api/minutes-templates"), "{\"name\":\"남의 양식\",\"items\":[{\"title\":\"x\"}]}"),
                "$.data.templateId");
        LocalDateTime startAt = LocalDateTime.of(2026, 10, 20, 10, 0);

        // REQ-123 작성자가 쓸 수 있는 양식이면 그 양식의 칸으로 만든다
        Long meetingId = newId();
        minutesService.createFromMeeting(new MeetingFinished(meetingId, workspaceOf(folderId), folderId, "회의", startAt, "",
                writer, List.of(writer, attendee), mine));
        call(writer, get("/api/meetings/" + meetingId + "/minutes"), null)
                .andExpect(jsonPath("$.data.templateId").value(mine))
                .andExpect(jsonPath("$.data.sections[*].title", contains("요약", "할 일")));

        // 다른 참석자의 개인 양식은 작성자가 쓸 수 없어 양식 없이 만든다 (작성 화면에서 고른다)
        Long another = newId();
        minutesService.createFromMeeting(new MeetingFinished(another, workspaceOf(folderId), folderId, "회의", startAt, "",
                writer, List.of(writer, attendee), othersTemplate));
        call(writer, get("/api/meetings/" + another + "/minutes"), null)
                .andExpect(jsonPath("$.data.templateId").isEmpty())
                .andExpect(jsonPath("$.data.sections.length()").value(0));
    }

    @Test
    void 양식을_바꾸면_제목이_같은_칸의_내용은_남는다() throws Exception {
        Long writer = newId();
        Long templateId = id(call(writer, post("/api/minutes-templates"), "{\"name\":\"짧은 양식\",\"items\":[{\"title\":\"요약\"},{\"title\":\"할 일\"}]}"),
                "$.data.templateId");
        Long minutesId = finishMeeting(newId(), folder(newId(), "기획", writer), "회의", LocalDateTime.of(2026, 10, 20, 10, 0),
                writer, writer);
        writeContent(minutesId, writer, "논의 내용", "결정 내용", "미결 내용", "할 일 내용");

        call(writer, put("/api/minutes/" + minutesId + "/template"), "{\"templateId\":%d}".formatted(templateId))
                .andExpect(jsonPath("$.data.sections[*].title", contains("요약", "할 일")))
                .andExpect(jsonPath("$.data.sections[*].content", contains("", "할 일 내용")))
                .andExpect(jsonPath("$.data.templateId").value(templateId));
    }
}
