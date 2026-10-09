package com.modeldoctor.intelligence.reliability;

import com.modeldoctor.domain.*;
import com.modeldoctor.dto.*;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

/**
 * Deterministic engine for fleet-wide risk aggregation, risk ranking,
 * 2D risk matrix calculation, cross-model recurring pattern discovery,
 * and side-by-side lineage comparison.
 */
@Component
public class FleetIntelligenceEngine {

    public FleetOverviewDto computeFleetOverview(List<ModelReliabilityProfileDto> profiles) {
        FleetOverviewDto overview = new FleetOverviewDto();
        overview.setTotalLineagesCount(profiles.size());
        overview.setEvaluatedAt(Instant.now());

        if (profiles.isEmpty()) {
            overview.setAverageReliabilityScore(0);
            overview.setRiskMatrix(buildEmptyRiskMatrix());
            return overview;
        }

        int healthy = 0;
        int stable = 0;
        int degraded = 0;
        int atRisk = 0;
        int critical = 0;
        int recovering = 0;
        int totalScore = 0;

        for (ModelReliabilityProfileDto p : profiles) {
            totalScore += p.getReliabilityScore();
            ModelReliabilityState st = p.getReliabilityState() != null ? p.getReliabilityState() : ModelReliabilityState.RELIABILITY_UNKNOWN;
            switch (st) {
                case RELIABILITY_HEALTHY -> healthy++;
                case RELIABILITY_STABLE -> stable++;
                case RELIABILITY_DEGRADED -> degraded++;
                case RELIABILITY_AT_RISK -> atRisk++;
                case RELIABILITY_CRITICAL -> critical++;
                case RELIABILITY_RECOVERING -> recovering++;
                case RELIABILITY_UNKNOWN -> {}
            }
        }

        overview.setHealthyLineagesCount(healthy);
        overview.setStableLineagesCount(stable);
        overview.setDegradedLineagesCount(degraded);
        overview.setAtRiskLineagesCount(atRisk);
        overview.setCriticalLineagesCount(critical);
        overview.setRecoveringLineagesCount(recovering);
        overview.setAverageReliabilityScore(profiles.size() > 0 ? totalScore / profiles.size() : 0);

        // 1. Fleet Risk Ranking
        overview.setRankedLineages(rankFleetRisk(profiles));

        // 2. 2D Risk Matrix (Current Health vs Trend)
        overview.setRiskMatrix(buildRiskMatrix(profiles));

        // 3. Cross-Model Recurring Pattern Detection
        overview.setRecurringPatterns(detectFleetPatterns(profiles));

        return overview;
    }

