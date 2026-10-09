package com.modeldoctor.intelligence.reliability;

import com.modeldoctor.domain.*;
import com.modeldoctor.dto.*;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

/**
 * Deterministic engine for computing longitudinal model reliability profiles,
 * bounded scores (0-100), traceable penalty breakdowns, recovery metrics,
 * durability rates, and governance recommendations.
 */
@Component
public class ModelReliabilityEngine {

    public static class EvaluationContext {
        public String modelLineageId;
        public String modelName;
        public List<DiagnosticRun> baselineRuns;
        public List<DiagnosticHealthSnapshot> healthSnapshots;
        public List<DiagnosticIncident> incidents;
        public List<IssueTrackDto> issueTracks;
        public List<RemediationDurabilityDto> remediationDurabilities;
        public ModelHealthDecisionDto currentHealth;
        public DiagnosticMonitoringPolicy policy;

        public EvaluationContext(String modelLineageId,
                                 String modelName,
                                 List<DiagnosticRun> baselineRuns,
                                 List<DiagnosticHealthSnapshot> healthSnapshots,
                                 List<DiagnosticIncident> incidents,
                                 List<IssueTrackDto> issueTracks,
                                 List<RemediationDurabilityDto> remediationDurabilities,
                                 ModelHealthDecisionDto currentHealth,
                                 DiagnosticMonitoringPolicy policy) {
            this.modelLineageId = modelLineageId;
            this.modelName = modelName;
            this.baselineRuns = baselineRuns != null ? baselineRuns : Collections.emptyList();
            this.healthSnapshots = healthSnapshots != null ? healthSnapshots : Collections.emptyList();
            this.incidents = incidents != null ? incidents : Collections.emptyList();
            this.issueTracks = issueTracks != null ? issueTracks : Collections.emptyList();
            this.remediationDurabilities = remediationDurabilities != null ? remediationDurabilities : Collections.emptyList();
            this.currentHealth = currentHealth;
            this.policy = policy;
        }
    }

