package com.jjenus.qliina_management.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SuccessResponse {
    private boolean success;
    private String message;
    private LocalDateTime timestamp;
    private Object data;
    
    public static SuccessResponse of(String message) {
        return SuccessResponse.builder()
            .success(true)
            .message(message)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    public static SuccessResponse of(String message, Object data) {
        return SuccessResponse.builder()
            .success(true)
            .message(message)
            .data(data)
            .timestamp(LocalDateTime.now())
            .build();
    }
}