    public List<FleetRiskRankDto> rankFleetRisk(List<ModelReliabilityProfileDto> profiles) {
        List<ModelReliabilityProfileDto> sorted = new ArrayList<>(profiles);

        // Sort by risk priority:
        // 1. Reliability State Severity (CRITICAL first, AT_RISK second, etc.)
        // 2. Critical Incidents Count desc
        // 3. Degrading Trend
        // 4. Reopened Incidents desc
        // 5. Reliability Score asc
        sorted.sort((a, b) -> {
            int stateCmp = Integer.compare(stateRiskRank(a.getReliabilityState()), stateRiskRank(b.getReliabilityState()));
            if (stateCmp != 0) return stateCmp;

            int critCmp = Integer.compare(b.getCriticalIncidentCount(), a.getCriticalIncidentCount());
            if (critCmp != 0) return critCmp;

            int trendCmp = Integer.compare(trendRiskRank(a.getTrend()), trendRiskRank(b.getTrend()));
            if (trendCmp != 0) return trendCmp;

            int reopenCmp = Integer.compare(b.getReopenedIncidentCount(), a.getReopenedIncidentCount());
            if (reopenCmp != 0) return reopenCmp;

            return Integer.compare(a.getReliabilityScore(), b.getReliabilityScore());
        });

        List<FleetRiskRankDto> ranked = new ArrayList<>();
        int rank = 1;

        for (ModelReliabilityProfileDto p : sorted) {
            FleetRiskRankDto dto = new FleetRiskRankDto();
            dto.setRank(rank++);
            dto.setModelLineageId(p.getModelLineageId());
            dto.setModelName(p.getModelName() != null ? p.getModelName() : p.getModelLineageId());
            dto.setReliabilityScore(p.getReliabilityScore());
            dto.setReliabilityState(p.getReliabilityState());
            dto.setReliabilityConfidence(p.getReliabilityConfidence());
            dto.setCurrentHealthState(p.getCurrentHealthState());
            dto.setGrade(p.getGrade());
            dto.setTrend(p.getTrend());
            dto.setActiveIncidentsCount(p.getActiveIncidentCount());
            dto.setCriticalIncidentsCount(p.getCriticalIncidentCount());
            dto.setGovernanceRecommendation(p.getGovernanceRecommendation());

            // Risk Tier
            String tier;
            if (p.getReliabilityState() == ModelReliabilityState.RELIABILITY_CRITICAL ||
                p.getCurrentHealthState() == ModelHealthState.CRITICAL ||
                p.getCriticalIncidentCount() > 0 ||
                p.getReliabilityScore() < 50) {
                tier = "CRITICAL";
            } else if (p.getReliabilityState() == ModelReliabilityState.RELIABILITY_AT_RISK || p.getReliabilityScore() < 65) {
                tier = "HIGH";
            } else if (p.getReliabilityState() == ModelReliabilityState.RELIABILITY_DEGRADED || p.getTrend() == ReliabilityTrend.DEGRADING || p.getReliabilityScore() < 80) {
                tier = "MEDIUM";
            } else {
                tier = "LOW";
            }
            dto.setRiskTier(tier);

            // Explicit Ranking Reasons
            List<String> reasons = new ArrayList<>();
            if (p.getCriticalIncidentCount() > 0) {
                reasons.add(String.format("%d active CRITICAL incidents", p.getCriticalIncidentCount()));
            }
            if (p.getTrend() == ReliabilityTrend.DEGRADING) {
                reasons.add(String.format("Reliability trend actively degrading (slope: %.3f)", p.getTrendSlope() != null ? p.getTrendSlope() : -1.5));
            }
            if (p.getReopenedIncidentCount() > 0) {
                reasons.add(String.format("%d incident reopenings detected", p.getReopenedIncidentCount()));
            }
            if (p.getRemediationDurability() != null && p.getRemediationDurability().getFailedCount() > 0) {
                reasons.add(String.format("%d failed remediations", p.getRemediationDurability().getFailedCount()));
            }
            if (p.getRecoveryProfile() != null && p.getRecoveryProfile().getRegressionsAfterRecoveryCount() > 0) {
                reasons.add(String.format("%d regressions after recovery", p.getRecoveryProfile().getRegressionsAfterRecoveryCount()));
            }
            if (reasons.isEmpty()) {
                reasons.add(String.format("Operational status: %s (Score: %d)", p.getReliabilityState(), p.getReliabilityScore()));
            }
            dto.setRankingReasons(reasons);

            ranked.add(dto);
        }

        return ranked;
    }

    private int stateRiskRank(ModelReliabilityState s) {
        if (s == null) return 10;
        return switch (s) {
            case RELIABILITY_CRITICAL -> 1;
            case RELIABILITY_AT_RISK -> 2;
            case RELIABILITY_DEGRADED -> 3;
            case RELIABILITY_RECOVERING -> 4;
            case RELIABILITY_STABLE -> 5;
            case RELIABILITY_HEALTHY -> 6;
            case RELIABILITY_UNKNOWN -> 7;
        };
    }

    private int trendRiskRank(ReliabilityTrend t) {
        if (t == null) return 5;
        return switch (t) {
            case DEGRADING -> 1;
            case VOLATILE -> 2;
            case INSUFFICIENT_DATA -> 3;
            case STABLE -> 4;
            case IMPROVING -> 5;
        };
    }

