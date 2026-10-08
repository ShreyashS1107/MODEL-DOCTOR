package com.modeldoctor.intelligence.experiment;

import com.modeldoctor.domain.DiagnosticExperiment;
import com.modeldoctor.domain.DiagnosticRemediation;
import com.modeldoctor.domain.DiagnosticRun;
import com.modeldoctor.domain.ExperimentType;
import com.modeldoctor.dto.InterventionConfigDto;

public interface DiagnosticExperimentStrategy {

    boolean supports(ExperimentType type);

    PrerequisiteValidationResult validatePrerequisites(
            DiagnosticRun baselineRun,
            DiagnosticRemediation remediation,
            InterventionConfigDto config
    );

    ExperimentExecutionResult execute(
            DiagnosticRun baselineRun,
            DiagnosticRemediation remediation,
            DiagnosticExperiment experiment,
            InterventionConfigDto config
    );
}
