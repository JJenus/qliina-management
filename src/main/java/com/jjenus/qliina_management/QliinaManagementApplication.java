package com.jjenus.qliina_management;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing  // ← ADD THIS
public class QliinaManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(QliinaManagementApplication.class, args);
    }

}