package com.jjenus.qliina_management.audit.repository;

import com.jjenus.qliina_management.audit.model.DataSubjectRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DataSubjectRequestRepository extends JpaRepository<DataSubjectRequest, UUID> {
    
    Optional<DataSubjectRequest> findByRequestNumber(String requestNumber);
    
    Page<DataSubjectRequest> findByCustomerId(UUID customerId, Pageable pageable);
    
    @Query("SELECT d FROM DataSubjectRequest d WHERE d.businessId = :businessId AND " +
           "(:customerId IS NULL OR d.customerId = :customerId) AND " +
           "(:type IS NULL OR d.requestType = :type) AND " +
           "(:status IS NULL OR d.status = :status) AND " +
           "(:fromDate IS NULL OR d.submittedAt >= :fromDate) AND " +
           "(:toDate IS NULL OR d.submittedAt <= :toDate)")
    Page<DataSubjectRequest> searchRequests(@Param("businessId") UUID businessId,
                                             @Param("customerId") UUID customerId,
                                             @Param("type") DataSubjectRequest.RequestType type,
                                             @Param("status") DataSubjectRequest.RequestStatus status,
                                             @Param("fromDate") LocalDateTime fromDate,
                                             @Param("toDate") LocalDateTime toDate,
                                             Pageable pageable);
    
    @Query("SELECT d FROM DataSubjectRequest d WHERE d.dueDate < :now AND d.status = 'IN_PROGRESS'")
    List<DataSubjectRequest> findOverdueRequests(@Param("now") LocalDateTime now);
    
    long countByBusinessIdAndStatus(UUID businessId, DataSubjectRequest.RequestStatus status);
}
