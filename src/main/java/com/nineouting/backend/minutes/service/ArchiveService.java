package com.nineouting.backend.minutes.service;

import com.nineouting.backend.common.client.AccessibleFolder;
import com.nineouting.backend.common.client.UserClient;
import com.nineouting.backend.common.client.UserSummary;
import com.nineouting.backend.common.client.WorkspaceClient;
import com.nineouting.backend.minutes.domain.Minutes;
import com.nineouting.backend.minutes.domain.MinutesAttendee;
import com.nineouting.backend.minutes.domain.MinutesMark;
import com.nineouting.backend.minutes.domain.MinutesReview;
import com.nineouting.backend.minutes.domain.MinutesStatus;
import com.nineouting.backend.minutes.domain.MinutesView;
import com.nineouting.backend.minutes.domain.ReviewStatus;
import com.nineouting.backend.minutes.dto.ArchiveItemResponse;
import com.nineouting.backend.minutes.dto.ArchivePageResponse;
import com.nineouting.backend.minutes.dto.ArchiveSort;
import com.nineouting.backend.minutes.dto.MarkFilter;
import com.nineouting.backend.minutes.dto.MarksRequest;
import com.nineouting.backend.minutes.dto.PersonResponse;
import com.nineouting.backend.minutes.dto.RecentMinutesResponse;
import com.nineouting.backend.minutes.dto.ReviewRequestItemResponse;
import com.nineouting.backend.minutes.dto.ReviewRequestsResponse;
import com.nineouting.backend.minutes.dto.ViewedMinutesResponse;
import com.nineouting.backend.minutes.dto.WorkspaceArchiveItemResponse;
import com.nineouting.backend.minutes.dto.WorkspaceArchivePageResponse;
import com.nineouting.backend.minutes.dto.WritingMinutesResponse;
import com.nineouting.backend.minutes.repository.MinutesAttendeeRepository;
import com.nineouting.backend.minutes.repository.MinutesMarkRepository;
import com.nineouting.backend.minutes.repository.MinutesRepository;
import com.nineouting.backend.minutes.repository.MinutesReviewRepository;
import com.nineouting.backend.minutes.repository.MinutesSearchRepository;
import com.nineouting.backend.minutes.repository.MinutesViewRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
 * 아카이브 목록·검색·필터, 즐겨찾기·중요 표시 (REQ-053~056), 최근 본 회의록 (REQ-146),
 * 홈의 검토 대기·최근 확정·작성 중 (REQ-021~023, 026~027, 142).
 */
