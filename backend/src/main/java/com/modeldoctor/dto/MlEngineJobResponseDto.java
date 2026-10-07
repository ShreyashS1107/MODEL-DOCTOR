package com.modeldoctor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class MlEngineJobResponseDto {

    @JsonProperty("runId")
    private String runId;

    @JsonProperty("status")
    private String status; // COMPLETED, FAILED

    @JsonProperty("executionTimeMs")
    private Double executionTimeMs;

    @JsonProperty("modules")
    private List<MlEngineModuleResultDto> modules;

    @JsonProperty("error")
    private String error;

    public MlEngineJobResponseDto() {}

    public MlEngineJobResponseDto(String runId, String status, Double executionTimeMs,
                                  List<MlEngineModuleResultDto> modules, String error) {
        this.runId = runId;
        this.status = status;
        this.executionTimeMs = executionTimeMs;
        this.modules = modules;
        this.error = error;
    }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Double getExecutionTimeMs() { return executionTimeMs; }
    public void setExecutionTimeMs(Double executionTimeMs) { this.executionTimeMs = executionTimeMs; }

    public List<MlEngineModuleResultDto> getModules() { return modules; }
    public void setModules(List<MlEngineModuleResultDto> modules) { this.modules = modules; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String runId;
        private String status;
        private Double executionTimeMs;
        private List<MlEngineModuleResultDto> modules;
        private String error;

        public Builder runId(String runId) { this.runId = runId; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder executionTimeMs(Double executionTimeMs) { this.executionTimeMs = executionTimeMs; return this; }
        public Builder modules(List<MlEngineModuleResultDto> modules) { this.modules = modules; return this; }
        public Builder error(String error) { this.error = error; return this; }

        public MlEngineJobResponseDto build() {
            return new MlEngineJobResponseDto(runId, status, executionTimeMs, modules, error);
        }
    }
}
