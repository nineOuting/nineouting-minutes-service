package com.nineouting.backend.minutes.repository;

import com.nineouting.backend.minutes.domain.MinutesSection;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MinutesSectionRepository extends JpaRepository<MinutesSection, Long> {

    List<MinutesSection> findByMinutesIdOrderByPosition(Long minutesId);

    boolean existsByIdAndMinutesId(Long id, Long minutesId);

    @Modifying
    @Query("delete from MinutesSection s where s.minutesId in :minutesIds")
    void deleteByMinutesIdIn(Collection<Long> minutesIds);
}
