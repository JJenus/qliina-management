package com.jjenus.qliina_management.identity.repository;

import com.jjenus.qliina_management.identity.model.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SystemSettingRepository extends JpaRepository<SystemSetting, UUID> {

    Optional<SystemSetting> findBySettingKey(String settingKey);
}
