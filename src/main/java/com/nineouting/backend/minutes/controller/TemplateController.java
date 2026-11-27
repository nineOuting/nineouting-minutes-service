package com.nineouting.backend.minutes.controller;

import com.nineouting.backend.common.security.jwt.AuthenticatedUser;
import com.nineouting.backend.common.security.web.CurrentUser;
import com.nineouting.backend.common.web.response.ApiResponse;
import com.nineouting.backend.minutes.dto.BackgroundUploadRequest;
import com.nineouting.backend.minutes.dto.BackgroundUploadResponse;
import com.nineouting.backend.minutes.dto.TemplateImportRequest;
import com.nineouting.backend.minutes.dto.TemplateImportStatusResponse;
import com.nineouting.backend.minutes.dto.TemplateImportUploadRequest;
import com.nineouting.backend.minutes.dto.TemplateImportUploadResponse;
import com.nineouting.backend.minutes.dto.TemplateRequest;
import com.nineouting.backend.minutes.dto.TemplateResponse;
import com.nineouting.backend.minutes.dto.WorkspaceDefaultTemplateRequest;
import com.nineouting.backend.minutes.service.TemplateImportService;
import com.nineouting.backend.minutes.service.TemplateService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 회의록 양식 관리 (REQ-119~121) */
@RestController
@RequestMapping("/api/minutes-templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;
    private final TemplateImportService templateImportService;

    @GetMapping
    public ApiResponse<List<TemplateResponse>> list(@CurrentUser AuthenticatedUser user) {
        return ApiResponse.ok(templateService.list(user.userId()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TemplateResponse>> create(@CurrentUser AuthenticatedUser user,
                                                                @Valid @RequestBody TemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(templateService.create(user.userId(), request)));
    }

    /** REQ-157 양식 배경 이미지 업로드 주소 */
    @PostMapping("/background-upload-url")
    public ApiResponse<BackgroundUploadResponse> backgroundUploadUrl(@CurrentUser AuthenticatedUser user,
                                                                    @Valid @RequestBody BackgroundUploadRequest request) {
        return ApiResponse.ok(templateService.backgroundUploadUrl(user.userId(), request));
    }

    /** REQ-195 양식 가져오기: 파일(PDF, Word, Excel, 텍스트) 업로드 주소 */
    @PostMapping("/import-upload-url")
    public ApiResponse<TemplateImportUploadResponse> importUploadUrl(@CurrentUser AuthenticatedUser user,
                                                                     @Valid @RequestBody TemplateImportUploadRequest request) {
        return ApiResponse.ok(templateImportService.uploadUrl(user.userId(), request));
    }

    /**
     * REQ-195 양식 가져오기 접수: 올린 파일을 뒤에서 AI로 읽어 새 개인 양식으로 저장한다 (최대 약 2분).
     * 바로 202와 importId로 답하고, 화면은 GET /import/{importId}로 결과를 묻는다
     */
    @PostMapping("/import")
    public ResponseEntity<ApiResponse<TemplateImportStatusResponse>> importTemplate(@CurrentUser AuthenticatedUser user,
                                                                                    @Valid @RequestBody TemplateImportRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok(templateImportService.start(user.userId(), request)));
    }

    /** REQ-195 양식 가져오기 진행 상태 (PENDING, SUCCEEDED + templateId, FAILED + code, message) */
    @GetMapping("/import/{importId}")
    public ApiResponse<TemplateImportStatusResponse> importStatus(@CurrentUser AuthenticatedUser user,
                                                                  @PathVariable Long importId) {
        return ApiResponse.ok(templateImportService.status(user.userId(), importId));
    }

    @PutMapping("/{templateId}")
    public ApiResponse<TemplateResponse> update(@CurrentUser AuthenticatedUser user, @PathVariable Long templateId,
                                                @Valid @RequestBody TemplateRequest request) {
        return ApiResponse.ok(templateService.update(templateId, user.userId(), request));
    }

    @DeleteMapping("/{templateId}")
    public ApiResponse<Void> delete(@CurrentUser AuthenticatedUser user, @PathVariable Long templateId) {
        templateService.delete(templateId, user.userId());
        return ApiResponse.ok();
    }

    /** REQ-123 즐겨찾기 등록 */
    @PutMapping("/{templateId}/favorite")
    public ApiResponse<Void> addFavorite(@CurrentUser AuthenticatedUser user, @PathVariable Long templateId) {
        templateService.favorite(templateId, user.userId(), true);
        return ApiResponse.ok();
    }

    /** REQ-123 즐겨찾기 해제 */
    @DeleteMapping("/{templateId}/favorite")
    public ApiResponse<Void> removeFavorite(@CurrentUser AuthenticatedUser user, @PathVariable Long templateId) {
        templateService.favorite(templateId, user.userId(), false);
        return ApiResponse.ok();
    }

    /** REQ-199 워크스페이스 기본 양식으로 정한 내 양식을 그 워크스페이스 멤버 모두 쓰게 한다 */
    @PutMapping("/{templateId}/workspace-shares/{workspaceId}")
    public ApiResponse<Void> share(@CurrentUser AuthenticatedUser user, @PathVariable Long templateId,
                                   @PathVariable Long workspaceId) {
        templateService.share(templateId, workspaceId, user.userId(), true);
        return ApiResponse.ok();
    }

    /** REQ-199 기본 양식에서 빼면 공유도 거둔다 */
    /** REQ-213 워크스페이스 기본 양식 정하기 (워크스페이스 소유자). 고른 양식을 워크스페이스 사본으로 만들고 그 사본을 돌려준다 */
    @PutMapping("/workspaces/{workspaceId}/default")
    public ApiResponse<TemplateResponse> setWorkspaceDefault(@CurrentUser AuthenticatedUser user, @PathVariable Long workspaceId,
                                                            @Valid @RequestBody WorkspaceDefaultTemplateRequest request) {
        return ApiResponse.ok(templateService.setWorkspaceDefault(workspaceId, user.userId(), request.templateId()));
    }

    @DeleteMapping("/{templateId}/workspace-shares/{workspaceId}")
    public ApiResponse<Void> unshare(@CurrentUser AuthenticatedUser user, @PathVariable Long templateId,
                                     @PathVariable Long workspaceId) {
        templateService.share(templateId, workspaceId, user.userId(), false);
        return ApiResponse.ok();
    }
}
