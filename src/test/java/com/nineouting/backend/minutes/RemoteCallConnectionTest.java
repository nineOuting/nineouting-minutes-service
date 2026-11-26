package com.nineouting.backend.minutes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nineouting.backend.common.client.AccessibleFolder;
import com.nineouting.backend.common.client.UserSummary;
import com.zaxxer.hikari.HikariDataSource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 다른 서비스(workspace, user)를 기다리는 동안 DB 연결을 쥐고 있지 않는지 (2026-11-26 부하 시험: 연결을 쥔 채 기다려 풀이 바닥남).
 * 가짜 클라이언트가 불릴 때 사용 중인 연결 수를 잰다. 시험은 한 번에 하나씩 돌아 다른 요청이 연결을 쓰지 않는다
 */
class RemoteCallConnectionTest extends ApiTestSupport {

    @Autowired
    DataSource dataSource;

    private final List<Integer> activeDuringRemoteCall = new ArrayList<>();
    private Long writer;
    private Long workspaceId;

    @BeforeEach
    void recordConnectionsDuringRemoteCalls() throws Exception {
        writer = newId();
        Long folderId = folder(newId(), "기획", writer);
        workspaceId = workspaceOf(folderId);
        finishMeeting(newId(), folderId, "주간 회의", LocalDateTime.now(), writer, writer);

        HikariDataSource hikari = dataSource.unwrap(HikariDataSource.class);
        given(workspaceClient.accessibleFolders(anyLong())).willAnswer(invocation -> {
            activeDuringRemoteCall.add(hikari.getHikariPoolMXBean().getActiveConnections());
            return List.of(new AccessibleFolder(folderId, workspaceId, "워크스페이스", null, "기획"));
        });
        given(userClient.findUsers(anyCollection(), any())).willAnswer(invocation -> {
            activeDuringRemoteCall.add(hikari.getHikariPoolMXBean().getActiveConnections());
            Collection<Long> ids = invocation.getArgument(0);
            Map<Long, UserSummary> users = ids.stream().collect(Collectors.toMap(Function.identity(),
                    id -> new UserSummary(id, "user" + id, "user" + id + "@example.com", null), (a, b) -> a));
            return users;
        });
        activeDuringRemoteCall.clear();
    }

    @Test
    void 양식_목록() throws Exception {
        call(writer, get("/api/minutes-templates"), null).andExpect(status().isOk());
        assertNoConnectionHeld();
    }

    @Test
    void 양식_즐겨찾기() throws Exception {
        call(writer, put("/api/minutes-templates/" + DEFAULT_TEMPLATE_ID + "/favorite"), null).andExpect(status().isOk());
        assertNoConnectionHeld();
    }

    @Test
    void 아카이브_목록() throws Exception {
        call(writer, get("/api/workspaces/" + workspaceId + "/minutes"), null).andExpect(status().isOk());
        assertNoConnectionHeld();
    }

    @Test
    void 작성_중인_회의록() throws Exception {
        call(writer, get("/api/users/me/writing-minutes"), null).andExpect(status().isOk());
        assertNoConnectionHeld();
    }

    private void assertNoConnectionHeld() {
        assertThat(activeDuringRemoteCall).isNotEmpty().allMatch(active -> active == 0);
    }
}
