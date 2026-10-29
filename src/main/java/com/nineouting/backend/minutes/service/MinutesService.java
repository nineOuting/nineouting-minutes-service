package com.nineouting.backend.minutes.service;

import com.nineouting.backend.common.client.UserClient;
import com.nineouting.backend.common.client.UserSummary;
import com.nineouting.backend.common.event.meeting.MeetingFinished;
import com.nineouting.backend.common.event.minutes.MinutesEdited;
import com.nineouting.backend.common.outbox.OutboxEventWriter;
import com.nineouting.backend.common.web.error.BusinessException;
import com.nineouting.backend.common.web.error.CommonErrorCode;
import com.nineouting.backend.minutes.domain.Minutes;
import com.nineouting.backend.minutes.domain.MinutesAttendee;
import com.nineouting.backend.minutes.domain.MinutesMark;
import com.nineouting.backend.minutes.domain.MinutesRevision;
import com.nineouting.backend.minutes.domain.MinutesSection;
import com.nineouting.backend.minutes.domain.MinutesStatus;
import com.nineouting.backend.minutes.domain.Template;
import com.nineouting.backend.minutes.domain.TemplateItem;
import com.nineouting.backend.minutes.dto.MinutesDetailResponse;
import com.nineouting.backend.minutes.dto.PersonResponse;
import com.nineouting.backend.minutes.dto.RevisionResponse;
import com.nineouting.backend.minutes.dto.SectionRequest;
import com.nineouting.backend.minutes.dto.SectionResponse;
import com.nineouting.backend.minutes.error.MinutesErrorCode;
import com.nineouting.backend.minutes.repository.MinutesAttendeeRepository;
import com.nineouting.backend.minutes.repository.MinutesMarkRepository;
import com.nineouting.backend.minutes.repository.MinutesRepository;
import com.nineouting.backend.minutes.repository.MinutesRevisionRepository;
import com.nineouting.backend.minutes.repository.MinutesSectionRepository;
import com.nineouting.backend.minutes.repository.TemplateItemRepository;
import com.nineouting.backend.minutes.repository.TemplateRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 회의록 만들기·조회, 양식 선택, 내용 저장·수정 기록.
 */
@Service
@RequiredArgsConstructor
public class MinutesService {

    private final MinutesRepository minutesRepository;
    private final MinutesAttendeeRepository attendeeRepository;
    private final MinutesSectionRepository sectionRepository;
    private final MinutesRevisionRepository revisionRepository;
    private final MinutesMarkRepository markRepository;
    private final TemplateRepository templateRepository;
    private final TemplateItemRepository templateItemRepository;
    private final MinutesAccess access;
    private final OutboxEventWriter outboxEventWriter;
    private final UserClient userClient;
    private final JsonMapper jsonMapper;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** 회의 마무리(REQ-098)를 받아 작성 중인 회의록을 만든다. 같은 회의는 한 번만 만든다. */
    @Transactional
    public void createFromMeeting(MeetingFinished event) {
        if (minutesRepository.existsByMeetingId(event.meetingId())) {
            return;
        }
        Minutes minutes = minutesRepository.save(new Minutes(event.meetingId(), event.workspaceId(), event.folderId(),
                event.title(), event.startAt(), event.location(), event.writerUserId()));
        Set<Long> attendees = new LinkedHashSet<>(event.attendeeUserIds());
        attendees.add(event.writerUserId());
        attendees.forEach(userId -> attendeeRepository.save(new MinutesAttendee(minutes.getId(), userId)));
    }

    /** 회의에서 회의록으로 이동 (REQ-020). 회의 마무리 이벤트를 아직 처리하지 않았으면 "준비 중" */
    @Transactional(readOnly = true)
    public MinutesDetailResponse getByMeeting(Long meetingId, Long userId) {
        Minutes minutes = minutesRepository.findByMeetingId(meetingId)
                .orElseThrow(() -> new BusinessException(MinutesErrorCode.MINUTES_NOT_READY));
        access.requireViewable(minutes, userId);
        return detail(minutes, userId);
    }

