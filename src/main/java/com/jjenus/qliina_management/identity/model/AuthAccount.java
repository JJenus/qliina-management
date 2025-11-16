package com.jjenus.qliina_management.identity.model;

import com.jjenus.qliina_management.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "auth_credentials")
@Getter
@Setter
public class AuthAccount extends BaseEntity {
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @Column(name = "failed_attempts")
    private Integer failedAttempts = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "password_last_changed")
    private LocalDateTime passwordLastChanged;

    @Column(name = "totp_secret")
    private String totpSecret;

    @Column(name = "totp_enabled")
    private Boolean totpEnabled = false;
}
