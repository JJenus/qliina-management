// ./src/main/java/com/jjenus/qliina_management/order/dto/CreateDiscrepancyRequest.java
package com.jjenus.qliina_management.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDiscrepancyRequest {

    @NotNull(message = "Order is required")
    private UUID orderId;

    /** Optional item the discrepancy is about. */
    private UUID orderItemId;

    /** CODE_MISMATCH | COUNT_MISMATCH | DAMAGED | MISSING_ITEM | OTHER */
    @NotBlank(message = "Type is required")
    private String type;

    @NotBlank(message = "Description is required")
    @Size(max = 2000, message = "Description must be at most 2000 characters")
    private String description;
}
