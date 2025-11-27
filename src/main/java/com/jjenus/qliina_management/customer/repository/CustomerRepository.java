package com.jjenus.qliina_management.customer.repository;

import com.jjenus.qliina_management.customer.model.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID>, JpaSpecificationExecutor<Customer> {
    
    Optional<Customer> findByBusinessIdAndPhone(UUID businessId, String phone);
    
    List<Customer> findByBusinessId(UUID businessId);
    
    @Query("SELECT c FROM Customer c WHERE c.businessId = :businessId AND " +
           "(LOWER(c.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(c.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "c.phone LIKE CONCAT('%', :search, '%') OR " +
           "LOWER(c.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Customer> searchCustomers(@Param("businessId") UUID businessId, 
                                   @Param("search") String search, 
                                   Pageable pageable);
    
    @Query("SELECT c FROM Customer c WHERE c.businessId = :businessId AND " +
           "c.rfmSegment = :segment")
    Page<Customer> findByBusinessIdAndRfmSegment(@Param("businessId") UUID businessId,
                                                 @Param("segment") String segment,
                                                 Pageable pageable);
    
    @Query("SELECT c FROM Customer c WHERE c.businessId = :businessId " +
           "ORDER BY c.totalSpent DESC")
    Page<Customer> findTopSpenders(@Param("businessId") UUID businessId, Pageable pageable);
    
    @Query("SELECT c FROM Customer c WHERE c.businessId = :businessId " +
           "AND c.loyaltyTier = :tier")
    List<Customer> findByLoyaltyTier(@Param("businessId") UUID businessId, @Param("tier") String tier);
    
    @Query("SELECT COUNT(c) FROM Customer c WHERE c.businessId = :businessId " +
           "AND c.createdAt BETWEEN :startDate AND :endDate")
    long countNewCustomers(@Param("businessId") UUID businessId,
                          @Param("startDate") LocalDateTime startDate,
                          @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT c FROM Customer c WHERE c.businessId = :businessId " +
           "AND c.lastOrderDate IS NOT NULL " +
           "AND c.lastOrderDate < :date")
    List<Customer> findInactiveCustomers(@Param("businessId") UUID businessId,
                                         @Param("date") LocalDateTime date);
}
