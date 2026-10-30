package com.nineouting.backend.minutes.service;

import com.nineouting.backend.common.client.WorkspaceClient;
import com.nineouting.backend.common.web.error.BusinessException;
import com.nineouting.backend.common.web.error.CommonErrorCode;
import com.nineouting.backend.minutes.domain.Minutes;
import com.nineouting.backend.minutes.error.MinutesErrorCode;
import com.nineouting.backend.minutes.repository.MinutesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 회의록 권한 규칙 (docs/requirements.md "권한", docs/services/minutes-service.md 3장).
 * <ul>
 *   <li>모든 회의록: 회의록이 있는 폴더에 들어갈 수 있어야 한다</li>
 *   <li>확정 전(작성 중·검토 중): 회의 참석자만</li>
 *   <li>편집·검토 요청·확정: 회의록 작성자만</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class MinutesAccess {

    private final MinutesRepository minutesRepository;
    private final WorkspaceClient workspaceClient;

    public Minutes require(Long minutesId) {
        return minutesRepository.findById(minutesId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    /** 내용 저장·검토·확정처럼 상태를 바꿀 때는 잠가서 가져온다 */
    public Minutes requireForUpdate(Long minutesId) {
        return minutesRepository.findByIdForUpdate(minutesId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    public void requireFolderAccess(Long folderId, Long userId) {
        if (!workspaceClient.access(folderId, userId).allowed()) {
            throw new BusinessException(MinutesErrorCode.FOLDER_ACCESS_DENIED);
        }
    }

    /** REQ-053. 회의록이 있는 폴더의 멤버면 상태와 상관없이 볼 수 있다 (2026-10-26 기획서: 확정 전 참석자만 규칙 없앰) */
    public void requireViewable(Minutes minutes, Long userId) {
        requireFolderAccess(minutes.getFolderId(), userId);
    }

    public void requireWriter(Minutes minutes, Long userId) {
        requireViewable(minutes, userId);
        if (!minutes.isWriter(userId)) {
            throw new BusinessException(MinutesErrorCode.NOT_MINUTES_WRITER);
        }
    }
}
