package com.nineouting.backend.minutes.repository;

import com.nineouting.backend.minutes.domain.TemplateWorkspaceShare;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TemplateWorkspaceShareRepository extends JpaRepository<TemplateWorkspaceShare, Long> {

    boolean existsByTemplateIdAndWorkspaceId(Long templateId, Long workspaceId);

    List<TemplateWorkspaceShare> findByWorkspaceIdIn(Collection<Long> workspaceIds);

    @Modifying
    @Query("delete from TemplateWorkspaceShare s where s.templateId = :templateId and s.workspaceId = :workspaceId")
    void deleteOne(Long templateId, Long workspaceId);

    @Modifying
    @Query("delete from TemplateWorkspaceShare s where s.templateId = :templateId")
    void deleteByTemplateId(Long templateId);
}
