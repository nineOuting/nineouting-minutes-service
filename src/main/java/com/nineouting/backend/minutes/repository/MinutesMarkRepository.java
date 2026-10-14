package com.nineouting.backend.minutes.repository;

import com.nineouting.backend.minutes.domain.MinutesMark;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MinutesMarkRepository extends JpaRepository<MinutesMark, Long> {

    Optional<MinutesMark> findByMinutesIdAndUserId(Long minutesId, Long userId);

    List<MinutesMark> findByUserIdAndMinutesIdIn(Long userId, Collection<Long> minutesIds);

    @Modifying
    @Query("delete from MinutesMark m where m.minutesId in :minutesIds")
    void deleteByMinutesIdIn(Collection<Long> minutesIds);
}
