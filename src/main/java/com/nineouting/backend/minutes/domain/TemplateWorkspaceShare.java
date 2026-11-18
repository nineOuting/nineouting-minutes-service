package com.nineouting.backend.minutes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** REQ-199 워크스페이스 기본 양식으로 정한 개인 양식. 그 워크스페이스 멤버 모두 쓸 수 있다 (고치는 건 만든 사람만) */
@Getter
@Entity
@Table(name = "template_workspace_shares")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TemplateWorkspaceShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public TemplateWorkspaceShare(Long templateId, Long workspaceId, LocalDateTime createdAt) {
        this.templateId = templateId;
        this.workspaceId = workspaceId;
        this.createdAt = createdAt;
    }
}
