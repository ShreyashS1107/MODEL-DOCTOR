package com.modeldoctor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public class MlEngineModuleResultDto {

    @JsonProperty("module")
    private String module;

    @JsonProperty("status")
    private String status; // COMPLETED, NOT_IMPLEMENTED, FAILED

    @JsonProperty("message")
    private String message;

    @JsonProperty("result")
    private Map<String, Object> result;

    @JsonProperty("error")
    private String error;

    public MlEngineModuleResultDto() {}

    public MlEngineModuleResultDto(String module, String status, String message, Map<String, Object> result, String error) {
        this.module = module;
        this.status = status;
        this.message = message;
        this.result = result;
        this.error = error;
    }

    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Map<String, Object> getResult() { return result; }
    public void setResult(Map<String, Object> result) { this.result = result; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String module;
        private String status;
        private String message;
        private Map<String, Object> result;
        private String error;

        public Builder module(String module) { this.module = module; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder message(String message) { this.message = message; return this; }
        public Builder result(Map<String, Object> result) { this.result = result; return this; }
        public Builder error(String error) { this.error = error; return this; }

        public MlEngineModuleResultDto build() {
            return new MlEngineModuleResultDto(module, status, message, result, error);
        }
    }
}
