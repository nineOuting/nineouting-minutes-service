package com.nineouting.backend.minutes.repository;

import com.nineouting.backend.minutes.domain.MinutesView;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MinutesViewRepository extends JpaRepository<MinutesView, Long> {

    Optional<MinutesView> findByUserIdAndMinutesId(Long userId, Long minutesId);

    List<MinutesView> findByUserIdOrderByViewedAtDesc(Long userId, Pageable pageable);

    @Modifying
    @Query("delete from MinutesView v where v.minutesId in :minutesIds")
    void deleteByMinutesIdIn(Collection<Long> minutesIds);
}
