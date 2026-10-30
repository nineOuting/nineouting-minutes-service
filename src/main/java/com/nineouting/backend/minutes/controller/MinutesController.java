package com.nineouting.backend.minutes.controller;

import com.nineouting.backend.common.security.jwt.AuthenticatedUser;
import com.nineouting.backend.common.security.web.CurrentUser;
import com.nineouting.backend.common.web.response.ApiResponse;
import com.nineouting.backend.minutes.dto.ExportFormat;
import com.nineouting.backend.minutes.dto.MinutesDetailResponse;
import com.nineouting.backend.minutes.dto.RevisionResponse;
import com.nineouting.backend.minutes.dto.SaveSectionsRequest;
import com.nineouting.backend.minutes.dto.SelectTemplateRequest;
import com.nineouting.backend.minutes.service.DraftService;
import com.nineouting.backend.minutes.service.MinutesExportService;
import com.nineouting.backend.minutes.service.MinutesService;
import com.nineouting.backend.minutes.service.ReviewService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 회의록 조회·양식 선택·편집·수정 기록·태그 */
@RestController
@RequiredArgsConstructor
public class MinutesController {

    private final MinutesService minutesService;
    private final MinutesExportService exportService;
    private final ReviewService reviewService;
    private final DraftService draftService;

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

    /** REQ-137. format: PDF / DOCX */
    @GetMapping("/api/minutes/{minutesId}/export")
    public ResponseEntity<byte[]> export(@CurrentUser AuthenticatedUser user, @PathVariable Long minutesId,
                                         @RequestParam(defaultValue = "PDF") ExportFormat format) {
        MinutesExportService.ExportFile file = exportService.export(minutesId, user.userId(), format);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }

    /** REQ-136 작성 완료: 바로 확정하고 이번 회의 참석자에게 공유 */
    @PostMapping("/api/minutes/{minutesId}/complete")
    public ApiResponse<Void> complete(@CurrentUser AuthenticatedUser user, @PathVariable Long minutesId) {
        reviewService.complete(minutesId, user.userId());
        return ApiResponse.ok();
    }

    /**
     * REQ-101~103 AI 초안 (다시) 요청. 양식을 처음 고르면 자동으로 요청하므로, 실패했을 때 다시 누른다.
     * 결과가 오기 전에는 draftStatus가 REQUESTED(화면 "준비 중")
     */
    @PostMapping("/api/minutes/{minutesId}/draft")
    public ApiResponse<MinutesDetailResponse> requestDraft(@CurrentUser AuthenticatedUser user, @PathVariable Long minutesId) {
        draftService.request(minutesId, user.userId());
        return ApiResponse.ok(minutesService.get(minutesId, user.userId()));
    }

    /** REQ-140 양식 변경 (REQ-100에서 이어짐) */
    @PutMapping("/api/minutes/{minutesId}/template")
    public ApiResponse<MinutesDetailResponse> selectTemplate(@CurrentUser AuthenticatedUser user, @PathVariable Long minutesId,
                                                             @Valid @RequestBody SelectTemplateRequest request) {
        return ApiResponse.ok(minutesService.selectTemplate(minutesId, user.userId(), request.templateId()));
    }

    /** REQ-104 (레거시), 111 */
    @PutMapping("/api/minutes/{minutesId}/sections")
    public ApiResponse<MinutesDetailResponse> saveSections(@CurrentUser AuthenticatedUser user, @PathVariable Long minutesId,
                                                           @Valid @RequestBody SaveSectionsRequest request) {
        return ApiResponse.ok(minutesService.saveSections(minutesId, user.userId(), request.version(), request.sections()));
    }

    /** REQ-104 (레거시) */
    @GetMapping("/api/minutes/{minutesId}/revisions")
    public ApiResponse<List<RevisionResponse>> revisions(@CurrentUser AuthenticatedUser user, @PathVariable Long minutesId) {
        return ApiResponse.ok(minutesService.revisions(minutesId, user.userId()));
    }
}
