package com.jjenus.qliina_management.payment.repository;

import com.jjenus.qliina_management.payment.model.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    
    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);
    
    Page<Invoice> findByAccountId(UUID accountId, Pageable pageable);
    
    @Query("SELECT i FROM Invoice i WHERE i.businessId = :businessId " +
           "AND (:accountId IS NULL OR i.accountId = :accountId) " +
           "AND (:status IS NULL OR i.status = :status) " +
           "AND (cast(:fromDate as date) IS NULL OR i.periodEnd >= :fromDate) " +
           "AND (cast(:toDate as date) IS NULL OR i.periodStart <= :toDate)")
    Page<Invoice> findByFilters(@Param("businessId") UUID businessId,
                                @Param("accountId") UUID accountId,
                                @Param("status") String status,
                                @Param("fromDate") LocalDate fromDate,
                                @Param("toDate") LocalDate toDate,
                                Pageable pageable);
    
    List<Invoice> findByStatusAndDueDateBefore(String status, LocalDate date);
    
    @Query("SELECT COUNT(i) FROM Invoice i WHERE i.status = :status AND i.dueDate < :date")
long countByStatusAndDueDateBefore(@Param("status") String status, @Param("date") LocalDate date);
}
