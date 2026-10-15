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

/** 아카이브 태그 (REQ-053). 회의록 작성자가 자유롭게 붙인다 */
@Getter
@Entity
@Table(name = "minutes_tags")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MinutesTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "minutes_id", nullable = false)
    private Long minutesId;

    @Column(nullable = false, length = 20)
    private String tag;

    public MinutesTag(Long minutesId, String tag) {
        this.minutesId = minutesId;
        this.tag = tag;
    }
}
