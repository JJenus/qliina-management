package com.jjenus.qliina_management.audit.repository;

import com.jjenus.qliina_management.audit.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {
    
    Page<AuditLog> findByBusinessId(UUID businessId, Pageable pageable);
    
    Page<AuditLog> findByUserId(UUID userId, Pageable pageable);
    
    Page<AuditLog> findByEntityTypeAndEntityId(String entityType, UUID entityId, Pageable pageable);
    
    @Query("SELECT a FROM AuditLog a WHERE a.businessId = :businessId AND a.entityType = :entityType AND a.timestamp < :timestamp")
List<AuditLog> findByBusinessIdAndEntityTypeAndTimestampBefore(
    @Param("businessId") UUID businessId,
    @Param("entityType") String entityType,
    @Param("timestamp") LocalDateTime timestamp);
    
    @Query("SELECT a FROM AuditLog a WHERE a.businessId = :businessId AND " +
           "(:userId IS NULL OR a.userId = :userId) AND " +
           "(:entityType IS NULL OR a.entityType = :entityType) AND " +
           "(:entityId IS NULL OR a.entityId = :entityId) AND " +
           "(:action IS NULL OR a.action LIKE %:action%) AND " +
           "(:category IS NULL OR a.category = :category) AND " +
           "(:severity IS NULL OR a.severity = :severity) AND " +
           "(:fromDate IS NULL OR a.timestamp >= :fromDate) AND " +
           "(:toDate IS NULL OR a.timestamp <= :toDate) AND " +
           "(:ipAddress IS NULL OR a.ipAddress = :ipAddress)")
    Page<AuditLog> searchAuditLogs(
            @Param("businessId") UUID businessId,
            @Param("userId") UUID userId,
            @Param("entityType") String entityType,
            @Param("entityId") UUID entityId,
            @Param("action") String action,
            @Param("category") String category,
            @Param("severity") AuditLog.AuditSeverity severity,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            @Param("ipAddress") String ipAddress,
            Pageable pageable);
    
    @Query("SELECT a FROM AuditLog a WHERE a.entityType = :entityType AND a.entityId = :entityId " +
           "ORDER BY a.timestamp DESC")
    List<AuditLog> findEntityHistory(@Param("entityType") String entityType, 
                                      @Param("entityId") UUID entityId);
    
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.businessId = :businessId " +
           "AND a.timestamp >= :since")
    long countRecentActivity(@Param("businessId") UUID businessId, 
                              @Param("since") LocalDateTime since);
    
    @Query("SELECT a.action, COUNT(a) FROM AuditLog a WHERE a.businessId = :businessId " +
           "AND a.timestamp BETWEEN :startDate AND :endDate GROUP BY a.action")
    List<Object[]> getActivitySummary(@Param("businessId") UUID businessId,
                                       @Param("startDate") LocalDateTime startDate,
                                       @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT a FROM AuditLog a WHERE a.retentionUntil < :cutoff AND a.retentionUntil IS NOT NULL")
    List<AuditLog> findExpiredLogs(@Param("cutoff") LocalDateTime cutoff);
    
    @Modifying
    @Query("DELETE FROM AuditLog a WHERE a.id IN :ids")
    void deleteByIds(@Param("ids") List<UUID> ids);
    
    
    @Query("SELECT a FROM AuditLog a WHERE a.businessId = :businessId AND a.timestamp BETWEEN :startDate AND :endDate")
List<AuditLog> findByBusinessIdAndTimestampBetween(@Param("businessId") UUID businessId, 
                                                    @Param("startDate") LocalDateTime startDate, 
                                                    @Param("endDate") LocalDateTime endDate);

@Query("SELECT a FROM AuditLog a WHERE a.entityType = :entityType AND a.entityId = :entityId")
List<AuditLog> findByEntityTypeAndEntityId(@Param("entityType") String entityType, 
                                            @Param("entityId") UUID entityId);
}
