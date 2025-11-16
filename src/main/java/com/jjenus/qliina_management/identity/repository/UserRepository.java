package com.jjenus.qliina_management.identity.repository;

import com.jjenus.qliina_management.identity.model.User;
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
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsername(String username);
    
    Optional<User> findByEmail(String email);
    
    Optional<User> findByPhone(String phone);
    
    @Query("SELECT u FROM User u WHERE u.businessId = :businessId")
    Page<User> findByBusinessId(@Param("businessId") UUID businessId, Pageable pageable);
    
    @Query("SELECT u FROM User u WHERE u.businessId = :businessId AND " +
           "(LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "u.phone LIKE CONCAT('%', :search, '%') OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<User> searchUsers(@Param("businessId") UUID businessId, 
                          @Param("search") String search, 
                          Pageable pageable);
    
    @Query("SELECT u FROM User u JOIN u.shops s WHERE s.id = :shopId")
    Page<User> findByShopId(@Param("shopId") UUID shopId, Pageable pageable);
    
    Boolean existsByUsername(String username);
    
    Boolean existsByEmail(String email);
    
    Boolean existsByPhone(String phone);
    
    // Add to UserRepository.java:
    @Query("SELECT u.id FROM User u WHERE u.businessId = :businessId")
    List<UUID> findUserIdsByBusinessId(@Param("businessId") UUID businessId);

    @Query("SELECT u FROM User u JOIN u.roles ur JOIN ur.role r " +
            "WHERE ur.businessId = :businessId AND r.name IN :roles")
    List<User> findByBusinessIdAndRoles(@Param("businessId") UUID businessId, @Param("roles") List<String> roles);

    @Query("SELECT COUNT(u) FROM User u WHERE u.businessId = :businessId AND u.enabled = true")
    long countByBusinessIdAndEnabledTrue(@Param("businessId") UUID businessId);
}
