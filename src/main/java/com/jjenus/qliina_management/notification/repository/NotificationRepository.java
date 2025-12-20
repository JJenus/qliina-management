package com.jjenus.qliina_management.notification.repository;

import com.jjenus.qliina_management.notification.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    
    Page<Notification> findByUserId(UUID userId, Pageable pageable);
    
    @Query("SELECT n FROM Notification n WHERE n.userId = :userId AND n.status = 'PENDING'")
    List<Notification> findPendingByUserId(@Param("userId") UUID userId);
    
    @Query("SELECT n FROM Notification n WHERE n.status = 'PENDING' AND n.scheduledFor <= :now")
    List<Notification> findDueNotifications(@Param("now") LocalDateTime now);
    
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.userId = :userId AND n.status = 'DELIVERED'")
    long countUnreadByUserId(@Param("userId") UUID userId);
    
    @Query("SELECT n FROM Notification n WHERE n.businessId = :businessId AND " +
           "(:userId IS NULL OR n.userId = :userId) AND " +
           "(:type IS NULL OR n.type = :type) AND " +
           "(:status IS NULL OR n.status = :status) AND " +
           "(:fromDate IS NULL OR n.createdAt >= :fromDate) AND " +
           "(:toDate IS NULL OR n.createdAt <= :toDate)")
    Page<Notification> findByFilters(@Param("businessId") UUID businessId,
                                       @Param("userId") UUID userId,
                                       @Param("type") Notification.NotificationType type,
                                       @Param("status") Notification.NotificationStatus status,
                                       @Param("fromDate") LocalDateTime fromDate,
                                       @Param("toDate") LocalDateTime toDate,
                                       Pageable pageable);
    
    @Modifying
    @Query("UPDATE Notification n SET n.status = 'READ', n.readAt = :now WHERE n.id = :id")
    void markAsRead(@Param("id") UUID id, @Param("now") LocalDateTime now);
    
    @Modifying
    @Query("UPDATE Notification n SET n.status = 'READ', n.readAt = :now WHERE n.userId = :userId")
    void markAllAsRead(@Param("userId") UUID userId, @Param("now") LocalDateTime now);
    
    @Query("SELECT n FROM Notification n WHERE n.businessId = :businessId AND n.type = 'ORDER_STATUS' " +
           "AND JSON_EXTRACT(n.data, '$.orderId') = :orderId")
    List<Notification> findByOrderId(@Param("businessId") UUID businessId, @Param("orderId") String orderId);
}