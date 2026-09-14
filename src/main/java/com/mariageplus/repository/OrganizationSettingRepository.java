package com.mariageplus.repository;

import com.mariageplus.entity.OrganizationSetting;
import org.springframework.data.jpa.repository.JpaRepository;

/** Réglages par organisation — la clé primaire est l'identifiant de l'organisation. */
public interface OrganizationSettingRepository extends JpaRepository<OrganizationSetting, Long> {
}
