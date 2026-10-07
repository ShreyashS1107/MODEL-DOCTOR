package com.modeldoctor.dto;

import java.time.Instant;
import java.util.Map;

public class SystemHealthResponseDto {
    private String status; // UP, DEGRADED, DOWN
    private Map<String, Object> components;
    private Instant timestamp;

    public SystemHealthResponseDto() {}

    public SystemHealthResponseDto(String status, Map<String, Object> components, Instant timestamp) {
        this.status = status;
        this.components = components;
        this.timestamp = timestamp;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Map<String, Object> getComponents() { return components; }
    public void setComponents(Map<String, Object> components) { this.components = components; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
