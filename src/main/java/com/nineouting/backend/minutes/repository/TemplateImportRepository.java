package com.nineouting.backend.minutes.repository;

import com.nineouting.backend.minutes.domain.TemplateImport;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TemplateImportRepository extends JpaRepository<TemplateImport, Long> {

    Optional<TemplateImport> findByIdAndUserId(Long id, Long userId);
}
