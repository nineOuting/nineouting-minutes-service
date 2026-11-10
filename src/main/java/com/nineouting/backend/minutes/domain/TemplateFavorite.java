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

/** 회의록 양식 즐겨찾기 (REQ-123). 개인 표시라 사용자마다 따로 저장한다 */
@Getter
@Entity
@Table(name = "template_favorites")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TemplateFavorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public TemplateFavorite(Long templateId, Long userId, LocalDateTime createdAt) {
        this.templateId = templateId;
        this.userId = userId;
        this.createdAt = createdAt;
    }
}
