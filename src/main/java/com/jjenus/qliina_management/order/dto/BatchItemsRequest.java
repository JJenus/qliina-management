// ./src/main/java/com/jjenus/qliina_management/order/dto/BatchItemsRequest.java
package com.jjenus.qliina_management.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class BatchItemsRequest {
    @NotEmpty
    private List<UUID> itemIds;

    @NotBlank
    private String action;
}
