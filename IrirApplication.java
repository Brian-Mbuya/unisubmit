package com.chuka.irir;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * IRIR — Intelligent Research & Innovation Repository
 *
 * Main entry point for the Spring Boot application.
 * Manages the lifecycle of final-year CS student projects at Chuka University,
 * including submission, similarity detection, supervisor review, and analytics.
 * 
 * Added @EnableScheduling to allow scheduled tasks (e.g., password reset token cleanup).
 * 
 * Author: IRIR Development Team
 * Version: 1.0.0
 */
@SpringBootApplication
@EnableScheduling
public class IrirApplication {

    public static void main(String[] args) {
        SpringApplication.run(IrirApplication.class, args);
    }
}