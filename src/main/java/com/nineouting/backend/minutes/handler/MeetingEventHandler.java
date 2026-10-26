package com.nineouting.backend.minutes.handler;

import com.nineouting.backend.common.event.meeting.MeetingFinished;
import com.nineouting.backend.common.event.meeting.MeetingMoved;
import com.nineouting.backend.common.inbox.InboxHandler;
import com.nineouting.backend.common.inbox.InboxMessage;
import com.nineouting.backend.minutes.repository.MinutesRepository;
import com.nineouting.backend.minutes.service.MinutesService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * meeting-service 이벤트.
 * <ul>
 *   <li>MeetingFinished (REQ-098): 작성 중인 회의록을 만든다</li>
 *   <li>MeetingMoved (REQ-045, 레거시): 회의록의 폴더를 바꾼다. 아직 회의록이 없으면(회의가 끝나기 전) 할 일이 없다</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class MeetingEventHandler implements InboxHandler {

    private final JsonMapper jsonMapper;
    private final MinutesService minutesService;
    private final MinutesRepository minutesRepository;

    @Override
    public boolean supports(String eventType) {
        return MeetingFinished.TYPE.equals(eventType) || MeetingMoved.TYPE.equals(eventType);
    }

    @Override
    public void handle(InboxMessage message) {
        if (MeetingFinished.TYPE.equals(message.eventType())) {
            minutesService.createFromMeeting(message.payload(jsonMapper, MeetingFinished.class));
            return;
        }
        MeetingMoved event = message.payload(jsonMapper, MeetingMoved.class);
        minutesRepository.findByMeetingId(event.meetingId()).ifPresent(minutes -> minutes.moveTo(event.toFolderId()));
    }
}
