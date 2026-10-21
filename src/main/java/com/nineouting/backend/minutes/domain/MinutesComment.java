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

/** 검토 코멘트 (REQ-108): 고른 문장(quote)에 남긴 의견. 작성자가 반영하면 해결 표시 */
@Getter
@Entity
@Table(name = "minutes_comments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MinutesComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "minutes_id", nullable = false)
    private Long minutesId;

    @Column(name = "section_id", nullable = false)
    private Long sectionId;

    @Column(nullable = false, length = 1000)
    private String quote;

    @Column(nullable = false, length = 1000)
    private String body;

    @Column(name = "author_user_id", nullable = false)
    private Long authorUserId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    public MinutesComment(Long minutesId, Long sectionId, String quote, String body, Long authorUserId,
                          LocalDateTime createdAt) {
        this.minutesId = minutesId;
        this.sectionId = sectionId;
        this.quote = quote;
        this.body = body;
        this.authorUserId = authorUserId;
        this.createdAt = createdAt;
    }

    public void resolve(LocalDateTime now) {
        if (resolvedAt == null) {
            this.resolvedAt = now;
        }
    }
}
