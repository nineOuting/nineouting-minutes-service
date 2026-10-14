package com.nineouting.backend.minutes.repository;

import com.nineouting.backend.minutes.domain.MinutesRevision;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MinutesRevisionRepository extends JpaRepository<MinutesRevision, Long> {

    List<MinutesRevision> findByMinutesIdOrderByVersionDesc(Long minutesId);

    @Modifying
    @Query("delete from MinutesRevision r where r.minutesId in :minutesIds")
    void deleteByMinutesIdIn(Collection<Long> minutesIds);
}