    public ModelReliabilityProfileDto evaluateReliability(EvaluationContext ctx) {
        ModelReliabilityProfileDto profile = new ModelReliabilityProfileDto();
        profile.setModelLineageId(ctx.modelLineageId);
        profile.setModelName(ctx.modelName != null ? ctx.modelName : ctx.modelLineageId);
        profile.setObservationWindow(ctx.policy != null && ctx.policy.getObservationWindow() != null ?
                ctx.policy.getObservationWindow() : "ALL_AVAILABLE");
        profile.setOperationalRunCount(ctx.baselineRuns.size());
        profile.setUpdatedAt(Instant.now());

        ModelHealthState currentHealthState = ctx.currentHealth != null && ctx.currentHealth.getOverallState() != null && ctx.currentHealth.getOverallState() != ModelHealthState.UNKNOWN ?
                ctx.currentHealth.getOverallState() :
                (!ctx.healthSnapshots.isEmpty() ? ctx.healthSnapshots.get(0).getOverallState() : ModelHealthState.UNKNOWN);
        profile.setCurrentHealthState(currentHealthState);

        int degradedDims = 0;
        if (ctx.currentHealth != null && ctx.currentHealth.getHealthVector() != null) {
            degradedDims = (int) ctx.currentHealth.getHealthVector().values().stream()
                    .filter(d -> d.getState() == DimensionHealthState.DEGRADED || d.getState() == DimensionHealthState.CRITICAL)
                    .count();
        } else if (currentHealthState == ModelHealthState.CRITICAL || currentHealthState == ModelHealthState.DEGRADED) {
            degradedDims = 1;
        }
        profile.setDegradedDimensionsCount(degradedDims);

        // 1. Data Sufficiency & Confidence
        DecisionConfidence confidence = determineConfidence(ctx.baselineRuns.size(), ctx.healthSnapshots.size());
        profile.setReliabilityConfidence(confidence);

        if (ctx.baselineRuns.isEmpty() || confidence == DecisionConfidence.INSUFFICIENT) {
            profile.setReliabilityScore(50);
            profile.setReliabilityState(ModelReliabilityState.RELIABILITY_UNKNOWN);
            profile.setGrade("N/A");
            profile.setTrend(ReliabilityTrend.INSUFFICIENT_DATA);
            profile.setGovernanceRecommendation(GovernanceRecommendation.INSUFFICIENT_EVIDENCE);
            profile.setRecommendationReason("Insufficient operational baseline history to establish authoritative reliability profile.");

            ReliabilityScoreBreakdownDto breakdown = new ReliabilityScoreBreakdownDto(100, 50, 0, 50,
                    List.of(new ReliabilityScoreBreakdownDto.ScoreItem("INSUFFICIENT_DATA",
                            "Model lineage has fewer than 2 operational baseline runs", -50,
                            String.format("Observed baseline runs: %d", ctx.baselineRuns.size()))));
            profile.setScoreBreakdown(breakdown);

            profile.setRecoveryProfile(new RecoveryProfileDto(0, 0, 0, 0.0, 0, 0, "No operational degradation history."));
            profile.setRemediationDurability(new RemediationDurabilitySummaryDto(0, 0, 0, 0, 0, 0, 0.0, "No remediation history."));
            return profile;
        }

        // 2. Aggregate Incident Metrics
        int activeInc = 0;
        int criticalInc = 0;
        int highInc = 0;
        int recurringInc = 0;
        int reopenedInc = 0;
        int unresolvedInc = 0;
        String lastIncidentCode = null;

        for (DiagnosticIncident inc : ctx.incidents) {
            if (lastIncidentCode == null) {
                lastIncidentCode = inc.getIncidentCode();
            }
            if (inc.getLifecycleState() != IncidentLifecycleState.RESOLVED) {
                activeInc++;
                unresolvedInc++;
                String sev = inc.getSeverity() != null ? inc.getSeverity().name() : "MEDIUM";
                if ("CRITICAL".equalsIgnoreCase(sev)) {
                    criticalInc++;
                } else if ("HIGH".equalsIgnoreCase(sev)) {
                    highInc++;
                }
            }
            if (inc.isRecurring()) {
                recurringInc++;
            }
            if (inc.getReopenedCount() > 0) {
                reopenedInc += inc.getReopenedCount();
            }
        }

        profile.setActiveIncidentCount(activeInc);
        profile.setCriticalIncidentCount(criticalInc);
        profile.setHistoricalIncidentCount(ctx.incidents.size());
        profile.setRecurringIncidentCount(recurringInc);
        profile.setReopenedIncidentCount(reopenedInc);
        profile.setUnresolvedIncidentCount(unresolvedInc);
        profile.setLastIncidentCode(lastIncidentCode);

        // 3. Aggregate Remediation Durability
        RemediationDurabilitySummaryDto durabilitySummary = computeRemediationDurability(ctx.remediationDurabilities);
        profile.setRemediationDurability(durabilitySummary);
        profile.setRemediationCount(durabilitySummary.getProposedCount());
        profile.setValidatedRemediationCount(durabilitySummary.getValidatedCount());
        profile.setFailedRemediationCount(durabilitySummary.getFailedCount());

        // 4. Longitudinal Trajectory & Recovery Profile
        List<ReliabilityTrajectoryPointDto> trajectory = buildTrajectory(ctx.baselineRuns, ctx.healthSnapshots, ctx.incidents);
        profile.setTrajectory(trajectory);

        RecoveryProfileDto recoveryProfile = computeRecoveryProfile(trajectory);
        profile.setRecoveryProfile(recoveryProfile);
        profile.setRecoveryRate(recoveryProfile.getRecoveryRate());

        // 5. Linear Trend & Regression
        TrendResult trendResult = computeReliabilityTrend(trajectory);
        profile.setTrend(trendResult.trend);
        profile.setTrendSlope(trendResult.slope);
        profile.setTrendR2(trendResult.r2);

        // 6. Traceable Score Calculation
        ReliabilityScoreBreakdownDto scoreBreakdown = computeScoreBreakdown(
                ctx,
                activeInc,
                criticalInc,
                highInc,
                reopenedInc,
                recurringInc,
                durabilitySummary,
                recoveryProfile,
                trendResult
        );
        profile.setScoreBreakdown(scoreBreakdown);
        profile.setReliabilityScore(scoreBreakdown.getNetScore());

        // 7. Deterministic Reliability State
        ModelReliabilityState state = determineReliabilityState(
                scoreBreakdown.getNetScore(),
                currentHealthState,
                criticalInc,
                activeInc,
                reopenedInc,
                trendResult.trend,
                recoveryProfile
        );
        profile.setReliabilityState(state);

        // 8. Deterministic Grade
        profile.setGrade(determineGrade(scoreBreakdown.getNetScore(), state));

        // 9. Risk Factors & Strengths
        List<ReliabilityRiskFactorDto> riskFactors = generateRiskFactors(
                ctx,
                state,
                activeInc,
                criticalInc,
                reopenedInc,
                durabilitySummary,
                trendResult,
                recoveryProfile
        );
        profile.setRiskFactors(riskFactors);

        List<ReliabilityStrengthDto> strengths = generateStrengths(
                ctx,
                scoreBreakdown.getNetScore(),
                state,
                activeInc,
                durabilitySummary,
                recoveryProfile,
                trendResult
        );
        profile.setStrengths(strengths);

        // 10. Governance Recommendation
        GovernanceRecommendation recommendation = determineRecommendation(
                state,
                scoreBreakdown.getNetScore(),
                criticalInc,
                activeInc,
                reopenedInc,
                trendResult.trend,
                confidence
        );
        profile.setGovernanceRecommendation(recommendation);
        profile.setRecommendationReason(generateRecommendationReason(recommendation, state, scoreBreakdown.getNetScore(), criticalInc, trendResult.trend));

        // 11. Last Run Identifiers
        findRunMilestones(ctx.healthSnapshots, profile);

        return profile;
    }

