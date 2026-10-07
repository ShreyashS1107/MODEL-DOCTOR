package com.modeldoctor.dto;

import com.modeldoctor.domain.DiagnosticModule;
import com.modeldoctor.domain.ModuleExecutionStatus;
import java.time.Instant;

public class ModuleStatusDto {
    private DiagnosticModule module;
    private ModuleExecutionStatus status;
    private String statusMessage;
    private Instant startedAt;
    private Instant completedAt;
    private Long executionDurationMs;

    public ModuleStatusDto() {}

    public ModuleStatusDto(DiagnosticModule module, ModuleExecutionStatus status, String statusMessage,
                           Instant startedAt, Instant completedAt, Long executionDurationMs) {
        this.module = module;
        this.status = status;
        this.statusMessage = statusMessage;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.executionDurationMs = executionDurationMs;
    }

    public DiagnosticModule getModule() { return module; }
    public void setModule(DiagnosticModule module) { this.module = module; }

    public ModuleExecutionStatus getStatus() { return status; }
    public void setStatus(ModuleExecutionStatus status) { this.status = status; }

    public String getStatusMessage() { return statusMessage; }
    public void setStatusMessage(String statusMessage) { this.statusMessage = statusMessage; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public Long getExecutionDurationMs() { return executionDurationMs; }
    public void setExecutionDurationMs(Long executionDurationMs) { this.executionDurationMs = executionDurationMs; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private DiagnosticModule module;
        private ModuleExecutionStatus status;
        private String statusMessage;
        private Instant startedAt;
        private Instant completedAt;
        private Long executionDurationMs;

        public Builder module(DiagnosticModule module) { this.module = module; return this; }
        public Builder status(ModuleExecutionStatus status) { this.status = status; return this; }
        public Builder statusMessage(String statusMessage) { this.statusMessage = statusMessage; return this; }
        public Builder startedAt(Instant startedAt) { this.startedAt = startedAt; return this; }
        public Builder completedAt(Instant completedAt) { this.completedAt = completedAt; return this; }
        public Builder executionDurationMs(Long executionDurationMs) { this.executionDurationMs = executionDurationMs; return this; }

        public ModuleStatusDto build() {
            return new ModuleStatusDto(module, status, statusMessage, startedAt, completedAt, executionDurationMs);
        }
    }
}