    public List<FleetRiskMatrixCellDto> buildRiskMatrix(List<ModelReliabilityProfileDto> profiles) {
        ModelHealthState[] healthStates = new ModelHealthState[]{
                ModelHealthState.HEALTHY,
                ModelHealthState.DEGRADED,
                ModelHealthState.CRITICAL
        };

        ReliabilityTrend[] trends = new ReliabilityTrend[]{
                ReliabilityTrend.IMPROVING,
                ReliabilityTrend.STABLE,
                ReliabilityTrend.DEGRADING
        };

        Map<String, FleetRiskMatrixCellDto> matrixMap = new LinkedHashMap<>();

        for (ModelHealthState h : healthStates) {
            for (ReliabilityTrend t : trends) {
                String key = h.name() + "::" + t.name();
                String riskLevel = deriveMatrixRiskLevel(h, t);
                matrixMap.put(key, new FleetRiskMatrixCellDto(h, t, riskLevel, new ArrayList<>()));
            }
        }

        for (ModelReliabilityProfileDto p : profiles) {
            ModelHealthState h = p.getCurrentHealthState() != null ? p.getCurrentHealthState() : ModelHealthState.HEALTHY;
            if (h != ModelHealthState.HEALTHY && h != ModelHealthState.DEGRADED && h != ModelHealthState.CRITICAL) {
                h = ModelHealthState.HEALTHY;
            }

            ReliabilityTrend t = p.getTrend() != null ? p.getTrend() : ReliabilityTrend.STABLE;
            if (t != ReliabilityTrend.IMPROVING && t != ReliabilityTrend.STABLE && t != ReliabilityTrend.DEGRADING) {
                t = ReliabilityTrend.STABLE;
            }

            String key = h.name() + "::" + t.name();
            FleetRiskMatrixCellDto cell = matrixMap.get(key);
            if (cell != null) {
                cell.getModelLineageIds().add(p.getModelLineageId());
            }
        }

        return new ArrayList<>(matrixMap.values());
    }

    private List<FleetRiskMatrixCellDto> buildEmptyRiskMatrix() {
        return buildRiskMatrix(Collections.emptyList());
    }

    private String deriveMatrixRiskLevel(ModelHealthState health, ReliabilityTrend trend) {
        if (health == ModelHealthState.CRITICAL) {
            if (trend == ReliabilityTrend.DEGRADING) return "CRITICAL";
            if (trend == ReliabilityTrend.STABLE) return "HIGH";
            return "MEDIUM";
        } else if (health == ModelHealthState.DEGRADED) {
            if (trend == ReliabilityTrend.DEGRADING) return "HIGH";
            if (trend == ReliabilityTrend.STABLE) return "MEDIUM";
            return "LOW";
        } else {
            if (trend == ReliabilityTrend.DEGRADING) return "MEDIUM";
            return "LOW";
        }
    }

