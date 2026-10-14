package com.nineouting.backend.minutes.repository;

import com.nineouting.backend.minutes.domain.MinutesComment;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MinutesCommentRepository extends JpaRepository<MinutesComment, Long> {

    List<MinutesComment> findByMinutesIdOrderById(Long minutesId);

    @Modifying
    @Query("delete from MinutesComment c where c.minutesId in :minutesIds")
    void deleteByMinutesIdIn(Collection<Long> minutesIds);
}
