package com.nineouting.backend.minutes.service;

import com.nineouting.backend.common.client.UserClient;
import com.nineouting.backend.common.client.UserSummary;
import com.nineouting.backend.common.event.minutes.MinutesConfirmed;
import com.nineouting.backend.common.event.minutes.MinutesReviewRequested;
import com.nineouting.backend.common.outbox.OutboxEventWriter;
import com.nineouting.backend.common.web.error.BusinessException;
import com.nineouting.backend.common.web.error.CommonErrorCode;
import com.nineouting.backend.minutes.domain.Minutes;
import com.nineouting.backend.minutes.domain.MinutesAttendee;
import com.nineouting.backend.minutes.domain.MinutesComment;
import com.nineouting.backend.minutes.domain.MinutesReview;
import com.nineouting.backend.minutes.domain.MinutesStatus;
import com.nineouting.backend.minutes.domain.ReviewStatus;
import com.nineouting.backend.minutes.dto.CommentRequest;
import com.nineouting.backend.minutes.dto.CommentResponse;
import com.nineouting.backend.minutes.dto.PersonResponse;
import com.nineouting.backend.minutes.dto.ReviewResponse;
import com.nineouting.backend.minutes.error.MinutesErrorCode;
import com.nineouting.backend.minutes.repository.MinutesAttendeeRepository;
import com.nineouting.backend.minutes.repository.MinutesCommentRepository;
import com.nineouting.backend.minutes.repository.MinutesReviewRepository;
import com.nineouting.backend.minutes.repository.MinutesSectionRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 참석자 검토 요청·동의·코멘트·검토 현황, 확정 (REQ-095~099).
 */
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final MinutesAttendeeRepository attendeeRepository;
    private final MinutesReviewRepository reviewRepository;
    private final MinutesCommentRepository commentRepository;
    private final MinutesSectionRepository sectionRepository;
    private final MinutesAccess access;
    private final UserClient userClient;
    private final OutboxEventWriter outboxEventWriter;
    private final Clock clock;

    /**
     * REQ-095, 097. 작성자, 확정 전. 참석자(작성자 제외)의 검토를 모두 미확인으로 되돌린다 (8장 결정 2).
     * 검토할 참석자가 없으면(작성자 혼자) 바로 확정한다.
     */
    @Transactional
    public void requestReview(Long minutesId, Long userId) {
        Minutes minutes = access.requireForUpdate(minutesId);
        access.requireWriter(minutes, userId);
        if (minutes.is(MinutesStatus.CONFIRMED)) {
            throw new BusinessException(MinutesErrorCode.MINUTES_ALREADY_CONFIRMED);
        }
        List<Long> reviewers = reviewerIds(minutes);
        Map<Long, MinutesReview> existing = reviewRepository.findByMinutesIdOrderById(minutesId).stream()
                .collect(Collectors.toMap(MinutesReview::getUserId, Function.identity()));
        for (Long reviewer : reviewers) {
            MinutesReview review = existing.get(reviewer);
            if (review == null) {
                reviewRepository.save(new MinutesReview(minutesId, reviewer));
            } else {
                review.reset();
            }
        }
        minutes.requestReview();
        if (reviewers.isEmpty()) {
            confirm(minutes, userId);
            return;
        }
        outboxEventWriter.append(MinutesReviewRequested.TOPIC, minutes.getMeetingId().toString(), MinutesReviewRequested.TYPE,
                new MinutesReviewRequested(minutesId, minutes.getMeetingId(), minutes.getWorkspaceId(), minutes.getTitle(),
                        minutes.getWriterUserId(), reviewers));
    }

    /** REQ-098. 참석자별 동의·미확인·수정 요청 */
    @Transactional(readOnly = true)
    public List<ReviewResponse> reviews(Long minutesId, Long userId) {
        Minutes minutes = access.require(minutesId);
        access.requireViewable(minutes, userId);
        List<MinutesReview> reviews = reviewRepository.findByMinutesIdOrderById(minutesId);
        Map<Long, UserSummary> users = userClient.findUsers(reviews.stream().map(MinutesReview::getUserId).toList());
        return reviews.stream()
                .map(review -> new ReviewResponse(PersonResponse.of(review.getUserId(), users.get(review.getUserId())),
                        review.getStatus(), review.getDecidedAt()))
                .toList();
    }

    /** REQ-096, 099. 검토 대상 참석자, 검토 중. 모두 동의하면 자동 확정 */
    @Transactional
    public void approve(Long minutesId, Long userId) {
        Minutes minutes = access.requireForUpdate(minutesId);
        MinutesReview review = requireReviewer(minutes, userId);
        review.decide(ReviewStatus.APPROVED, LocalDateTime.now(clock));
        boolean allApproved = reviewRepository.findByMinutesIdOrderById(minutesId).stream()
                .allMatch(MinutesReview::isApproved);
        if (allApproved) {
            confirm(minutes, null);
        }
    }

    /** REQ-097. 검토 대상 참석자, 검토 중. 고른 문장에 의견을 남기면 내 검토는 수정 요청이 된다 */
    @Transactional
    public CommentResponse comment(Long minutesId, Long userId, CommentRequest request) {
        Minutes minutes = access.requireForUpdate(minutesId);
        MinutesReview review = requireReviewer(minutes, userId);
        if (!sectionRepository.existsByIdAndMinutesId(request.sectionId(), minutesId)) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        MinutesComment comment = commentRepository.save(new MinutesComment(minutesId, request.sectionId(),
                request.quote(), request.body(), userId, now));
        review.decide(ReviewStatus.CHANGES_REQUESTED, now);
        return toResponse(comment, userClient.findUsers(List.of(userId)));
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> comments(Long minutesId, Long userId) {
        Minutes minutes = access.require(minutesId);
        access.requireViewable(minutes, userId);
        List<MinutesComment> comments = commentRepository.findByMinutesIdOrderById(minutesId);
        Map<Long, UserSummary> users = userClient.findUsers(comments.stream().map(MinutesComment::getAuthorUserId).toList());
        return comments.stream().map(comment -> toResponse(comment, users)).toList();
    }

    /** REQ-097. 작성자가 코멘트를 반영했다고 표시 */
    @Transactional
    public void resolveComment(Long minutesId, Long commentId, Long userId) {
        Minutes minutes = access.require(minutesId);
        access.requireWriter(minutes, userId);
        MinutesComment comment = commentRepository.findById(commentId)
                .filter(found -> found.getMinutesId().equals(minutesId))
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        comment.resolve(LocalDateTime.now(clock));
    }

    /** REQ-096. 작성자, 검토 중. 동의하지 않은 참석자가 있어도 확정할 수 있다 */
    @Transactional
    public void confirmByWriter(Long minutesId, Long userId) {
        Minutes minutes = access.requireForUpdate(minutesId);
        access.requireWriter(minutes, userId);
        if (minutes.is(MinutesStatus.CONFIRMED)) {
            throw new BusinessException(MinutesErrorCode.MINUTES_ALREADY_CONFIRMED);
        }
        if (!minutes.is(MinutesStatus.REVIEWING)) {
            throw new BusinessException(MinutesErrorCode.MINUTES_NOT_REVIEWING);
        }
        confirm(minutes, userId);
    }

    /** REQ-099, 100. 확정하고 참석자 알림·메일·회의 목록 표시용 이벤트를 남긴다 */
    private void confirm(Minutes minutes, Long confirmedByUserId) {
        LocalDateTime now = LocalDateTime.now(clock);
        minutes.confirm(now, confirmedByUserId);
        List<Long> attendees = attendeeRepository.findByMinutesIdOrderById(minutes.getId()).stream()
                .map(MinutesAttendee::getUserId)
                .toList();
        outboxEventWriter.append(MinutesConfirmed.TOPIC, minutes.getMeetingId().toString(), MinutesConfirmed.TYPE,
                new MinutesConfirmed(minutes.getId(), minutes.getMeetingId(), minutes.getWorkspaceId(), minutes.getFolderId(),
                        minutes.getTitle(), attendees, now));
    }

    private MinutesReview requireReviewer(Minutes minutes, Long userId) {
        access.requireViewable(minutes, userId);
        if (!minutes.is(MinutesStatus.REVIEWING)) {
            throw new BusinessException(MinutesErrorCode.MINUTES_NOT_REVIEWING);
        }
        return reviewRepository.findByMinutesIdAndUserId(minutes.getId(), userId)
                .orElseThrow(() -> new BusinessException(MinutesErrorCode.NOT_MINUTES_REVIEWER));
    }

    private List<Long> reviewerIds(Minutes minutes) {
        return attendeeRepository.findByMinutesIdOrderById(minutes.getId()).stream()
                .map(MinutesAttendee::getUserId)
                .filter(id -> !minutes.isWriter(id))
                .toList();
    }

    private static CommentResponse toResponse(MinutesComment comment, Map<Long, UserSummary> users) {
        return new CommentResponse(comment.getId(), comment.getSectionId(), comment.getQuote(), comment.getBody(),
                PersonResponse.of(comment.getAuthorUserId(), users.get(comment.getAuthorUserId())),
                comment.getCreatedAt(), comment.getResolvedAt());
    }
}
