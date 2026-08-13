package com.jjenus.qliina_management.notification.repository;

import com.jjenus.qliina_management.notification.model.NotificationDeliveryEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationDeliveryEventRepository extends JpaRepository<NotificationDeliveryEvent, UUID> {

    List<NotificationDeliveryEvent> findAllByDeliveryIdOrderByOccurredAtAsc(UUID deliveryId);

    List<NotificationDeliveryEvent> findAllByDeliveryId(UUID deliveryId);
}