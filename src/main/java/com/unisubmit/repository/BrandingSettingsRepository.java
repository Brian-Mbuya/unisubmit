package com.unisubmit.repository;

import com.unisubmit.domain.BrandingSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BrandingSettingsRepository extends JpaRepository<BrandingSettings, Long> {
}
