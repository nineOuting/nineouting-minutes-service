package com.nineouting.backend.minutes.service;

import com.nineouting.backend.minutes.repository.MinutesAttendeeRepository;
import com.nineouting.backend.minutes.repository.MinutesCommentRepository;
import com.nineouting.backend.minutes.repository.MinutesMarkRepository;
import com.nineouting.backend.minutes.repository.MinutesRepository;
import com.nineouting.backend.minutes.repository.MinutesReviewRepository;
import com.nineouting.backend.minutes.repository.MinutesRevisionRepository;
import com.nineouting.backend.minutes.repository.MinutesSectionRepository;
import com.nineouting.backend.minutes.repository.MinutesViewRepository;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 회의록과 딸린 데이터를 지운다. 폴더 영구 삭제(REQ-058)와 회의 영구 삭제(REQ-143)가 함께 쓴다 */
@Component
@RequiredArgsConstructor
public class MinutesPurger {

    private final MinutesRepository minutesRepository;
    private final MinutesAttendeeRepository attendeeRepository;
    private final MinutesSectionRepository sectionRepository;
    private final MinutesRevisionRepository revisionRepository;
    private final MinutesReviewRepository reviewRepository;
    private final MinutesCommentRepository commentRepository;
    private final MinutesMarkRepository markRepository;
    private final MinutesViewRepository viewRepository;

    public void purge(Collection<Long> minutesIds) {
        if (minutesIds.isEmpty()) {
            return;
        }
        attendeeRepository.deleteByMinutesIdIn(minutesIds);
        sectionRepository.deleteByMinutesIdIn(minutesIds);
        revisionRepository.deleteByMinutesIdIn(minutesIds);
        reviewRepository.deleteByMinutesIdIn(minutesIds);
        commentRepository.deleteByMinutesIdIn(minutesIds);
        markRepository.deleteByMinutesIdIn(minutesIds);
        viewRepository.deleteByMinutesIdIn(minutesIds);
        minutesRepository.deleteAllByIdIn(minutesIds);
    }
}
