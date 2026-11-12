package com.nineouting.backend.minutes.repository;

import com.nineouting.backend.minutes.domain.Minutes;
import com.nineouting.backend.minutes.domain.MinutesStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
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
    List<Minutes> findByFolderIdInAndStatusAndTrashedAtIsNullOrderByConfirmedAtDescIdDesc(Collection<Long> folderIds,
                                                                                         MinutesStatus status,
                                                                                         Pageable pageable);

    /** 홈의 작성 중인 회의록 (REQ-149~150) */
    List<Minutes> findByWriterUserIdAndStatusAndTrashedAtIsNullOrderByMeetingStartAtAscIdAsc(Long writerUserId,
                                                                                            MinutesStatus status);

    /** 아카이브 장소 필터의 선택지 (REQ-055) */
    @Query("""
            select distinct m.location from Minutes m
            where m.folderId in :folderIds and m.location <> '' and m.trashedAt is null
            order by m.location""")
    List<String> findLocationsByFolderIdIn(Collection<Long> folderIds);

    @Query("select m.id from Minutes m where m.folderId in :folderIds")
    List<Long> findIdsByFolderIdIn(Collection<Long> folderIds);

    /** REQ-185 챗봇이 찾을 회의록: 폴더의 작성 완료 회의록, 쓰레기통 제외, 최근 회의부터 */
    @Query("""
            select m.id from Minutes m
            where m.folderId = :folderId and m.status = com.nineouting.backend.minutes.domain.MinutesStatus.CONFIRMED
              and m.trashedAt is null
            order by m.meetingStartAt desc, m.id desc""")
    List<Long> findConfirmedIdsByFolderId(Long folderId, Pageable pageable);

    /** 검색용 저장소에 보낼 차례인 회의록 하나. 여러 인스턴스가 같은 회의록을 보내지 않게 잠근다 */
    @Query(value = """
            select * from minutes
            where rag_status = 'PENDING' and (rag_next_attempt_at is null or rag_next_attempt_at <= :now)
            order by rag_next_attempt_at nulls first, id
            limit 1
            for update skip locked""", nativeQuery = true)
    Optional<Minutes> lockNextRagDue(LocalDateTime now);

    /** 접수됐는데 콜백이 오지 않은 회의록 하나 (상태 조회로 확인한다) */
    @Query(value = """
            select * from minutes
            where rag_status = 'ACCEPTED' and rag_sent_at <= :sentBefore
            order by rag_sent_at, id
            limit 1
            for update skip locked""", nativeQuery = true)
    Optional<Minutes> lockNextRagStale(LocalDateTime sentBefore);

    @Modifying
    @Query("delete from Minutes m where m.id in :ids")
    void deleteAllByIdIn(Collection<Long> ids);
}