    private DecisionConfidence determineConfidence(int runCount, int snapshotCount) {
        if (runCount < 2) return DecisionConfidence.INSUFFICIENT;
        if (runCount < 4) return DecisionConfidence.LOW;
        if (runCount < 8) return DecisionConfidence.MEDIUM;
        return DecisionConfidence.HIGH;
    }

    private RemediationDurabilitySummaryDto computeRemediationDurability(List<RemediationDurabilityDto> durabilities) {
        if (durabilities == null || durabilities.isEmpty()) {
            return new RemediationDurabilitySummaryDto(0, 0, 0, 0, 0, 0, 100.0, "No remediation hypotheses evaluated for this lineage.");
        }

        int proposed = 0;
        int validated = 0;
        int sustained = 0;
        int temporary = 0;
        int failed = 0;
        int insufficient = 0;

        for (RemediationDurabilityDto d : durabilities) {
            proposed++;
            String status = d.getDurabilityStatus() != null ? d.getDurabilityStatus().toUpperCase() : "PROPOSED";
            switch (status) {
                case "SUSTAINED" -> { sustained++; validated++; }
                case "TEMPORARY" -> { temporary++; validated++; }
                case "FAILED" -> failed++;
                case "INSUFFICIENT_FOLLOWUP", "PENDING" -> insufficient++;
                case "VALIDATED" -> validated++;
            }
        }

        int evaluated = sustained + temporary + failed;
        double rate = evaluated > 0 ? ((double) sustained / evaluated) * 100.0 : 100.0;

        String summary = String.format("Remediation durability: %d sustained, %d temporary, %d failed out of %d evaluated.",
                sustained, temporary, failed, evaluated);

        return new RemediationDurabilitySummaryDto(proposed, validated, sustained, temporary, failed, insufficient,
                Math.round(rate * 10.0) / 10.0, summary);
    }

