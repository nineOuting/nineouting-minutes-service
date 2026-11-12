package com.nineouting.backend.minutes.service;

import com.nineouting.backend.minutes.client.RagClient;
import com.nineouting.backend.minutes.config.DraftProperties;
import com.nineouting.backend.minutes.config.RagProperties;
import com.nineouting.backend.minutes.domain.Minutes;
import com.nineouting.backend.minutes.domain.MinutesSection;
import com.nineouting.backend.minutes.domain.MinutesStatus;
import com.nineouting.backend.minutes.domain.RagStatus;
import com.nineouting.backend.minutes.domain.TemplateItem;
import com.nineouting.backend.minutes.repository.MinutesRepository;
import com.nineouting.backend.minutes.repository.MinutesSectionRepository;
import com.nineouting.backend.minutes.repository.TemplateItemRepository;
import com.nineouting.backend.minutes.repository.TemplateRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * REQ-185 작성 완료 회의록을 ai-service 검색용 저장소에 넣는다 (docs/services/rag-chatbot.md 3장, minutes-draft-contract.md 4장).
 * 작성 완료 때 처음, 그 뒤 저장할 때마다 다시 넣는다 (Minutes.ragPending). 작성 중인 회의록은 넣지 않는다.
 * 보관함 표 없이 회의록 행의 rag_status로 보내고 다시 보낸다. 쓰레기통의 회의록은 질문할 때 목록에서 빠지므로 지우지 않고,
 * 영구 삭제할 때 지운다. 표 칸은 마크다운 표로 바꿔 보낸다.
 * ai-service가 안 되거나 RAG가 꺼져 있으면(404) 실패로 굳히지 않고 간격을 늘려(최대 약 30분) 계속 다시 보낸다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MinutesRagSync {

    static final int MAX_DOCS = 1000;
    private static final int MAX_PER_RUN = 10;
    private static final int MAX_TITLE = 500;
    private static final String FORMAT_VERSION = "1.0";
    private static final Set<String> RETRYABLE = Set.of("internal_error");
    private static final String REJECTED = "rejected";
    private static final String UNAVAILABLE = "unavailable";

    private final MinutesRepository minutesRepository;
    private final MinutesSectionRepository sectionRepository;
    private final TemplateRepository templateRepository;
    private final TemplateItemRepository templateItemRepository;
    private final RagClient ragClient;
    private final DraftProperties draftProperties;
    private final RagProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${nineouting.minutes.rag.send-interval:5s}")
    public void run() {
        if (!draftProperties.enabled()) {
            return;
        }
        for (int i = 0; i < MAX_PER_RUN; i++) {
            if (!Boolean.TRUE.equals(transactionTemplate.execute(status -> sendNext()))) {
                break;
            }
        }
        for (int i = 0; i < MAX_PER_RUN; i++) {
            if (!Boolean.TRUE.equals(transactionTemplate.execute(status -> checkNextStale()))) {
                break;
            }
        }
    }

    /** meeting-service 챗봇이 찾을 회의록: 폴더의 작성 완료 회의록 (최근 회의부터 1,000개, 쓰레기통 제외) */
    public List<Long> confirmedIds(Long folderId) {
        return minutesRepository.findConfirmedIdsByFolderId(folderId, PageRequest.of(0, MAX_DOCS));
    }

    /** ai-service 완료 콜백 (POST /internal/rag/ingest-result). 지금 보낸 버전이 아니면 무시한다 */
    public void receiveResult(JsonNode body) {
        if (!RagClient.SOURCE_TYPE.equals(body.path("sourceType").asString(""))) {
            return;
        }
        long docId = body.path("docId").asLong(0);
        String version = body.path("docVersion").asString("");
        transactionTemplate.executeWithoutResult(status -> minutesRepository.findByIdForUpdate(docId)
                .filter(minutes -> minutes.getRagStatus() == RagStatus.ACCEPTED)
                .filter(minutes -> version.equals(String.valueOf(minutes.getRagVersion())))
                .ifPresent(minutes -> apply(minutes, body.path("status").asString(""),
                        body.path("errorCode").asString(null))));
    }

    /** 영구 삭제한 회의록을 검색용 저장소에서도 지운다 (지운 트랜잭션이 끝난 뒤). 실패해도 삭제를 막지 않는다 */
    public void deleteQuietly(Collection<Long> minutesIds) {
        if (!draftProperties.enabled() || minutesIds.isEmpty()) {
            return;
        }
        List<Long> ids = List.copyOf(minutesIds);
        Runnable delete = () -> ids.forEach(id -> {
            try {
                ragClient.delete(id);
            } catch (RagClient.Unavailable e) {
                log.warn("검색용 저장소에서 회의록을 지우지 못했습니다 (minutesId={}): {}", id, e.getMessage());
            }
        });
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    delete.run();
                }
            });
        } else {
            delete.run();
        }
    }

    private boolean sendNext() {
        LocalDateTime now = LocalDateTime.now(clock);
        Optional<Minutes> due = minutesRepository.lockNextRagDue(now);
        if (due.isEmpty()) {
            return false;
        }
        Minutes minutes = due.get();
        if (!minutes.is(MinutesStatus.CONFIRMED)) {
            minutes.ragFailed(REJECTED);
            return true;
        }
        int version = minutes.getContentVersion();
        try {
            ragClient.ingest(body(minutes, version));
            minutes.ragAccepted(version, now);
        } catch (RagClient.Rejected e) {
            minutes.ragFailed(REJECTED);
            log.warn("ai-service가 회의록 넣기를 거절했습니다 (minutesId={}): {}", minutes.getId(), e.getMessage());
        } catch (RagClient.Unavailable e) {
            minutes.ragRetryLater(UNAVAILABLE, now.plus(backoff(minutes)));
            log.warn("회의록을 검색용 저장소에 보내지 못해 나중에 다시 보냅니다 (minutesId={}, {}회째): {}",
                    minutes.getId(), minutes.getRagAttempts(), e.getMessage());
        }
        return true;
    }

    /** 접수됐는데 콜백이 없으면 상태를 묻는다. 넣은 적 없거나 옛 버전이면 다시 보낸다 */
    private boolean checkNextStale() {
        LocalDateTime now = LocalDateTime.now(clock);
        Optional<Minutes> stale = minutesRepository.lockNextRagStale(now.minus(properties.acceptedTimeout()));
        if (stale.isEmpty()) {
            return false;
        }
        Minutes minutes = stale.get();
        try {
            Optional<JsonNode> result = ragClient.status(minutes.getId());
            if (result.isEmpty()
                    || !String.valueOf(minutes.getRagVersion()).equals(result.get().path("docVersion").asString(""))) {
                minutes.ragPending();
            } else {
                apply(minutes, result.get().path("status").asString(""), result.get().path("errorCode").asString(null));
                if (minutes.getRagStatus() == RagStatus.ACCEPTED) {
                    minutes.ragAccepted(minutes.getRagVersion(), now);
                }
            }
        } catch (RagClient.Unavailable e) {
            minutes.ragAccepted(minutes.getRagVersion(), now);
            log.warn("회의록 검색 상태를 확인하지 못했습니다 (minutesId={}): {}", minutes.getId(), e.getMessage());
        }
        return true;
    }

    private void apply(Minutes minutes, String status, String errorCode) {
        if ("ready".equals(status)) {
            minutes.ragReady();
        } else if ("failed".equals(status)) {
            if (RETRYABLE.contains(errorCode)) {
                minutes.ragRetry(errorCode, LocalDateTime.now(clock).plus(backoff(minutes)), properties.maxAttempts());
            } else {
                minutes.ragFailed(errorCode == null ? "internal_error" : errorCode);
                log.warn("회의록을 검색용 저장소에 넣지 못했습니다 (minutesId={}, errorCode={})", minutes.getId(), errorCode);
            }
        }
    }

    /** 넣기 요청 (minutes-draft-contract.md 4.1) */
    private ObjectNode body(Minutes minutes, int version) {
        ObjectNode body = jsonMapper.createObjectNode();
        body.put("docId", minutes.getId());
        body.put("docVersion", String.valueOf(version));
        body.put("workspaceId", minutes.getWorkspaceId());
        body.put("sourceType", RagClient.SOURCE_TYPE);
        String title = minutes.getTitle();
        body.put("title", title.length() > MAX_TITLE ? title.substring(0, MAX_TITLE) : title);
        ObjectNode content = body.putObject("minutes");
        content.put("formatVersion", FORMAT_VERSION);
        content.put("meetingId", minutes.getMeetingId());
        content.put("meetingStartAt", minutes.getMeetingStartAt().toString());
        LocalDateTime savedAt = minutes.getUpdatedAt() == null ? minutes.getConfirmedAt() : minutes.getUpdatedAt();
        if (savedAt != null) {
            content.put("savedAt", savedAt.toString());
        }
        JsonNode layout = minutes.getTemplateId() == null ? jsonMapper.createObjectNode()
                : templateRepository.findById(minutes.getTemplateId())
                        .map(found -> jsonMapper.readTree(found.getLayout())).orElse(jsonMapper.createObjectNode());
        putEdge(content, "header", layout.path("header"));
        putEdge(content, "footer", layout.path("footer"));
        Set<String> tables = tableTitles(minutes.getTemplateId());
        ArrayNode sections = content.putArray("sections");
        for (MinutesSection section : sectionRepository.findByMinutesIdOrderByPosition(minutes.getId())) {
            if (section.getTitle() == null || section.getTitle().isBlank()) {
                continue;
            }
            sections.addObject()
                    .put("title", section.getTitle())
                    .put("content", tables.contains(section.getTitle())
                            ? DraftService.toMarkdownTable(section.getContent()) : section.getContent());
        }
        body.put("callbackUrl", draftProperties.callbackBaseUrl() + "/internal/rag/ingest-result");
        return body;
    }

    /** 머리글, 바닥글: 껐거나 비었으면 null */
    private static void putEdge(ObjectNode content, String key, JsonNode settings) {
        String text = settings.path("text").asString("");
        if (settings.path("enabled").asBoolean(false) && !text.isBlank()) {
            content.put(key, text);
        } else {
            content.putNull(key);
        }
    }

    private Set<String> tableTitles(Long templateId) {
        Set<String> tables = new HashSet<>();
        if (templateId != null) {
            for (TemplateItem item : templateItemRepository.findByTemplateIdOrderByPosition(templateId)) {
                if ("TABLE".equals(jsonMapper.readTree(item.getSettings()).path("recordType").asString(""))) {
                    tables.add(item.getTitle());
                }
            }
        }
        return tables;
    }

    private Duration backoff(Minutes minutes) {
        return properties.retryDelay().multipliedBy(1L << Math.min(minutes.getRagAttempts(), 6));
    }
}
