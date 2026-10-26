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

/** 양식 항목. position 순서대로 회의록 칸이 된다 */
@Getter
@Entity
@Table(name = "template_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TemplateItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 200)
    private String description;

    /** 항목 설정 JSON (TemplateSettings) */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String settings;

    public TemplateItem(Long templateId, int position, String title, String description, String settings) {
        this.templateId = templateId;
        this.position = position;
        this.title = title;
        this.description = description;
        this.settings = settings;
    }
}
