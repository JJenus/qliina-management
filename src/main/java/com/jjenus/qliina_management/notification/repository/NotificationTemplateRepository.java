package com.jjenus.qliina_management.notification.repository;

import com.jjenus.qliina_management.notification.model.NotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {
    
    Optional<NotificationTemplate> findByName(String name);
    
    @Query("SELECT nt FROM NotificationTemplate nt WHERE nt.businessId IS NULL OR nt.businessId = :businessId")
    List<NotificationTemplate> findByBusinessId(@Param("businessId") UUID businessId);
    
    // Use fully qualified class names
    @Query("SELECT nt FROM NotificationTemplate nt WHERE (nt.businessId IS NULL OR nt.businessId = :businessId) " +
           "AND nt.type = :type AND nt.channel = :channel AND nt.isActive = true")
    Optional<NotificationTemplate> findActiveTemplate(@Param("businessId") UUID businessId,
                                                       @Param("type") com.jjenus.qliina_management.notification.model.Notification.NotificationType type,
                                                       @Param("channel") com.jjenus.qliina_management.notification.model.Notification.NotificationChannel channel);
    
    List<NotificationTemplate> findByIsActiveTrue();
}