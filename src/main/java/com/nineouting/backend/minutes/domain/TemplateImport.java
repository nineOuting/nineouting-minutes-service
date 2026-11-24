package com.nineouting.backend.minutes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** REQ-195 양식 가져오기 한 번. AI 서버가 최대 약 2분 걸려 접수와 결과를 나눈다 (화면은 상태를 다시 묻는다) */
@Getter
@Entity
@Table(name = "template_imports")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TemplateImport {

    public enum Status { PENDING, SUCCEEDED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "file_key", nullable = false)
    private String fileKey;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "template_id")
    private Long templateId;

    /** 실패 이유 (MinutesErrorCode 이름) */
    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public TemplateImport(Long userId, String fileKey, String name, LocalDateTime now) {
        this.userId = userId;
        this.fileKey = fileKey;
        this.name = name;
        this.status = Status.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void succeed(Long templateId, LocalDateTime now) {
        this.status = Status.SUCCEEDED;
        this.templateId = templateId;
        this.updatedAt = now;
    }

    public void fail(String errorCode, LocalDateTime now) {
        this.status = Status.FAILED;
        this.errorCode = errorCode;
        this.updatedAt = now;
    }
}
