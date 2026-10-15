package com.nineouting.backend.minutes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nineouting.backend.common.event.minutes.MinutesConfirmed;
import com.nineouting.backend.common.event.minutes.MinutesReviewRequested;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 검토 요청 → 코멘트·동의 → 확정, 확정 후 수정 (REQ-095~099, 110) */
class ReviewFlowTest extends ApiTestSupport {

    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 20, 14, 0);

    private Long writer;
    private Long first;
    private Long second;
    private Long folderMember;
    private Long folderId;
    private Long minutesId;

    @BeforeEach
    void setUp() throws Exception {
        writer = newId();
        first = newId();
        second = newId();
        folderMember = newId();
        folderId = folder(newId(), "기획", writer, first, second, folderMember);
        minutesId = finishMeeting(newId(), folderId, "주간 회의", START, writer, writer, first, second);
        writeContent(minutesId, writer, "배포 일정을 논의했다");
    }

    @Test
    void 코멘트를_반영해_다시_요청하면_모두_미확인으로_돌아가고_전원_동의하면_자동_확정된다() throws Exception {
        call(first, post("/api/minutes/" + minutesId + "/review-request"), null)
                .andExpect(jsonPath("$.code").value("NOT_MINUTES_WRITER"));
        call(first, post("/api/minutes/" + minutesId + "/approve"), null)
                .andExpect(jsonPath("$.code").value("MINUTES_NOT_REVIEWING"));
        call(writer, post("/api/minutes/" + minutesId + "/review-request"), null).andExpect(status().isOk());
        assertThat(outboxContains(MinutesReviewRequested.TYPE,
                "\"reviewerUserIds\":[%d,%d]".formatted(first, second))).isTrue();

        Long sectionId = id(call(writer, get("/api/minutes/" + minutesId), null), "$.data.sections[0].sectionId");
        call(first, post("/api/minutes/" + minutesId + "/approve"), null).andExpect(status().isOk());
        Long commentId = id(call(second, post("/api/minutes/" + minutesId + "/comments"),
                "{\"sectionId\":%d,\"quote\":\"배포 일정\",\"body\":\"날짜를 적어 주세요\"}".formatted(sectionId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.author.name").value("user" + second)), "$.data.commentId");
        call(writer, post("/api/minutes/" + minutesId + "/approve"), null)
                .andExpect(jsonPath("$.code").value("NOT_MINUTES_REVIEWER"));
        call(first, get("/api/minutes/" + minutesId + "/reviews"), null)
                .andExpect(jsonPath("$.data[*].status", contains("APPROVED", "CHANGES_REQUESTED")))
                .andExpect(jsonPath("$.data[0].reviewer.userId").value(first));

        call(second, post("/api/minutes/" + minutesId + "/comments/" + commentId + "/resolve"), null)
                .andExpect(jsonPath("$.code").value("NOT_MINUTES_WRITER"));
        call(writer, post("/api/minutes/" + minutesId + "/comments/" + commentId + "/resolve"), null)
                .andExpect(status().isOk());
        call(writer, post("/api/minutes/" + minutesId + "/review-request"), null).andExpect(status().isOk());
        call(writer, get("/api/minutes/" + minutesId + "/reviews"), null)
                .andExpect(jsonPath("$.data[*].status", contains("PENDING", "PENDING")));
        call(writer, get("/api/minutes/" + minutesId + "/comments"), null)
                .andExpect(jsonPath("$.data[0].resolvedAt").isNotEmpty());

        call(first, post("/api/minutes/" + minutesId + "/approve"), null);
        call(second, post("/api/minutes/" + minutesId + "/approve"), null);

        call(folderMember, get("/api/minutes/" + minutesId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.reviewRound").value(2))
                .andExpect(jsonPath("$.data.confirmedByUserId").doesNotExist())
                .andExpect(jsonPath("$.data.confirmedAt").isNotEmpty());
        assertThat(outboxContains(MinutesConfirmed.TYPE,
                "\"attendeeUserIds\":[%d,%d,%d]".formatted(writer, first, second))).isTrue();
        call(writer, post("/api/minutes/" + minutesId + "/review-request"), null)
                .andExpect(jsonPath("$.code").value("MINUTES_ALREADY_CONFIRMED"));
    }

    @Test
    void 작성자는_동의하지_않은_사람이_있어도_확정할_수_있고_확정_후에도_고칠_수_있다() throws Exception {
        call(writer, post("/api/minutes/" + minutesId + "/confirm"), null)
                .andExpect(jsonPath("$.code").value("MINUTES_NOT_REVIEWING"));
        call(writer, post("/api/minutes/" + minutesId + "/review-request"), null);
        call(first, post("/api/minutes/" + minutesId + "/confirm"), null)
                .andExpect(jsonPath("$.code").value("NOT_MINUTES_WRITER"));
        call(writer, post("/api/minutes/" + minutesId + "/confirm"), null).andExpect(status().isOk());

        String detail = call(writer, get("/api/minutes/" + minutesId), null)
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.confirmedByUserId").value(writer))
                .andReturn().getResponse().getContentAsString();
        int version = com.jayway.jsonpath.JsonPath.read(detail, "$.data.version");
        call(writer, put("/api/minutes/" + minutesId + "/sections"),
                "{\"version\":%d,\"sections\":[{\"title\":\"정정\",\"content\":\"날짜 수정\"}]}".formatted(version))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
        call(first, get("/api/minutes/" + minutesId + "/revisions"), null)
                .andExpect(jsonPath("$.data[0].afterConfirm").value(true));
    }

    @Test
    void 작성자_혼자인_회의록은_검토_요청하면_바로_확정된다() throws Exception {
        Long alone = finishMeeting(newId(), folderId, "혼자", START, writer, writer);

        call(writer, post("/api/minutes/" + alone + "/review-request"), null).andExpect(status().isOk());

        call(writer, get("/api/minutes/" + alone), null).andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }
}
