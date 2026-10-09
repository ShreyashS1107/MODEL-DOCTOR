package com.modeldoctor.intelligence.temporal;

import com.modeldoctor.domain.DiagnosticExperiment;
import com.modeldoctor.domain.DiagnosticRemediation;
import com.modeldoctor.domain.DiagnosticRun;
import com.modeldoctor.domain.DiagnosticTemporalObservation;
import com.modeldoctor.dto.RemediationDurabilityDto;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class DurabilityAnalyzer {

    private final TemporalMetricRegistry metricRegistry;

    public DurabilityAnalyzer(TemporalMetricRegistry metricRegistry) {
        this.metricRegistry = metricRegistry;
    }

    public List<RemediationDurabilityDto> evaluateDurability(
            String modelLineageId,
            List<DiagnosticRun> runs,
            List<DiagnosticRemediation> remediations,
            List<DiagnosticExperiment> experiments,
            List<DiagnosticTemporalObservation> observations) {

        List<RemediationDurabilityDto> results = new ArrayList<>();
        if (experiments == null || experiments.isEmpty()) {
            return results;
        }

        // Map runs chronologically
        Map<String, Integer> runOrder = new HashMap<>();
        for (int i = 0; i < runs.size(); i++) {
            runOrder.put(runs.get(i).getId(), i);
        }

        for (DiagnosticExperiment exp : experiments) {
            String baselineRunId = exp.getBaselineRunId();
            Integer baseIdx = runOrder.get(baselineRunId);
            if (baseIdx == null) continue;

            RemediationDurabilityDto dto = new RemediationDurabilityDto();
            dto.setRemediationId(exp.getRemediationId());
            dto.setExperimentId(exp.getId());
            dto.setTargetKey(exp.getTargetKey());
            dto.setExperimentType(exp.getExperimentType() != null ? exp.getExperimentType().name() : "");
            dto.setBaselineRunId(baselineRunId);
            dto.setCandidateRunId(exp.getCandidateRunId());
            dto.setExperimentConclusion(exp.getConclusion() != null ? exp.getConclusion().name() : "INCONCLUSIVE");

            // Look up matching remediation if present
            if (remediations != null && exp.getRemediationId() != null) {
                for (DiagnosticRemediation rem : remediations) {
                    if (rem.getId().equals(exp.getRemediationId())) {
                        dto.setRemediationType(rem.getRemediationType() != null ? rem.getRemediationType().name() : "");
                        break;
                    }
                }
            }

            // Extract target metric (e.g. PSI or F1 or default metric)
            String targetMetric = "psi";
            if (exp.getTargetKey() != null && exp.getTargetKey().startsWith("FEATURE::")) {
                targetMetric = "psi";
            } else if ("THRESHOLD_COUNTERFACTUAL".equalsIgnoreCase(dto.getExperimentType()) || "CALIBRATION_COUNTERFACTUAL".equalsIgnoreCase(dto.getExperimentType())) {
                targetMetric = "expected_calibration_error";
            }
            dto.setMetricName(targetMetric);

            // Baseline metric value
            double baselineVal = 0.0;
            for (DiagnosticTemporalObservation o : observations) {
                if (baselineRunId.equals(o.getRunId()) && targetMetric.equalsIgnoreCase(o.getMetricName()) &&
                        (exp.getTargetKey() == null || exp.getTargetKey().equalsIgnoreCase(o.getTargetKey()))) {
                    baselineVal = o.getMetricValue();
                    break;
                }
            }
            dto.setBaselineValue(round(baselineVal, 4));

            // Candidate metric value if available
            double candidateVal = baselineVal;
            if (exp.getCandidateRunId() != null) {
                for (DiagnosticTemporalObservation o : observations) {
                    if (exp.getCandidateRunId().equals(o.getRunId()) && targetMetric.equalsIgnoreCase(o.getMetricName()) &&
                            (exp.getTargetKey() == null || exp.getTargetKey().equalsIgnoreCase(o.getTargetKey()))) {
                        candidateVal = o.getMetricValue();
                        break;
                    }
                }
            }
            dto.setExperimentCandidateValue(round(candidateVal, 4));

            // Subsequent operational baseline runs (indices > baseIdx with runType == "BASELINE")
            List<Double> followUps = new ArrayList<>();
            List<String> followUpRuns = new ArrayList<>();

            for (int i = baseIdx + 1; i < runs.size(); i++) {
                DiagnosticRun subsequentRun = runs.get(i);
                if (!"BASELINE".equalsIgnoreCase(subsequentRun.getRunType())) {
                    continue; // Skip experiment overlay runs
                }
                for (DiagnosticTemporalObservation o : observations) {
                    if (subsequentRun.getId().equals(o.getRunId()) && targetMetric.equalsIgnoreCase(o.getMetricName()) &&
                            (exp.getTargetKey() == null || exp.getTargetKey().equalsIgnoreCase(o.getTargetKey()))) {
                        followUps.add(round(o.getMetricValue(), 4));
                        followUpRuns.add(subsequentRun.getId());
                        break;
                    }
                }
            }

            dto.setFollowUpValues(followUps);
            dto.setFollowUpRunIds(followUpRuns);

            // Assess durability
            if (!"VALIDATED".equalsIgnoreCase(dto.getExperimentConclusion())) {
                dto.setDurabilityStatus("NOT_APPLICABLE");
                dto.setAssessment("Experiment was not validated; durability tracking is inactive.");
            } else if (followUps.isEmpty()) {
                dto.setDurabilityStatus("INSUFFICIENT_FOLLOWUP");
                dto.setAssessment("Intervention validated under experiment; awaiting subsequent operational baseline runs.");
            } else {
                boolean allSustained = true;
                boolean anyRegressed = false;

                for (double fVal : followUps) {
                    String sev = metricRegistry.computeSeverity(targetMetric, fVal);
                    if ("CRITICAL".equalsIgnoreCase(sev) || "HIGH".equalsIgnoreCase(sev)) {
                        allSustained = false;
                        anyRegressed = true;
                    }
                }

                if (allSustained) {
                    dto.setDurabilityStatus("SUSTAINED");
                    dto.setAssessment("Remediation improvement has been sustained across " + followUps.size() + " subsequent baseline run(s).");
                } else if (anyRegressed) {
                    dto.setDurabilityStatus("FAILED_TO_SUSTAIN");
                    dto.setAssessment("Target metric regressed in subsequent baseline observations after initial remediation experiment.");
                } else {
                    dto.setDurabilityStatus("TEMPORARY");
                    dto.setAssessment("Mixed durability observed across subsequent baseline runs.");
                }
            }

            results.add(dto);
        }

        return results;
    }

    private double round(double val, int decimals) {
        if (Double.isNaN(val) || Double.isInfinite(val)) return 0.0;
        double p = Math.pow(10, decimals);
        return Math.round(val * p) / p;
    }
}
