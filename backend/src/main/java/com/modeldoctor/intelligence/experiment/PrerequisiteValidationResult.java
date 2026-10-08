package com.modeldoctor.intelligence.experiment;

import java.util.ArrayList;
import java.util.List;

public class PrerequisiteValidationResult {

    private final boolean executable;
    private final String reason;
    private final List<String> missingPrerequisites;

    public PrerequisiteValidationResult(boolean executable, String reason, List<String> missingPrerequisites) {
        this.executable = executable;
        this.reason = reason;
        this.missingPrerequisites = missingPrerequisites != null ? missingPrerequisites : new ArrayList<>();
    }

    public static PrerequisiteValidationResult ok() {
        return new PrerequisiteValidationResult(true, "Prerequisites satisfied", new ArrayList<>());
    }

    public static PrerequisiteValidationResult notExecutable(String reason, List<String> missing) {
        return new PrerequisiteValidationResult(false, reason, missing);
    }

    public boolean isExecutable() { return executable; }
    public String getReason() { return reason; }
    public List<String> getMissingPrerequisites() { return missingPrerequisites; }
}
