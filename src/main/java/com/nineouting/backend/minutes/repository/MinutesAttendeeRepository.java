package com.nineouting.backend.minutes.repository;

import com.nineouting.backend.minutes.domain.MinutesAttendee;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MinutesAttendeeRepository extends JpaRepository<MinutesAttendee, Long> {

    List<MinutesAttendee> findByMinutesIdOrderById(Long minutesId);

    List<MinutesAttendee> findByMinutesIdInOrderById(Collection<Long> minutesIds);

    boolean existsByMinutesIdAndUserId(Long minutesId, Long userId);

    @Modifying
    @Query("delete from MinutesAttendee a where a.minutesId in :minutesIds")
    void deleteByMinutesIdIn(Collection<Long> minutesIds);
}
