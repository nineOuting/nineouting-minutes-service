package com.nineouting.backend.minutes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nineouting.backend.common.event.minutes.MinutesConfirmed;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** 작성 완료 = 확정·공유 (REQ-136, 2026-10-28 기획서에서 검토 단계가 빠짐) */
class CompleteMinutesTest extends ApiTestSupport {

    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 28, 10, 0);

    @Test
    void 작성자가_작성_완료하면_검토_없이_확정되고_참석자에게_공유된다() throws Exception {
        Long writer = newId();
        Long attendee = newId();
        Long folderId = folder(newId(), "기획", writer, attendee);
        Long minutesId = finishMeeting(newId(), folderId, "주간 회의", START, writer, writer, attendee);
        writeContent(minutesId, writer, "배포 일정을 정했다");

        call(attendee, post("/api/minutes/" + minutesId + "/complete"), null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_MINUTES_WRITER"));
        call(writer, post("/api/minutes/" + minutesId + "/complete"), null).andExpect(status().isOk());

        call(attendee, get("/api/minutes/" + minutesId), null)
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.confirmedByUserId").value(writer));
        assertThat(outboxContains(MinutesConfirmed.TYPE, "\"minutesId\":" + minutesId)).isTrue();
        call(writer, post("/api/minutes/" + minutesId + "/complete"), null)
                .andExpect(jsonPath("$.code").value("MINUTES_ALREADY_CONFIRMED"));
    }
}
