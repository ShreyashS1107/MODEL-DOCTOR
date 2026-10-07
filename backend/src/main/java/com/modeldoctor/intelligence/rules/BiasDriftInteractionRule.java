package com.modeldoctor.intelligence.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeldoctor.domain.DiagnosticCorrelation;
import com.modeldoctor.domain.DiagnosticModule;
import com.modeldoctor.domain.EvidenceConfidence;
import com.modeldoctor.domain.InvestigationPriority;
import com.modeldoctor.domain.SeverityLevel;
import com.modeldoctor.intelligence.normalization.NormalizedModuleData;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class BiasDriftInteractionRule implements DiagnosticCorrelationRule {

    @Override
    public String getRuleId() {
        return "BIAS_DRIFT_INTERACTION";
    }

    @Override
    public String getRuleName() {
        return "Fairness Disparity & Demographic Shift";
    }

    @Override
    public List<DiagnosticModule> getRequiredModules() {
        return List.of(DiagnosticModule.BIAS, DiagnosticModule.DRIFT);
    }

    @Override
    public List<DiagnosticCorrelation> evaluate(NormalizedModuleData norm, ObjectMapper objectMapper) {
        List<DiagnosticCorrelation> findings = new ArrayList<>();
        if (!norm.getAvailableModules().containsAll(getRequiredModules())) {
            return findings;
        }

        NormalizedModuleData.BiasSummary bias = norm.getBiasSummary();
        if (bias == null) return findings;

        boolean hasFairnessDisparity = bias.worstDisparateImpactRatio < 0.85 || bias.demographicParityGap > 0.05;
        if (!hasFairnessDisparity) return findings;

        // Check if the protected attribute itself or a primary feature drifted
        String prot = bias.protectedAttribute;
        NormalizedModuleData.FeatureDriftData protDrift = norm.getDriftByFeature().get(prot);

        if (protDrift != null && (protDrift.psi >= 0.08 || protDrift.driftDetected)) {
            SeverityLevel severity = protDrift.psi >= 0.20 ? SeverityLevel.HIGH : SeverityLevel.MEDIUM;
            InvestigationPriority priority = InvestigationPriority.HIGH;
            EvidenceConfidence confidence = protDrift.psi >= 0.10 ? EvidenceConfidence.HIGH : EvidenceConfidence.MEDIUM;

            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("protectedAttribute", prot);
            evidence.put("disparateImpactRatio", bias.worstDisparateImpactRatio);
            evidence.put("demographicParityGap", bias.demographicParityGap);
            evidence.put("protectedAttributePsi", protDrift.psi);
            evidence.put("driftSeverity", protDrift.severity);

            String summary = String.format("Protected demographic attribute '%s' has undergone distribution shift (PSI: %.4f) alongside observed fairness disparity (Disparate Impact: %.4f).",
                    prot, protDrift.psi, bias.worstDisparateImpactRatio);

            String whyItMatters = "Changes in subgroup representation or baseline demographic frequencies can alter group-conditional model calibration and exacerbate disparity metrics.";

            String investigationDirection = "1. Investigate demographic composition shifts between baseline and evaluation periods.\n2. Assess if shifting group proportions explain the elevated parity gap.";

            findings.add(RuleEvaluationHelper.createCorrelation(
                    norm,
                    getRuleId(),
                    prot,
                    "DEMOGRAPHIC_SHIFT_FAIRNESS_RISK",
                    severity,
                    priority,
                    confidence,
                    prot,
                    "Demographic Representation Shift & Fairness Disparity: " + prot,
                    summary,
                    whyItMatters,
                    investigationDirection,
                    evidence,
                    getRequiredModules(),
                    objectMapper
            ));
        }

        return findings;
    }
}
