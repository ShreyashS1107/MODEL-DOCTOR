package com.modeldoctor.exception;

import java.time.Instant;
import java.util.List;

public class ErrorResponseDto {
    private int statusCode;
    private String error;
    private String message;
    private String path;
    private Instant timestamp;
    private List<String> validationDetails;

    public ErrorResponseDto() {}

    public ErrorResponseDto(int statusCode, String error, String message, String path, Instant timestamp, List<String> validationDetails) {
        this.statusCode = statusCode;
        this.error = error;
        this.message = message;
        this.path = path;
        this.timestamp = timestamp;
        this.validationDetails = validationDetails;
    }

    public int getStatusCode() { return statusCode; }
    public void setStatusCode(int statusCode) { this.statusCode = statusCode; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public List<String> getValidationDetails() { return validationDetails; }
    public void setValidationDetails(List<String> validationDetails) { this.validationDetails = validationDetails; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private int statusCode;
        private String error;
        private String message;
        private String path;
        private Instant timestamp;
        private List<String> validationDetails;

        public Builder statusCode(int statusCode) { this.statusCode = statusCode; return this; }
        public Builder error(String error) { this.error = error; return this; }
        public Builder message(String message) { this.message = message; return this; }
        public Builder path(String path) { this.path = path; return this; }
        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
        public Builder validationDetails(List<String> validationDetails) { this.validationDetails = validationDetails; return this; }

        public ErrorResponseDto build() {
            return new ErrorResponseDto(statusCode, error, message, path, timestamp, validationDetails);
        }
    }
}
