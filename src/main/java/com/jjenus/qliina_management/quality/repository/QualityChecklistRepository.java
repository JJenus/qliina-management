package com.jjenus.qliina_management.quality.repository;

import com.jjenus.qliina_management.quality.model.QualityChecklist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface QualityChecklistRepository extends JpaRepository<QualityChecklist, UUID> {
    
    List<QualityChecklist> findByBusinessIdAndIsActiveTrue(UUID businessId);
    
    @Query("SELECT qc FROM QualityChecklist qc WHERE qc.businessId = :businessId " +
           "AND (qc.serviceTypeId = :serviceTypeId OR qc.serviceTypeId IS NULL)")
    List<QualityChecklist> findByBusinessIdAndServiceType(@Param("businessId") UUID businessId,
                                                          @Param("serviceTypeId") UUID serviceTypeId);
}
