package com.modeldoctor.dto;

import java.time.Instant;
import java.util.Map;

public class HealthResponseDto {
    private String status;
    private String service;
    private String version;
    private Instant timestamp;
    private Map<String, String> components;

    public HealthResponseDto() {}

    public HealthResponseDto(String status, String service, String version, Instant timestamp, Map<String, String> components) {
        this.status = status;
        this.service = service;
        this.version = version;
        this.timestamp = timestamp;
        this.components = components;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getService() { return service; }
    public void setService(String service) { this.service = service; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public Map<String, String> getComponents() { return components; }
    public void setComponents(Map<String, String> components) { this.components = components; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String status;
        private String service;
        private String version;
        private Instant timestamp;
        private Map<String, String> components;

        public Builder status(String status) { this.status = status; return this; }
        public Builder service(String service) { this.service = service; return this; }
        public Builder version(String version) { this.version = version; return this; }
        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
        public Builder components(Map<String, String> components) { this.components = components; return this; }

        public HealthResponseDto build() {
            return new HealthResponseDto(status, service, version, timestamp, components);
        }
    }
}
