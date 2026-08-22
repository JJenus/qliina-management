// ./src/main/java/com/jjenus/qliina_management/inventory/dto/ReviewStockRequestRequest.java
package com.jjenus.qliina_management.inventory.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Managerial decision on a stock request.
 * action: APPROVE | REJECT | FULFILL
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewStockRequestRequest {

    @NotNull(message = "Action is required")
    private String action;

    private String note;
}
