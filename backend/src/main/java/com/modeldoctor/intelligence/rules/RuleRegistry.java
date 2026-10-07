package com.modeldoctor.intelligence.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeldoctor.domain.DiagnosticCorrelation;
import com.modeldoctor.intelligence.normalization.NormalizedModuleData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class RuleRegistry {

    private static final Logger logger = LoggerFactory.getLogger(RuleRegistry.class);
    private final List<DiagnosticCorrelationRule> rules;
    private final ObjectMapper objectMapper;

    public RuleRegistry(List<DiagnosticCorrelationRule> rules, ObjectMapper objectMapper) {
        this.rules = rules != null ? rules : Collections.emptyList();
        this.objectMapper = objectMapper;
    }

    public List<DiagnosticCorrelationRule> getRules() {
        return rules;
    }

    public List<DiagnosticCorrelation> evaluateAll(NormalizedModuleData norm) {
        List<DiagnosticCorrelation> allCorrelations = new ArrayList<>();
        if (norm == null) return allCorrelations;

        for (DiagnosticCorrelationRule rule : rules) {
            try {
                List<DiagnosticCorrelation> results = rule.evaluate(norm, objectMapper);
                if (results != null && !results.isEmpty()) {
                    allCorrelations.addAll(results);
                }
            } catch (Exception e) {
                logger.warn("Rule '{}' failed during evaluation for run {}: {}",
                        rule.getRuleId(), norm.getRunId(), e.getMessage(), e);
            }
        }

        // Deterministic sorting by Priority Score DESC, then Rule ID ASC, then Feature ASC
        allCorrelations.sort((a, b) -> {
            int scoreCmp = Double.compare(b.getPriorityScore(), a.getPriorityScore());
            if (scoreCmp != 0) return scoreCmp;
            int ruleCmp = a.getRuleId().compareTo(b.getRuleId());
            if (ruleCmp != 0) return ruleCmp;
            String fA = a.getFeature() != null ? a.getFeature() : "";
            String fB = b.getFeature() != null ? b.getFeature() : "";
            return fA.compareTo(fB);
        });

        return allCorrelations;
    }
}
