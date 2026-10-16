package com.nineouting.backend.minutes.domain;

import com.nineouting.backend.common.web.entity.BaseTimeEntity;
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

/**
 * 회의록. 회의마다 1개이고, 회의 마무리(MeetingFinished)를 받아 만든다.
 * contentVersion은 내용을 저장할 때마다 1씩 늘어난다 (먼저 저장한 내용을 덮어쓰지 않도록 요청의 version과 비교).
 */
@Getter
@Entity
@Table(name = "minutes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Minutes extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "folder_id", nullable = false)
    private Long folderId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(name = "meeting_start_at", nullable = false)
    private LocalDateTime meetingStartAt;

    @Column(name = "writer_user_id", nullable = false)
    private Long writerUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MinutesStatus status;

    @Column(name = "template_id")
    private Long templateId;

    @Column(name = "content_version", nullable = false)
    private int contentVersion;

    @Column(name = "review_round", nullable = false)
    private int reviewRound;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    /** 작성자가 확정했으면 작성자, 전원 동의로 자동 확정됐으면 null */
    @Column(name = "confirmed_by_user_id")
    private Long confirmedByUserId;

    /** 검색용: 회의명 + 모든 칸 제목·내용 (pg_trgm 인덱스) */
    @Column(name = "search_text", nullable = false, columnDefinition = "TEXT")
    private String searchText;

    public Minutes(Long meetingId, Long workspaceId, Long folderId, String title, LocalDateTime meetingStartAt,
                   Long writerUserId) {
        this.meetingId = meetingId;
        this.workspaceId = workspaceId;
        this.folderId = folderId;
        this.title = title;
        this.meetingStartAt = meetingStartAt;
        this.writerUserId = writerUserId;
        this.status = MinutesStatus.WRITING;
        this.searchText = title;
    }

    public boolean isWriter(Long userId) {
        return writerUserId.equals(userId);
    }

    public boolean is(MinutesStatus status) {
        return this.status == status;
    }

    /** 내용(칸)이 바뀌었다. 새 version을 돌려준다 */
    public int contentChanged(Long templateId, String searchText) {
        if (templateId != null) {
            this.templateId = templateId;
        }
        this.searchText = searchText;
        return ++contentVersion;
    }

    public void requestReview() {
        this.status = MinutesStatus.REVIEWING;
        this.reviewRound++;
    }

    public void confirm(LocalDateTime now, Long confirmedByUserId) {
        this.status = MinutesStatus.CONFIRMED;
        this.confirmedAt = now;
        this.confirmedByUserId = confirmedByUserId;
    }

    public void moveTo(Long folderId) {
        this.folderId = folderId;
    }
}