    private List<ReliabilityTrajectoryPointDto> buildTrajectory(
            List<DiagnosticRun> baselineRuns,
            List<DiagnosticHealthSnapshot> healthSnapshots,
            List<DiagnosticIncident> incidents) {

        Map<String, DiagnosticHealthSnapshot> snapMap = new HashMap<>();
        if (healthSnapshots != null) {
            for (DiagnosticHealthSnapshot s : healthSnapshots) {
                if (s.getRunId() != null) snapMap.put(s.getRunId(), s);
            }
        }

        List<ReliabilityTrajectoryPointDto> points = new ArrayList<>();
        int runningScore = 95;

        for (DiagnosticRun run : baselineRuns) {
            DiagnosticHealthSnapshot snap = snapMap.get(run.getId());
            ModelHealthState healthState = snap != null ? snap.getOverallState() : ModelHealthState.HEALTHY;
            int healthIndex = snap != null && snap.getHealthIndex() != null ? snap.getHealthIndex() : 90;

            int activeInc = 0;
            int criticalInc = 0;
            if (incidents != null) {
                for (DiagnosticIncident inc : incidents) {
                    if (inc.getLastSeenRunId() != null && inc.getLastSeenRunId().equalsIgnoreCase(run.getId())) {
                        activeInc++;
                        if ("CRITICAL".equalsIgnoreCase(String.valueOf(inc.getSeverity()))) {
                            criticalInc++;
                        }
                    }
                }
            }

            // Trajectory score synthesis
            if (healthState == ModelHealthState.CRITICAL) {
                runningScore = Math.max(30, Math.min(runningScore - 15, healthIndex));
            } else if (healthState == ModelHealthState.DEGRADED) {
                runningScore = Math.max(50, Math.min(runningScore - 8, healthIndex));
            } else if (healthState == ModelHealthState.RECOVERING) {
                runningScore = Math.min(85, runningScore + 6);
            } else {
                runningScore = Math.min(100, runningScore + 4);
            }

            ModelReliabilityState relState;
            if (runningScore >= 88 && healthState == ModelHealthState.HEALTHY) {
                relState = ModelReliabilityState.RELIABILITY_HEALTHY;
            } else if (runningScore >= 75) {
                relState = ModelReliabilityState.RELIABILITY_STABLE;
            } else if (runningScore >= 60) {
                relState = ModelReliabilityState.RELIABILITY_DEGRADED;
            } else if (runningScore >= 45) {
                relState = ModelReliabilityState.RELIABILITY_AT_RISK;
            } else {
                relState = ModelReliabilityState.RELIABILITY_CRITICAL;
            }

            points.add(new ReliabilityTrajectoryPointDto(
                    run.getId(),
                    "BASELINE",
                    runningScore,
                    relState,
                    healthState,
                    healthIndex,
                    activeInc,
                    criticalInc,
                    run.getCreatedAt() != null ? run.getCreatedAt() : Instant.now()
            ));
        }

        return points;
    }

    private RecoveryProfileDto computeRecoveryProfile(List<ReliabilityTrajectoryPointDto> trajectory) {
        if (trajectory.size() < 2) {
            return new RecoveryProfileDto(0, 0, 0, 100.0, 0, 0, "Insufficient trajectory for recovery cycle evaluation.");
        }

        int degradations = 0;
        int recoveries = 0;
        int regressions = 0;
        boolean inDegradedPeriod = false;

        for (int i = 0; i < trajectory.size(); i++) {
            ReliabilityTrajectoryPointDto pt = trajectory.get(i);
            boolean isDegraded = pt.getHealthState() == ModelHealthState.DEGRADED || pt.getHealthState() == ModelHealthState.CRITICAL;

            if (isDegraded && !inDegradedPeriod) {
                degradations++;
                inDegradedPeriod = true;
                if (recoveries > 0) {
                    regressions++;
                }
            } else if (!isDegraded && inDegradedPeriod) {
                recoveries++;
                inDegradedPeriod = false;
            }
        }

        int unresolved = inDegradedPeriod ? 1 : 0;
        double rate = degradations > 0 ? ((double) recoveries / degradations) * 100.0 : 100.0;
        String summary = String.format("Recovery behavior: %d degradation periods, %d recovered (%d%% recovery rate), %d regressions.",
                degradations, recoveries, (int) Math.round(rate), regressions);

        return new RecoveryProfileDto(degradations, recoveries, unresolved, Math.round(rate * 10.0) / 10.0, regressions, degradations, summary);
    }

    public static class TrendResult {
        public ReliabilityTrend trend;
        public Double slope;
        public Double r2;

        public TrendResult(ReliabilityTrend trend, Double slope, Double r2) {
            this.trend = trend;
            this.slope = slope;
            this.r2 = r2;
        }
    }

