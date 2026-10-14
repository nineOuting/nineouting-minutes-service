package com.nineouting.backend.minutes.service;

import com.nineouting.backend.common.client.AccessibleFolder;
import com.nineouting.backend.common.client.WorkspaceClient;
import com.nineouting.backend.minutes.domain.Minutes;
import com.nineouting.backend.minutes.domain.MinutesMark;
import com.nineouting.backend.minutes.domain.MinutesReview;
import com.nineouting.backend.minutes.domain.MinutesStatus;
import com.nineouting.backend.minutes.domain.MinutesTag;
import com.nineouting.backend.minutes.domain.ReviewStatus;
import com.nineouting.backend.minutes.dto.ArchiveItemResponse;
import com.nineouting.backend.minutes.dto.ArchivePageResponse;
import com.nineouting.backend.minutes.dto.MarkFilter;
import com.nineouting.backend.minutes.dto.MarksRequest;
import com.nineouting.backend.minutes.dto.RecentMinutesResponse;
import com.nineouting.backend.minutes.dto.ReviewRequestItemResponse;
import com.nineouting.backend.minutes.dto.ReviewRequestsResponse;
import com.nineouting.backend.minutes.repository.MinutesMarkRepository;
import com.nineouting.backend.minutes.repository.MinutesRepository;
import com.nineouting.backend.minutes.repository.MinutesReviewRepository;
import com.nineouting.backend.minutes.repository.MinutesSearchRepository;
import com.nineouting.backend.minutes.repository.MinutesTagRepository;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 아카이브 목록·검색·필터, 즐겨찾기·중요 표시 (REQ-051~054), 홈의 검토 대기·최근 확정 (REQ-021~023, 026~027).
 */
@Service
@RequiredArgsConstructor
public class ArchiveService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_RECENT_SIZE = 50;
    private static final List<ReviewStatus> WAITING_FOR_ME = List.of(ReviewStatus.PENDING, ReviewStatus.CHANGES_REQUESTED);

    private final MinutesRepository minutesRepository;
    private final MinutesSearchRepository searchRepository;
    private final MinutesTagRepository tagRepository;
    private final MinutesMarkRepository markRepository;
    private final MinutesReviewRepository reviewRepository;
    private final MinutesAccess access;
    private final WorkspaceClient workspaceClient;

    /** REQ-051~054. 폴더의 확정된 회의록, 회의 날짜 최신순. 필터는 함께 쓸 수 있다 */
    @Transactional(readOnly = true)
    public ArchivePageResponse folderMinutes(Long folderId, Long userId, String query, LocalDate from, LocalDate to,
                                             String tag, Long attendeeId, MarkFilter mark, int page, int size) {
        access.requireFolderAccess(folderId, userId);
        int pageNumber = Math.max(page, 0);
        int pageSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        MinutesSearchRepository.Page result = searchRepository.search(new MinutesSearchRepository.Filter(folderId, query,
                from, to, tag, attendeeId, mark == null ? null : mark.name(), userId), pageNumber, pageSize);

        Map<Long, Minutes> found = minutesRepository.findAllById(result.ids()).stream()
                .collect(Collectors.toMap(Minutes::getId, Function.identity()));
        Map<Long, List<String>> tags = tagRepository.findByMinutesIdIn(result.ids()).stream()
                .collect(Collectors.groupingBy(MinutesTag::getMinutesId,
                        Collectors.mapping(MinutesTag::getTag, Collectors.toList())));
        Map<Long, MinutesMark> marks = markRepository.findByUserIdAndMinutesIdIn(userId, result.ids()).stream()
                .collect(Collectors.toMap(MinutesMark::getMinutesId, Function.identity()));
        List<ArchiveItemResponse> items = result.ids().stream()
                .map(found::get)
                .map(minutes -> {
                    MinutesMark minutesMark = marks.get(minutes.getId());
                    return new ArchiveItemResponse(minutes.getId(), minutes.getMeetingId(), minutes.getTitle(),
                            minutes.getMeetingStartAt(), minutes.getConfirmedAt(), tags.getOrDefault(minutes.getId(), List.of()),
                            minutesMark != null && minutesMark.isFavorite(), minutesMark != null && minutesMark.isImportant());
                })
                .toList();
        boolean hasNext = (long) (pageNumber + 1) * pageSize < result.totalCount();
        return new ArchivePageResponse(items, pageNumber, pageSize, result.totalCount(), hasNext);
    }

    /** REQ-054. 회의록을 볼 수 있는 사람, 내 표시만 바꾼다 */
    @Transactional
    public void mark(Long minutesId, Long userId, MarksRequest request) {
        Minutes minutes = access.require(minutesId);
        access.requireViewable(minutes, userId);
        MinutesMark mark = markRepository.findByMinutesIdAndUserId(minutesId, userId)
                .orElseGet(() -> markRepository.save(new MinutesMark(minutesId, userId)));
        mark.change(request.favorite(), request.important());
    }

    /** REQ-021~023. 내가 검토할(미확인·수정 요청) 검토 중 회의록, 회의 날짜가 오래된 순 */
    @Transactional(readOnly = true)
    public ReviewRequestsResponse reviewRequests(Long userId) {
        Map<Long, ReviewStatus> myStatus = reviewRepository.findByUserIdAndStatusIn(userId, WAITING_FOR_ME).stream()
                .collect(Collectors.toMap(MinutesReview::getMinutesId, MinutesReview::getStatus));
        List<ReviewRequestItemResponse> items = minutesRepository.findAllById(myStatus.keySet()).stream()
                .filter(minutes -> minutes.is(MinutesStatus.REVIEWING))
                .sorted(Comparator.comparing(Minutes::getMeetingStartAt).thenComparing(Minutes::getId))
                .map(minutes -> new ReviewRequestItemResponse(minutes.getId(), minutes.getMeetingId(),
                        minutes.getWorkspaceId(), minutes.getFolderId(), minutes.getTitle(), minutes.getMeetingStartAt(),
                        myStatus.get(minutes.getId())))
                .toList();
        return new ReviewRequestsResponse(items.size(), items);
    }

    /** REQ-026, 027. 내가 들어갈 수 있는 모든 폴더의 확정 회의록, 최근 확정 순 */
    @Transactional(readOnly = true)
    public List<RecentMinutesResponse> recent(Long userId, int size) {
        Map<Long, AccessibleFolder> folders = workspaceClient.accessibleFolders(userId).stream()
                .collect(Collectors.toMap(AccessibleFolder::folderId, Function.identity(), (a, b) -> a));
        if (folders.isEmpty()) {
            return List.of();
        }
        return minutesRepository.findByFolderIdInAndStatusOrderByConfirmedAtDescIdDesc(folders.keySet(),
                        MinutesStatus.CONFIRMED, PageRequest.of(0, Math.clamp(size, 1, MAX_RECENT_SIZE))).stream()
                .map(minutes -> {
                    AccessibleFolder folder = folders.get(minutes.getFolderId());
                    return new RecentMinutesResponse(minutes.getId(), minutes.getMeetingId(), minutes.getTitle(),
                            minutes.getMeetingStartAt(), minutes.getConfirmedAt(), minutes.getWorkspaceId(),
                            folder.workspaceName(), minutes.getFolderId(), folder.name());
                })
                .toList();
    }
}
