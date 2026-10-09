package com.modeldoctor.intelligence.incident;

import com.modeldoctor.domain.DiagnosticOperationalAlert;
import com.modeldoctor.domain.IncidentCategory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Deterministic engine for evaluating alert relationships, calculating transparent
 * correlation scores, and forming operational incident clusters without ML blackboxes or LLMs.
 */
@Component
public class IncidentCorrelationEngine {

    public record AlertCorrelationResult(int score, List<String> reasons, boolean isCorrelated) {}

    /**
     * Evaluates correlation between two operational alerts using deterministic evidence.
     */
    public AlertCorrelationResult evaluateCorrelation(DiagnosticOperationalAlert a1, DiagnosticOperationalAlert a2) {
        int score = 0;
        List<String> reasons = new ArrayList<>();

        if (a1 == null || a2 == null) {
            return new AlertCorrelationResult(0, List.of("Null alert comparison"), false);
        }

        // 1. Same target key (Strongest signal)
        String t1 = a1.getTargetKey() != null ? a1.getTargetKey().trim() : "GLOBAL";
        String t2 = a2.getTargetKey() != null ? a2.getTargetKey().trim() : "GLOBAL";
        boolean sameTarget = !t1.equalsIgnoreCase("GLOBAL") && t1.equalsIgnoreCase(t2);

        if (sameTarget) {
            score += 35;
            reasons.add("+35 Same target entity: " + t1);
        }

        // 2. Same Phase 6 Investigation Target Key
        String inv1 = a1.getRelatedInvestigationTargetKey();
        String inv2 = a2.getRelatedInvestigationTargetKey();
        if (inv1 != null && !inv1.isBlank() && inv1.equalsIgnoreCase(inv2)) {
            score += 25;
            reasons.add("+25 Connected to same Phase 6 investigation target: " + inv1);
        }

        // 3. Same Phase 9 Temporal Issue Track
        Long track1 = a1.getRelatedIssueTrackId();
        Long track2 = a2.getRelatedIssueTrackId();
        if (track1 != null && track1.equals(track2)) {
            score += 20;
            reasons.add("+20 Associated with same Phase 9 temporal issue track #" + track1);
        }

        // 4. Shared Source Diagnostic Run / Target Evidence
        String srcRun1 = a1.getFirstSeenRunId();
        String srcRun2 = a2.getFirstSeenRunId();
        if (srcRun1 != null && !srcRun1.isBlank() && srcRun1.equals(srcRun2)) {
            score += 20;
            reasons.add("+20 Shared source diagnostic run: " + srcRun1);
        }

        // 5. Same Diagnostic Module
        String mod1 = a1.getSourceModule();
        String mod2 = a2.getSourceModule();
        if (mod1 != null && !mod1.isBlank() && mod1.equalsIgnoreCase(mod2)) {
            score += 10;
            reasons.add("+10 Co-occurring within module: " + mod1);
        }

        // 6. Same Metric Name
        String m1 = a1.getMetricName();
        String m2 = a2.getMetricName();
        if (m1 != null && !m1.isBlank() && m1.equalsIgnoreCase(m2)) {
            score += 10;
            reasons.add("+10 Shared underlying diagnostic metric: " + m1);
        }

        // 7. Same Subgroup target
        if (t1.startsWith("SUBGROUP::") && t2.startsWith("SUBGROUP::") && t1.equalsIgnoreCase(t2)) {
            score += 15;
            reasons.add("+15 Subgroup slice alignment: " + t1);
        }

        // 8. Temporal Proximity (Observed in same run)
        String lastRun1 = a1.getLastSeenRunId();
        String lastRun2 = a2.getLastSeenRunId();
        if (lastRun1 != null && lastRun1.equalsIgnoreCase(lastRun2)) {
            score += 10;
            reasons.add("+10 Co-observed in identical operational run: " + lastRun1);
        }

        int finalScore = Math.min(100, score);
        boolean distinctSpecificTargets = !t1.equalsIgnoreCase("GLOBAL") && !t2.equalsIgnoreCase("GLOBAL") && !t1.equalsIgnoreCase(t2);
        boolean sharedContext = (inv1 != null && !inv1.isBlank() && inv1.equalsIgnoreCase(inv2))
                || (track1 != null && track1.equals(track2));

        boolean correlated;
        if (distinctSpecificTargets && !sharedContext) {
            correlated = false;
        } else {
            correlated = sameTarget || sharedContext || finalScore >= 50;
        }

        return new AlertCorrelationResult(finalScore, reasons, correlated);
    }