    private TrendResult computeReliabilityTrend(List<ReliabilityTrajectoryPointDto> trajectory) {
        int n = trajectory.size();
        if (n < 3) {
            return new TrendResult(ReliabilityTrend.INSUFFICIENT_DATA, null, null);
        }

        double sumX = 0;
        double sumY = 0;
        double sumXY = 0;
        double sumX2 = 0;
        double sumY2 = 0;

        for (int i = 0; i < n; i++) {
            double x = i;
            double y = trajectory.get(i).getReliabilityScore();
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
            sumY2 += y * y;
        }

        double denominator = (n * sumX2 - sumX * sumX);
        if (Math.abs(denominator) < 1e-9) {
            return new TrendResult(ReliabilityTrend.STABLE, 0.0, 1.0);
        }

        double slope = (n * sumXY - sumX * sumY) / denominator;
        double numeratorR = (n * sumXY - sumX * sumY);
        double denomR = Math.sqrt((n * sumX2 - sumX * sumX) * (n * sumY2 - sumY * sumY));
        double r2 = denomR > 1e-9 ? Math.pow(numeratorR / denomR, 2) : 0.0;

        ReliabilityTrend trend;
        if (slope < -1.5) {
            trend = ReliabilityTrend.DEGRADING;
        } else if (slope > 1.5) {
            trend = ReliabilityTrend.IMPROVING;
        } else {
            trend = ReliabilityTrend.STABLE;
        }

        return new TrendResult(trend, Math.round(slope * 1000.0) / 1000.0, Math.round(r2 * 1000.0) / 1000.0);
    }

