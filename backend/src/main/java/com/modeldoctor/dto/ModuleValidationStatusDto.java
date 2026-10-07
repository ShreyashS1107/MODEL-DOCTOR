package com.modeldoctor.dto;

import java.util.List;

public class ModuleValidationStatusDto {
    private String module;
    private Boolean compatible;
    private List<String> missingPrerequisites;
    private String message;

    public ModuleValidationStatusDto() {}

    public ModuleValidationStatusDto(String module, Boolean compatible, List<String> missingPrerequisites, String message) {
        this.module = module;
        this.compatible = compatible;
        this.missingPrerequisites = missingPrerequisites;
        this.message = message;
    }

    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }

    public Boolean getCompatible() { return compatible; }
    public void setCompatible(Boolean compatible) { this.compatible = compatible; }

    public List<String> getMissingPrerequisites() { return missingPrerequisites; }
    public void setMissingPrerequisites(List<String> missingPrerequisites) { this.missingPrerequisites = missingPrerequisites; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String module;
        private Boolean compatible;
        private List<String> missingPrerequisites;
        private String message;

        public Builder module(String module) { this.module = module; return this; }
        public Builder compatible(Boolean compatible) { this.compatible = compatible; return this; }
        public Builder missingPrerequisites(List<String> missingPrerequisites) { this.missingPrerequisites = missingPrerequisites; return this; }
        public Builder message(String message) { this.message = message; return this; }

        public ModuleValidationStatusDto build() {
            return new ModuleValidationStatusDto(module, compatible, missingPrerequisites, message);
        }
    }
}
