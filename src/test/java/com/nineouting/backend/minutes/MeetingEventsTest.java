package com.nineouting.backend.minutes;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.nineouting.backend.common.event.EventEnvelope;
import com.nineouting.backend.common.event.meeting.MeetingFinished;
import com.nineouting.backend.common.event.meeting.MeetingMoved;
import com.nineouting.backend.common.event.workspace.FolderDeleted;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.IntPredicate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

/** 실제 Kafka 토픽에 이벤트를 넣고 inbox → 처리기까지 확인한다 */
class MeetingEventsTest extends ApiTestSupport {

    private static final Duration WAIT = Duration.ofSeconds(20);

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    JsonMapper jsonMapper;

    @Test
    void 회의_마무리로_회의록이_생기고_폴더_이동과_삭제를_따른다() throws Exception {
        Long workspaceId = newId();
        Long writer = newId();
        Long from = folder(workspaceId, "기획", writer);
        Long to = folder(workspaceId, "보관", writer);
        Long meetingId = newId();

        publish(MeetingFinished.TOPIC, MeetingFinished.TYPE, new MeetingFinished(meetingId, workspaceId, from, "주간 회의",
                LocalDateTime.of(2026, 10, 20, 14, 0), writer, List.of(writer)));
        await(writer, meetingId, status -> status == 200);
        call(writer, get("/api/meetings/" + meetingId + "/minutes"), null)
                .andExpect(jsonPath("$.data.title").value("주간 회의"))
                .andExpect(jsonPath("$.data.folderId").value(from));

        publish(MeetingMoved.TOPIC, MeetingMoved.TYPE, new MeetingMoved(meetingId, workspaceId, from, to));
        awaitFolder(writer, meetingId, to);

        publish(FolderDeleted.TOPIC, FolderDeleted.TYPE, new FolderDeleted(workspaceId, List.of(to)));
        await(writer, meetingId, status -> status == 404);
    }

    private void publish(String topic, String type, Object payload) throws Exception {
        kafkaTemplate.send(topic, jsonMapper.writeValueAsString(EventEnvelope.of(type, "test", null, payload))).get();
    }

    private void await(Long userId, Long meetingId, IntPredicate status) throws Exception {
        Instant deadline = Instant.now().plus(WAIT);
        while (Instant.now().isBefore(deadline)) {
            if (status.test(call(userId, get("/api/meetings/" + meetingId + "/minutes"), null)
                    .andReturn().getResponse().getStatus())) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError(WAIT + " 안에 조건을 만족하지 못했습니다");
    }

    private void awaitFolder(Long userId, Long meetingId, Long folderId) throws Exception {
        Instant deadline = Instant.now().plus(WAIT);
        while (Instant.now().isBefore(deadline)) {
            String body = call(userId, get("/api/meetings/" + meetingId + "/minutes"), null)
                    .andReturn().getResponse().getContentAsString();
            if (body.contains("\"folderId\":" + folderId + ",")) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError(WAIT + " 안에 폴더가 바뀌지 않았습니다");
    }
}
