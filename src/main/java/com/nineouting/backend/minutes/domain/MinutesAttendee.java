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

/** 회의 마무리 시점의 참석자 (작성자 포함). 검토 대상과 참석자 필터에 쓴다. */
@Getter
@Entity
@Table(name = "minutes_attendees")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MinutesAttendee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "minutes_id", nullable = false)
    private Long minutesId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    public MinutesAttendee(Long minutesId, Long userId) {
        this.minutesId = minutesId;
        this.userId = userId;
    }
}
