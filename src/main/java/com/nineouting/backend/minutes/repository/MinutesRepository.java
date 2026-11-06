package com.nineouting.backend.minutes.repository;

import com.nineouting.backend.minutes.domain.Minutes;
import com.nineouting.backend.minutes.domain.MinutesStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MinutesRepository extends JpaRepository<Minutes, Long> {

    Optional<Minutes> findByMeetingId(Long meetingId);

    boolean existsByMeetingId(Long meetingId);

    /** 내용 저장·검토·확정이 동시에 일어나지 않게 잠근다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Minutes m where m.id = :id")
    Optional<Minutes> findByIdForUpdate(Long id);

    /** 오늘 회의 요약을 기다리는 초안. 여러 인스턴스가 같은 회의록을 함께 요청하지 않게 잠긴 행은 건너뛴다 */
    @Query(value = "select * from minutes where draft_status = 'WAITING_SOURCE' order by id limit :limit for update skip locked",
            nativeQuery = true)
    List<Minutes> lockWaitingForSource(int limit);

    /** 최근 확정 (REQ-026, 레거시) */
    List<Minutes> findByFolderIdInAndStatusOrderByConfirmedAtDescIdDesc(Collection<Long> folderIds, MinutesStatus status,
                                                                       Pageable pageable);

    /** 홈의 작성 중인 회의록 (REQ-149~150) */
    List<Minutes> findByWriterUserIdAndStatusOrderByMeetingStartAtAscIdAsc(Long writerUserId, MinutesStatus status);

    /** 아카이브 장소 필터의 선택지 (REQ-055) */
    @Query("select distinct m.location from Minutes m where m.folderId in :folderIds and m.location <> '' order by m.location")
    List<String> findLocationsByFolderIdIn(Collection<Long> folderIds);

    @Query("select m.id from Minutes m where m.folderId in :folderIds")
    List<Long> findIdsByFolderIdIn(Collection<Long> folderIds);

    @Modifying
    @Query("delete from Minutes m where m.id in :ids")
    void deleteAllByIdIn(Collection<Long> ids);
}
