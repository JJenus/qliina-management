package com.jjenus.qliina_management.customer.dto;

import lombok.Data;

@Data
public class CustomerSearchRequest {
    private String query;
    private int page = 0;
    private int size = 20;
    private String sort = "createdAt,desc";
}
