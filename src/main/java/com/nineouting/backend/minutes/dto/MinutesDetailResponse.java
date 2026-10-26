package com.nineouting.backend.minutes.dto;

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
        Long confirmedByUserId
) {
}
