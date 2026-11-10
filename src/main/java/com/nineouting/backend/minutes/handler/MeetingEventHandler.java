package com.nineouting.backend.minutes.handler;

import com.nineouting.backend.common.event.meeting.MeetingFinished;
import com.nineouting.backend.common.event.meeting.MeetingMoved;
import com.nineouting.backend.common.inbox.InboxHandler;
import com.nineouting.backend.common.inbox.InboxMessage;
import com.nineouting.backend.minutes.domain.Minutes;
import com.nineouting.backend.minutes.event.MeetingTrashEvents.MeetingPurged;
import com.nineouting.backend.minutes.event.MeetingTrashEvents.MeetingRestored;
import com.nineouting.backend.minutes.event.MeetingTrashEvents.MeetingTrashed;
import com.nineouting.backend.minutes.repository.MinutesRepository;
import com.nineouting.backend.minutes.service.MinutesPurger;
import com.nineouting.backend.minutes.service.MinutesService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * meeting-service 이벤트.
 * <ul>
 *   <li>MeetingFinished (REQ-098): 작성 중인 회의록을 만든다</li>
 *   <li>MeetingMoved (REQ-045, 레거시): 회의록의 폴더를 바꾼다. 아직 회의록이 없으면(회의가 끝나기 전) 할 일이 없다</li>
 *   <li>MeetingTrashed, MeetingRestored, MeetingPurged (REQ-166, REQ-143): 회의록을 숨기거나 다시 보이거나 지운다</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class MeetingEventHandler implements InboxHandler {

    private final JsonMapper jsonMapper;
    private final MinutesService minutesService;
    private final MinutesRepository minutesRepository;
    private final MinutesPurger minutesPurger;
    private final Clock clock;

    private static final Set<String> TYPES = Set.of(MeetingFinished.TYPE, MeetingMoved.TYPE, MeetingTrashed.TYPE,
            MeetingRestored.TYPE, MeetingPurged.TYPE);

    @Override
    public boolean supports(String eventType) {
        return TYPES.contains(eventType);
    }

    @Override
    public void handle(InboxMessage message) {
        if (MeetingFinished.TYPE.equals(message.eventType())) {
            minutesService.createFromMeeting(message.payload(jsonMapper, MeetingFinished.class));
            return;
        }
        switch (message.eventType()) {
            case MeetingTrashed.TYPE -> minutesRepository
                    .findByMeetingId(message.payload(jsonMapper, MeetingTrashed.class).meetingId())
                    .ifPresent(minutes -> minutes.trash(LocalDateTime.now(clock)));
            case MeetingRestored.TYPE -> minutesRepository
                    .findByMeetingId(message.payload(jsonMapper, MeetingRestored.class).meetingId())
                    .ifPresent(Minutes::restore);
            case MeetingPurged.TYPE -> minutesRepository
                    .findByMeetingId(message.payload(jsonMapper, MeetingPurged.class).meetingId())
                    .ifPresent(minutes -> minutesPurger.purge(List.of(minutes.getId())));
            default -> {
                MeetingMoved event = message.payload(jsonMapper, MeetingMoved.class);
                minutesRepository.findByMeetingId(event.meetingId()).ifPresent(minutes -> minutes.moveTo(event.toFolderId()));
            }
        }
    }
}
