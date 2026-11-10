package com.nineouting.backend.minutes.repository;

import com.nineouting.backend.minutes.domain.TemplateFavorite;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TemplateFavoriteRepository extends JpaRepository<TemplateFavorite, Long> {

    List<TemplateFavorite> findByUserId(Long userId);

    boolean existsByTemplateIdAndUserId(Long templateId, Long userId);

    void deleteByTemplateIdAndUserId(Long templateId, Long userId);
}
