package com.nineouting.backend.minutes.controller;

import com.nineouting.backend.common.security.jwt.AuthenticatedUser;
import com.nineouting.backend.common.security.web.CurrentUser;
import com.nineouting.backend.common.web.response.ApiResponse;
import com.nineouting.backend.minutes.dto.MinutesDetailResponse;
import com.nineouting.backend.minutes.dto.RevisionResponse;
import com.nineouting.backend.minutes.dto.SaveSectionsRequest;
import com.nineouting.backend.minutes.dto.SelectTemplateRequest;
import com.nineouting.backend.minutes.service.MinutesService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 회의록 조회·양식 선택·편집·수정 기록·태그 */
@RestController
@RequiredArgsConstructor
public class MinutesController {

    private final MinutesService minutesService;

    /** REQ-020: 회의에서 회의록으로 이동 */
    @GetMapping("/api/meetings/{meetingId}/minutes")
    public ApiResponse<MinutesDetailResponse> getByMeeting(@CurrentUser AuthenticatedUser user, @PathVariable Long meetingId) {
        return ApiResponse.ok(minutesService.getByMeeting(meetingId, user.userId()));
    }

    /** REQ-053 */
    @GetMapping("/api/minutes/{minutesId}")
    public ApiResponse<MinutesDetailResponse> get(@CurrentUser AuthenticatedUser user, @PathVariable Long minutesId) {
        return ApiResponse.ok(minutesService.get(minutesId, user.userId()));
    }

    /** REQ-100 */
    @PutMapping("/api/minutes/{minutesId}/template")
    public ApiResponse<MinutesDetailResponse> selectTemplate(@CurrentUser AuthenticatedUser user, @PathVariable Long minutesId,
                                                             @Valid @RequestBody SelectTemplateRequest request) {
        return ApiResponse.ok(minutesService.selectTemplate(minutesId, user.userId(), request.templateId()));
    }

    /** REQ-104, 111 */
    @PutMapping("/api/minutes/{minutesId}/sections")
    public ApiResponse<MinutesDetailResponse> saveSections(@CurrentUser AuthenticatedUser user, @PathVariable Long minutesId,
                                                           @Valid @RequestBody SaveSectionsRequest request) {
        return ApiResponse.ok(minutesService.saveSections(minutesId, user.userId(), request.version(), request.sections()));
    }

    /** REQ-104 */
    @GetMapping("/api/minutes/{minutesId}/revisions")
    public ApiResponse<List<RevisionResponse>> revisions(@CurrentUser AuthenticatedUser user, @PathVariable Long minutesId) {
        return ApiResponse.ok(minutesService.revisions(minutesId, user.userId()));
    }
}
