package com.nineouting.backend.minutes.event;

/**
 * meeting-service 회의 쓰레기통 이벤트 (REQ-166, REQ-143). meeting-service의 같은 이름 클래스와 모양이 같다.
 * 아직 nineouting-common에 없어 여기 두고, meeting.moved 토픽으로 받는다. 다음 common 릴리스 때 event-contract로 옮긴다.
 */
public final class MeetingTrashEvents {

    private MeetingTrashEvents() {
    }

    public record MeetingTrashed(Long meetingId, Long workspaceId, Long folderId) {
        public static final String TYPE = "MeetingTrashed";
    }

    public record MeetingRestored(Long meetingId, Long workspaceId, Long folderId) {
        public static final String TYPE = "MeetingRestored";
    }

    public record MeetingPurged(Long meetingId, Long workspaceId) {
        public static final String TYPE = "MeetingPurged";
    }
}
