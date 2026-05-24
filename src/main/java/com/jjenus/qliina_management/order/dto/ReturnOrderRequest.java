package com.jjenus.qliina_management.order.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * Request body for the POST /{orderId}/return endpoint.
 * Allows a manager to process a customer return after an order is COMPLETED.
 */
@Data
public class ReturnOrderRequest {

    /** Why the customer returned the order (required). */
    @NotBlank
    private String reason;

    /**
     * IDs of specific items being returned.
     * If null or empty, all items in the order are marked as returned.
     */
    private List<UUID> itemIds;

    /** Any additional notes provided by or about the customer. */
    private String customerNotes;

    /** True if the customer is requesting a refund. */
    private boolean refundRequested;
}
