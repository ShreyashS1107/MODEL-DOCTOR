package com.modeldoctor.intelligence.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeldoctor.domain.DiagnosticCorrelation;
import com.modeldoctor.domain.DiagnosticModule;
import com.modeldoctor.intelligence.normalization.NormalizedModuleData;

import java.util.List;

public interface DiagnosticCorrelationRule {
    String getRuleId();
    String getRuleName();
    List<DiagnosticModule> getRequiredModules();
    List<DiagnosticCorrelation> evaluate(NormalizedModuleData norm, ObjectMapper objectMapper);
}
