package com.modeldoctor.intelligence.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeldoctor.domain.DiagnosticCorrelation;
import com.modeldoctor.domain.DiagnosticModule;
import com.modeldoctor.domain.EvidenceConfidence;
import com.modeldoctor.domain.InvestigationPriority;
import com.modeldoctor.domain.SeverityLevel;
import com.modeldoctor.intelligence.normalization.NormalizedModuleData;

import java.time.Instant;
import java.util.*;

public class RuleEvaluationHelper {

    public static DiagnosticCorrelation createCorrelation(
            NormalizedModuleData norm,
            String ruleId,
            String correlationKey,
            String findingType,
            SeverityLevel severity,
            InvestigationPriority priority,
            EvidenceConfidence confidence,
            String feature,
            String title,
            String summary,
            String whyItMatters,
            String investigationDirection,
            Map<String, Object> evidenceMap,
            List<DiagnosticModule> sourceModules,
            ObjectMapper objectMapper) {

        DiagnosticCorrelation corr = new DiagnosticCorrelation();
        corr.setRunId(norm.getRunId());
        corr.setRuleId(ruleId);
        corr.setCorrelationKey(correlationKey != null && !correlationKey.isBlank() ? correlationKey : "__GLOBAL__");
        corr.setFindingType(findingType);
        corr.setSeverity(severity);
        corr.setPriority(priority);
        corr.setConfidence(confidence);
        corr.setFeature(feature);
        corr.setTitle(title);
        corr.setSummary(summary);
        corr.setWhyItMatters(whyItMatters);
        corr.setInvestigationDirection(investigationDirection);
        corr.setPriorityScore(calculatePriorityScore(priority, severity, sourceModules.size(), evidenceMap));
        corr.setCreatedAt(Instant.now());

        try {
            corr.setEvidenceJson(objectMapper.writeValueAsString(evidenceMap));
        } catch (Exception e) {
            corr.setEvidenceJson("{}");
        }

        try {
            List<String> moduleNames = sourceModules.stream().map(Enum::name).toList();
            corr.setSourceModulesJson(objectMapper.writeValueAsString(moduleNames));
        } catch (Exception e) {
            corr.setSourceModulesJson("[]");
        }

        List<Long> resultIds = new ArrayList<>();
        for (DiagnosticModule mod : sourceModules) {
            Long rid = norm.getModuleResultIds().get(mod);
            if (rid != null) {
                resultIds.add(rid);
            }
        }
        try {
            corr.setSourceResultIdsJson(objectMapper.writeValueAsString(resultIds));
        } catch (Exception e) {
            corr.setSourceResultIdsJson("[]");
        }

        return corr;
    }

    public static double calculatePriorityScore(
            InvestigationPriority priority,
            SeverityLevel severity,
            int sourceModuleCount,
            Map<String, Object> evidence) {

        double base = switch (priority) {
            case CRITICAL -> 90.0;
            case HIGH -> 70.0;
            case MEDIUM -> 50.0;
            case LOW -> 30.0;
            case INFO -> 10.0;
        };

        double severityBonus = switch (severity) {
            case CRITICAL -> 8.0;
            case HIGH -> 5.0;
            case MEDIUM -> 3.0;
            case LOW -> 1.0;
            case INFO -> 0.0;
        };

        double moduleBonus = Math.min(sourceModuleCount * 2.5, 10.0);
        return base + severityBonus + moduleBonus;
    }
}