    private ReliabilityScoreBreakdownDto computeScoreBreakdown(
            EvaluationContext ctx,
            int activeInc,
            int criticalInc,
            int highInc,
            int reopenedInc,
            int recurringInc,
            RemediationDurabilitySummaryDto durability,
            RecoveryProfileDto recovery,
            TrendResult trend) {

        int base = 100;
        List<ReliabilityScoreBreakdownDto.ScoreItem> items = new ArrayList<>();
        int deductions = 0;
        int bonuses = 0;

        // Health State Penalties
        ModelHealthState hState = ctx.currentHealth != null && ctx.currentHealth.getOverallState() != null && ctx.currentHealth.getOverallState() != ModelHealthState.UNKNOWN ?
                ctx.currentHealth.getOverallState() :
                (!ctx.healthSnapshots.isEmpty() ? ctx.healthSnapshots.get(0).getOverallState() : ModelHealthState.UNKNOWN);

        if (hState == ModelHealthState.CRITICAL) {
            int pts = 25;
            deductions += pts;
            items.add(new ReliabilityScoreBreakdownDto.ScoreItem("CRITICAL_HEALTH_STATE",
                    "Current operational health evaluated in CRITICAL state", -pts,
                    "Active critical degradation observed in operational baseline"));
        } else if (hState == ModelHealthState.DEGRADED) {
            int pts = 12;
            deductions += pts;
            items.add(new ReliabilityScoreBreakdownDto.ScoreItem("DEGRADED_HEALTH_STATE",
                    "Current operational health evaluated in DEGRADED state", -pts,
                    "Active degraded dimension observed in operational baseline"));
        }

        // Incident Penalties
        if (criticalInc > 0) {
            int pts = Math.min(50, criticalInc * 25);
            deductions += pts;
            items.add(new ReliabilityScoreBreakdownDto.ScoreItem("CRITICAL_INCIDENTS",
                    "Active CRITICAL priority incidents burdening operational reliability", -pts,
                    String.format("%d active critical incidents", criticalInc)));
        }

        if (highInc > 0) {
            int pts = Math.min(20, highInc * 8);
            deductions += pts;
            items.add(new ReliabilityScoreBreakdownDto.ScoreItem("HIGH_INCIDENTS",
                    "Active HIGH priority incidents impacting model reliability", -pts,
                    String.format("%d active high incidents", highInc)));
        }

        if (reopenedInc > 0) {
            int pts = Math.min(18, reopenedInc * 6);
            deductions += pts;
            items.add(new ReliabilityScoreBreakdownDto.ScoreItem("REOPENED_INCIDENTS",
                    "Repeated incident reopenings indicating unresolved root cause", -pts,
                    String.format("%d incident reopening events", reopenedInc)));
        }

        if (recurringInc > 0) {
            int pts = Math.min(12, recurringInc * 4);
            deductions += pts;
            items.add(new ReliabilityScoreBreakdownDto.ScoreItem("RECURRING_INCIDENTS",
                    "Recurring operational incident patterns", -pts,
                    String.format("%d recurring incident occurrences", recurringInc)));
        }

        // Durability Penalties
        if (durability.getFailedCount() > 0) {
            int pts = Math.min(15, durability.getFailedCount() * 8);
            deductions += pts;
            items.add(new ReliabilityScoreBreakdownDto.ScoreItem("FAILED_REMEDIATION",
                    "Failed remediation hypotheses observed during experimental validation", -pts,
                    String.format("%d failed remediations", durability.getFailedCount())));
        }

        if (durability.getTemporaryCount() > 0) {
            int pts = Math.min(12, durability.getTemporaryCount() * 6);
            deductions += pts;
            items.add(new ReliabilityScoreBreakdownDto.ScoreItem("UNSUSTAINED_REMEDIATION",
                    "Remediations whose operational improvement was not sustained in production", -pts,
                    String.format("%d temporary / unsustained remediations", durability.getTemporaryCount())));
        }

        // Regression Penalties
        if (recovery.getRegressionsAfterRecoveryCount() > 0) {
            int pts = Math.min(16, recovery.getRegressionsAfterRecoveryCount() * 8);
            deductions += pts;
            items.add(new ReliabilityScoreBreakdownDto.ScoreItem("REGRESSION_AFTER_RECOVERY",
                    "Model operational regression detected following previous recovery", -pts,
                    String.format("%d post-recovery regressions", recovery.getRegressionsAfterRecoveryCount())));
        }

        // Trend Penalty / Bonus
        if (trend.trend == ReliabilityTrend.DEGRADING) {
            int pts = 10;
            deductions += pts;
            items.add(new ReliabilityScoreBreakdownDto.ScoreItem("DEGRADING_TREND",
                    "Longitudinal operational reliability trend is actively deteriorating", -pts,
                    String.format("Slope beta = %.3f, R2 = %.2f", trend.slope != null ? trend.slope : -1.5, trend.r2 != null ? trend.r2 : 0.5)));
        }

        // Positive Evidence Bonuses
        if (ctx.baselineRuns.size() >= 5 && activeInc == 0 && criticalInc == 0) {
            int pts = 5;
            bonuses += pts;
            items.add(new ReliabilityScoreBreakdownDto.ScoreItem("CLEAN_HISTORY",
                    "Consistent clean operational baseline run history", pts,
                    String.format("%d operational runs without active incidents", ctx.baselineRuns.size())));
        }

        if (durability.getSustainedCount() > 0) {
            int pts = Math.min(10, durability.getSustainedCount() * 5);
            bonuses += pts;
            items.add(new ReliabilityScoreBreakdownDto.ScoreItem("SUSTAINED_REMEDIATION",
                    "Verified durable remediations sustained in operational production", pts,
                    String.format("%d sustained remediations", durability.getSustainedCount())));
        }

        if (recovery.getRecoveredEventsCount() > 0 && recovery.getUnresolvedEventsCount() == 0) {
            int pts = 5;
            bonuses += pts;
            items.add(new ReliabilityScoreBreakdownDto.ScoreItem("SUCCESSFUL_RECOVERY",
                    "Demonstrated full operational recovery from previous degradations", pts,
                    String.format("%d successful recoveries", recovery.getRecoveredEventsCount())));
        }

        int net = Math.max(0, Math.min(100, base - deductions + bonuses));
        return new ReliabilityScoreBreakdownDto(base, deductions, bonuses, net, items);
    }

    private ModelReliabilityState determineReliabilityState(
            int score,
            ModelHealthState currentHealth,
            int criticalInc,
            int activeInc,
            int reopenedInc,
            ReliabilityTrend trend,
            RecoveryProfileDto recovery) {

        if (currentHealth == ModelHealthState.CRITICAL || criticalInc >= 1 || score < 50) {
            return ModelReliabilityState.RELIABILITY_CRITICAL;
        }

        if ((trend == ReliabilityTrend.DEGRADING && score < 75) || reopenedInc >= 2 || score < 65) {
            return ModelReliabilityState.RELIABILITY_AT_RISK;
        }

        if (currentHealth == ModelHealthState.DEGRADED || activeInc > 0 || score < 78) {
            return ModelReliabilityState.RELIABILITY_DEGRADED;
        }

        if (currentHealth == ModelHealthState.RECOVERING || (recovery.getRecoveredEventsCount() > 0 && recovery.getUnresolvedEventsCount() == 0 && score < 88)) {
            return ModelReliabilityState.RELIABILITY_RECOVERING;
        }

        if (score >= 80 && activeInc == 0 && criticalInc == 0) {
            return ModelReliabilityState.RELIABILITY_HEALTHY;
        }

        return ModelReliabilityState.RELIABILITY_STABLE;
    }

