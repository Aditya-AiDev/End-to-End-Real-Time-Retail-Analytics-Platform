package com.apex.storeintelligence;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * StoreIntelligenceApplication
 * ==============================
 * WHY Spring Boot: Auto-configuration for REST + JPA + WebSocket + Validation.
 * WHY @EnableScheduling: AnomalyService runs a 60-second background scan
 *   to detect queue spikes / conversion drops — no external scheduler needed.
 *
 * HOW TO RUN:
 *   cd backend && mvn spring-boot:run
 *   API available at http://localhost:8080
 *   H2 console at  http://localhost:8080/h2-console  (JDBC URL: jdbc:h2:mem:storedb)
 */
@SpringBootApplication
@EnableScheduling
public class StoreIntelligenceApplication {
    public static void main(String[] args) {
        SpringApplication.run(StoreIntelligenceApplication.class, args);
    }
}
