package com.modeldoctor.intelligence.temporal;

import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
public class PersistenceAnalyzer {

    public static class PersistenceResult {
        public String currentSeverity = "LOW";
        public String previousSeverity = "LOW";
        public String peakSeverity = "LOW";
        public int consecutiveCount = 0;
        public String status = "EMERGING"; // "EMERGING", "PERSISTENT", "RECURRING", "TRANSIENT", "RECOVERED", "ESCALATING", "DEESCALATING"
        public List<String> severityTransitions = new ArrayList<>();
    }

    public PersistenceResult evaluatePersistence(List<String> severitySequence) {
        PersistenceResult res = new PersistenceResult();
        if (severitySequence == null || severitySequence.isEmpty()) {
            return res;
        }

        int n = severitySequence.size();
        res.currentSeverity = severitySequence.get(n - 1);
        res.previousSeverity = n > 1 ? severitySequence.get(n - 2) : res.currentSeverity;

        // Peak severity & severity transitions
        int peakRank = 0;
        int currentRank = severityRank(res.currentSeverity);

        for (int i = 0; i < n; i++) {
            String s = severitySequence.get(i);
            int rank = severityRank(s);
            if (rank > peakRank) {
                peakRank = rank;
                res.peakSeverity = s;
            }
            if (i > 0 && !s.equalsIgnoreCase(severitySequence.get(i - 1))) {
                res.severityTransitions.add(severitySequence.get(i - 1) + " -> " + s);
            }
        }

        // Consecutive count at current severity or elevated rank
        int consec = 0;
        if (currentRank >= 2) {
            for (int i = n - 1; i >= 0; i--) {
                if (severityRank(severitySequence.get(i)) >= 2) {
                    consec++;
                } else {
                    break;
                }
            }
        } else {
            for (int i = n - 1; i >= 0; i--) {
                if (severitySequence.get(i).equalsIgnoreCase(res.currentSeverity)) {
                    consec++;
                } else {
                    break;
                }
            }
        }
        res.consecutiveCount = consec;

        // Count elevated observations (MEDIUM, HIGH, CRITICAL)
        int elevatedCount = 0;
        List<Integer> elevatedIndices = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (severityRank(severitySequence.get(i)) >= 2) { // HIGH or CRITICAL
                elevatedCount++;
                elevatedIndices.add(i);
            }
        }

        // Check monotonicity
        boolean isEscalating = n >= 3;
        boolean isDeescalating = n >= 3;
        for (int i = 1; i < n; i++) {
            int rPrev = severityRank(severitySequence.get(i - 1));
            int rCurr = severityRank(severitySequence.get(i));
            if (rCurr <= rPrev) isEscalating = false;
            if (rCurr >= rPrev) isDeescalating = false;
        }

        // Classify Status
        if (isEscalating && currentRank >= 2) {
            res.status = "ESCALATING";
        } else if (isDeescalating && currentRank <= 1) {
            res.status = "DEESCALATING";
        } else if (currentRank <= 1 && peakRank >= 2 && consec >= 2) {
            res.status = "RECOVERED";
        } else if (currentRank <= 1 && peakRank >= 2 && elevatedCount == 1) {
            res.status = "TRANSIENT";
        } else if (currentRank >= 2 && consec >= 3) {
            res.status = "PERSISTENT";
        } else if (currentRank >= 2 && elevatedCount >= 2 && hasGapBetweenElevated(elevatedIndices)) {
            res.status = "RECURRING";
        } else if (currentRank >= 2 && elevatedCount <= 2 && (elevatedIndices.contains(n - 1) || elevatedIndices.contains(n - 2))) {
            res.status = "EMERGING";
        } else if (currentRank >= 2 && elevatedCount >= 3) {
            res.status = "PERSISTENT";
        } else {
            res.status = (currentRank >= 2) ? "EMERGING" : "TRANSIENT";
        }

        return res;
    }

    private boolean hasGapBetweenElevated(List<Integer> indices) {
        if (indices.size() < 2) return false;
        for (int i = 1; i < indices.size(); i++) {
            if (indices.get(i) - indices.get(i - 1) > 1) {
                return true;
            }
        }
        return false;
    }

    public int severityRank(String severity) {
        if (severity == null) return 0;
        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> 3;
            case "HIGH" -> 2;
            case "MEDIUM" -> 1;
            default -> 0; // LOW, NONE
        };
    }
}
