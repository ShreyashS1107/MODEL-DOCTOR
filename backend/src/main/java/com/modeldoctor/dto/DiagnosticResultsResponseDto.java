package com.modeldoctor.dto;

import com.modeldoctor.domain.DiagnosticStatus;
import java.time.Instant;
import java.util.List;

public class DiagnosticResultsResponseDto {

    private String runId;
    private DiagnosticStatus status;
    private Instant completedAt;
    private List<ModuleResultDto> results;
    private String errorMessage;

    public DiagnosticResultsResponseDto() {}

    public DiagnosticResultsResponseDto(String runId, DiagnosticStatus status, Instant completedAt,
                                        List<ModuleResultDto> results, String errorMessage) {
        this.runId = runId;
        this.status = status;
        this.completedAt = completedAt;
        this.results = results;
        this.errorMessage = errorMessage;
    }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public DiagnosticStatus getStatus() { return status; }
    public void setStatus(DiagnosticStatus status) { this.status = status; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public List<ModuleResultDto> getResults() { return results; }
    public void setResults(List<ModuleResultDto> results) { this.results = results; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String runId;
        private DiagnosticStatus status;
        private Instant completedAt;
        private List<ModuleResultDto> results;
        private String errorMessage;

        public Builder runId(String runId) { this.runId = runId; return this; }
        public Builder status(DiagnosticStatus status) { this.status = status; return this; }
        public Builder completedAt(Instant completedAt) { this.completedAt = completedAt; return this; }
        public Builder results(List<ModuleResultDto> results) { this.results = results; return this; }
        public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }

        public DiagnosticResultsResponseDto build() {
            return new DiagnosticResultsResponseDto(runId, status, completedAt, results, errorMessage);
        }
    }
}
