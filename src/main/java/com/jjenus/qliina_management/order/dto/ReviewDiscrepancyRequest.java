// ./src/main/java/com/jjenus/qliina_management/order/dto/ReviewDiscrepancyRequest.java
package com.jjenus.qliina_management.order.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Front desk / managerial update on a discrepancy.
 * action: ACKNOWLEDGE | RESOLVE | DISMISS
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewDiscrepancyRequest {

    @NotBlank(message = "Action is required")
    private String action;

    private String note;
}
