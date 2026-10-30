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

    /** 회의 장소 (아카이브 장소 필터, REQ-055). 없으면 빈 문자열 */
    @Column(nullable = false, length = 100)
    private String location;

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

    /** AI 회의록 초안 (REQ-101~103). 요청한 적 없으면 null */
    @Enumerated(EnumType.STRING)
    @Column(name = "draft_status", length = 20)
    private DraftStatus draftStatus;

    /** 지금 기다리는 초안 요청 번호. 이 번호의 결과만 받는다 */
    @Column(name = "draft_request_id", length = 36)
    private String draftRequestId;

    /** 실패했을 때 ai-service 오류 코드 */
    @Column(name = "draft_error", length = 100)
    private String draftError;

    @Column(name = "draft_requested_at")
    private LocalDateTime draftRequestedAt;

    public Minutes(Long meetingId, Long workspaceId, Long folderId, String title, LocalDateTime meetingStartAt,
                   String location, Long writerUserId) {
        this.meetingId = meetingId;
        this.workspaceId = workspaceId;
        this.folderId = folderId;
        this.title = title;
        this.meetingStartAt = meetingStartAt;
        this.location = location == null ? "" : location;
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

    public void draftRequested(String requestId, LocalDateTime now) {
        this.draftStatus = DraftStatus.REQUESTED;
        this.draftRequestId = requestId;
        this.draftError = null;
        this.draftRequestedAt = now;
    }

    public void draftSucceeded() {
        this.draftStatus = DraftStatus.SUCCEEDED;
    }

    public void draftFailed(String error) {
        this.draftStatus = DraftStatus.FAILED;
        this.draftError = error == null ? null : error.substring(0, Math.min(error.length(), 100));
    }
}
