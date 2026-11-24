package com.nineouting.backend.minutes.service;

import com.nineouting.backend.common.web.error.BusinessException;
import com.nineouting.backend.common.web.error.CommonErrorCode;
import com.nineouting.backend.minutes.client.TemplateImportClient;
import com.nineouting.backend.minutes.config.TemplateImportProperties;
import com.nineouting.backend.minutes.domain.TemplateImport;
import com.nineouting.backend.minutes.dto.TemplateImportRequest;
import com.nineouting.backend.minutes.dto.TemplateImportStatusResponse;
import com.nineouting.backend.minutes.dto.TemplateImportUploadRequest;
import com.nineouting.backend.minutes.dto.TemplateImportUploadResponse;
import com.nineouting.backend.minutes.dto.TemplateItemRequest;
import com.nineouting.backend.minutes.dto.TemplateRequest;
import com.nineouting.backend.minutes.error.MinutesErrorCode;
import com.nineouting.backend.minutes.repository.TemplateImportRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/**
 * REQ-195 양식 가져오기. 브라우저가 template-imports/{사용자}/ 아래에 PDF나 Word 파일을 올리면, 접수만 바로 하고
 * 뒤에서 서명 주소로 ai-service에 읽기를 맡긴다(최대 약 2분, CloudFront 30초 한도 때문에 나눈다). 읽은 항목, 머리말,
 * 꼬리말로 새 개인 양식을 저장하고, 화면은 상태를 다시 묻는다. 올린 파일은 끝나면 지운다.
 * 진짜 형식은 ai-service가 파일 내용으로 판단한다 (여기서는 브라우저가 알려 준 형식과 크기만 본다)
 */
@Slf4j
@Service
public class TemplateImportService {

    private static final String PREFIX = "template-imports/";
    private static final String PDF = "application/pdf";
    private static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final Set<String> TYPES = Set.of(PDF, DOCX);
    private static final int MAX_NAME = 50;
    private static final int MAX_TITLE = 100;
    private static final int MAX_DESCRIPTION = 200;
    /** 양식 편집 화면, AI 서버와 같은 표 열 상한 */
    private static final int MAX_COLUMNS = 8;
    private static final Map<String, String> RECORD_TYPES = Map.of(
            "free", "FREE", "list", "LIST", "checklist", "CHECKLIST", "table", "TABLE");

    private final BackgroundStorage storage;
    private final TemplateImportClient client;
    private final TemplateService templateService;
    private final TemplateImportRepository importRepository;
    private final TemplateImportProperties properties;
    private final TaskExecutor executor;
    private final Clock clock;

    public TemplateImportService(BackgroundStorage storage, TemplateImportClient client, TemplateService templateService,
                                 TemplateImportRepository importRepository, TemplateImportProperties properties,
                                 @Qualifier("templateImportExecutor") TaskExecutor executor, Clock clock) {
        this.storage = storage;
        this.client = client;
        this.templateService = templateService;
        this.importRepository = importRepository;
        this.properties = properties;
        this.executor = executor;
        this.clock = clock;
    }

    public TemplateImportUploadResponse uploadUrl(Long userId, TemplateImportUploadRequest request) {
        if (!TYPES.contains(request.contentType()) || request.size() > properties.maxFileBytes()) {
            throw new BusinessException(MinutesErrorCode.TEMPLATE_IMPORT_INVALID);
        }
        String key = PREFIX + userId + "/" + UUID.randomUUID() + (PDF.equals(request.contentType()) ? ".pdf" : ".docx");
        return new TemplateImportUploadResponse(key, storage.uploadUrl(key, request.contentType()), request.contentType());
    }

    /** 올라간 파일인지 확인하고 접수한다. 결과는 status로 묻는다 */
    public TemplateImportStatusResponse start(Long userId, TemplateImportRequest request) {
        String key = request.fileKey();
        if (!key.startsWith(PREFIX + userId + "/")) {
            throw new BusinessException(MinutesErrorCode.TEMPLATE_IMPORT_INVALID);
        }
        long size = storage.size(key)
                .orElseThrow(() -> new BusinessException(MinutesErrorCode.TEMPLATE_IMPORT_NOT_UPLOADED));
        if (size > properties.maxFileBytes()) {
            deleteQuietly(key);
            throw new BusinessException(MinutesErrorCode.TEMPLATE_IMPORT_INVALID);
        }
        TemplateImport job = importRepository.save(new TemplateImport(userId, key, cut(request.name(), MAX_NAME), now()));
        try {
            executor.execute(() -> run(job.getId()));
        } catch (RejectedExecutionException e) {
            finish(job.getId(), null, MinutesErrorCode.TEMPLATE_IMPORT_UNAVAILABLE);
            deleteQuietly(key);
            throw new BusinessException(MinutesErrorCode.TEMPLATE_IMPORT_UNAVAILABLE);
        }
        return response(job);
    }

