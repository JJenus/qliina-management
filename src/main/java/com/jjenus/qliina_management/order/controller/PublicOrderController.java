package com.jjenus.qliina_management.order.controller;

import com.jjenus.qliina_management.order.dto.PublicOrderTrackDTO;
import com.jjenus.qliina_management.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Unauthenticated public endpoints — intentionally no auth annotations.
 * Matches the "/api/v1/public/**" permitAll rule in SecurityConfig.
 */
@RestController
@RequestMapping("/api/v1/public/orders")
@RequiredArgsConstructor
public class PublicOrderController {

    private final OrderService orderService;

    /**
     * Returns publicly-safe tracking information for a given tracking number.
     * No authentication required; safe to call from a customer-facing page.
     */
    @GetMapping("/track/{trackingNumber}")
    public ResponseEntity<PublicOrderTrackDTO> trackOrder(
            @PathVariable String trackingNumber) {
        return ResponseEntity.ok(orderService.getPublicTrackingInfo(trackingNumber));
    }
}
