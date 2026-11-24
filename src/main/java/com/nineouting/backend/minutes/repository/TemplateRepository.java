package com.nineouting.backend.minutes.repository;

import com.nineouting.backend.minutes.domain.Template;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TemplateRepository extends JpaRepository<Template, Long> {

    /** 기본 양식이 먼저, 그다음 내 양식 (만든 순) */
    @Query("select t from Template t where t.ownerUserId is null or t.ownerUserId = :userId order by t.ownerUserId nulls first, t.id")
    List<Template> findUsableBy(Long userId);

    Optional<Template> findByOwnerUserIdAndPersonalDefaultTrue(Long ownerUserId);

    /** 항목만 바꿔도 수정 시각이 바뀌게 한다 (이름, 설명, 배치가 그대로면 JPA가 양식 행을 고치지 않는다) */
    @Modifying(flushAutomatically = true)
    @Query("update Template t set t.updatedAt = :now where t.id = :id")
    void touch(Long id, LocalDateTime now);
}
