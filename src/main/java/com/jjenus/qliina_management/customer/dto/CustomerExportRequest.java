package com.jjenus.qliina_management.customer.dto;

import lombok.Data;

import java.util.List;

@Data
public class CustomerExportRequest {
    private String format;
    private List<String> fields;
    private CustomerFilter filter;
}
