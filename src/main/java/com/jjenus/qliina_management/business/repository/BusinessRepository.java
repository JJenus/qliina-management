package com.jjenus.qliina_management.business.repository;

import com.jjenus.qliina_management.business.model.Business;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Business entities.
 *
 * Used by BusinessService for CRUD and by AuthService during
 * the open-registration flow and the login business-status check.
 */
@Repository
public interface BusinessRepository extends JpaRepository<Business, UUID> {

    Optional<Business> findBySlug(String slug);

    boolean existsBySlug(String slug);

    /** Filter by status — used by the Superadmin businesses overview. */
    Page<Business> findByStatus(Business.Status status, Pageable pageable);

    /** Case-insensitive partial name search — used by the Superadmin search bar. */
    @Query("SELECT b FROM Business b WHERE LOWER(b.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    Page<Business> searchByName(@Param("name") String name, Pageable pageable);
}
