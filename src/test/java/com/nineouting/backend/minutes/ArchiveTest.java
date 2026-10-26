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

/** 아카이브 검색·필터·즐겨찾기·최근 본 회의록 (REQ-053~056, 146), 홈 검토 대기·최근 확정·작성 중 (REQ-021~023, 026~027, 142) */
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
    void 폴더의_회의록을_상태와_상관없이_검색어_기간_장소_참석자로_찾는다() throws Exception {
        Long deploy = confirmed("배포 회의", LocalDateTime.of(2026, 10, 5, 10, 0), "3층 회의실",
                "서버 배포 일정을 정했다 (API 서버)", attendee);
        Long budget = confirmed("예산 회의", LocalDateTime.of(2026, 10, 15, 10, 0), "대회의실", "광고 예산을 승인했다");
        Long writing = finishMeeting(newId(), folderId, "배포 준비", LocalDateTime.of(2026, 10, 25, 10, 0), writer, writer);
        writeContent(writing, writer, "아직 작성 중인 배포 내용");

        // REQ-053. 작성 중인 회의록도 목록에 나온다
        archive(folderMember).andExpect(jsonPath("$.data.items[*].title", contains("배포 준비", "예산 회의", "배포 회의")))
                .andExpect(jsonPath("$.data.items[*].status", contains("WRITING", "CONFIRMED", "CONFIRMED")))
                .andExpect(jsonPath("$.data.items[1].location").value("대회의실"))
                .andExpect(jsonPath("$.data.totalCount").value(3));
        archive(folderMember).param("q", "배포 일정")
                .andExpect(jsonPath("$.data.items[*].minutesId", contains(deploy.intValue())));
        archive(folderMember).param("q", "예산")
                .andExpect(jsonPath("$.data.items[*].minutesId", contains(budget.intValue())));
        archive(folderMember).param("q", "광")
                .andExpect(jsonPath("$.data.items[*].minutesId", contains(budget.intValue())));
        archive(folderMember).param("q", "api")   // 대소문자를 구분하지 않는다
                .andExpect(jsonPath("$.data.items[*].minutesId", contains(deploy.intValue())));
        archive(folderMember).param("from", "2026-10-10").param("to", "2026-10-20")
                .andExpect(jsonPath("$.data.items[*].minutesId", contains(budget.intValue())));
        // REQ-055. 장소는 일부만 맞아도 찾는다
        archive(folderMember).param("location", "3층")
                .andExpect(jsonPath("$.data.items[*].minutesId", contains(deploy.intValue())));
        archive(folderMember).param("location", "회의실").param("from", "2026-10-10")
                .andExpect(jsonPath("$.data.items[*].minutesId", contains(budget.intValue())));
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

    @Test
    void 워크스페이스_아카이브는_들어갈_수_있는_모든_폴더의_회의록을_여러_장소와_참석자로_거른다() throws Exception {
        Long workspaceId = newId();
        Long planning = folder(workspaceId, "기획", writer, attendee, folderMember);
        Long design = folder(workspaceId, "디자인", writer, attendee);
        Long other = folder(newId(), "다른 곳", writer);
        Long first = finishMeetingAt(newId(), planning, "기획 회의", LocalDateTime.of(2026, 10, 5, 10, 0), "회의실 A", writer,
                writer, attendee);
        Long second = finishMeetingAt(newId(), design, "디자인 회의", LocalDateTime.of(2026, 10, 15, 10, 0), "회의실 B",
                writer, writer);
        Long third = finishMeetingAt(newId(), planning, "온라인 회의", LocalDateTime.of(2026, 10, 25, 10, 0), "", writer,
                writer, folderMember);
        finishMeetingAt(newId(), other, "다른 워크스페이스 회의", LocalDateTime.of(2026, 10, 20, 10, 0), "회의실 A", writer,
                writer);
        String url = "/api/workspaces/" + workspaceId + "/minutes";

        call(writer, get(url), null)
                .andExpect(jsonPath("$.data.items[*].minutesId", contains(third.intValue(), second.intValue(), first.intValue())))
                .andExpect(jsonPath("$.data.items[1].folderName").value("디자인"))
                .andExpect(jsonPath("$.data.items[2].attendees[*].userId", contains(writer.intValue(), attendee.intValue())))
                .andExpect(jsonPath("$.data.items[2].attendees[1].name").value("user" + attendee))
                .andExpect(jsonPath("$.data.totalCount").value(3));
        call(writer, get(url).param("sort", "OLDEST"), null)
                .andExpect(jsonPath("$.data.items[*].minutesId", contains(first.intValue(), second.intValue(), third.intValue())));
        // 장소는 고른 것 중 하나와 같아야 한다
        call(writer, get(url).param("location", "회의실 A", "회의실 B"), null)
                .andExpect(jsonPath("$.data.items[*].minutesId", contains(second.intValue(), first.intValue())));
        call(writer, get(url).param("location", "회의실"), null).andExpect(jsonPath("$.data.totalCount").value(0));
        // 참석자는 고른 사람 중 한 명이라도 참석했으면
        call(writer, get(url).param("attendeeId", attendee.toString(), folderMember.toString()), null)
                .andExpect(jsonPath("$.data.items[*].minutesId", contains(third.intValue(), first.intValue())));
        call(writer, get(url).param("attendeeId", attendee.toString()).param("location", "회의실 B"), null)
                .andExpect(jsonPath("$.data.totalCount").value(0));
        // 들어갈 수 없는 폴더의 회의록은 보이지 않는다
        call(folderMember, get(url), null)
                .andExpect(jsonPath("$.data.items[*].minutesId", contains(third.intValue(), first.intValue())));
        call(newId(), get(url), null).andExpect(jsonPath("$.data.totalCount").value(0));

        call(writer, get(url + "/locations"), null)
                .andExpect(jsonPath("$.data[*]", contains("회의실 A", "회의실 B")));
        call(folderMember, get(url + "/locations"), null).andExpect(jsonPath("$.data[*]", contains("회의실 A")));
    }

    @Test
    void 최근_본_회의록은_워크스페이스마다_최근_본_순으로_보인다() throws Exception {
        Long workspaceId = newId();
        Long planning = folder(workspaceId, "기획", writer, folderMember);
        Long first = finishMeeting(newId(), planning, "첫 회의", LocalDateTime.of(2026, 10, 5, 10, 0), writer, writer);
        Long second = finishMeeting(newId(), planning, "둘째 회의", LocalDateTime.of(2026, 10, 6, 10, 0), writer, writer);
        Long elsewhere = finishMeeting(newId(), folderId, "다른 워크스페이스", LocalDateTime.of(2026, 10, 7, 10, 0), writer,
                writer);
        String url = "/api/workspaces/" + workspaceId + "/viewed-minutes";

        call(folderMember, post("/api/minutes/" + first + "/view"), null).andExpect(status().isOk());
        call(folderMember, post("/api/minutes/" + second + "/view"), null).andExpect(status().isOk());
        call(folderMember, post("/api/minutes/" + elsewhere + "/view"), null).andExpect(status().isOk());
        call(folderMember, get(url), null)
                .andExpect(jsonPath("$.data[*].minutesId", contains(second.intValue(), first.intValue())));

        call(folderMember, post("/api/minutes/" + first + "/view"), null).andExpect(status().isOk());
        call(folderMember, get(url).param("size", "1"), null)
                .andExpect(jsonPath("$.data[*].minutesId", contains(first.intValue())))
                .andExpect(jsonPath("$.data[0].title").value("첫 회의"));
        call(writer, get(url), null).andExpect(jsonPath("$.data.length()").value(0));
        call(newId(), post("/api/minutes/" + first + "/view"), null).andExpect(status().isForbidden());
    }

    @Test
    void 홈에_내가_작성자인_작성_중_회의록이_오래된_순으로_보인다() throws Exception {
        Long later = finishMeetingAt(newId(), folderId, "나중 회의", LocalDateTime.of(2026, 10, 20, 10, 0), "회의실 B", writer,
                writer, attendee);
        Long earlier = finishMeeting(newId(), folderId, "먼저 회의", LocalDateTime.of(2026, 10, 10, 10, 0), writer, writer);
        Long reviewing = finishMeeting(newId(), folderId, "검토 중 회의", LocalDateTime.of(2026, 10, 1, 10, 0), writer, writer);
        writeContent(reviewing, writer, "내용");
        call(writer, post("/api/minutes/" + reviewing + "/review-request"), null);
        finishMeeting(newId(), folderId, "다른 사람이 쓰는 회의", LocalDateTime.of(2026, 10, 2, 10, 0), attendee, attendee);

        call(writer, get("/api/users/me/writing-minutes"), null)
                .andExpect(jsonPath("$.data[*].minutesId", contains(earlier.intValue(), later.intValue())))
                .andExpect(jsonPath("$.data[1].location").value("회의실 B"))
                .andExpect(jsonPath("$.data[1].folderName").value("기획"))
                .andExpect(jsonPath("$.data[1].workspaceName").isNotEmpty());
        call(folderMember, get("/api/users/me/writing-minutes"), null).andExpect(jsonPath("$.data.length()").value(0));
    }

    private Long confirmed(String title, LocalDateTime startAt, String content, Long... others) throws Exception {
        return confirmed(title, startAt, "", content, others);
    }

    private Long confirmed(String title, LocalDateTime startAt, String location, String content, Long... others)
            throws Exception {
        Long[] attendees = new Long[others.length + 1];
        attendees[0] = writer;
        System.arraycopy(others, 0, attendees, 1, others.length);
        Long minutesId = finishMeetingAt(newId(), folderId, title, startAt, location, writer, attendees);
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
