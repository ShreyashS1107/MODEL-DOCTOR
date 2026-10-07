package com.modeldoctor.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "diagnostic_results", uniqueConstraints = {
    @UniqueConstraint(name = "uk_result_run_module", columnNames = {"run_id", "module"})
})
public class DiagnosticResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", length = 64, nullable = false)
    private String runId;

    @Enumerated(EnumType.STRING)
    @Column(name = "module", length = 64, nullable = false)
    private DiagnosticModule module;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private ModuleExecutionStatus status;

    @Column(name = "result_json", columnDefinition = "TEXT")
    private String resultJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public DiagnosticResult() {}

    public DiagnosticResult(Long id, String runId, DiagnosticModule module, ModuleExecutionStatus status, String resultJson, Instant createdAt) {
        this.id = id;
        this.runId = runId;
        this.module = module;
        this.status = status;
        this.resultJson = resultJson;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public DiagnosticModule getModule() { return module; }
    public void setModule(DiagnosticModule module) { this.module = module; }

    public ModuleExecutionStatus getStatus() { return status; }
    public void setStatus(ModuleExecutionStatus status) { this.status = status; }

    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long id;
        private String runId;
        private DiagnosticModule module;
        private ModuleExecutionStatus status;
        private String resultJson;
        private Instant createdAt;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder runId(String runId) { this.runId = runId; return this; }
        public Builder module(DiagnosticModule module) { this.module = module; return this; }
        public Builder status(ModuleExecutionStatus status) { this.status = status; return this; }
        public Builder resultJson(String resultJson) { this.resultJson = resultJson; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }

        public DiagnosticResult build() {
            return new DiagnosticResult(id, runId, module, status, resultJson, createdAt);
        }
    }
}