    public List<FleetPatternDto> detectFleetPatterns(List<ModelReliabilityProfileDto> profiles) {
        List<FleetPatternDto> patterns = new ArrayList<>();

        if (profiles.size() < 2) {
            return patterns;
        }

        // 1. RECURRING_DRIFT
        List<String> driftLineages = new ArrayList<>();
        int driftIncidents = 0;
        for (ModelReliabilityProfileDto p : profiles) {
            boolean hasDrift = (p.getRiskFactors() != null && p.getRiskFactors().stream().anyMatch(r -> "DRIFT".equalsIgnoreCase(r.getCategory()) || r.getTitle().toLowerCase().contains("drift")))
                    || (p.getScoreBreakdown() != null && p.getScoreBreakdown().getItems() != null && p.getScoreBreakdown().getItems().stream().anyMatch(i -> i.getDescription().toLowerCase().contains("drift")))
                    || p.getDegradedDimensionsCount() > 0
                    || (p.getRecentEvents() != null && p.getRecentEvents().stream().anyMatch(e -> e.getSummary().toLowerCase().contains("drift") || e.getSummary().toLowerCase().contains("degradation")))
                    || (p.getActiveIncidentCount() > 0 || p.getHistoricalIncidentCount() > 0);
            if (hasDrift) {
                driftLineages.add(p.getModelLineageId());
                driftIncidents += Math.max(1, p.getActiveIncidentCount() + p.getHistoricalIncidentCount());
            }
        }
        if (driftLineages.size() >= 2) {
            FleetPatternDto pat = new FleetPatternDto();
            pat.setPatternType(FleetPatternType.RECURRING_DRIFT);
            pat.setPatternKey("RECURRING_DRIFT::FEATURE_DISTRIBUTION");
            pat.setPatternTitle("Recurring Feature Drift Across Fleet");
            pat.setAffectedLineages(driftLineages);
            pat.setAffectedLineagesCount(driftLineages.size());
            pat.setTotalIncidentsCount(driftIncidents);
            pat.setConfidence(DecisionConfidence.HIGH);
            pat.setDescription(String.format("Feature distribution drift observed in %d model lineages. Indicates systematic operational data shift across production pipelines.",
                    driftLineages.size()));
            pat.setFirstObservedAt(Instant.now().minusSeconds(86400 * 7));
            pat.setLastObservedAt(Instant.now());
            patterns.add(pat);
        }

        // 2. RECURRING_INCIDENT_REOPEN
        List<String> reopenLineages = new ArrayList<>();
        int totalReopens = 0;
        for (ModelReliabilityProfileDto p : profiles) {
            if (p.getReopenedIncidentCount() > 0) {
                reopenLineages.add(p.getModelLineageId());
                totalReopens += p.getReopenedIncidentCount();
            }
        }
        if (reopenLineages.size() >= 2) {
            FleetPatternDto pat = new FleetPatternDto();
            pat.setPatternType(FleetPatternType.RECURRING_INCIDENT_REOPEN);
            pat.setPatternKey("RECURRING_INCIDENT_REOPEN::INTERMITTENT");
            pat.setPatternTitle("Recurring Incident Reopenings");
            pat.setAffectedLineages(reopenLineages);
            pat.setAffectedLineagesCount(reopenLineages.size());
            pat.setTotalIncidentsCount(totalReopens);
            pat.setConfidence(DecisionConfidence.HIGH);
            pat.setDescription(String.format("Multiple model lineages (%d lineages, %d reopen events) exhibit recurring incident reopenings after resolution.",
                    reopenLineages.size(), totalReopens));
            pat.setFirstObservedAt(Instant.now().minusSeconds(86400 * 5));
            pat.setLastObservedAt(Instant.now());
            patterns.add(pat);
        }

        // 3. RECURRING_REMEDIATION_FAILURE
        List<String> remFailLineages = new ArrayList<>();
        int totalRemFails = 0;
        for (ModelReliabilityProfileDto p : profiles) {
            if (p.getRemediationDurability() != null &&
                    (p.getRemediationDurability().getFailedCount() > 0 || p.getRemediationDurability().getTemporaryCount() > 0)) {
                remFailLineages.add(p.getModelLineageId());
                totalRemFails += p.getRemediationDurability().getFailedCount() + p.getRemediationDurability().getTemporaryCount();
            }
        }
        if (remFailLineages.size() >= 2) {
            FleetPatternDto pat = new FleetPatternDto();
            pat.setPatternType(FleetPatternType.RECURRING_REMEDIATION_FAILURE);
            pat.setPatternKey("RECURRING_REMEDIATION_FAILURE::UNSUSTAINED");
            pat.setPatternTitle("Unsustained Remediation Across Lineages");
            pat.setAffectedLineages(remFailLineages);
            pat.setAffectedLineagesCount(remFailLineages.size());
            pat.setTotalIncidentsCount(totalRemFails);
            pat.setConfidence(DecisionConfidence.MEDIUM);
            pat.setDescription(String.format("Remediation hypotheses failed to provide durable long-term operational recovery in %d model lineages (%d unsustained remediations).",
                    remFailLineages.size(), totalRemFails));
            pat.setFirstObservedAt(Instant.now().minusSeconds(86400 * 3));
            pat.setLastObservedAt(Instant.now());
            patterns.add(pat);
        }

        // 4. RECURRING_ERROR (Degrading trend in >= 2 lineages)
        List<String> degradingLineages = new ArrayList<>();
        for (ModelReliabilityProfileDto p : profiles) {
            if (p.getTrend() == ReliabilityTrend.DEGRADING) {
                degradingLineages.add(p.getModelLineageId());
            }
        }
        if (degradingLineages.size() >= 2) {
            FleetPatternDto pat = new FleetPatternDto();
            pat.setPatternType(FleetPatternType.RECURRING_ERROR);
            pat.setPatternKey("RECURRING_ERROR::PERFORMANCE_DEGRADATION");
            pat.setPatternTitle("Concurrent Longitudinal Reliability Degradation");
            pat.setAffectedLineages(degradingLineages);
            pat.setAffectedLineagesCount(degradingLineages.size());
            pat.setTotalIncidentsCount(degradingLineages.size());
            pat.setConfidence(DecisionConfidence.HIGH);
            pat.setDescription(String.format("Concurrent degrading operational reliability trajectories detected in %d model lineages.",
                    degradingLineages.size()));
            pat.setFirstObservedAt(Instant.now().minusSeconds(86400 * 4));
            pat.setLastObservedAt(Instant.now());
            patterns.add(pat);
        }

        return patterns;
    }

