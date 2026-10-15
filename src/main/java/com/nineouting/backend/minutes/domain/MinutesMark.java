package com.nineouting.backend.minutes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 즐겨찾기·중요 표시 (REQ-054). 둘 다 개인 표시라 사용자마다 따로 저장한다 */
@Getter
@Entity
@Table(name = "minutes_marks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MinutesMark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "minutes_id", nullable = false)
    private Long minutesId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private boolean favorite;

    @Column(nullable = false)
    private boolean important;

    public MinutesMark(Long minutesId, Long userId) {
        this.minutesId = minutesId;
        this.userId = userId;
    }

    public void change(boolean favorite, boolean important) {
        this.favorite = favorite;
        this.important = important;
    }
}
