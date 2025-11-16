package com.jjenus.qliina_management.identity.repository;

import com.jjenus.qliina_management.identity.model.AuthAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuthAccountRepository extends JpaRepository<AuthAccount, UUID> {
    Optional<AuthAccount> findByUserId(UUID userId);
    
    Optional<AuthAccount> findByUserUsername(String username);
}
