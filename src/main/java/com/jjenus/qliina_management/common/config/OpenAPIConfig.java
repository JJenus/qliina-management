package com.jjenus.qliina_management.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAPIConfig {
    
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Qliina Laundry Management System API")
                .description("Complete API for managing laundry businesses, orders, customers, and payments")
                .version("1.0.0")
                .contact(new Contact()
                    .name("Jjenus")
                    .email("support@jjenus.com")
                    .url("https://jjenus.vercel.app"))
                .license(new License()
                    .name("Private")
                    .url("https://jjenus.github.io")))
            .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
            .components(new Components()
                .addSecuritySchemes("Bearer Authentication", createSecurityScheme()));
    }
    
    private SecurityScheme createSecurityScheme() {
        return new SecurityScheme()
            .name("Bearer Authentication")
            .type(SecurityScheme.Type.HTTP)
            .scheme("bearer")
            .bearerFormat("JWT")
            .description("Please enter JWT token with Bearer prefix");
    }
}
