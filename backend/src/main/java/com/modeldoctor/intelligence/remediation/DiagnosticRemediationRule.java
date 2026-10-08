package com.modeldoctor.intelligence.remediation;

import com.modeldoctor.domain.DiagnosticInvestigation;
import com.modeldoctor.domain.DiagnosticRemediation;
import com.modeldoctor.domain.DiagnosticRun;
import com.modeldoctor.domain.DiagnosticCorrelation;
import com.modeldoctor.intelligence.normalization.NormalizedModuleData;

import java.util.List;

public interface DiagnosticRemediationRule {

    String getRuleId();

    String getRemediationType();

    List<DiagnosticRemediation> evaluate(
            NormalizedModuleData norm,
            List<DiagnosticCorrelation> correlations,
            List<DiagnosticInvestigation> investigations,
            DiagnosticRun run
    );
}
