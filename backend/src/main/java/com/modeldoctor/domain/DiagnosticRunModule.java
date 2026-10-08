package com.modeldoctor.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "diagnostic_run_modules", uniqueConstraints = {
    @UniqueConstraint(name = "uk_run_module", columnNames = {"run_id", "module"})
})
public class DiagnosticRunModule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    @JsonIgnore
    private DiagnosticRun run;

    @Enumerated(EnumType.STRING)
    @Column(name = "module", length = 64, nullable = false)
    private DiagnosticModule module;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private ModuleExecutionStatus status;

    @Column(name = "status_message", columnDefinition = "TEXT")
    private String statusMessage;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "execution_duration_ms")
    private Long executionDurationMs;

    public DiagnosticRunModule() {}

    public DiagnosticRunModule(DiagnosticModule module, ModuleExecutionStatus status) {
        this.module = module;
        this.status = status;
    }

    public DiagnosticRunModule(Long id, DiagnosticRun run, DiagnosticModule module, ModuleExecutionStatus status,
                               String statusMessage, Instant startedAt, Instant completedAt, Long executionDurationMs) {
        this.id = id;
        this.run = run;
        this.module = module;
        this.status = status;
        this.statusMessage = statusMessage;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.executionDurationMs = executionDurationMs;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public DiagnosticRun getRun() { return run; }
    public void setRun(DiagnosticRun run) { this.run = run; }

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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long id;
        private DiagnosticRun run;
        private DiagnosticModule module;
        private ModuleExecutionStatus status;
        private String statusMessage;
        private Instant startedAt;
        private Instant completedAt;
        private Long executionDurationMs;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder run(DiagnosticRun run) { this.run = run; return this; }
        public Builder module(DiagnosticModule module) { this.module = module; return this; }
        public Builder status(ModuleExecutionStatus status) { this.status = status; return this; }
        public Builder statusMessage(String statusMessage) { this.statusMessage = statusMessage; return this; }
        public Builder startedAt(Instant startedAt) { this.startedAt = startedAt; return this; }
        public Builder completedAt(Instant completedAt) { this.completedAt = completedAt; return this; }
        public Builder executionDurationMs(Long executionDurationMs) { this.executionDurationMs = executionDurationMs; return this; }

        public DiagnosticRunModule build() {
            return new DiagnosticRunModule(id, run, module, status, statusMessage, startedAt, completedAt, executionDurationMs);
        }
    }
}