@Service
@RequiredArgsConstructor
public class ArchiveService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_RECENT_SIZE = 50;
    private static final int MAX_VIEWED_READ = 100;
    private static final List<ReviewStatus> WAITING_FOR_ME = List.of(ReviewStatus.PENDING, ReviewStatus.CHANGES_REQUESTED);

    private final MinutesRepository minutesRepository;
    private final MinutesSearchRepository searchRepository;
    private final MinutesMarkRepository markRepository;
    private final MinutesReviewRepository reviewRepository;
    private final MinutesAccess access;
    private final MinutesAttendeeRepository attendeeRepository;
    private final MinutesViewRepository viewRepository;
    private final WorkspaceClient workspaceClient;
    private final UserClient userClient;
    private final Clock clock;

    /** REQ-053~056. 폴더의 회의록 전체(작성 중·검토 중·확정), 회의 날짜 최신순. 필터(기간·참석자·장소)는 함께 쓸 수 있다 */
    @Transactional(readOnly = true)
    public ArchivePageResponse folderMinutes(Long folderId, Long userId, String query, LocalDate from, LocalDate to,
                                             String location, Long attendeeId, MarkFilter mark, int page, int size) {
        access.requireFolderAccess(folderId, userId);
        int pageNumber = Math.max(page, 0);
        int pageSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        MinutesSearchRepository.Page result = searchRepository.search(new MinutesSearchRepository.Filter(List.of(folderId),
                query, from, to, location, List.of(), attendeeId == null ? List.of() : List.of(attendeeId),
                mark == null ? null : mark.name(), userId, false), pageNumber, pageSize);

        Map<Long, Minutes> found = minutesRepository.findAllById(result.ids()).stream()
                .collect(Collectors.toMap(Minutes::getId, Function.identity()));
        Map<Long, MinutesMark> marks = markRepository.findByUserIdAndMinutesIdIn(userId, result.ids()).stream()
                .collect(Collectors.toMap(MinutesMark::getMinutesId, Function.identity()));
        List<ArchiveItemResponse> items = result.ids().stream()
                .map(found::get)
                .map(minutes -> {
                    MinutesMark minutesMark = marks.get(minutes.getId());
                    return new ArchiveItemResponse(minutes.getId(), minutes.getMeetingId(), minutes.getTitle(),
                            minutes.getMeetingStartAt(), minutes.getLocation(), minutes.getStatus(), minutes.getConfirmedAt(),
                            minutesMark != null && minutesMark.isFavorite(), minutesMark != null && minutesMark.isImportant());
                })
                .toList();
        boolean hasNext = (long) (pageNumber + 1) * pageSize < result.totalCount();
        return new ArchivePageResponse(items, pageNumber, pageSize, result.totalCount(), hasNext);
    }

    /**
     * REQ-053~055. 워크스페이스에서 내가 들어갈 수 있는 모든 폴더의 회의록. 장소는 고른 것 중 하나와 같고,
     * 참석자는 고른 사람 중 한 명이라도 참석했으면 보인다. 들어갈 수 있는 폴더가 없으면 빈 목록
     */
    @Transactional(readOnly = true)
    public WorkspaceArchivePageResponse workspaceMinutes(Long workspaceId, Long userId, String query, LocalDate from,
                                                         LocalDate to, List<String> locations, List<Long> attendeeIds,
                                                         ArchiveSort sort, int page, int size) {
        int pageNumber = Math.max(page, 0);
        int pageSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        Map<Long, AccessibleFolder> folders = foldersOf(workspaceId, userId);
        if (folders.isEmpty()) {
            return new WorkspaceArchivePageResponse(List.of(), pageNumber, pageSize, 0, false);
        }
        MinutesSearchRepository.Page result = searchRepository.search(new MinutesSearchRepository.Filter(folders.keySet(),
                query, from, to, null, nonNull(locations), nonNull(attendeeIds), null, userId, sort == ArchiveSort.OLDEST),
                pageNumber, pageSize);

        Map<Long, Minutes> found = minutesRepository.findAllById(result.ids()).stream()
                .collect(Collectors.toMap(Minutes::getId, Function.identity()));
        Map<Long, MinutesMark> marks = markRepository.findByUserIdAndMinutesIdIn(userId, result.ids()).stream()
                .collect(Collectors.toMap(MinutesMark::getMinutesId, Function.identity()));
        Map<Long, List<Long>> attendees = attendeeRepository.findByMinutesIdInOrderById(result.ids()).stream()
                .collect(Collectors.groupingBy(MinutesAttendee::getMinutesId,
                        Collectors.mapping(MinutesAttendee::getUserId, Collectors.toList())));
        Map<Long, UserSummary> users = userClient.findUsers(
                attendees.values().stream().flatMap(List::stream).distinct().toList());
        List<WorkspaceArchiveItemResponse> items = result.ids().stream()
                .map(found::get)
                .map(minutes -> {
                    MinutesMark minutesMark = marks.get(minutes.getId());
                    List<PersonResponse> people = attendees.getOrDefault(minutes.getId(), List.of()).stream()
                            .map(attendeeId -> PersonResponse.of(attendeeId, users.get(attendeeId)))
                            .toList();
                    return new WorkspaceArchiveItemResponse(minutes.getId(), minutes.getMeetingId(), minutes.getTitle(),
                            minutes.getMeetingStartAt(), minutes.getLocation(), minutes.getStatus(), minutes.getFolderId(),
                            folders.get(minutes.getFolderId()).name(), people,
                            minutesMark != null && minutesMark.isFavorite(), minutesMark != null && minutesMark.isImportant());
                })
                .toList();
        boolean hasNext = (long) (pageNumber + 1) * pageSize < result.totalCount();
        return new WorkspaceArchivePageResponse(items, pageNumber, pageSize, result.totalCount(), hasNext);
    }

    /** REQ-055. 장소 필터의 선택지: 워크스페이스에서 내가 볼 수 있는 회의록의 장소, 가나다순 */
    @Transactional(readOnly = true)
    public List<String> locations(Long workspaceId, Long userId) {
        Map<Long, AccessibleFolder> folders = foldersOf(workspaceId, userId);
        return folders.isEmpty() ? List.of() : minutesRepository.findLocationsByFolderIdIn(folders.keySet());
    }

    /** REQ-146. 회의록을 열 때마다 부른다 */
    @Transactional
    public void view(Long minutesId, Long userId) {
        Minutes minutes = access.require(minutesId);
        access.requireViewable(minutes, userId);
        LocalDateTime now = LocalDateTime.now(clock);
        viewRepository.findByUserIdAndMinutesId(userId, minutesId).ifPresentOrElse(
                view -> view.touch(now),
                () -> viewRepository.save(new MinutesView(userId, minutesId, now)));
    }

    /** REQ-146. 워크스페이스에서 최근 본 회의록, 최근 본 순. 더는 볼 수 없는 회의록은 뺀다 */
    @Transactional(readOnly = true)
    public List<ViewedMinutesResponse> viewed(Long workspaceId, Long userId, int size) {
        Map<Long, AccessibleFolder> folders = foldersOf(workspaceId, userId);
        if (folders.isEmpty()) {
            return List.of();
        }
        List<MinutesView> views = viewRepository.findByUserIdOrderByViewedAtDesc(userId, PageRequest.of(0, MAX_VIEWED_READ));
        Map<Long, Minutes> found = minutesRepository.findAllById(views.stream().map(MinutesView::getMinutesId).toList())
                .stream().collect(Collectors.toMap(Minutes::getId, Function.identity()));
        return views.stream()
                .filter(view -> found.containsKey(view.getMinutesId()))
                .filter(view -> folders.containsKey(found.get(view.getMinutesId()).getFolderId()))
                .limit(Math.clamp(size, 1, MAX_RECENT_SIZE))
                .map(view -> {
                    Minutes minutes = found.get(view.getMinutesId());
                    return new ViewedMinutesResponse(minutes.getId(), minutes.getMeetingId(), minutes.getTitle(),
                            minutes.getMeetingStartAt(), view.getViewedAt());
                })
                .toList();
    }

    /** REQ-142. 내가 작성자인 작성 중 회의록, 회의 날짜가 오래된 순. 들어갈 수 없게 된 폴더의 회의록은 뺀다 */
    @Transactional(readOnly = true)
    public List<WritingMinutesResponse> writing(Long userId) {
        List<Minutes> writing = minutesRepository.findByWriterUserIdAndStatusOrderByMeetingStartAtAscIdAsc(userId,
                MinutesStatus.WRITING);
        if (writing.isEmpty()) {
            return List.of();
        }
        Map<Long, AccessibleFolder> folders = accessibleFolders(userId);
        return writing.stream()
                .filter(minutes -> folders.containsKey(minutes.getFolderId()))
                .map(minutes -> {
                    AccessibleFolder folder = folders.get(minutes.getFolderId());
                    return new WritingMinutesResponse(minutes.getId(), minutes.getMeetingId(), minutes.getTitle(),
                            minutes.getMeetingStartAt(), minutes.getLocation(), minutes.getWorkspaceId(),
                            folder.workspaceName(), minutes.getFolderId(), folder.name());
                })
                .toList();
    }

    /** REQ-056. 회의록을 볼 수 있는 사람, 내 표시만 바꾼다 */
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

    /** REQ-026~027. 내가 들어갈 수 있는 모든 폴더의 확정 회의록, 최근 확정 순 */
    @Transactional(readOnly = true)
    public List<RecentMinutesResponse> recent(Long userId, int size) {
        Map<Long, AccessibleFolder> folders = accessibleFolders(userId);
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

    private Map<Long, AccessibleFolder> accessibleFolders(Long userId) {
        return workspaceClient.accessibleFolders(userId).stream()
                .collect(Collectors.toMap(AccessibleFolder::folderId, Function.identity(), (a, b) -> a));
    }

    private Map<Long, AccessibleFolder> foldersOf(Long workspaceId, Long userId) {
        Map<Long, AccessibleFolder> folders = accessibleFolders(userId);
        folders.values().removeIf(folder -> !folder.workspaceId().equals(workspaceId));
        return folders;
    }

    private static <T> List<T> nonNull(List<T> values) {
        return values == null ? List.of() : values;
    }
}
