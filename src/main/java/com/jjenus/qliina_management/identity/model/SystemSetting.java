package com.jjenus.qliina_management.identity.model;

import com.jjenus.qliina_management.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Platform-global key/value setting (maintenance mode, signup toggle,
 * default trial days, global feature-flag overrides with the "flag." prefix).
 * Distinct from per-tenant BusinessConfig.
 */
@Entity
@Table(name = "system_settings")
@Getter
@Setter
public class SystemSetting extends BaseEntity {

    @Column(name = "setting_key", nullable = false, unique = true, length = 100)
    private String settingKey;

    @Column(name = "setting_value", columnDefinition = "TEXT")
    private String settingValue;

    private String description;

    @Column(name = "updated_by_username")
    private String updatedByUsername;
}
