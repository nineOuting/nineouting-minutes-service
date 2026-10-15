package com.nineouting.backend.minutes.handler;

import com.nineouting.backend.common.event.workspace.FolderDeleted;
import com.nineouting.backend.common.inbox.InboxHandler;
import com.nineouting.backend.common.inbox.InboxMessage;
import com.nineouting.backend.minutes.repository.MinutesAttendeeRepository;
import com.nineouting.backend.minutes.repository.MinutesCommentRepository;
import com.nineouting.backend.minutes.repository.MinutesMarkRepository;
import com.nineouting.backend.minutes.repository.MinutesRepository;
import com.nineouting.backend.minutes.repository.MinutesReviewRepository;
import com.nineouting.backend.minutes.repository.MinutesRevisionRepository;
import com.nineouting.backend.minutes.repository.MinutesSectionRepository;
import com.nineouting.backend.minutes.repository.MinutesTagRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * REQ-114: 폴더(하위 폴더 포함)가 지워지면 그 안의 회의록과 딸린 데이터를 모두 지운다.
 * ai-service 챗봇 복사본 삭제는 2차 (챗봇 동기화).
 */
@Component
@RequiredArgsConstructor
public class FolderDeletedHandler implements InboxHandler {

    private final JsonMapper jsonMapper;
    private final MinutesRepository minutesRepository;
    private final MinutesAttendeeRepository attendeeRepository;
    private final MinutesSectionRepository sectionRepository;
    private final MinutesRevisionRepository revisionRepository;
    private final MinutesReviewRepository reviewRepository;
    private final MinutesCommentRepository commentRepository;
    private final MinutesTagRepository tagRepository;
    private final MinutesMarkRepository markRepository;

    @Override
    public boolean supports(String eventType) {
        return FolderDeleted.TYPE.equals(eventType);
    }

    @Override
    public void handle(InboxMessage message) {
        FolderDeleted event = message.payload(jsonMapper, FolderDeleted.class);
        if (event.folderIds() == null || event.folderIds().isEmpty()) {
            return;
        }
        List<Long> minutesIds = minutesRepository.findIdsByFolderIdIn(event.folderIds());
        if (minutesIds.isEmpty()) {
            return;
        }
        attendeeRepository.deleteByMinutesIdIn(minutesIds);
        sectionRepository.deleteByMinutesIdIn(minutesIds);
        revisionRepository.deleteByMinutesIdIn(minutesIds);
        reviewRepository.deleteByMinutesIdIn(minutesIds);
        commentRepository.deleteByMinutesIdIn(minutesIds);
        tagRepository.deleteByMinutesIdIn(minutesIds);
        markRepository.deleteByMinutesIdIn(minutesIds);
        minutesRepository.deleteAllByIdIn(minutesIds);
    }
}
