package com.nineouting.backend.minutes.repository;

import com.nineouting.backend.minutes.domain.TemplateItem;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TemplateItemRepository extends JpaRepository<TemplateItem, Long> {

    List<TemplateItem> findByTemplateIdOrderByPosition(Long templateId);

    List<TemplateItem> findByTemplateIdInOrderByPosition(Collection<Long> templateIds);

    @Modifying(flushAutomatically = true)
    @Query("delete from TemplateItem i where i.templateId = :templateId")
    void deleteByTemplateId(Long templateId);
}
