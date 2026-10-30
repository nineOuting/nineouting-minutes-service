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

/** 참석자(작성자 제외)별 검토. 검토를 요청할 때마다 모두 미확인으로 돌아간다. */
@Getter
@Entity
@Table(name = "minutes_reviews")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MinutesReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "minutes_id", nullable = false)
    private Long minutesId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewStatus status;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    public MinutesReview(Long minutesId, Long userId) {
        this.minutesId = minutesId;
        this.userId = userId;
        this.status = ReviewStatus.PENDING;
    }

    public void reset() {
        this.status = ReviewStatus.PENDING;
        this.decidedAt = null;
    }

    public void decide(ReviewStatus status, LocalDateTime now) {
        this.status = status;
        this.decidedAt = now;
    }

    public boolean isApproved() {
        return status == ReviewStatus.APPROVED;
    }
}
