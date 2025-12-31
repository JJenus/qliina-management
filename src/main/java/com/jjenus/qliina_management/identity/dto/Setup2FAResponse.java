package com.jjenus.qliina_management.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Setup2FAResponse {
    private String secret;
    private String qrCodeUrl;
    private List<String> backupCodes;
}
