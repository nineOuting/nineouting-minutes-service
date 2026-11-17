package com.nineouting.backend.minutes.dto;

import com.nineouting.backend.minutes.domain.DraftStatus;
import com.nineouting.backend.minutes.domain.MinutesStatus;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 회의록 상세 (REQ-053, 104).
 *
 * @param version           내용을 저장할 때 그대로 돌려보낸다
 * @param confirmedByUserId 작성자가 확정했으면 작성자, 전원 동의로 자동 확정됐으면 null
 * @param favorite          내 즐겨찾기 (개인 표시)
 * @param important         내 중요 표시 (개인 표시)
 * @param draftStatus       AI 초안 (REQ-101~103): REQUESTED면 화면에 "준비 중" (REQ-098), FAILED면 다시 요청할 수 있다. 요청 전이면 null
 * @param draftError        초안 실패 코드 (ai-service minutes-draft-contract.md 6장)
 * @param richText          서식 기능(REQ-194)이 켜져 있는지. 꺼져 있으면 화면은 예전처럼 서식 없는 입력 칸을 쓴다
 * @param template          쓰는 양식의 레이아웃과 칸 설정. 참석자가 양식 목록에서 볼 수 없는 개인 양식이어도 회의록을 같은 모양으로 그린다 (2026-11-17). 양식이 없으면 null
 */
public record MinutesDetailResponse(
        Long minutesId,
        Long meetingId,
        Long workspaceId,
        Long folderId,
        String title,
        LocalDateTime meetingStartAt,
        String location,
        MinutesStatus status,
        PersonResponse writer,
        List<PersonResponse> attendees,
        Long templateId,
        int version,
        int reviewRound,
        List<SectionResponse> sections,
        boolean favorite,
        boolean important,
        LocalDateTime confirmedAt,
        Long confirmedByUserId,
        DraftStatus draftStatus,
        String draftError,
        boolean richText,
        TemplateResponse template
) {
}
