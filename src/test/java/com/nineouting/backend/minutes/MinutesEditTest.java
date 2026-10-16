package com.nineouting.backend.minutes;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 회의록 보기 권한, 양식 선택, 내용 저장·수정 기록, 태그 */
class MinutesEditTest extends ApiTestSupport {

    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 20, 14, 0);

    private Long writer;
    private Long attendee;
    private Long folderMember;
    private Long outsider;
    private Long folderId;

    @BeforeEach
    void setUp() {
        writer = newId();
        attendee = newId();
        folderMember = newId();
        outsider = newId();
        folderId = folder(newId(), "기획", writer, attendee, folderMember);
    }

    @Test
    void 회의가_끝나기_전에는_준비_중이고_확정_전에는_참석자만_본다() throws Exception {
        Long meetingId = newId();
        call(writer, get("/api/meetings/" + meetingId + "/minutes"), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MINUTES_NOT_READY"));

        Long minutesId = finishMeeting(meetingId, folderId, "주간 회의", START, writer, writer, attendee);

        call(attendee, get("/api/minutes/" + minutesId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WRITING"))
                .andExpect(jsonPath("$.data.meetingStartAt").value("2026-10-20T14:00:00"))
                .andExpect(jsonPath("$.data.writer.name").value("user" + writer))
                .andExpect(jsonPath("$.data.attendees[*].userId", contains(writer.intValue(), attendee.intValue())))
                .andExpect(jsonPath("$.data.sections.length()").value(0));
        call(folderMember, get("/api/minutes/" + minutesId), null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_MINUTES_ATTENDEE"));
        call(outsider, get("/api/minutes/" + minutesId), null)
                .andExpect(jsonPath("$.code").value("FOLDER_ACCESS_DENIED"));
    }

    @Test
    void 같은_회의의_마무리를_두_번_받아도_회의록은_하나다() throws Exception {
        Long meetingId = newId();
        Long first = finishMeeting(meetingId, folderId, "회의", START, writer, writer);
        Long second = finishMeeting(meetingId, folderId, "회의", START, writer, writer);

        org.assertj.core.api.Assertions.assertThat(second).isEqualTo(first);
    }

    @Test
    void 작성자가_양식을_고르면_칸이_생기고_저장할_때마다_기록이_남는다() throws Exception {
        Long minutesId = finishMeeting(newId(), folderId, "회의", START, writer, writer, attendee);

        call(attendee, put("/api/minutes/" + minutesId + "/template"), "{\"templateId\":1}")
                .andExpect(jsonPath("$.code").value("NOT_MINUTES_WRITER"));
        Long firstSection = id(call(writer, put("/api/minutes/" + minutesId + "/template"), "{\"templateId\":1}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.templateId").value(1))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.sections[*].title", contains("안건별 논의", "결정 사항", "미결 안건", "할 일"))),
                "$.data.sections[0].sectionId");

        // 첫 칸은 고치고, 새 칸을 더하고, 나머지는 지운다
        String body = """
                {"version":1,"sections":[
                  {"sectionId":%d,"title":"안건별 논의","content":"배포 일정을 논의했다"},
                  {"title":"참고","content":"자료 링크"}]}""".formatted(firstSection);
        call(writer, put("/api/minutes/" + minutesId + "/sections"), body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.sections[0].sectionId").value(firstSection))
                .andExpect(jsonPath("$.data.sections[0].content").value("배포 일정을 논의했다"))
                .andExpect(jsonPath("$.data.sections[1].title").value("참고"))
                .andExpect(jsonPath("$.data.sections.length()").value(2));

        call(writer, put("/api/minutes/" + minutesId + "/sections"), body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MINUTES_VERSION_CONFLICT"));
        call(attendee, put("/api/minutes/" + minutesId + "/sections"), body.replace("\"version\":1", "\"version\":2"))
                .andExpect(jsonPath("$.code").value("NOT_MINUTES_WRITER"));
        call(attendee, get("/api/minutes/" + minutesId + "/revisions"), null)
                .andExpect(jsonPath("$.data[*].version", contains(2, 1)))
                .andExpect(jsonPath("$.data[0].editor.name").value("user" + writer))
                .andExpect(jsonPath("$.data[0].afterConfirm").value(false));
    }

    @Test
    void 검토_중에는_양식을_바꿀_수_없다() throws Exception {
        Long minutesId = finishMeeting(newId(), folderId, "회의", START, writer, writer, attendee);
        writeContent(minutesId, writer, "내용");
        call(writer, post("/api/minutes/" + minutesId + "/review-request"), null).andExpect(status().isOk());

        call(writer, put("/api/minutes/" + minutesId + "/template"), "{\"templateId\":1}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MINUTES_NOT_WRITING"));
        call(writer, put("/api/minutes/" + minutesId + "/template"), "{\"templateId\":999999}")
                .andExpect(status().isConflict());
    }

    @Test
    void 작성자가_태그를_붙인다() throws Exception {
        Long minutesId = finishMeeting(newId(), folderId, "회의", START, writer, writer);

        call(writer, put("/api/minutes/" + minutesId + "/tags"), "{\"tags\":[\" 배포 \",\"배포\",\"예산\",\"\"]}")
                .andExpect(jsonPath("$.data", contains("배포", "예산")));
        call(writer, get("/api/minutes/" + minutesId), null)
                .andExpect(jsonPath("$.data.tags", contains("배포", "예산")));
        // 대소문자만 다른 태그는 같은 태그로 보고 처음 것만 남긴다
        call(writer, put("/api/minutes/" + minutesId + "/tags"), "{\"tags\":[\"AI\",\"ai\",\"Ai\"]}")
                .andExpect(jsonPath("$.data", contains("AI")));
        call(writer, put("/api/minutes/" + minutesId + "/tags"),
                "{\"tags\":[\"1\",\"2\",\"3\",\"4\",\"5\",\"6\",\"7\",\"8\",\"9\",\"10\",\"11\"]}")
                .andExpect(jsonPath("$.code").value("TOO_MANY_TAGS"));
        call(writer, put("/api/minutes/" + minutesId + "/tags"), "{\"tags\":[\"%s\"]}".formatted("가".repeat(21)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 로그인하지_않으면_401이다() throws Exception {
        mockMvc.perform(get("/api/minutes/1")).andExpect(status().isUnauthorized());
    }
}
