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

/**
 * 수정 기록 (REQ-093, 110). 저장할 때마다 전체 내용(칸 제목·내용 JSON)과 수정한 사람·시각을 남긴다.
 */
@Getter
@Entity
@Table(name = "minutes_revisions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MinutesRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "minutes_id", nullable = false)
    private Long minutesId;

    @Column(nullable = false)
    private int version;

    @Column(name = "editor_user_id", nullable = false)
    private Long editorUserId;

    @Column(name = "edited_at", nullable = false)
    private LocalDateTime editedAt;

    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String snapshot;

    /** 확정 후 수정 (REQ-110) */
    @Column(name = "after_confirm", nullable = false)
    private boolean afterConfirm;

    public MinutesRevision(Long minutesId, int version, Long editorUserId, LocalDateTime editedAt, String snapshot,
                           boolean afterConfirm) {
        this.minutesId = minutesId;
        this.version = version;
        this.editorUserId = editorUserId;
        this.editedAt = editedAt;
        this.snapshot = snapshot;
        this.afterConfirm = afterConfirm;
    }
}
