package com.jjenus.qliina_management.notification.repository;

import com.jjenus.qliina_management.notification.model.NotificationLog;
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
public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {
    
    Page<NotificationLog> findByNotificationId(UUID notificationId, Pageable pageable);
    
    @Query("SELECT nl FROM NotificationLog nl WHERE nl.businessId = :businessId " +
       "AND nl.sentAt BETWEEN :startDate AND :endDate")
List<NotificationLog> findByBusinessIdAndDateRange(@Param("businessId") UUID businessId,
                                                   @Param("startDate") LocalDateTime startDate,
                                                   @Param("endDate") LocalDateTime endDate);
    
@Query("SELECT nl FROM NotificationLog nl WHERE nl.businessId = :businessId AND " +
       "(:channel IS NULL OR nl.channel = :channel) AND " +
       "(:status IS NULL OR nl.status = :status) AND " +
       "(:fromDate IS NULL OR nl.sentAt >= :fromDate) AND " +
       "(:toDate IS NULL OR nl.sentAt <= :toDate)")
Page<NotificationLog> findByFilters(@Param("businessId") UUID businessId,
                                     @Param("channel") com.jjenus.qliina_management.notification.model.Notification.NotificationChannel channel,
                                     @Param("status") NotificationLog.DeliveryStatus status,
                                     @Param("fromDate") LocalDateTime fromDate,
                                     @Param("toDate") LocalDateTime toDate,
                                     Pageable pageable);
    
    @Query("SELECT COUNT(nl) FROM NotificationLog nl WHERE nl.status = 'FAILED' " +
           "AND nl.createdAt >= :since")
    long countFailedSince(@Param("since") LocalDateTime since);
    
    @Query("SELECT nl.channel, COUNT(nl) FROM NotificationLog nl " +
           "WHERE nl.createdAt BETWEEN :startDate AND :endDate " +
           "GROUP BY nl.channel")
    List<Object[]> getDeliveryStats(@Param("startDate") LocalDateTime startDate,
                                     @Param("endDate") LocalDateTime endDate);
}
