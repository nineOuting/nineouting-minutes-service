package com.nineouting.backend.minutes.repository;

import com.nineouting.backend.minutes.domain.MinutesReview;
import com.nineouting.backend.minutes.domain.ReviewStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MinutesReviewRepository extends JpaRepository<MinutesReview, Long> {

    List<MinutesReview> findByMinutesIdOrderById(Long minutesId);

    Optional<MinutesReview> findByMinutesIdAndUserId(Long minutesId, Long userId);

    List<MinutesReview> findByUserIdAndStatusIn(Long userId, Collection<ReviewStatus> statuses);

    @Modifying
    @Query("delete from MinutesReview r where r.minutesId in :minutesIds")
    void deleteByMinutesIdIn(Collection<Long> minutesIds);
}
