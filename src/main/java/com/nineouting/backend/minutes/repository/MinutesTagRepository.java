package com.nineouting.backend.minutes.repository;

import com.nineouting.backend.minutes.domain.MinutesTag;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MinutesTagRepository extends JpaRepository<MinutesTag, Long> {

    List<MinutesTag> findByMinutesIdOrderById(Long minutesId);

    List<MinutesTag> findByMinutesIdIn(Collection<Long> minutesIds);

    @Modifying(flushAutomatically = true)
    @Query("delete from MinutesTag t where t.minutesId = :minutesId")
    void deleteByMinutesId(Long minutesId);

    @Modifying
    @Query("delete from MinutesTag t where t.minutesId in :minutesIds")
    void deleteByMinutesIdIn(Collection<Long> minutesIds);
}