    /** 내 가져오기 상태. 처리하던 서버가 꺼져 오래 PENDING이면 실패로 바꾼다 */
    public TemplateImportStatusResponse status(Long userId, Long importId) {
        TemplateImport job = importRepository.findByIdAndUserId(importId, userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (job.getStatus() == TemplateImport.Status.PENDING
                && job.getCreatedAt().plus(properties.pendingTimeout()).isBefore(now())) {
            job = finish(job.getId(), null, MinutesErrorCode.TEMPLATE_IMPORT_UNAVAILABLE);
            deleteQuietly(job.getFileKey());
        }
        return response(job);
    }

    /** 뒤에서 한 번 처리한다. 어떤 결과든 파일은 지운다 */
    void run(Long importId) {
        TemplateImport job = importRepository.findById(importId).orElseThrow();
        try {
            TemplateImportClient.Result result = client.importTemplate(
                    storage.downloadUrl(job.getFileKey(), properties.fileUrlTtl()), "template-import-" + importId);
            switch (result.status()) {
                case SUCCEEDED -> finish(importId,
                        templateService.create(job.getUserId(), toTemplate(job.getName(), result.body())).templateId(), null);
                case FAILED -> finish(importId, null, errorOf(result.errorCode()));
                case UNAVAILABLE -> finish(importId, null, MinutesErrorCode.TEMPLATE_IMPORT_UNAVAILABLE);
            }
        } catch (BusinessException e) {
            finish(importId, null, e.getErrorCode() instanceof MinutesErrorCode code ? code : MinutesErrorCode.TEMPLATE_IMPORT_FAILED);
        } catch (RuntimeException e) {
            log.error("양식 가져오기를 처리하지 못했습니다 (importId={}): {}", importId, e.toString());
            finish(importId, null, MinutesErrorCode.TEMPLATE_IMPORT_FAILED);
        } finally {
            deleteQuietly(job.getFileKey());
        }
    }

    /** AI 결과(회의록 초안 명세 5.8 TemplateField)를 양식 저장 형식으로. 항목이 없으면 실패로 본다 (aiFill은 양식에 두지 않는다) */
    TemplateRequest toTemplate(String name, JsonNode body) {
        List<TemplateItemRequest> items = new ArrayList<>();
        for (JsonNode field : body.path("fields")) {
            String title = field.path("title").asString("").trim();
            if (title.isEmpty()) {
                continue;
            }
            String recordType = RECORD_TYPES.getOrDefault(field.path("type").asString(""), "FREE");
            Map<String, Object> settings = new LinkedHashMap<>();
            settings.put("recordType", recordType);
            if ("TABLE".equals(recordType)) {
                settings.put("table", Map.of("cols", Math.clamp(field.path("columns").asInt(1), 1, MAX_COLUMNS)));
            }
            items.add(new TemplateItemRequest(cut(title, MAX_TITLE),
                    cut(field.path("description").asString(""), MAX_DESCRIPTION), settings));
        }
        if (items.isEmpty()) {
            throw new BusinessException(MinutesErrorCode.TEMPLATE_IMPORT_FAILED);
        }
        Map<String, Object> layout = new LinkedHashMap<>();
        putText(layout, "header", body.path("header").asString(""));
        putText(layout, "footer", body.path("footer").asString(""));
        return new TemplateRequest(cut(name, MAX_NAME), "", items, layout);
    }

    private TemplateImport finish(Long importId, Long templateId, MinutesErrorCode error) {
        TemplateImport job = importRepository.findById(importId).orElseThrow();
        if (error == null) {
            job.succeed(templateId, now());
        } else {
            job.fail(error.name(), now());
        }
        return importRepository.save(job);
    }

    private static TemplateImportStatusResponse response(TemplateImport job) {
        MinutesErrorCode error = job.getErrorCode() == null ? null : MinutesErrorCode.valueOf(job.getErrorCode());
        return new TemplateImportStatusResponse(job.getId(), job.getStatus().name(), job.getTemplateId(),
                error == null ? null : error.name(), error == null ? null : error.getMessage());
    }

    private static MinutesErrorCode errorOf(String aiErrorCode) {
        return switch (aiErrorCode == null ? "" : aiErrorCode) {
            case "unsupported_format" -> MinutesErrorCode.TEMPLATE_IMPORT_UNSUPPORTED;
            case "encrypted" -> MinutesErrorCode.TEMPLATE_IMPORT_ENCRYPTED;
            case "too_large" -> MinutesErrorCode.TEMPLATE_IMPORT_TOO_LARGE;
            case "no_text" -> MinutesErrorCode.TEMPLATE_IMPORT_NO_TEXT;
            // 서명 주소 만료, 내려받기 실패, AI 시간 초과는 다시 하면 될 수 있는 일이라 잠시 뒤 다시 하라고 안내한다
            case "url_expired", "download_failed", "LLM_TIMEOUT", "LLM_FAILED" -> MinutesErrorCode.TEMPLATE_IMPORT_UNAVAILABLE;
            default -> MinutesErrorCode.TEMPLATE_IMPORT_FAILED;
        };
    }

    private static void putText(Map<String, Object> layout, String name, String text) {
        if (!text.isBlank()) {
            layout.put(name, Map.of("enabled", true, "text", text.trim()));
        }
    }

    private static String cut(String text, int max) {
        String trimmed = text == null ? "" : text.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            log.warn("가져온 양식 파일을 지우지 못했습니다 ({}): {}", key, e.getClass().getSimpleName());
        }
    }
}
