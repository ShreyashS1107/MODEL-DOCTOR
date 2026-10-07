package com.modeldoctor.dto;

import com.modeldoctor.domain.DiagnosticModule;
import com.modeldoctor.domain.ModuleExecutionStatus;
import java.util.Map;

public class ModuleResultDto {
    private DiagnosticModule module;
    private ModuleExecutionStatus status;
    private String statusMessage;
    private Map<String, Object> result;

    public ModuleResultDto() {}

    public ModuleResultDto(DiagnosticModule module, ModuleExecutionStatus status, String statusMessage, Map<String, Object> result) {
        this.module = module;
        this.status = status;
        this.statusMessage = statusMessage;
        this.result = result;
    }

    public DiagnosticModule getModule() { return module; }
    public void setModule(DiagnosticModule module) { this.module = module; }

    public ModuleExecutionStatus getStatus() { return status; }
    public void setStatus(ModuleExecutionStatus status) { this.status = status; }

    public String getStatusMessage() { return statusMessage; }
    public void setStatusMessage(String statusMessage) { this.statusMessage = statusMessage; }

    public Map<String, Object> getResult() { return result; }
    public void setResult(Map<String, Object> result) { this.result = result; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private DiagnosticModule module;
        private ModuleExecutionStatus status;
        private String statusMessage;
        private Map<String, Object> result;

        public Builder module(DiagnosticModule module) { this.module = module; return this; }
        public Builder status(ModuleExecutionStatus status) { this.status = status; return this; }
        public Builder statusMessage(String statusMessage) { this.statusMessage = statusMessage; return this; }
        public Builder result(Map<String, Object> result) { this.result = result; return this; }

        public ModuleResultDto build() {
            return new ModuleResultDto(module, status, statusMessage, result);
        }
    }
}
