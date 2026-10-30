package com.nineouting.backend.minutes.service;

import com.nineouting.backend.common.client.UserClient;
import com.nineouting.backend.common.client.UserSummary;
import com.nineouting.backend.common.web.error.BusinessException;
import com.nineouting.backend.minutes.client.DraftClient;
import com.nineouting.backend.minutes.config.DraftProperties;
import com.nineouting.backend.minutes.domain.Minutes;
import com.nineouting.backend.minutes.domain.MinutesAttendee;
import com.nineouting.backend.minutes.domain.MinutesSection;
import com.nineouting.backend.minutes.domain.MinutesStatus;
import com.nineouting.backend.minutes.domain.TemplateItem;
import com.nineouting.backend.minutes.error.MinutesErrorCode;
import com.nineouting.backend.minutes.repository.MinutesAttendeeRepository;
import com.nineouting.backend.minutes.repository.MinutesSectionRepository;
import com.nineouting.backend.minutes.repository.TemplateItemRepository;
import com.nineouting.backend.minutes.repository.TemplateRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 *   <li>양식의 칸마다 비어 있으면 AI가 채우고(aiFill), 작성자가 이미 쓴 칸은 그대로 둔다. 표 칸은 ai-service가 아직
 *       채우지 못해(7.6 구현 전) 작성자가 채우는 칸으로 보낸다. 칸 설명·표의 열 개수·머리글·바닥글은 양식 그대로 (5.8, 2.1)</li>
 *   <li>표 칸은 주고받을 때 마크다운 표, 저장은 줄 = 행·탭 = 열 (회의록 작성 화면 형식)</li>
 *   <li>양식을 처음 고르면 자동으로 요청하고(REQ-101 "선택한 양식에 맞춰"), 실패하면 작성자가 다시 요청한다</li>
 *   <li>결과는 콜백으로 받아, 그사이 작성자가 쓴 칸은 덮어쓰지 않고 빈 칸만 채운다</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DraftService {

    /** 표 칸 저장 형식의 열 구분 (MinutesExportService와 같음) */
    private static final String CELL_SEPARATOR = "	";

    private final MinutesAccess access;
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
        send(minutes);
    }

    /** 양식을 처음 고르면 저장이 끝난 뒤 자동으로 요청한다. 안 되면 기록만 하고 작성자가 다시 요청한다 */
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
            send(minutes);
        } catch (BusinessException e) {
            log.info("양식을 고른 뒤 초안을 바로 요청하지 못했습니다 (minutesId={}): {}", minutes.getId(), e.getMessage());
        }
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
            Map<String, String> drafted = new HashMap<>();
            body.path("sections").forEach(section -> {
                if (section.path("aiFilled").asBoolean(false)) {
                    String content = section.path("content").asString("");
                    drafted.put(section.path("title").asString(""),
                            "table".equals(section.path("type").asString("")) ? fromMarkdownTable(content) : content);
                }
            });
            List<MinutesSection> sections = sectionRepository.findByMinutesIdOrderByPosition(minutesId);
            boolean changed = false;
            for (MinutesSection section : sections) {
                String content = drafted.get(section.getTitle());
                if (section.getContent().isBlank() && content != null && !content.isBlank()) {
                    section.change(section.getPosition(), section.getTitle(), content);
                    changed = true;
                }
            }
            if (changed) {
                minutesService.recordDraft(minutes, sections);
            }
        }
        minutes.draftSucceeded();
    }

    private void send(Minutes minutes) {
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
        String requestId = UUID.randomUUID().toString();
        DraftClient.Result result = draftClient.requestDraft(jsonMapper.writeValueAsString(body(minutes, source, requestId)));
        switch (result) {
            case ACCEPTED -> minutes.draftRequested(requestId, LocalDateTime.now(clock));
            case REJECTED -> {
                minutes.draftRequested(requestId, LocalDateTime.now(clock));
                minutes.draftFailed("INVALID_INPUT");
            }
            default -> throw new BusinessException(MinutesErrorCode.DRAFT_UNAVAILABLE);
        }
    }

    private ObjectNode body(Minutes minutes, JsonNode source, String requestId) {
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
        template.set("fields", fields(minutes));
        body.set("agendas", source.path("agendas"));
        body.set("summary", source.path("summary"));
        body.put("callbackUrl", properties.callbackBaseUrl() + "/internal/minutes/" + minutes.getId() + "/draft-result");
        return body;
    }

    /** 머리글·바닥글: 껐거나 비었으면 null */
    private static String edge(JsonNode settings) {
        String text = settings.path("text").asString("");
        return settings.path("enabled").asBoolean(false) && !text.isBlank() ? text : null;
    }

    /** 양식 칸 그대로. 비어 있는 글 칸만 AI가 채운다 (표 칸은 ai-service 7.6 구현 전이라 작성자 칸으로) */
    private ArrayNode fields(Minutes minutes) {
        Map<String, TemplateItem> items = new HashMap<>();
        for (TemplateItem item : templateItemRepository.findByTemplateIdOrderByPosition(minutes.getTemplateId())) {
            items.put(item.getTitle(), item);
        }
        ArrayNode fields = jsonMapper.createArrayNode();
        for (MinutesSection section : sectionRepository.findByMinutesIdOrderByPosition(minutes.getId())) {
            TemplateItem item = items.get(section.getTitle());
            JsonNode settings = item == null ? jsonMapper.createObjectNode() : jsonMapper.readTree(item.getSettings());
            boolean table = "TABLE".equals(settings.path("recordType").asString(""));
            boolean aiFill = !table && section.getContent().isBlank();
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
