package com.jjenus.qliina_management.customer.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Data
public class CustomerImportRequest {
    private MultipartFile file;
    private Boolean updateExisting = false;
    private Map<String, String> mapping;
}
