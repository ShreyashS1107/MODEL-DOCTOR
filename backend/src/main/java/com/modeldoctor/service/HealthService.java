package com.modeldoctor.service;

import com.modeldoctor.dto.HealthResponseDto;
import com.modeldoctor.repository.DiagnosticRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class HealthService {

    private static final Logger log = LoggerFactory.getLogger(HealthService.class);

    @Value("${spring.application.name:model-doctor-backend}")
    private String applicationName;

    @Value("${modeldoctor.ml-engine.base-url:http://localhost:8000}")
    private String mlEngineBaseUrl;

    private final DiagnosticRunRepository runRepository;
    private final RestTemplate healthPingRestTemplate;

    public HealthService(DiagnosticRunRepository runRepository, RestTemplateBuilder restTemplateBuilder) {
        this.runRepository = runRepository;
        this.healthPingRestTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(2000))
                .setReadTimeout(Duration.ofMillis(2000))
                .build();
    }

    public HealthResponseDto getSystemHealth() {
        Map<String, String> components = new HashMap<>();
        boolean isAllUp = true;

        // 1. Database Reachability
        try {
            long runCount = runRepository.count();
            components.put("database", "UP (runs: " + runCount + ")");
        } catch (Exception e) {
            log.warn("Database health check failed: {}", e.getMessage());
            components.put("database", "DOWN: " + e.getMessage());
            isAllUp = false;
        }

        // 2. Python ML Engine Reachability
        try {
            String healthUrl = mlEngineBaseUrl + "/health";
            healthPingRestTemplate.getForObject(healthUrl, String.class);
            components.put("mlEngine", "UP");
        } catch (Exception e) {
            log.warn("ML Engine health check failed at {}: {}", mlEngineBaseUrl, e.getMessage());
            components.put("mlEngine", "DOWN");
            isAllUp = false;
        }

        components.put("apiGateway", "UP");
        components.put("storageBridge", "READY");

        return HealthResponseDto.builder()
                .status(isAllUp ? "UP" : "DEGRADED")
                .service(applicationName)
                .version("0.1.0-SNAPSHOT")
                .timestamp(Instant.now())
                .components(components)
                .build();
    }
}
