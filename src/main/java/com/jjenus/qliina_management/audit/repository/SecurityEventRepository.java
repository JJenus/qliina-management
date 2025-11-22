package com.jjenus.qliina_management.audit.repository;

import com.jjenus.qliina_management.audit.model.AuditLog;
import com.jjenus.qliina_management.audit.model.SecurityEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface SecurityEventRepository extends JpaRepository<SecurityEvent, UUID> {
    
    Page<SecurityEvent> findByBusinessId(UUID businessId, Pageable pageable);
    
    Page<SecurityEvent> findByUserId(UUID userId, Pageable pageable);
    
    @Query("SELECT s FROM SecurityEvent s WHERE s.businessId = :businessId AND " +
           "(:userId IS NULL OR s.userId = :userId) AND " +
           "(:eventType IS NULL OR s.eventType = :eventType) AND " +
           "(:severity IS NULL OR s.severity = :severity) AND " +
           "(:fromDate IS NULL OR s.timestamp >= :fromDate) AND " +
           "(:toDate IS NULL OR s.timestamp <= :toDate) AND " +
           "(:ipAddress IS NULL OR s.ipAddress = :ipAddress)")
    Page<SecurityEvent> searchEvents(@Param("businessId") UUID businessId,
                                      @Param("userId") UUID userId,
                                      @Param("eventType") SecurityEvent.SecurityEventType eventType,
                                      @Param("severity") AuditLog.AuditSeverity severity,
                                      @Param("fromDate") LocalDateTime fromDate,
                                      @Param("toDate") LocalDateTime toDate,
                                      @Param("ipAddress") String ipAddress,
                                      Pageable pageable);
    
    @Query("SELECT s FROM SecurityEvent s WHERE s.eventType = 'LOGIN_FAILED' " +
           "AND s.ipAddress = :ipAddress AND s.timestamp >= :since")
    List<SecurityEvent> findFailedLoginsFromIp(@Param("ipAddress") String ipAddress,
                                                @Param("since") LocalDateTime since);
    
    @Query("SELECT COUNT(s) FROM SecurityEvent s WHERE s.eventType = 'LOGIN_FAILED' " +
           "AND s.username = :username AND s.timestamp >= :since")
    long countFailedLoginsForUser(@Param("username") String username,
                                   @Param("since") LocalDateTime since);
    
    @Query("SELECT s FROM SecurityEvent s WHERE s.blocked = true AND s.resolvedAt IS NULL")
    List<SecurityEvent> findActiveBlocks();
}
