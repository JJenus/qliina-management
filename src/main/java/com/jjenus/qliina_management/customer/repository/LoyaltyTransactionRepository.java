
package com.jjenus.qliina_management.customer.repository;

import com.jjenus.qliina_management.customer.model.LoyaltyTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LoyaltyTransactionRepository extends JpaRepository<LoyaltyTransaction, UUID> {
    
    Page<LoyaltyTransaction> findByCustomerId(UUID customerId, Pageable pageable);
    
    @Query("SELECT SUM(lt.points) FROM LoyaltyTransaction lt WHERE lt.customer.id = :customerId")
    Optional<Integer> sumPointsByCustomerId(@Param("customerId") UUID customerId);
}