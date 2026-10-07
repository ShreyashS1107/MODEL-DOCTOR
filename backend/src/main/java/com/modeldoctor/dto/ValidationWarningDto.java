package com.modeldoctor.dto;

public class ValidationWarningDto {
    private String code;
    private String field;
    private String resourceId;
    private String message;

    public ValidationWarningDto() {}

    public ValidationWarningDto(String code, String field, String resourceId, String message) {
        this.code = code;
        this.field = field;
        this.resourceId = resourceId;
        this.message = message;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getField() { return field; }
    public void setField(String field) { this.field = field; }

    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String code;
        private String field;
        private String resourceId;
        private String message;

        public Builder code(String code) { this.code = code; return this; }
        public Builder field(String field) { this.field = field; return this; }
        public Builder resourceId(String resourceId) { this.resourceId = resourceId; return this; }
        public Builder message(String message) { this.message = message; return this; }

        public ValidationWarningDto build() {
            return new ValidationWarningDto(code, field, resourceId, message);
        }
    }
}