    private String determineGrade(int score, ModelReliabilityState state) {
        if (state == ModelReliabilityState.RELIABILITY_CRITICAL) return "F";
        if (score >= 90) return "A";
        if (score >= 80) return "B";
        if (score >= 70) return "C";
        if (score >= 60) return "D";
        return "F";
    }

    private List<ReliabilityRiskFactorDto> generateRiskFactors(
            EvaluationContext ctx,
            ModelReliabilityState state,
            int activeInc,
            int criticalInc,
            int reopenedInc,
            RemediationDurabilitySummaryDto durability,
            TrendResult trend,
            RecoveryProfileDto recovery) {

        List<ReliabilityRiskFactorDto> risks = new ArrayList<>();

        if (criticalInc > 0) {
            risks.add(new ReliabilityRiskFactorDto("CRITICAL", "INCIDENT",
                    "Active Critical Operational Incidents",
                    String.format("%d critical incidents currently unresolved and affecting operational workload.", criticalInc),
                    "INCIDENTS"));
        }

        if (trend.trend == ReliabilityTrend.DEGRADING) {
            risks.add(new ReliabilityRiskFactorDto("HIGH", "TREND",
                    "Degrading Longitudinal Reliability Trend",
                    String.format("Operational reliability is actively deteriorating over time (trend slope beta = %.3f).",
                            trend.slope != null ? trend.slope : -1.5),
                    "TRAJECTORY"));
        }

        if (reopenedInc > 0) {
            risks.add(new ReliabilityRiskFactorDto("HIGH", "INCIDENT",
                    "Repeated Incident Reopenings",
                    String.format("%d incident reopening events detected, indicating underlying issue recurrence.", reopenedInc),
                    "INCIDENTS"));
        }

        if (durability.getTemporaryCount() > 0 || durability.getFailedCount() > 0) {
            risks.add(new ReliabilityRiskFactorDto("MEDIUM", "REMEDIATION",
                    "Remediation Durability Risk",
                    String.format("Remediation durability rate is %.1f%% (%d failed, %d temporary).",
                            durability.getDurabilityRate() != null ? durability.getDurabilityRate() : 0.0,
                            durability.getFailedCount(), durability.getTemporaryCount()),
                    "REMEDIATIONS"));
        }

        if (recovery.getRegressionsAfterRecoveryCount() > 0) {
            risks.add(new ReliabilityRiskFactorDto("MEDIUM", "RECOVERY",
                    "Post-Recovery Operational Regressions",
                    String.format("%d regressions observed following previous recovery periods.", recovery.getRegressionsAfterRecoveryCount()),
                    "RECOVERY"));
        }

        return risks;
    }

    private List<ReliabilityStrengthDto> generateStrengths(
            EvaluationContext ctx,
            int score,
            ModelReliabilityState state,
            int activeInc,
            RemediationDurabilitySummaryDto durability,
            RecoveryProfileDto recovery,
            TrendResult trend) {

        List<ReliabilityStrengthDto> strengths = new ArrayList<>();

        if (score >= 80 && activeInc == 0) {
            strengths.add(new ReliabilityStrengthDto("STABILITY",
                    "Zero Active Operational Incidents",
                    String.format("Model lineage has maintained clean operational status over %d baseline runs.", ctx.baselineRuns.size()),
                    "INCIDENTS"));
        }

        if (durability.getSustainedCount() > 0) {
            strengths.add(new ReliabilityStrengthDto("REMEDIATION",
                    "Verified Durable Remediations",
                    String.format("%d remediation strategies successfully sustained in operational production.", durability.getSustainedCount()),
                    "REMEDIATIONS"));
        }

        if (recovery.getRecoveredEventsCount() > 0 && recovery.getUnresolvedEventsCount() == 0) {
            strengths.add(new ReliabilityStrengthDto("RECOVERY",
                    "High Recovery Rate",
                    String.format("Model demonstrated %.0f%% recovery rate from previous operational degradations.", recovery.getRecoveryRate()),
                    "RECOVERY"));
        }

        if (trend.trend == ReliabilityTrend.IMPROVING) {
            strengths.add(new ReliabilityStrengthDto("TREND",
                    "Improving Reliability Trajectory",
                    "Longitudinal operational reliability score is actively improving over recent runs.",
                    "TRAJECTORY"));
        }

        return strengths;
    }

