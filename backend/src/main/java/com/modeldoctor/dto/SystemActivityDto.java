package com.modeldoctor.dto;

import com.modeldoctor.domain.SeverityLevel;
import java.time.Instant;

public class SystemActivityDto {
    private String id;
    private String eventType;
    private String message;
    private SeverityLevel severity;
    private String component;
    private Instant timestamp;
    private Boolean isMockData;

    public SystemActivityDto() {}

    public SystemActivityDto(String id, String eventType, String message, SeverityLevel severity,
                             String component, Instant timestamp, Boolean isMockData) {
        this.id = id;
        this.eventType = eventType;
        this.message = message;
        this.severity = severity;
        this.component = component;
        this.timestamp = timestamp;
        this.isMockData = isMockData;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public SeverityLevel getSeverity() { return severity; }
    public void setSeverity(SeverityLevel severity) { this.severity = severity; }

    public String getComponent() { return component; }
    public void setComponent(String component) { this.component = component; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public Boolean getIsMockData() { return isMockData; }
    public void setIsMockData(Boolean isMockData) { this.isMockData = isMockData; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String id;
        private String eventType;
        private String message;
        private SeverityLevel severity;
        private String component;
        private Instant timestamp;
        private Boolean isMockData;

        public Builder id(String id) { this.id = id; return this; }
        public Builder eventType(String eventType) { this.eventType = eventType; return this; }
        public Builder message(String message) { this.message = message; return this; }
        public Builder severity(SeverityLevel severity) { this.severity = severity; return this; }
        public Builder component(String component) { this.component = component; return this; }
        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
        public Builder isMockData(Boolean isMockData) { this.isMockData = isMockData; return this; }

        public SystemActivityDto build() {
            return new SystemActivityDto(id, eventType, message, severity, component, timestamp, isMockData);
        }
    }
}
