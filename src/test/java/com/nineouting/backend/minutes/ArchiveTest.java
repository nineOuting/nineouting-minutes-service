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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 아카이브 검색·필터·즐겨찾기 (REQ-053~056), 홈 검토 대기·최근 확정 (REQ-021~023, 026~027) */
class ArchiveTest extends ApiTestSupport {

    private Long writer;
    private Long attendee;
    private Long folderMember;
    private Long folderId;

    @BeforeEach
    void setUp() {
        writer = newId();
        attendee = newId();
        folderMember = newId();
        folderId = folder(newId(), "기획", writer, attendee, folderMember);
    }

    @Test
    void 확정된_회의록만_검색어_기간_태그_참석자로_찾는다() throws Exception {
        Long deploy = confirmed("배포 회의", LocalDateTime.of(2026, 10, 5, 10, 0), "서버 배포 일정을 정했다 (API 서버)", attendee);
        Long budget = confirmed("예산 회의", LocalDateTime.of(2026, 10, 15, 10, 0), "광고 예산을 승인했다");
        call(writer, put("/api/minutes/" + deploy + "/tags"), "{\"tags\":[\"개발\",\"Backend\"]}");
        Long writing = finishMeeting(newId(), folderId, "배포 준비", LocalDateTime.of(2026, 10, 25, 10, 0), writer, writer);
        writeContent(writing, writer, "아직 작성 중인 배포 내용");

        archive(folderMember).andExpect(jsonPath("$.data.items[*].title", contains("예산 회의", "배포 회의")))
                .andExpect(jsonPath("$.data.totalCount").value(2));
        archive(folderMember).param("q", "배포 일정")
                .andExpect(jsonPath("$.data.items[*].minutesId", contains(deploy.intValue())));
        archive(folderMember).param("q", "예산")
                .andExpect(jsonPath("$.data.items[*].minutesId", contains(budget.intValue())));
        archive(folderMember).param("q", "광")
                .andExpect(jsonPath("$.data.items[*].minutesId", contains(budget.intValue())));
        archive(folderMember).param("q", "api")   // 대소문자를 구분하지 않는다
                .andExpect(jsonPath("$.data.items[*].minutesId", contains(deploy.intValue())));
        archive(folderMember).param("from", "2026-10-10").param("to", "2026-10-31")
                .andExpect(jsonPath("$.data.items[*].minutesId", contains(budget.intValue())));
        archive(folderMember).param("tag", "개발")
                .andExpect(jsonPath("$.data.items[0].tags", contains("개발", "Backend")))
                .andExpect(jsonPath("$.data.totalCount").value(1));
        archive(folderMember).param("tag", "backend")
                .andExpect(jsonPath("$.data.items[*].minutesId", contains(deploy.intValue())));
        archive(folderMember).param("attendeeId", attendee.toString()).param("q", "배포")
                .andExpect(jsonPath("$.data.items[*].minutesId", contains(deploy.intValue())));
        archive(folderMember).param("size", "1")
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(true));
        archive(newId()).andExpect(status().isForbidden());
    }

    @Test
    void 즐겨찾기와_중요_표시는_내_화면에만_보인다() throws Exception {
        Long marked = confirmed("표시할 회의", LocalDateTime.of(2026, 10, 5, 10, 0), "내용");
        confirmed("다른 회의", LocalDateTime.of(2026, 10, 6, 10, 0), "내용");

        call(folderMember, put("/api/minutes/" + marked + "/marks"), "{\"favorite\":true,\"important\":false}")
                .andExpect(status().isOk());

        archive(folderMember).param("mark", "FAVORITE")
                .andExpect(jsonPath("$.data.items[*].minutesId", contains(marked.intValue())))
                .andExpect(jsonPath("$.data.items[0].favorite").value(true));
        archive(folderMember).param("mark", "IMPORTANT").andExpect(jsonPath("$.data.totalCount").value(0));
        archive(writer).param("mark", "FAVORITE").andExpect(jsonPath("$.data.totalCount").value(0));
        call(folderMember, get("/api/minutes/" + marked), null).andExpect(jsonPath("$.data.favorite").value(true));
        call(writer, get("/api/minutes/" + marked), null).andExpect(jsonPath("$.data.favorite").value(false));
    }

    @Test
    void 홈에_내가_검토할_회의록과_최근_확정_회의록이_보인다() throws Exception {
        Long later = finishMeeting(newId(), folderId, "나중 회의", LocalDateTime.of(2026, 10, 20, 10, 0), writer, writer, attendee);
        Long earlier = finishMeeting(newId(), folderId, "먼저 회의", LocalDateTime.of(2026, 10, 10, 10, 0), writer, writer, attendee);
        for (Long minutesId : new Long[] {later, earlier}) {
            writeContent(minutesId, writer, "내용");
            call(writer, post("/api/minutes/" + minutesId + "/review-request"), null);
        }

        call(attendee, get("/api/users/me/review-requests"), null)
                .andExpect(jsonPath("$.data.count").value(2))
                .andExpect(jsonPath("$.data.items[*].title", contains("먼저 회의", "나중 회의")))
                .andExpect(jsonPath("$.data.items[0].myStatus").value("PENDING"));

        call(attendee, post("/api/minutes/" + earlier + "/approve"), null);
        call(attendee, get("/api/users/me/review-requests"), null)
                .andExpect(jsonPath("$.data.count").value(1));
        call(writer, get("/api/users/me/review-requests"), null).andExpect(jsonPath("$.data.count").value(0));

        call(folderMember, get("/api/users/me/recent-minutes"), null)
                .andExpect(jsonPath("$.data[0].minutesId").value(earlier))
                .andExpect(jsonPath("$.data[0].folderName").value("기획"))
                .andExpect(jsonPath("$.data[0].workspaceName").isNotEmpty());
    }

    private Long confirmed(String title, LocalDateTime startAt, String content, Long... others) throws Exception {
        Long[] attendees = new Long[others.length + 1];
        attendees[0] = writer;
        System.arraycopy(others, 0, attendees, 1, others.length);
        Long minutesId = finishMeeting(newId(), folderId, title, startAt, writer, attendees);
        writeContent(minutesId, writer, content);
        call(writer, post("/api/minutes/" + minutesId + "/review-request"), null);
        if (others.length > 0) {
            call(writer, post("/api/minutes/" + minutesId + "/confirm"), null).andExpect(status().isOk());
        }
        return minutesId;
    }

    private ArchiveCall archive(Long userId) {
        return new ArchiveCall(userId, get("/api/folders/" + folderId + "/minutes"));
    }

    /** 검색 조건을 붙이고 andExpect로 실행한다 */
    private final class ArchiveCall {

        private final Long userId;
        private final MockHttpServletRequestBuilder request;

        private ArchiveCall(Long userId, MockHttpServletRequestBuilder request) {
            this.userId = userId;
            this.request = request;
        }

        ArchiveCall param(String name, String value) {
            request.param(name, value);
            return this;
        }

        org.springframework.test.web.servlet.ResultActions andExpect(
                org.springframework.test.web.servlet.ResultMatcher matcher) throws Exception {
            return call(userId, request, null).andExpect(matcher);
        }
    }
}