    @Transactional(readOnly = true)
    public MinutesDetailResponse get(Long minutesId, Long userId) {
        Minutes minutes = access.require(minutesId);
        access.requireViewable(minutes, userId);
        return detail(minutes, userId);
    }

    /**
     * REQ-140 양식 변경 (REQ-100에서 이어짐). 작성자, 작성 중에만. 칸을 양식 항목대로 다시 만들고,
     * 제목이 같은 칸은 쓰던 내용을 옮긴다 (나머지는 비운다).
     */
    @Transactional
    public MinutesDetailResponse selectTemplate(Long minutesId, Long userId, Long templateId) {
        Minutes minutes = access.requireForUpdate(minutesId);
        access.requireWriter(minutes, userId);
        if (!minutes.is(MinutesStatus.WRITING)) {
            throw new BusinessException(MinutesErrorCode.MINUTES_NOT_WRITING);
        }
        Template template = templateRepository.findById(templateId)
                .filter(found -> found.isUsableBy(userId))
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));

        List<MinutesSection> previous = sectionRepository.findByMinutesIdOrderByPosition(minutesId);
        Map<String, String> contents = new HashMap<>();
        previous.forEach(section -> contents.putIfAbsent(section.getTitle(), section.getContent()));
        sectionRepository.deleteAll(previous);
        List<MinutesSection> sections = new ArrayList<>();
        for (TemplateItem item : templateItemRepository.findByTemplateIdOrderByPosition(template.getId())) {
            sections.add(sectionRepository.save(new MinutesSection(minutesId, item.getPosition(), item.getTitle(),
                    contents.getOrDefault(item.getTitle(), ""))));
        }
        boolean firstTemplate = minutes.getTemplateId() == null;
        recordRevision(minutes, template.getId(), sections, userId);
        if (firstTemplate) {
            // 양식을 처음 고르면 저장이 끝난 뒤 AI 초안을 요청한다 (REQ-101, DraftService)
            events.publishEvent(new TemplateSelected(minutesId));
        }
        return detail(minutes, userId);
    }

    /**
     * REQ-104 (레거시), 111. 작성자만, 작성 중·검토 중·확정 모두 가능 (확정 후에는 다시 검토하지 않는다).
     * 요청의 version이 지금과 다르면 다른 곳에서 먼저 저장한 것이므로 거절한다.
     */
    @Transactional
    public MinutesDetailResponse saveSections(Long minutesId, Long userId, int version, List<SectionRequest> requests) {
        Minutes minutes = access.requireForUpdate(minutesId);
        access.requireWriter(minutes, userId);
        if (minutes.getContentVersion() != version) {
            throw new BusinessException(MinutesErrorCode.MINUTES_VERSION_CONFLICT);
        }

        Map<Long, MinutesSection> existing = sectionRepository.findByMinutesIdOrderByPosition(minutesId).stream()
                .collect(Collectors.toMap(MinutesSection::getId, Function.identity()));
        List<MinutesSection> sections = new ArrayList<>();
        for (int position = 0; position < requests.size(); position++) {
            SectionRequest request = requests.get(position);
            MinutesSection section = request.sectionId() == null ? null : existing.remove(request.sectionId());
            if (section == null) {
                section = sectionRepository.save(new MinutesSection(minutesId, position, request.title(), request.content()));
            } else {
                section.change(position, request.title(), request.content());
            }
            sections.add(section);
        }
        sectionRepository.deleteAll(existing.values());
        recordRevision(minutes, null, sections, userId);
        if (minutes.is(MinutesStatus.CONFIRMED)) {
            notifyEdited(minutes, userId);
        }
        return detail(minutes, userId);
    }

    /** REQ-104 (레거시). 수정한 사람·시각 (최근 순) */
    @Transactional(readOnly = true)
    public List<RevisionResponse> revisions(Long minutesId, Long userId) {
        Minutes minutes = access.require(minutesId);
        access.requireViewable(minutes, userId);
        List<MinutesRevision> revisions = revisionRepository.findByMinutesIdOrderByVersionDesc(minutesId);
        Map<Long, UserSummary> users = userClient.findUsers(revisions.stream().map(MinutesRevision::getEditorUserId).toList(),
                minutes.getWorkspaceId());
        return revisions.stream()
                .map(revision -> new RevisionResponse(revision.getVersion(),
                        PersonResponse.of(revision.getEditorUserId(), users.get(revision.getEditorUserId())),
                        revision.getEditedAt(), revision.isAfterConfirm()))
                .toList();
    }

    MinutesDetailResponse detail(Minutes minutes, Long viewerId) {
        List<Long> attendeeIds = attendeeRepository.findByMinutesIdOrderById(minutes.getId()).stream()
                .map(MinutesAttendee::getUserId)
                .toList();
        Map<Long, UserSummary> users = userClient.findUsers(attendeeIds, minutes.getWorkspaceId());
        Optional<MinutesMark> mark = markRepository.findByMinutesIdAndUserId(minutes.getId(), viewerId);
        return new MinutesDetailResponse(
                minutes.getId(), minutes.getMeetingId(), minutes.getWorkspaceId(), minutes.getFolderId(),
                minutes.getTitle(), minutes.getMeetingStartAt(), minutes.getLocation(), minutes.getStatus(),
                PersonResponse.of(minutes.getWriterUserId(), users.get(minutes.getWriterUserId())),
                attendeeIds.stream().map(id -> PersonResponse.of(id, users.get(id))).toList(),
                minutes.getTemplateId(), minutes.getContentVersion(), minutes.getReviewRound(),
                sectionRepository.findByMinutesIdOrderByPosition(minutes.getId()).stream().map(SectionResponse::from).toList(),
                mark.map(MinutesMark::isFavorite).orElse(false),
                mark.map(MinutesMark::isImportant).orElse(false),
                minutes.getConfirmedAt(), minutes.getConfirmedByUserId(), minutes.getDraftStatus(), minutes.getDraftError());
    }

    /** AI 초안으로 빈 칸을 채웠다 (DraftService). 작성자가 고친 것처럼 저장 기록을 남긴다 */
    void recordDraft(Minutes minutes, List<MinutesSection> sections) {
        recordRevision(minutes, null, sections, minutes.getWriterUserId());
    }

    /** 회의록의 양식을 처음 골랐다 */
    public record TemplateSelected(Long minutesId) {
    }

    /** REQ-111. 확정된 회의록을 고치면 참석자에게 알린다 (notification이 고친 사람은 뺀다) */
    private void notifyEdited(Minutes minutes, Long editorUserId) {
        List<Long> attendees = attendeeRepository.findByMinutesIdOrderById(minutes.getId()).stream()
                .map(MinutesAttendee::getUserId)
                .toList();
        outboxEventWriter.append(MinutesEdited.TOPIC, minutes.getMeetingId().toString(), MinutesEdited.TYPE,
                new MinutesEdited(minutes.getId(), minutes.getMeetingId(), minutes.getWorkspaceId(), minutes.getFolderId(),
                        minutes.getTitle(), editorUserId, attendees, LocalDateTime.now(clock)));
    }

    /** 저장할 때마다 전체 내용과 수정한 사람·시각을 남기고, 검색용 글을 다시 만든다 (8장 결정 9) */
    private void recordRevision(Minutes minutes, Long templateId, List<MinutesSection> sections, Long editorUserId) {
        StringBuilder searchText = new StringBuilder(minutes.getTitle());
        List<Map<String, String>> snapshot = new ArrayList<>();
        for (MinutesSection section : sections) {
            searchText.append('\n').append(section.getTitle()).append('\n').append(section.getContent());
            snapshot.add(Map.of("title", section.getTitle(), "content", section.getContent()));
        }
        int version = minutes.contentChanged(templateId, searchText.toString());
        revisionRepository.save(new MinutesRevision(minutes.getId(), version, editorUserId, LocalDateTime.now(clock),
                jsonMapper.writeValueAsString(snapshot), minutes.is(MinutesStatus.CONFIRMED)));
    }
}
