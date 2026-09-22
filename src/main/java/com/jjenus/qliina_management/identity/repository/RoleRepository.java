package com.jjenus.qliina_management.identity.repository;

import com.jjenus.qliina_management.identity.model.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByName(String name);
    
    @Query("SELECT r FROM Role r WHERE r.businessId IS NULL OR r.businessId = :businessId ORDER BY r.type, r.name")
    List<Role> findAllAvailableByBusinessId(@Param("businessId") UUID businessId);
    
    @Query("SELECT r FROM Role r WHERE r.businessId IS NULL OR r.businessId = :businessId")
    Page<Role> findByBusinessId(@Param("businessId") UUID businessId, Pageable pageable);
    
    @Query("SELECT r FROM Role r WHERE r.businessId = :businessId AND r.type = 'CUSTOM'")
    Page<Role> findCustomRoles(@Param("businessId") UUID businessId, Pageable pageable);

    /** Enabled BUSINESS_ADMIN assignments for a business (used for the last-admin guard). */
    @Query("SELECT count(ur) FROM UserRole ur WHERE ur.businessId = :businessId "
            + "AND ur.role.name = 'BUSINESS_ADMIN' AND ur.user.enabled = true")
    long countActiveBusinessAdmins(@Param("businessId") UUID businessId);

    Boolean existsByName(String name);
}
