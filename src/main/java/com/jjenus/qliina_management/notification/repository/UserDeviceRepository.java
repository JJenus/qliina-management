package com.jjenus.qliina_management.notification.repository;

import com.jjenus.qliina_management.notification.model.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;

@Repository
public interface UserDeviceRepository extends JpaRepository<UserDevice, UUID> {
    
    List<UserDevice> findByUserIdAndIsActiveTrue(UUID userId);
    
    Optional<UserDevice> findByPushToken(String pushToken);
    
    @Query("SELECT ud FROM UserDevice ud WHERE ud.userId = :userId AND ud.deviceId = :deviceId")
    Optional<UserDevice> findByUserIdAndDeviceId(@Param("userId") UUID userId,
                                                   @Param("deviceId") String deviceId);
    
    @Modifying
    @Query("UPDATE UserDevice ud SET ud.isActive = false WHERE ud.userId = :userId")
    void deactivateAllForUser(@Param("userId") UUID userId);
    
    @Modifying
    @Query("DELETE FROM UserDevice ud WHERE ud.lastUsedAt < :date")
    void deleteInactiveDevices(@Param("date") LocalDateTime date);
}
