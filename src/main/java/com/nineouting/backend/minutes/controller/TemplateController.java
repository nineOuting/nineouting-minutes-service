package com.nineouting.backend.minutes.controller;

import com.nineouting.backend.common.security.jwt.AuthenticatedUser;
import com.nineouting.backend.common.security.web.CurrentUser;
import com.nineouting.backend.common.web.response.ApiResponse;
import com.nineouting.backend.minutes.dto.TemplateRequest;
import com.nineouting.backend.minutes.dto.TemplateResponse;
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

/** 회의록 양식 관리 (REQ-107~109) */
@RestController
@RequestMapping("/api/minutes-templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    @GetMapping
    public ApiResponse<List<TemplateResponse>> list(@CurrentUser AuthenticatedUser user) {
        return ApiResponse.ok(templateService.list(user.userId()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TemplateResponse>> create(@CurrentUser AuthenticatedUser user,
                                                                @Valid @RequestBody TemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(templateService.create(user.userId(), request)));
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
}
