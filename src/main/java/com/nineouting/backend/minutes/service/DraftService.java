package com.nineouting.backend.minutes.service;

import com.nineouting.backend.common.client.UserClient;
import com.nineouting.backend.common.client.UserSummary;
import com.nineouting.backend.common.web.error.BusinessException;
import com.nineouting.backend.minutes.client.DraftClient;
import com.nineouting.backend.minutes.config.DraftProperties;
import com.nineouting.backend.minutes.domain.DraftStatus;
import com.nineouting.backend.minutes.domain.Minutes;
import com.nineouting.backend.minutes.domain.MinutesAttendee;
import com.nineouting.backend.minutes.domain.MinutesSection;
import com.nineouting.backend.minutes.domain.MinutesStatus;
import com.nineouting.backend.minutes.domain.TemplateItem;
import com.nineouting.backend.minutes.error.MinutesErrorCode;
import com.nineouting.backend.minutes.repository.MinutesAttendeeRepository;
import com.nineouting.backend.minutes.repository.MinutesRepository;
import com.nineouting.backend.minutes.repository.MinutesSectionRepository;
import com.nineouting.backend.minutes.repository.TemplateItemRepository;
import com.nineouting.backend.minutes.repository.TemplateRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * AI 회의록 초안 (REQ-101~103, ai-service docs/api/minutes-draft-contract.md).
 * <ul>
 *   <li>재료: meeting-service의 안건과 점검을 마친 오늘 회의 요약(todaySummary final). 전사 전체는 보내지 않는다</li>
 *   <li>양식의 칸마다 비어 있으면 AI가 채우고(aiFill), 작성자가 이미 쓴 칸은 그대로 둔다. 글 칸과 표 칸 모두 같다 (표는 7.6).
 *       칸 설명·표의 열 개수·머리글·바닥글은 양식 그대로 (5.8, 2.1)</li>
 *   <li>표 칸은 주고받을 때 마크다운 표, 저장은 줄 = 행·탭 = 열 (회의록 작성 화면 형식)</li>
 *   <li>양식을 처음 고르면 자동으로 요청하고(REQ-101 "선택한 양식에 맞춰"), 실패하면 작성자가 다시 요청한다</li>
 *   <li>오늘 회의 요약이 아직 없으면 기다리는 상태로 두고, 요약이 오면 자동으로 요청한다. 한도(10분)를 넘으면 실패로 둔다</li>
 *   <li>결과는 콜백으로 받아, 그사이 작성자가 쓴 칸은 덮어쓰지 않고 빈 칸만 채운다. 채운 칸의 줄마다 근거, 출처를 저장한다 (DraftLines)</li>
 *   <li>초안을 만든 뒤 양식을 바꾸면 바꾸기 전 칸의 줄을 previousLines로 보내 다시 배치한다 (명세 8장). 결과가 올 때까지
 *       작성자가 저장하지 않았으면 결과로 칸을 바꾸고, 저장했으면 빈 칸만 채운다. 실패하면 제목이 같은 칸의 글을 옮긴 그대로 둔다</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DraftService {

    /** 표 칸 저장 형식의 열 구분 (MinutesExportService와 같음) */
    private static final String CELL_SEPARATOR = "	";
    /** 한 번에 확인하는 요약 기다리는 회의록 수 */
    private static final int WAITING_BATCH_SIZE = 20;
    /** 요약을 한도 안에 받지 못했을 때의 draftError */
    private static final String DRAFT_SOURCE_TIMEOUT = "DRAFT_SOURCE_TIMEOUT";

    private final MinutesAccess access;
    private final MinutesRepository minutesRepository;
    private final MinutesService minutesService;
    private final MinutesSectionRepository sectionRepository;
    private final MinutesAttendeeRepository attendeeRepository;
    private final TemplateRepository templateRepository;
    private final TemplateItemRepository templateItemRepository;
    private final DraftClient draftClient;
    private final DraftProperties properties;
    private final UserClient userClient;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    /** 작성자가 요청 (처음이든 다시든). 작성 중, 양식을 고른 뒤 */
    @Transactional
    public void request(Long minutesId, Long userId) {
        Minutes minutes = access.requireForUpdate(minutesId);
        access.requireWriter(minutes, userId);
        if (!minutes.is(MinutesStatus.WRITING)) {
            throw new BusinessException(MinutesErrorCode.MINUTES_NOT_WRITING);
        }
        if (minutes.getTemplateId() == null) {
            throw new BusinessException(MinutesErrorCode.MINUTES_TEMPLATE_REQUIRED);
        }
        if (isInProgress(minutes)) {
            throw new BusinessException(MinutesErrorCode.DRAFT_IN_PROGRESS);
        }
        sendOrWait(minutes);
    }

    /** 양식을 처음 고르면 저장이 끝난 뒤 자동으로 요청한다. 요약이 없으면 기다리고, 그 밖의 실패는 FAILED로 남긴다 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTemplateSelected(MinutesService.TemplateSelected event) {
        if (!properties.enabled()) {
            return;
        }
        Minutes minutes = access.requireForUpdate(event.minutesId());
        if (minutes.getDraftStatus() != null || !minutes.is(MinutesStatus.WRITING)) {
            return;
        }
        try {
            sendOrWait(minutes);
        } catch (BusinessException e) {
            log.info("양식을 고른 뒤 초안을 바로 요청하지 못했습니다 (minutesId={}): {}", minutes.getId(), e.getMessage());
            minutes.draftFailed(e.getErrorCode().getCode());
        }
    }

    /**
     * 초안을 만든 뒤 양식을 바꾸면 같은 저장 안에서 다시 배치를 "만드는 중"으로 둔다. 양식 변경 응답에 이 상태가 실려
     * 화면이 결과를 기다린다. 보낼 줄이 없거나 상한을 넘으면 제목이 같은 칸의 글을 옮긴 그대로 둔다 (REQ-140)
     */
    @EventListener
    public void markRelayout(MinutesService.TemplateChanged event) {
        if (!properties.enabled()) {
            return;
        }
        Minutes minutes = access.requireForUpdate(event.minutesId());
        // 초안을 한 번이라도 받아 줄을 저장해 둔 회의록만. 만드는 중이면 그 결과를 기다린다
        if (minutes.getDraftLines() == null || !minutes.is(MinutesStatus.WRITING) || isInProgress(minutes)) {
            return;
        }
        if (relayoutLines(minutes, event) == null) {
            log.info("다시 배치할 줄이 없거나 상한을 넘어 제목이 같은 칸만 옮깁니다 (minutesId={})", minutes.getId());
            return;
        }
        minutes.relayoutRequested(UUID.randomUUID().toString(), LocalDateTime.now(clock));
    }

    /** 저장이 끝난 뒤 다시 배치를 보낸다. 보내지 못하면 실패로 둔다 (칸은 제목이 같은 칸의 글을 옮긴 그대로) */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTemplateChanged(MinutesService.TemplateChanged event) {
        Minutes minutes = access.requireForUpdate(event.minutesId());
        if (minutes.getDraftStatus() != DraftStatus.REQUESTED || minutes.getDraftRelayoutVersion() == null) {
            return;
        }
        try {
            send(minutes, relayoutLines(minutes, event), minutes.getDraftRequestId());
        } catch (BusinessException e) {
            log.info("양식을 바꾼 뒤 다시 배치를 요청하지 못했습니다 (minutesId={}): {}", minutes.getId(), e.getMessage());
            minutes.draftFailed(e.getErrorCode().getCode());
        }
    }

    /** 다시 배치의 이전 줄. 없거나 상한을 넘으면 null */
    private ArrayNode relayoutLines(Minutes minutes, MinutesService.TemplateChanged event) {
        ArrayNode lines = DraftLines.previousLines(event.before(),
                minutes.getDraftLines() == null ? null : jsonMapper.readTree(minutes.getDraftLines()));
        return lines == null || lines.isEmpty() ? null : lines;
    }

    /** 오늘 회의 요약을 기다리는 초안: 요약이 오면 요청하고, 한도를 넘으면 실패로 둔다 */
    @Scheduled(fixedDelayString = "${nineouting.minutes.draft.source-check-interval:10s}")
    @Transactional
    public void requestWaitingDrafts() {
        if (!properties.enabled()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        for (Minutes minutes : minutesRepository.lockWaitingForSource(WAITING_BATCH_SIZE)) {
            if (!minutes.is(MinutesStatus.WRITING)) {
                minutes.draftFailed(MinutesErrorCode.MINUTES_NOT_WRITING.getCode());
                continue;
            }
            try {
                send(minutes);
            } catch (BusinessException e) {
                if (!minutes.getDraftRequestedAt().plus(properties.sourceWait()).isAfter(now)) {
                    boolean notReady = e.getErrorCode() == MinutesErrorCode.DRAFT_SOURCE_NOT_READY;
                    log.info("초안 재료를 기다리다 한도를 넘었습니다 (minutesId={}): {}", minutes.getId(), e.getMessage());
                    minutes.draftFailed(notReady ? DRAFT_SOURCE_TIMEOUT : e.getErrorCode().getCode());
                }
            }
        }
    }

    /** 요약이 없으면 기다리는 상태로 두고, 그 밖의 실패는 그대로 던진다 */
    private void sendOrWait(Minutes minutes) {
        try {
            send(minutes);
        } catch (BusinessException e) {
            if (e.getErrorCode() != MinutesErrorCode.DRAFT_SOURCE_NOT_READY) {
                throw e;
            }
            minutes.draftWaitingForSource(LocalDateTime.now(clock));
        }
    }

    /** 기다리는 중이거나, 요청한 지 한도 안이면 만드는 중으로 본다 */
    private boolean isInProgress(Minutes minutes) {
        if (minutes.getDraftStatus() == DraftStatus.WAITING_SOURCE) {
            return true;
        }
        return minutes.getDraftStatus() == DraftStatus.REQUESTED
                && minutes.getDraftRequestedAt().plus(properties.inProgressGuard()).isAfter(LocalDateTime.now(clock));
    }

    /** ai-service 결과 (POST /internal/minutes/{minutesId}/draft-result). 지금 기다리는 요청의 결과만 받는다 */
    @Transactional
    public void receiveResult(Long minutesId, JsonNode body) {
        Minutes minutes = access.requireForUpdate(minutesId);
        String requestId = body.path("requestId").asString("");
        if (!requestId.equals(minutes.getDraftRequestId())) {
            log.info("기다리지 않는 초안 결과라 버립니다 (minutesId={}, requestId={})", minutesId, requestId);
            return;
        }
        if (!"SUCCEEDED".equals(body.path("status").asString(""))) {
            minutes.draftFailed(body.path("error").path("code").asString("UNKNOWN"));
            return;
        }
        if (minutes.is(MinutesStatus.WRITING)) {
            apply(minutes, body);
        }
        minutes.draftSucceeded();
    }

    /**
     * 결과의 AI 칸을 반영한다. 다시 배치이고 그사이 작성자가 저장하지 않았으면 모든 칸을 결과로 바꾸고, 새 양식에 맞는
     * 칸이 없는 줄(unplacedLines)은 잃지 않게 마지막 글 칸 끝에 붙인다. 그 밖에는 빈 칸만 채운다
     */
    private void apply(Minutes minutes, JsonNode body) {
        boolean relayout = minutes.getDraftRelayoutVersion() != null
                && minutes.getDraftRelayoutVersion() == minutes.getContentVersion();
        Map<String, String> drafted = new HashMap<>();
        body.path("sections").forEach(section -> {
            if (relayout || section.path("aiFilled").asBoolean(false)) {
                String content = section.path("content").asString("");
                drafted.put(section.path("title").asString(""),
                        "table".equals(section.path("type").asString("")) ? fromMarkdownTable(content) : content);
            }
        });
        List<MinutesSection> sections = sectionRepository.findByMinutesIdOrderByPosition(minutes.getId());
        Set<String> filled = new HashSet<>();
        for (MinutesSection section : sections) {
            String content = drafted.get(section.getTitle());
            if ((relayout || section.getContent().isBlank()) && content != null && !content.isBlank()) {
                section.change(section.getPosition(), section.getTitle(), content);
                filled.add(section.getTitle());
            }
        }
        if (relayout) {
            keepUnplacedLines(minutes, sections, body.path("unplacedLines"));
        }
        if (!filled.isEmpty()) {
            minutesService.recordDraft(minutes, sections);
        }
        List<ObjectNode> lines = DraftLines.fromResult(body.path("sections"), filled);
        JsonNode stored = relayout || minutes.getDraftLines() == null ? null : jsonMapper.readTree(minutes.getDraftLines());
        minutes.keepDraftLines(jsonMapper.writeValueAsString(DraftLines.replace(stored, filled, lines)));
    }

    /** 다시 배치에서 새 양식에 맞는 칸이 없는 줄: 버리지 않고 마지막 글 칸 끝에 붙인다 */
    private void keepUnplacedLines(Minutes minutes, List<MinutesSection> sections, JsonNode unplacedLines) {
        if (!unplacedLines.isArray() || unplacedLines.isEmpty()) {
            return;
        }
        Set<String> tables = tableTitles(minutes.getTemplateId());
        for (int index = sections.size() - 1; index >= 0; index--) {
            MinutesSection section = sections.get(index);
            if (tables.contains(section.getTitle())) {
                continue;
            }
            List<String> lines = new ArrayList<>();
            if (!section.getContent().isBlank() && !DraftLines.NOTHING.equals(section.getContent().strip())) {
                lines.add(section.getContent());
            }
            unplacedLines.forEach(line -> lines.add("- " + line.path("sentence").asString("")));
            section.change(section.getPosition(), section.getTitle(), String.join("\n", lines));
            return;
        }
        log.warn("새 양식에 글 칸이 없어 칸에 맞지 않는 줄 {}개를 붙이지 못했습니다 (minutesId={})", unplacedLines.size(),
                minutes.getId());
    }

    private void send(Minutes minutes) {
        send(minutes, null, UUID.randomUUID().toString());
    }

    /** previousLines가 있으면 다시 배치 (명세 8장). 다시 배치는 양식을 바꿀 때 정해 둔 요청 번호를 쓴다 */
    private void send(Minutes minutes, ArrayNode previousLines, String requestId) {
        if (!properties.enabled()) {
            throw new BusinessException(MinutesErrorCode.DRAFT_UNAVAILABLE);
        }
        JsonNode source = draftClient.draftSource(minutes.getMeetingId());
        if (source == null) {
            throw new BusinessException(MinutesErrorCode.DRAFT_UNAVAILABLE);
        }
        if (!source.path("ready").asBoolean(false)) {
            throw new BusinessException(MinutesErrorCode.DRAFT_SOURCE_NOT_READY);
        }
        ObjectNode body = body(minutes, source, requestId, previousLines);
        DraftClient.Result result = draftClient.requestDraft(jsonMapper.writeValueAsString(body), requestId);
        switch (result) {
            case ACCEPTED -> {
                // 다시 배치는 양식을 바꿀 때 이미 "만드는 중"으로 두었다 (markRelayout)
                if (previousLines == null) {
                    minutes.draftRequested(requestId, LocalDateTime.now(clock));
                }
            }
            case REJECTED -> {
                if (previousLines == null) {
                    minutes.draftRequested(requestId, LocalDateTime.now(clock));
                }
                minutes.draftFailed("INVALID_INPUT");
            }
            default -> throw new BusinessException(MinutesErrorCode.DRAFT_UNAVAILABLE);
        }
    }

    private ObjectNode body(Minutes minutes, JsonNode source, String requestId, ArrayNode previousLines) {
        ObjectNode body = jsonMapper.createObjectNode();
        body.put("requestId", requestId);
        body.put("minutesId", minutes.getId());
        body.put("meetingId", minutes.getMeetingId());
        body.put("title", minutes.getTitle());
        body.put("meetingStartAt", minutes.getMeetingStartAt().toString());
        body.set("attendees", attendees(minutes));
        ObjectNode template = body.putObject("template");
        JsonNode layout = templateRepository.findById(minutes.getTemplateId())
                .map(found -> jsonMapper.readTree(found.getLayout())).orElse(jsonMapper.createObjectNode());
        template.put("header", edge(layout.path("header")));
        template.put("footer", edge(layout.path("footer")));
        template.set("fields", fields(minutes, previousLines != null));
        body.set("agendas", source.path("agendas"));
        body.set("summary", source.path("summary"));
        // 확정된 전사 줄. ai-service가 STT 오인식 교정의 표기 출처로만 쓴다 (선택 칸, 없으면 빈 목록)
        JsonNode transcripts = source.path("transcripts");
        body.set("transcripts", transcripts.isArray() ? transcripts : jsonMapper.createArrayNode());
        if (previousLines != null) {
            body.set("previousLines", previousLines);
        }
        body.put("callbackUrl", properties.callbackBaseUrl() + "/internal/minutes/" + minutes.getId() + "/draft-result");
        return body;
    }

    /** 머리글·바닥글: 껐거나 비었으면 null */
    private static String edge(JsonNode settings) {
        String text = settings.path("text").asString("");
        return settings.path("enabled").asBoolean(false) && !text.isBlank() ? text : null;
    }

    /**
     * 양식 칸 그대로. 비어 있는 칸은 AI가 채운다 (글 칸, 표 칸 모두). 다시 배치는 모든 칸을 AI가 채운다
     * (칸의 글은 previousLines로 가므로, 제목이 같아 옮겨 온 글도 다시 고른다)
     */
    private ArrayNode fields(Minutes minutes, boolean relayout) {
        Map<String, TemplateItem> items = new HashMap<>();
        for (TemplateItem item : templateItemRepository.findByTemplateIdOrderByPosition(minutes.getTemplateId())) {
            items.put(item.getTitle(), item);
        }
        ArrayNode fields = jsonMapper.createArrayNode();
        for (MinutesSection section : sectionRepository.findByMinutesIdOrderByPosition(minutes.getId())) {
            TemplateItem item = items.get(section.getTitle());
            JsonNode settings = item == null ? jsonMapper.createObjectNode() : jsonMapper.readTree(item.getSettings());
            boolean table = "TABLE".equals(settings.path("recordType").asString(""));
            boolean aiFill = relayout || section.getContent().isBlank();
            ObjectNode field = fields.addObject();
            field.put("title", section.getTitle());
            field.put("description", item == null ? "" : item.getDescription());
            field.put("type", table ? "table" : "text");
            if (table) {
                field.put("columns", Math.clamp(settings.path("table").path("cols").asInt(1), 1, 6));
            }
            field.put("aiFill", aiFill);
            if (!aiFill) {
                field.put("content", table ? toMarkdownTable(section.getContent()) : section.getContent());
            }
        }
        return fields;
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

    /** 저장 형식(줄 = 행, 탭 = 열, 첫 행은 머리 행) → 마크다운 표 */
    static String toMarkdownTable(String content) {
        if (content.isBlank()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        String[] rows = content.split("\n");
        for (int index = 0; index < rows.length; index++) {
            String[] cells = rows[index].split(CELL_SEPARATOR, -1);
            lines.add("| " + String.join(" | ", Arrays.stream(cells).map(cell -> cell.replace("|", "/").strip()).toList()) + " |");
            if (index == 0) {
                lines.add("|" + "---|".repeat(cells.length));
            }
        }
        return String.join("\n", lines);
    }

    /** 마크다운 표 → 저장 형식. 구분 행(|---|)은 뺀다 */
    static String fromMarkdownTable(String content) {
        List<String> rows = new ArrayList<>();
        for (String line : content.split("\n")) {
            String row = line.strip();
            if (!row.startsWith("|") || row.matches("\\|(\\s*:?-+:?\\s*\\|)+")) {
                continue;
            }
            row = row.substring(1, row.endsWith("|") && row.length() > 1 ? row.length() - 1 : row.length());
            rows.add(String.join(CELL_SEPARATOR, Arrays.stream(row.split("\\|", -1)).map(String::strip).toList()));
        }
        return rows.isEmpty() ? content : String.join("\n", rows);
    }

    private ArrayNode attendees(Minutes minutes) {
        List<Long> ids = new ArrayList<>(attendeeRepository.findByMinutesIdOrderById(minutes.getId()).stream()
                .map(MinutesAttendee::getUserId).toList());
        Map<Long, UserSummary> users = ids.isEmpty() ? Map.of() : userClient.findUsers(ids, minutes.getWorkspaceId());
        ArrayNode attendees = jsonMapper.createArrayNode();
        for (Long id : ids) {
            UserSummary user = users.get(id);
            attendees.addObject().put("userId", id).put("name", user == null ? "" : user.name());
        }
        return attendees;
    }
}
