package com.nineouting.backend.minutes;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.jayway.jsonpath.JsonPath;
import com.nineouting.backend.common.client.AccessibleFolder;
import com.nineouting.backend.common.client.FolderAccess;
import com.nineouting.backend.common.client.UserClient;
import com.nineouting.backend.common.client.UserSummary;
import com.nineouting.backend.common.client.WorkspaceClient;
import com.nineouting.backend.common.event.meeting.MeetingFinished;
import com.nineouting.backend.common.outbox.OutboxEventRepository;
import com.nineouting.backend.common.web.error.BusinessException;
import com.nineouting.backend.common.web.error.CommonErrorCode;
import com.nineouting.backend.minutes.service.MinutesService;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 실제 PostgreSQL·Kafka와 security-common 토큰 검증을 거치는 API 테스트의 공통 도우미.
 * user-service·workspace-service는 띄우지 않고 가짜로 바꾼다.
 * <ul>
 *   <li>사용자 이름: user{ID}</li>
 *   <li>폴더: {@link #folder}로 만든 폴더와 그 폴더에 들어갈 수 있는 사람</li>
 * </ul>
 */
// outbox는 보낸 이벤트를 바로 지우므로, 발행한 이벤트를 테이블에서 확인하려고 테스트에서는 전송을 멈춘다
@SpringBootTest(properties = "nineouting.outbox.relay-interval=1h")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class ApiTestSupport {

    /** Flyway가 넣은 기본 양식 */
    protected static final long DEFAULT_TEMPLATE_ID = 1L;

    private static final AtomicLong NEXT_ID = new AtomicLong(1000);
    private static final Map<Long, FakeFolder> FOLDERS = new ConcurrentHashMap<>();

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected OutboxEventRepository outboxEventRepository;

    @Autowired
    protected MinutesService minutesService;

    @MockitoBean
    protected UserClient userClient;

    @MockitoBean
    protected WorkspaceClient workspaceClient;

    @BeforeEach
    void fakeOtherServices() {
        given(userClient.findUsers(anyCollection())).willAnswer(invocation -> {
            Collection<Long> ids = invocation.getArgument(0);
            return ids.stream().collect(Collectors.toMap(Function.identity(),
                    id -> new UserSummary(id, "user" + id, "user" + id + "@example.com", null), (a, b) -> a));
        });
        given(workspaceClient.access(anyLong(), anyLong())).willAnswer(invocation -> {
            FakeFolder folder = requireFolder(invocation.getArgument(0));
            return new FolderAccess(folder.id(), folder.workspaceId(), folder.accessors().contains(invocation.<Long>getArgument(1)));
        });
        given(workspaceClient.accessibleFolders(anyLong())).willAnswer(invocation -> {
            Long userId = invocation.getArgument(0);
            return FOLDERS.values().stream()
                    .filter(folder -> folder.accessors().contains(userId))
                    .map(folder -> new AccessibleFolder(folder.id(), folder.workspaceId(), "워크스페이스" + folder.workspaceId(),
                            null, folder.name()))
                    .toList();
        });
    }

    protected static Long newId() {
        return NEXT_ID.incrementAndGet();
    }

    /** 가짜 workspace-service에 폴더를 만든다. accessors = 폴더에 들어갈 수 있는 사람 */
    protected static Long folder(Long workspaceId, String name, Long... accessors) {
        Long id = newId();
        FOLDERS.put(id, new FakeFolder(id, workspaceId, name, new LinkedHashSet<>(List.of(accessors))));
        return id;
    }

    private static FakeFolder requireFolder(Long folderId) {
        FakeFolder folder = FOLDERS.get(folderId);
        if (folder == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        return folder;
    }

    /** 회의 마무리 이벤트를 처리한 것처럼 회의록을 만들고 회의록 ID를 돌려준다 */
    protected Long finishMeeting(Long meetingId, Long folderId, String title, LocalDateTime startAt, Long writerId,
                                 Long... attendees) throws Exception {
        return finishMeetingAt(meetingId, folderId, title, startAt, "", writerId, attendees);
    }

    /** finishMeeting과 같고 회의 장소를 정한다 */
    protected Long finishMeetingAt(Long meetingId, Long folderId, String title, LocalDateTime startAt, String location,
                                   Long writerId, Long... attendees) throws Exception {
        minutesService.createFromMeeting(new MeetingFinished(meetingId, workspaceOf(folderId), folderId, title, startAt,
                location, writerId, List.of(attendees)));
        return id(call(writerId, get("/api/meetings/" + meetingId + "/minutes"), null), "$.data.minutesId");
    }

    /** 기본 양식을 고르고 내용을 채워 저장한다. 저장 후 version */
    protected int writeContent(Long minutesId, Long writerId, String... contents) throws Exception {
        String detail = call(writerId, put("/api/minutes/" + minutesId + "/template"), "{\"templateId\":1}")
                .andReturn().getResponse().getContentAsString();
        List<Integer> sectionIds = JsonPath.read(detail, "$.data.sections[*].sectionId");
        List<String> titles = JsonPath.read(detail, "$.data.sections[*].title");
        int version = JsonPath.read(detail, "$.data.version");
        StringBuilder sections = new StringBuilder();
        for (int i = 0; i < sectionIds.size(); i++) {
            String content = i < contents.length ? contents[i] : "";
            sections.append(i == 0 ? "" : ",").append("{\"sectionId\":%d,\"title\":\"%s\",\"content\":\"%s\"}"
                    .formatted(sectionIds.get(i), titles.get(i), content));
        }
        String saved = call(writerId, put("/api/minutes/" + minutesId + "/sections"),
                "{\"version\":%d,\"sections\":[%s]}".formatted(version, sections))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(saved, "$.data.version");
    }

    protected ResultActions call(Long userId, MockHttpServletRequestBuilder request, String body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + TestcontainersConfiguration.token(userId));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    protected boolean outboxContains(String eventType, String text) {
        return outboxEventRepository.findAll().stream()
                .anyMatch(event -> event.getEventType().equals(eventType) && event.getPayload().contains(text));
    }

    protected static Long id(ResultActions result, String path) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), path)).longValue();
    }

    private static Long workspaceOf(Long folderId) {
        return requireFolder(folderId).workspaceId();
    }

    private record FakeFolder(Long id, Long workspaceId, String name, Set<Long> accessors) {
    }
}
