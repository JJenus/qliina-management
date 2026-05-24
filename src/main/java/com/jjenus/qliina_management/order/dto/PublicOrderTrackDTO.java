package com.jjenus.qliina_management.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Publicly-visible order tracking payload — contains no sensitive customer or
 * pricing data, safe to return from an unauthenticated endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicOrderTrackDTO {

    private String orderNumber;
    private String trackingNumber;
    private String status;
    private String priority;

    /** Shop name (not address or internal ID). */
    private String shopName;

    /** Business / brand name. */
    private String businessName;

    /** Customer first name only — personalised but not private. */
    private String customerFirstName;

    private LocalDateTime receivedAt;
    private LocalDateTime promisedDate;
    private LocalDateTime completedAt;

    /** Lightweight item rows — no prices. */
    private List<ItemRow> items;

    /** Last 5 timeline events — status + description only. */
    private List<TimelineRow> recentTimeline;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ItemRow {
        private String serviceType;
        private String garmentType;
        private Integer quantity;
        private String status;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TimelineRow {
        private String status;
        private String description;
        private LocalDateTime timestamp;
    }
}
