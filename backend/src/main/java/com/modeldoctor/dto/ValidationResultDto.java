package com.modeldoctor.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ValidationResultDto {
    private boolean valid;
    private List<ValidationErrorDto> errors = new ArrayList<>();
    private List<ValidationWarningDto> warnings = new ArrayList<>();
    private Map<String, Object> compatibility = new HashMap<>();
    private Map<String, ModuleValidationStatusDto> moduleValidation = new HashMap<>();
    private List<TargetSuggestionDto> targetSuggestions = new ArrayList<>();
    private List<PredictionSuggestionDto> predictionSuggestions = new ArrayList<>();

    public ValidationResultDto() {}

    public ValidationResultDto(boolean valid, List<ValidationErrorDto> errors, List<ValidationWarningDto> warnings,
                               Map<String, Object> compatibility, Map<String, ModuleValidationStatusDto> moduleValidation,
                               List<TargetSuggestionDto> targetSuggestions, List<PredictionSuggestionDto> predictionSuggestions) {
        this.valid = valid;
        this.errors = errors != null ? errors : new ArrayList<>();
        this.warnings = warnings != null ? warnings : new ArrayList<>();
        this.compatibility = compatibility != null ? compatibility : new HashMap<>();
        this.moduleValidation = moduleValidation != null ? moduleValidation : new HashMap<>();
        this.targetSuggestions = targetSuggestions != null ? targetSuggestions : new ArrayList<>();
        this.predictionSuggestions = predictionSuggestions != null ? predictionSuggestions : new ArrayList<>();
    }

    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }

    public List<ValidationErrorDto> getErrors() { return errors; }
    public void setErrors(List<ValidationErrorDto> errors) { this.errors = errors; }

    public List<ValidationWarningDto> getWarnings() { return warnings; }
    public void setWarnings(List<ValidationWarningDto> warnings) { this.warnings = warnings; }

    public Map<String, Object> getCompatibility() { return compatibility; }
    public void setCompatibility(Map<String, Object> compatibility) { this.compatibility = compatibility; }

    public Map<String, ModuleValidationStatusDto> getModuleValidation() { return moduleValidation; }
    public void setModuleValidation(Map<String, ModuleValidationStatusDto> moduleValidation) { this.moduleValidation = moduleValidation; }

    public List<TargetSuggestionDto> getTargetSuggestions() { return targetSuggestions; }
    public void setTargetSuggestions(List<TargetSuggestionDto> targetSuggestions) { this.targetSuggestions = targetSuggestions; }

    public List<PredictionSuggestionDto> getPredictionSuggestions() { return predictionSuggestions; }
    public void setPredictionSuggestions(List<PredictionSuggestionDto> predictionSuggestions) { this.predictionSuggestions = predictionSuggestions; }

    public void addError(String code, String field, String resourceId, String message) {
        this.errors.add(new ValidationErrorDto(code, field, resourceId, message));
        this.valid = false;
    }

    public void addWarning(String code, String field, String resourceId, String message) {
        this.warnings.add(new ValidationWarningDto(code, field, resourceId, message));
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private boolean valid = true;
        private List<ValidationErrorDto> errors = new ArrayList<>();
        private List<ValidationWarningDto> warnings = new ArrayList<>();
        private Map<String, Object> compatibility = new HashMap<>();
        private Map<String, ModuleValidationStatusDto> moduleValidation = new HashMap<>();
        private List<TargetSuggestionDto> targetSuggestions = new ArrayList<>();
        private List<PredictionSuggestionDto> predictionSuggestions = new ArrayList<>();

        public Builder valid(boolean valid) { this.valid = valid; return this; }
        public Builder errors(List<ValidationErrorDto> errors) { this.errors = errors; return this; }
        public Builder warnings(List<ValidationWarningDto> warnings) { this.warnings = warnings; return this; }
        public Builder compatibility(Map<String, Object> compatibility) { this.compatibility = compatibility; return this; }
        public Builder moduleValidation(Map<String, ModuleValidationStatusDto> moduleValidation) { this.moduleValidation = moduleValidation; return this; }
        public Builder targetSuggestions(List<TargetSuggestionDto> targetSuggestions) { this.targetSuggestions = targetSuggestions; return this; }
        public Builder predictionSuggestions(List<PredictionSuggestionDto> predictionSuggestions) { this.predictionSuggestions = predictionSuggestions; return this; }

        public ValidationResultDto build() {
            return new ValidationResultDto(valid, errors, warnings, compatibility, moduleValidation, targetSuggestions, predictionSuggestions);
        }
    }
}
