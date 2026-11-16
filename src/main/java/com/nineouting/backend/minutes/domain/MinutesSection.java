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

/**
 * 회의록의 칸 (양식 항목마다 1개). 내용은 일반 텍스트이고 검색, AI, 챗봇은 이것을 쓴다.
 * richContent는 서식(굵게, 기울임, 밑줄, 목록, 정렬)을 담은 HTML로 화면과 PDF만 쓴다 (REQ-194). 없으면 null
 */
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

    @Column(name = "rich_content", columnDefinition = "TEXT")
    private String richContent;

    public MinutesSection(Long minutesId, int position, String title, String content) {
        this(minutesId, position, title, content, null);
    }

    public MinutesSection(Long minutesId, int position, String title, String content, String richContent) {
        this.minutesId = minutesId;
        this.position = position;
        this.title = title;
        this.content = content;
        this.richContent = richContent;
    }

    /** 서식 없이 내용만 바꾼다 (AI 초안 등). 예전 서식은 내용과 맞지 않으므로 지운다 */
    public void change(int position, String title, String content) {
        change(position, title, content, null);
    }

    public void change(int position, String title, String content, String richContent) {
        this.position = position;
        this.title = title;
        this.content = content;
        this.richContent = richContent;
    }
}
