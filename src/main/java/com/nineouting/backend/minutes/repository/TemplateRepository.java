package com.nineouting.backend.minutes.repository;

import com.nineouting.backend.minutes.domain.Template;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TemplateRepository extends JpaRepository<Template, Long> {

    /** 기본 양식이 먼저, 그다음 내 양식 (만든 순) */
    @Query("select t from Template t where t.ownerUserId is null or t.ownerUserId = :userId order by t.ownerUserId nulls first, t.id")
    List<Template> findUsableBy(Long userId);
}