    /**
     * Determines incident category based on involved modules and target structure.
     */
    public IncidentCategory determineCategory(List<DiagnosticOperationalAlert> alerts) {
        if (alerts == null || alerts.isEmpty()) return IncidentCategory.MULTI_MODULE_INCIDENT;

        Set<String> uniqueModules = new HashSet<>();
        for (DiagnosticOperationalAlert a : alerts) {
            if (a.getSourceModule() != null && !a.getSourceModule().isBlank()) {
                uniqueModules.add(a.getSourceModule().toUpperCase());
            }
        }

        if (uniqueModules.size() > 1) {
            return IncidentCategory.MULTI_MODULE_INCIDENT;
        }

        String singleModule = uniqueModules.isEmpty() ? "" : uniqueModules.iterator().next();
        return switch (singleModule) {
            case "DRIFT" -> IncidentCategory.DRIFT_INCIDENT;
            case "ERROR_FORENSICS", "ERROR" -> IncidentCategory.ERROR_INCIDENT;
            case "DATA_QUALITY" -> IncidentCategory.DATA_QUALITY_INCIDENT;
            case "LEAKAGE" -> IncidentCategory.LEAKAGE_INCIDENT;
            case "PERFORMANCE" -> IncidentCategory.PERFORMANCE_INCIDENT;
            case "CALIBRATION" -> IncidentCategory.CALIBRATION_INCIDENT;
            case "FAIRNESS", "BIAS" -> IncidentCategory.FAIRNESS_INCIDENT;
            case "ROBUSTNESS" -> IncidentCategory.ROBUSTNESS_INCIDENT;
            case "TEMPORAL" -> IncidentCategory.TEMPORAL_DEGRADATION;
            default -> IncidentCategory.MULTI_MODULE_INCIDENT;
        };
    }

    /**
     * Generates a deterministic SHA-256 fingerprint for an incident.
     */
    public String generateFingerprint(String modelLineageId, String primaryTarget, IncidentCategory category) {
        String raw = (modelLineageId != null ? modelLineageId.trim() : "default")
                + "::" + (primaryTarget != null ? primaryTarget.trim() : "GLOBAL")
                + "::" + (category != null ? category.name() : "MULTI_MODULE_INCIDENT");

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return Integer.toHexString(raw.hashCode());
        }
    }

    /**
     * Generates a human-readable, stable incident code like INC-0042.
     */
    public String generateIncidentCode(String fingerprint, long seedIndex) {
        if (fingerprint != null && fingerprint.length() >= 4) {
            return "INC-" + fingerprint.substring(0, 4).toUpperCase();
        }
        return String.format("INC-%04d", seedIndex % 10000);
    }

    /**
     * Generates a concise, technical, evidence-based title for the incident.
     */
    public String generateTitle(String severity, String primaryTarget, IncidentCategory category, List<DiagnosticOperationalAlert> alerts) {
        Set<String> modules = new LinkedHashSet<>();
        if (alerts != null) {
            for (DiagnosticOperationalAlert a : alerts) {
                if (a.getSourceModule() != null) modules.add(a.getSourceModule());
            }
        }

        String modStr = modules.isEmpty() ? category.name() : String.join(" + ", modules);
        return String.format("[%s] %s on %s (%s)",
                severity != null ? severity : "MEDIUM",
                formatCategoryName(category),
                primaryTarget != null ? primaryTarget : "GLOBAL",
                modStr);
    }

    private String formatCategoryName(IncidentCategory cat) {
        if (cat == null) return "Operational Incident";
        return switch (cat) {
            case MULTI_MODULE_INCIDENT -> "Multi-Module Degradation";
            case DRIFT_INCIDENT -> "Distribution Drift";
            case ERROR_INCIDENT -> "Error Forensics Concentration";
            case DATA_QUALITY_INCIDENT -> "Data Quality Breach";
            case LEAKAGE_INCIDENT -> "Target Leakage Anomaly";
            case PERFORMANCE_INCIDENT -> "Performance Degradation";
            case CALIBRATION_INCIDENT -> "Probability Calibration Error";
            case FAIRNESS_INCIDENT -> "Fairness & Bias Disparity";
            case ROBUSTNESS_INCIDENT -> "Adversarial Stress Fragility";
            case TEMPORAL_DEGRADATION -> "Longitudinal Temporal Degradation";
        };
    }
}
