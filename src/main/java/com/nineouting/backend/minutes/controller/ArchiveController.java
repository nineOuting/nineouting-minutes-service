package com.nineouting.backend.minutes.controller;

import com.nineouting.backend.common.security.jwt.AuthenticatedUser;
import com.nineouting.backend.common.security.web.CurrentUser;
import com.nineouting.backend.common.web.response.ApiResponse;
import com.nineouting.backend.minutes.dto.ArchivePageResponse;
import com.nineouting.backend.minutes.dto.ArchiveSort;
import com.nineouting.backend.minutes.dto.MarkFilter;
import com.nineouting.backend.minutes.dto.MarksRequest;
import com.nineouting.backend.minutes.dto.RecentMinutesResponse;
import com.nineouting.backend.minutes.dto.ReviewRequestsResponse;
import com.nineouting.backend.minutes.dto.ViewedMinutesResponse;
import com.nineouting.backend.minutes.dto.WorkspaceArchivePageResponse;
import com.nineouting.backend.minutes.dto.WritingMinutesResponse;
import com.nineouting.backend.minutes.service.ArchiveService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 아카이브(REQ-053~056, 146)와 홈(REQ-021~023, 026~027, 142) */
@RestController
@RequiredArgsConstructor
public class ArchiveController {

    private static final String DEFAULT_PAGE_SIZE = "20";
    private static final String DEFAULT_RECENT_SIZE = "10";
    private static final String DEFAULT_VIEWED_SIZE = "5";

    private final ArchiveService archiveService;

    /** REQ-053~056. q: 회의명·내용, from·to: 회의 날짜, location: 장소(일부만 맞아도 됨), attendeeId, mark: FAVORITE / IMPORTANT */
    @GetMapping("/api/folders/{folderId}/minutes")
    public ApiResponse<ArchivePageResponse> folderMinutes(@CurrentUser AuthenticatedUser user, @PathVariable Long folderId,
                                                          @RequestParam(required = false) String q,
                                                          @RequestParam(required = false) LocalDate from,
                                                          @RequestParam(required = false) LocalDate to,
                                                          @RequestParam(required = false) String location,
                                                          @RequestParam(required = false) Long attendeeId,
                                                          @RequestParam(required = false) MarkFilter mark,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) int size) {
        return ApiResponse.ok(archiveService.folderMinutes(folderId, user.userId(), q, from, to, location, attendeeId, mark,
                page, size));
    }

    /** REQ-053~055. q: 회의명·내용, from·to: 회의 날짜, location·attendeeId: 여러 개 가능, sort: LATEST / OLDEST */
    @GetMapping("/api/workspaces/{workspaceId}/minutes")
    public ApiResponse<WorkspaceArchivePageResponse> workspaceMinutes(@CurrentUser AuthenticatedUser user,
                                                                      @PathVariable Long workspaceId,
                                                                      @RequestParam(required = false) String q,
                                                                      @RequestParam(required = false) LocalDate from,
                                                                      @RequestParam(required = false) LocalDate to,
                                                                      @RequestParam(required = false) List<String> location,
                                                                      @RequestParam(required = false) List<Long> attendeeId,
                                                                      @RequestParam(defaultValue = "LATEST") ArchiveSort sort,
                                                                      @RequestParam(defaultValue = "0") int page,
                                                                      @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) int size) {
        return ApiResponse.ok(archiveService.workspaceMinutes(workspaceId, user.userId(), q, from, to, location, attendeeId,
                sort, page, size));
    }

    /** REQ-055 장소 필터의 선택지 */
    @GetMapping("/api/workspaces/{workspaceId}/minutes/locations")
    public ApiResponse<List<String>> locations(@CurrentUser AuthenticatedUser user, @PathVariable Long workspaceId) {
        return ApiResponse.ok(archiveService.locations(workspaceId, user.userId()));
    }

    /** REQ-146 회의록을 열 때마다 부른다 */
    @PostMapping("/api/minutes/{minutesId}/view")
    public ApiResponse<Void> view(@CurrentUser AuthenticatedUser user, @PathVariable Long minutesId) {
        archiveService.view(minutesId, user.userId());
        return ApiResponse.ok();
    }

    /** REQ-146 */
    @GetMapping("/api/workspaces/{workspaceId}/viewed-minutes")
    public ApiResponse<List<ViewedMinutesResponse>> viewed(@CurrentUser AuthenticatedUser user, @PathVariable Long workspaceId,
                                                           @RequestParam(defaultValue = DEFAULT_VIEWED_SIZE) int size) {
        return ApiResponse.ok(archiveService.viewed(workspaceId, user.userId(), size));
    }

    /** REQ-142 */
    @GetMapping("/api/users/me/writing-minutes")
    public ApiResponse<List<WritingMinutesResponse>> writing(@CurrentUser AuthenticatedUser user) {
        return ApiResponse.ok(archiveService.writing(user.userId()));
    }

    /** REQ-056 */
    @PutMapping("/api/minutes/{minutesId}/marks")
    public ApiResponse<Void> mark(@CurrentUser AuthenticatedUser user, @PathVariable Long minutesId,
                                  @RequestBody MarksRequest request) {
        archiveService.mark(minutesId, user.userId(), request);
        return ApiResponse.ok();
    }

    /** REQ-021~023 */
    @GetMapping("/api/users/me/review-requests")
    public ApiResponse<ReviewRequestsResponse> reviewRequests(@CurrentUser AuthenticatedUser user) {
        return ApiResponse.ok(archiveService.reviewRequests(user.userId()));
    }

    /** REQ-026~027 */
    @GetMapping("/api/users/me/recent-minutes")
    public ApiResponse<List<RecentMinutesResponse>> recent(@CurrentUser AuthenticatedUser user,
                                                           @RequestParam(defaultValue = DEFAULT_RECENT_SIZE) int size) {
        return ApiResponse.ok(archiveService.recent(user.userId(), size));
    }
}
