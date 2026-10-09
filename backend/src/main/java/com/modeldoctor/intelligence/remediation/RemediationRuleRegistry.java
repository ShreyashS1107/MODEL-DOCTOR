package com.modeldoctor.intelligence.remediation;

import com.modeldoctor.domain.DiagnosticInvestigation;
import com.modeldoctor.domain.DiagnosticRemediation;
import com.modeldoctor.domain.DiagnosticRun;
import com.modeldoctor.domain.DiagnosticCorrelation;
import com.modeldoctor.intelligence.normalization.NormalizedModuleData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@SuppressWarnings("null")
public class RemediationRuleRegistry {

    private static final Logger logger = LoggerFactory.getLogger(RemediationRuleRegistry.class);

    private final List<DiagnosticRemediationRule> rules;

    @Autowired
    public RemediationRuleRegistry(List<DiagnosticRemediationRule> rules) {
        // Sort rules deterministically by rule ID
        List<DiagnosticRemediationRule> sorted = new ArrayList<>(rules != null ? rules : Collections.emptyList());
        sorted.sort(Comparator.comparing(DiagnosticRemediationRule::getRuleId));
        this.rules = Collections.unmodifiableList(sorted);
        logger.info("Initialized RemediationRuleRegistry with {} deterministic remediation rules", this.rules.size());
    }

    public List<DiagnosticRemediationRule> getRules() {
        return rules;
    }

    public List<DiagnosticRemediation> evaluateAll(
            NormalizedModuleData norm,
            List<DiagnosticCorrelation> correlations,
            List<DiagnosticInvestigation> investigations,
            DiagnosticRun run) {

        List<DiagnosticRemediation> results = new ArrayList<>();
        Set<String> uniqueRemediationKeys = new HashSet<>();

        for (DiagnosticRemediationRule rule : rules) {
            try {
                List<DiagnosticRemediation> candid = rule.evaluate(norm, correlations, investigations, run);
                if (candid != null && !candid.isEmpty()) {
                    for (DiagnosticRemediation r : candid) {
                        String key = r.getRemediationType().name() + "::" + r.getTargetKey();
                        if (uniqueRemediationKeys.add(key)) {
                            results.add(r);
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Exception in remediation rule {}: {}", rule.getRuleId(), e.getMessage(), e);
            }
        }

        // Sort deterministically: priorityScore DESC, then priority severity DESC, then targetKey ASC, then remediationType ASC
        results.sort((a, b) -> {
            int scoreCmp = Double.compare(b.getPriorityScore(), a.getPriorityScore());
            if (scoreCmp != 0) return scoreCmp;
            int prioCmp = Integer.compare(b.getPriority().ordinal(), a.getPriority().ordinal());
            if (prioCmp != 0) return prioCmp;
            int keyCmp = a.getTargetKey().compareTo(b.getTargetKey());
            if (keyCmp != 0) return keyCmp;
            return a.getRemediationType().name().compareTo(b.getRemediationType().name());
        });

        return results;
    }
}
