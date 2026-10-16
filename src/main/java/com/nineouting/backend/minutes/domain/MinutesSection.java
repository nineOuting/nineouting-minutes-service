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

/** 회의록의 칸 (양식 항목마다 1개). 내용은 일반 텍스트 */
@Getter
@Entity
@Table(name = "minutes_sections")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MinutesSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "minutes_id", nullable = false)
    private Long minutesId;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    public MinutesSection(Long minutesId, int position, String title, String content) {
        this.minutesId = minutesId;
        this.position = position;
        this.title = title;
        this.content = content;
    }

    public void change(int position, String title, String content) {
        this.position = position;
        this.title = title;
        this.content = content;
    }
}
