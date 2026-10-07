package com.modeldoctor.dto;

import com.modeldoctor.domain.DiagnosticStatus;

public class DiagnosticProgressDto {
    private String runId;
    private DiagnosticStatus runStatus;
    private int selectedModulesCount;
    private int completedModulesCount;
    private int failedModulesCount;
    private int runningModulesCount;
    private int pendingModulesCount;
    private int skippedModulesCount;
    private int progressPercent;

    public DiagnosticProgressDto() {}

    public DiagnosticProgressDto(String runId, DiagnosticStatus runStatus, int selectedModulesCount,
                                 int completedModulesCount, int failedModulesCount, int runningModulesCount,
                                 int pendingModulesCount, int skippedModulesCount, int progressPercent) {
        this.runId = runId;
        this.runStatus = runStatus;
        this.selectedModulesCount = selectedModulesCount;
        this.completedModulesCount = completedModulesCount;
        this.failedModulesCount = failedModulesCount;
        this.runningModulesCount = runningModulesCount;
        this.pendingModulesCount = pendingModulesCount;
        this.skippedModulesCount = skippedModulesCount;
        this.progressPercent = progressPercent;
    }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public DiagnosticStatus getRunStatus() { return runStatus; }
    public void setRunStatus(DiagnosticStatus runStatus) { this.runStatus = runStatus; }

    public int getSelectedModulesCount() { return selectedModulesCount; }
    public void setSelectedModulesCount(int selectedModulesCount) { this.selectedModulesCount = selectedModulesCount; }

    public int getCompletedModulesCount() { return completedModulesCount; }
    public void setCompletedModulesCount(int completedModulesCount) { this.completedModulesCount = completedModulesCount; }

    public int getFailedModulesCount() { return failedModulesCount; }
    public void setFailedModulesCount(int failedModulesCount) { this.failedModulesCount = failedModulesCount; }

    public int getRunningModulesCount() { return runningModulesCount; }
    public void setRunningModulesCount(int runningModulesCount) { this.runningModulesCount = runningModulesCount; }

    public int getPendingModulesCount() { return pendingModulesCount; }
    public void setPendingModulesCount(int pendingModulesCount) { this.pendingModulesCount = pendingModulesCount; }

    public int getSkippedModulesCount() { return skippedModulesCount; }
    public void setSkippedModulesCount(int skippedModulesCount) { this.skippedModulesCount = skippedModulesCount; }

    public int getProgressPercent() { return progressPercent; }
    public void setProgressPercent(int progressPercent) { this.progressPercent = progressPercent; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String runId;
        private DiagnosticStatus runStatus;
        private int selectedModulesCount;
        private int completedModulesCount;
        private int failedModulesCount;
        private int runningModulesCount;
        private int pendingModulesCount;
        private int skippedModulesCount;
        private int progressPercent;

        public Builder runId(String runId) { this.runId = runId; return this; }
        public Builder runStatus(DiagnosticStatus runStatus) { this.runStatus = runStatus; return this; }
        public Builder selectedModulesCount(int selectedModulesCount) { this.selectedModulesCount = selectedModulesCount; return this; }
        public Builder completedModulesCount(int completedModulesCount) { this.completedModulesCount = completedModulesCount; return this; }
        public Builder failedModulesCount(int failedModulesCount) { this.failedModulesCount = failedModulesCount; return this; }
        public Builder runningModulesCount(int runningModulesCount) { this.runningModulesCount = runningModulesCount; return this; }
        public Builder pendingModulesCount(int pendingModulesCount) { this.pendingModulesCount = pendingModulesCount; return this; }
        public Builder skippedModulesCount(int skippedModulesCount) { this.skippedModulesCount = skippedModulesCount; return this; }
        public Builder progressPercent(int progressPercent) { this.progressPercent = progressPercent; return this; }

        public DiagnosticProgressDto build() {
            return new DiagnosticProgressDto(runId, runStatus, selectedModulesCount, completedModulesCount,
                    failedModulesCount, runningModulesCount, pendingModulesCount, skippedModulesCount, progressPercent);
        }
    }
}
