package com.modeldoctor.controller;

import com.modeldoctor.dto.HealthResponseDto;
import com.modeldoctor.service.HealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Health & Telemetry", description = "Backend operational health and node telemetry")
public class HealthController {

    private final HealthService healthService;

    @Autowired
    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/health")
    @Operation(summary = "Backend Service Health Check", description = "Returns the operational status of the Model Doctor backend orchestrator.")
    public ResponseEntity<HealthResponseDto> getHealth() {
        return ResponseEntity.ok(healthService.getSystemHealth());
    }
}