    private GovernanceRecommendation determineRecommendation(
            ModelReliabilityState state,
            int score,
            int criticalInc,
            int activeInc,
            int reopenedInc,
            ReliabilityTrend trend,
            DecisionConfidence confidence) {

        if (confidence == DecisionConfidence.INSUFFICIENT) {
            return GovernanceRecommendation.INSUFFICIENT_EVIDENCE;
        }

        if (state == ModelReliabilityState.RELIABILITY_CRITICAL || (criticalInc > 0 && trend == ReliabilityTrend.DEGRADING) || score < 45) {
            return GovernanceRecommendation.ESCALATE;
        }

        if (state == ModelReliabilityState.RELIABILITY_AT_RISK || criticalInc > 0 || (activeInc >= 2 && trend == ReliabilityTrend.DEGRADING)) {
            return GovernanceRecommendation.PRIORITY_REVIEW;
        }

        if (state == ModelReliabilityState.RELIABILITY_DEGRADED || trend == ReliabilityTrend.DEGRADING || reopenedInc >= 2) {
            return GovernanceRecommendation.REVIEW_REQUIRED;
        }

        if (state == ModelReliabilityState.RELIABILITY_STABLE || state == ModelReliabilityState.RELIABILITY_RECOVERING || activeInc > 0) {
            return GovernanceRecommendation.MONITOR;
        }

        return GovernanceRecommendation.NORMAL_OPERATION;
    }

    private String generateRecommendationReason(
            GovernanceRecommendation rec,
            ModelReliabilityState state,
            int score,
            int criticalInc,
            ReliabilityTrend trend) {

        return switch (rec) {
            case ESCALATE -> String.format("ESCALATE: Severe operational risk detected (Score: %d, State: %s, %d critical incidents, Trend: %s). Immediate governance intervention required.",
                    score, state.name(), criticalInc, trend.name());
            case PRIORITY_REVIEW -> String.format("PRIORITY REVIEW: Elevated operational risk (Score: %d, State: %s, %d critical incidents). Operator attention recommended prior to production impact.",
                    score, state.name(), criticalInc);
            case REVIEW_REQUIRED -> String.format("REVIEW REQUIRED: Material reliability degradation observed (Score: %d, Trend: %s). Investigation and remediation verification advised.",
                    score, trend.name());
            case MONITOR -> String.format("MONITOR: Moderate operational condition (Score: %d, State: %s). Maintain continuous baseline monitoring.",
                    score, state.name());
            case NORMAL_OPERATION -> String.format("NORMAL OPERATION: Model lineage exhibits healthy longitudinal operational reliability (Score: %d, State: %s).",
                    score, state.name());
            case INSUFFICIENT_EVIDENCE -> "INSUFFICIENT EVIDENCE: Additional operational baseline runs required for conclusive governance recommendation.";
        };
    }

    private void findRunMilestones(List<DiagnosticHealthSnapshot> snapshots, ModelReliabilityProfileDto profile) {
        if (snapshots == null) return;
        for (DiagnosticHealthSnapshot s : snapshots) {
            if (s.getOverallState() == ModelHealthState.HEALTHY && profile.getLastHealthyRunId() == null) {
                profile.setLastHealthyRunId(s.getRunId());
            } else if (s.getOverallState() == ModelHealthState.DEGRADED && profile.getLastDegradedRunId() == null) {
                profile.setLastDegradedRunId(s.getRunId());
            } else if (s.getOverallState() == ModelHealthState.CRITICAL && profile.getLastCriticalRunId() == null) {
                profile.setLastCriticalRunId(s.getRunId());
            }
        }
    }
}
