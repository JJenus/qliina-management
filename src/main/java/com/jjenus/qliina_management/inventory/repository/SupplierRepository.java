package com.jjenus.qliina_management.inventory.repository;

import com.jjenus.qliina_management.inventory.model.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, UUID> {
    
    @Query("SELECT s FROM Supplier s WHERE s.businessId = :businessId AND " +
           "(LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(s.contactPerson) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "s.email LIKE CONCAT('%', :search, '%') OR " +
           "s.phone LIKE CONCAT('%', :search, '%'))")
    Page<Supplier> searchSuppliers(@Param("businessId") UUID businessId,
                                    @Param("search") String search,
                                    Pageable pageable);
    
    @Query("SELECT s FROM Supplier s WHERE s.businessId = :businessId AND s.isActive = true")
    Page<Supplier> findActiveSuppliers(@Param("businessId") UUID businessId, Pageable pageable);
    
    @Query("SELECT s FROM Supplier s WHERE :category MEMBER OF s.categories")
    List<Supplier> findByCategory(@Param("category") String category);
    
    @Query("SELECT s FROM Supplier s WHERE s.businessId = :businessId ORDER BY s.rating DESC NULLS LAST")
    Page<Supplier> findTopRatedSuppliers(@Param("businessId") UUID businessId, Pageable pageable);
}
