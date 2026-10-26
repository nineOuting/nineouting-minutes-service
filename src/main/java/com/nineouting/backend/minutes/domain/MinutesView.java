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

/** 최근 본 회의록 (REQ-146). 사람마다 회의록당 1개 */
@Getter
@Entity
@Table(name = "minutes_views")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MinutesView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "minutes_id", nullable = false)
    private Long minutesId;

    @Column(name = "viewed_at", nullable = false)
    private LocalDateTime viewedAt;

    public MinutesView(Long userId, Long minutesId, LocalDateTime viewedAt) {
        this.userId = userId;
        this.minutesId = minutesId;
        this.viewedAt = viewedAt;
    }

    public void touch(LocalDateTime now) {
        this.viewedAt = now;
    }
}
