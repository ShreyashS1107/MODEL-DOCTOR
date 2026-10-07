package com.modeldoctor.dto;

import jakarta.validation.constraints.NotBlank;

public class ModelInfoDto {

    @NotBlank(message = "Model name is required")
    private String name;

    @NotBlank(message = "Model framework is required (e.g. xgboost, lightgbm, pytorch, sklearn)")
    private String framework;

    @NotBlank(message = "Task type is required (e.g. binary_classification, multiclass, regression)")
    private String taskType;

    private String storageUri;

    public ModelInfoDto() {}

    public ModelInfoDto(String name, String framework, String taskType, String storageUri) {
        this.name = name;
        this.framework = framework;
        this.taskType = taskType;
        this.storageUri = storageUri;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getFramework() { return framework; }
    public void setFramework(String framework) { this.framework = framework; }

    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }

    public String getStorageUri() { return storageUri; }
    public void setStorageUri(String storageUri) { this.storageUri = storageUri; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String name;
        private String framework;
        private String taskType;
        private String storageUri;

        public Builder name(String name) { this.name = name; return this; }
        public Builder framework(String framework) { this.framework = framework; return this; }
        public Builder taskType(String taskType) { this.taskType = taskType; return this; }
        public Builder storageUri(String storageUri) { this.storageUri = storageUri; return this; }

        public ModelInfoDto build() {
            return new ModelInfoDto(name, framework, taskType, storageUri);
        }
    }
}
