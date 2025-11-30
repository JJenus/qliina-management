package com.jjenus.qliina_management.customer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportResultDTO {
    private Integer totalRows;
    private Integer imported;
    private Integer updated;
    private Integer failed;
    private List<ErrorDTO> errors;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorDTO {
        private Integer row;
        private String message;
    }
}
