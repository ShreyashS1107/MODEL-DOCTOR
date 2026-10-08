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
public class ErrorExplainabilityInteractionRule implements DiagnosticCorrelationRule {

    @Override
    public String getRuleId() {
        return "ERROR_EXPLAINABILITY_INTERACTION";
    }

    @Override
    public String getRuleName() {
        return "Influential Feature Associated with Prediction Errors";
    }

    @Override
    public List<DiagnosticModule> getRequiredModules() {
        return List.of(DiagnosticModule.ERROR_FORENSICS, DiagnosticModule.EXPLAINABILITY);
    }

    @Override
    public List<DiagnosticCorrelation> evaluate(NormalizedModuleData norm, ObjectMapper objectMapper) {
        List<DiagnosticCorrelation> findings = new ArrayList<>();
        if (!norm.getAvailableModules().containsAll(getRequiredModules())) {
            return findings;
        }

        for (Map.Entry<String, NormalizedModuleData.FeatureErrorData> errEntry : norm.getErrorByFeature().entrySet()) {
            String feature = errEntry.getKey();
            NormalizedModuleData.FeatureErrorData errData = errEntry.getValue();
            NormalizedModuleData.FeatureImportanceData impData = norm.getImportanceByFeature().get(feature);

            if (impData == null) continue;

            boolean isHighImportance = impData.rank <= 5 || impData.attributionShare >= 0.10;
            boolean hasErrorAssoc = errData.absoluteAssociation >= 0.15 || errData.isErrorEnriched || errData.fpSeparation >= 0.30 || errData.fnSeparation >= 0.30;

            if (isHighImportance && hasErrorAssoc) {
                SeverityLevel severity = (impData.rank <= 2 && errData.absoluteAssociation >= 0.25) ? SeverityLevel.CRITICAL : SeverityLevel.HIGH;
                InvestigationPriority priority = severity == SeverityLevel.CRITICAL ? InvestigationPriority.CRITICAL : InvestigationPriority.HIGH;
                EvidenceConfidence confidence = (impData.rank <= 3 && errData.absoluteAssociation >= 0.20) ? EvidenceConfidence.HIGH : EvidenceConfidence.MEDIUM;

                Map<String, Object> evidence = new LinkedHashMap<>();
                evidence.put("feature", feature);
                evidence.put("importanceRank", impData.rank);
                evidence.put("meanAbsShap", impData.meanAbsShap);
                evidence.put("attributionShare", impData.attributionShare);
                evidence.put("errorAssociation", errData.correlation);
                evidence.put("absoluteAssociation", errData.absoluteAssociation);
                evidence.put("adjustedPValue", errData.adjustedPValue);
                evidence.put("fpSeparation", errData.fpSeparation);
                evidence.put("fnSeparation", errData.fnSeparation);

                String summary = String.format("High-impact feature '%s' (SHAP Rank #%d, Mean |SHAP| %.4f) is strongly associated with prediction errors (r = %.4f).",
                        feature, impData.rank, impData.meanAbsShap, errData.correlation);

                String whyItMatters = "Features that strongly influence model predictions while simultaneously showing elevated error correlation can systematically skew decision boundaries and trigger clusters of misclassifications.";

                String investigationDirection = "1. Examine feature values for False Positive vs False Negative records.\n" +
                        "2. Inspect SHAP interaction values and error range concentrations for '" + feature + "'.\n" +
                        "3. Verify whether nonlinear boundary constraints or feature engineering are necessary.";

                findings.add(RuleEvaluationHelper.createCorrelation(
                        norm,
                        getRuleId(),
                        feature,
                        "ERROR_EXPLAINABILITY_INTERACTION",
                        severity,
                        priority,
                        confidence,
                        feature,
                        "High-Attribution Feature Error Concentration: " + feature,
                        summary,
                        whyItMatters,
                        investigationDirection,
                        evidence,
                        getRequiredModules(),
                        objectMapper
                ));
            }
        }

        return findings;
    }
}