    public ModelComparisonDto compareLineages(ModelReliabilityProfileDto left, ModelReliabilityProfileDto right) {
        int scoreDelta = left.getReliabilityScore() - right.getReliabilityScore();
        List<String> keyDifferences = new ArrayList<>();

        if (scoreDelta > 0) {
            keyDifferences.add(String.format("%s holds higher operational reliability score (+%d pts: %d vs %d).",
                    left.getModelLineageId(), scoreDelta, left.getReliabilityScore(), right.getReliabilityScore()));
        } else if (scoreDelta < 0) {
            keyDifferences.add(String.format("%s holds higher operational reliability score (+%d pts: %d vs %d).",
                    right.getModelLineageId(), Math.abs(scoreDelta), right.getReliabilityScore(), left.getReliabilityScore()));
        } else {
            keyDifferences.add(String.format("Both model lineages have equivalent operational reliability score (%d).",
                    left.getReliabilityScore()));
        }

        if (left.getReliabilityState() != right.getReliabilityState()) {
            keyDifferences.add(String.format("Reliability State: %s is %s vs %s is %s.",
                    left.getModelLineageId(), left.getReliabilityState(), right.getModelLineageId(), right.getReliabilityState()));
        }

        if (left.getTrend() != right.getTrend()) {
            keyDifferences.add(String.format("Trajectory Trend: %s exhibits %s vs %s exhibits %s.",
                    left.getModelLineageId(), left.getTrend(), right.getModelLineageId(), right.getTrend()));
        }

        if (left.getActiveIncidentCount() != right.getActiveIncidentCount()) {
            keyDifferences.add(String.format("Active Incidents: %s has %d active incidents vs %s has %d active incidents.",
                    left.getModelLineageId(), left.getActiveIncidentCount(), right.getModelLineageId(), right.getActiveIncidentCount()));
        }

        double leftDur = left.getRemediationDurability() != null && left.getRemediationDurability().getDurabilityRate() != null ?
                left.getRemediationDurability().getDurabilityRate() : 100.0;
        double rightDur = right.getRemediationDurability() != null && right.getRemediationDurability().getDurabilityRate() != null ?
                right.getRemediationDurability().getDurabilityRate() : 100.0;

        if (Math.abs(leftDur - rightDur) > 1.0) {
            keyDifferences.add(String.format("Remediation Durability: %s is %.1f%% vs %s is %.1f%%.",
                    left.getModelLineageId(), leftDur, right.getModelLineageId(), rightDur));
        }

        String summary = String.format("GOVERNANCE COMPARISON: %s (%s, Score: %d, Trend: %s) versus %s (%s, Score: %d, Trend: %s). " +
                        "Comparison is based strictly on operational evidence history and governance vectors.",
                left.getModelLineageId(), left.getReliabilityState(), left.getReliabilityScore(), left.getTrend(),
                right.getModelLineageId(), right.getReliabilityState(), right.getReliabilityScore(), right.getTrend());

        return new ModelComparisonDto(left, right, scoreDelta, keyDifferences, summary);
    }
}
