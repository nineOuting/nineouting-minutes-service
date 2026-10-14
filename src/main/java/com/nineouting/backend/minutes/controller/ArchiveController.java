package com.nineouting.backend.minutes.controller;

import com.nineouting.backend.common.security.jwt.AuthenticatedUser;
import com.nineouting.backend.common.security.web.CurrentUser;
import com.nineouting.backend.common.web.response.ApiResponse;
import com.nineouting.backend.minutes.dto.ArchivePageResponse;
import com.nineouting.backend.minutes.dto.MarkFilter;
import com.nineouting.backend.minutes.dto.MarksRequest;
import com.nineouting.backend.minutes.dto.RecentMinutesResponse;
import com.nineouting.backend.minutes.dto.ReviewRequestsResponse;
import com.nineouting.backend.minutes.service.ArchiveService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 아카이브(REQ-051~054)와 홈(REQ-021~023, 026~027) */
@RestController
@RequiredArgsConstructor
public class ArchiveController {

    private static final String DEFAULT_PAGE_SIZE = "20";
    private static final String DEFAULT_RECENT_SIZE = "10";

    private final ArchiveService archiveService;

    /** REQ-051~054. q: 회의명·내용, from·to: 회의 날짜, tag, attendeeId, mark: FAVORITE / IMPORTANT */
    @GetMapping("/api/folders/{folderId}/minutes")
    public ApiResponse<ArchivePageResponse> folderMinutes(@CurrentUser AuthenticatedUser user, @PathVariable Long folderId,
                                                          @RequestParam(required = false) String q,
                                                          @RequestParam(required = false) LocalDate from,
                                                          @RequestParam(required = false) LocalDate to,
                                                          @RequestParam(required = false) String tag,
                                                          @RequestParam(required = false) Long attendeeId,
                                                          @RequestParam(required = false) MarkFilter mark,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) int size) {
        return ApiResponse.ok(archiveService.folderMinutes(folderId, user.userId(), q, from, to, tag, attendeeId, mark,
                page, size));
    }

    /** REQ-054 */
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

    /** REQ-026, 027 */
    @GetMapping("/api/users/me/recent-minutes")
    public ApiResponse<List<RecentMinutesResponse>> recent(@CurrentUser AuthenticatedUser user,
                                                           @RequestParam(defaultValue = DEFAULT_RECENT_SIZE) int size) {
        return ApiResponse.ok(archiveService.recent(user.userId(), size));
    }
}
