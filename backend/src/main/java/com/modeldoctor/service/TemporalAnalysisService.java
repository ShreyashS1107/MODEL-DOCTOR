package com.modeldoctor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeldoctor.domain.*;
import com.modeldoctor.dto.*;
import com.modeldoctor.intelligence.normalization.NormalizedModuleData;
import com.modeldoctor.intelligence.normalization.ResultNormalizer;
import com.modeldoctor.intelligence.temporal.*;
import com.modeldoctor.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class TemporalAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(TemporalAnalysisService.class);

    private final DiagnosticRunRepository runRepository;
    private final DiagnosticResultRepository resultRepository;
    private final DiagnosticRemediationRepository remediationRepository;
    private final DiagnosticExperimentRepository experimentRepository;
    private final DiagnosticTemporalObservationRepository observationRepository;
    private final DiagnosticIssueTrackRepository issueTrackRepository;
    private final DiagnosticTemporalAlertRepository alertRepository;
    private final DiagnosticChangePointRepository changePointRepository;

    private final ResultNormalizer resultNormalizer;
    private final TemporalMetricRegistry metricRegistry;
    private final TemporalTrendAnalyzer trendAnalyzer;
    private final PersistenceAnalyzer persistenceAnalyzer;
    private final ChangePointAnalyzer changePointAnalyzer;
    private final IssueTrackAnalyzer issueTrackAnalyzer;
    private final DurabilityAnalyzer durabilityAnalyzer;
    private final TemporalAlertAnalyzer alertAnalyzer;
    private final ObjectMapper objectMapper;

    public TemporalAnalysisService(
            DiagnosticRunRepository runRepository,
            DiagnosticResultRepository resultRepository,
            DiagnosticRemediationRepository remediationRepository,
            DiagnosticExperimentRepository experimentRepository,
            DiagnosticTemporalObservationRepository observationRepository,
            DiagnosticIssueTrackRepository issueTrackRepository,
            DiagnosticTemporalAlertRepository alertRepository,
            DiagnosticChangePointRepository changePointRepository,
            ResultNormalizer resultNormalizer,
            TemporalMetricRegistry metricRegistry,
            TemporalTrendAnalyzer trendAnalyzer,
            PersistenceAnalyzer persistenceAnalyzer,
            ChangePointAnalyzer changePointAnalyzer,
            IssueTrackAnalyzer issueTrackAnalyzer,
            DurabilityAnalyzer durabilityAnalyzer,
            TemporalAlertAnalyzer alertAnalyzer,
            ObjectMapper objectMapper) {
        this.runRepository = runRepository;
        this.resultRepository = resultRepository;
        this.remediationRepository = remediationRepository;
        this.experimentRepository = experimentRepository;
        this.observationRepository = observationRepository;
        this.issueTrackRepository = issueTrackRepository;
        this.alertRepository = alertRepository;
        this.changePointRepository = changePointRepository;
        this.resultNormalizer = resultNormalizer;
        this.metricRegistry = metricRegistry;
        this.trendAnalyzer = trendAnalyzer;
        this.persistenceAnalyzer = persistenceAnalyzer;
        this.changePointAnalyzer = changePointAnalyzer;
        this.issueTrackAnalyzer = issueTrackAnalyzer;
        this.durabilityAnalyzer = durabilityAnalyzer;
        this.alertAnalyzer = alertAnalyzer;
        this.objectMapper = objectMapper;
    }

    public String resolveModelLineageId(String identifier) {
        if (identifier == null || identifier.isBlank()) return "default_model";

        // Check if identifier matches a run directly
        Optional<DiagnosticRun> runOpt = runRepository.findById(identifier);
        if (runOpt.isPresent()) {
            return runOpt.get().getModelName();
        }

        return identifier;
    }

    public List<DiagnosticRun> getLineageRuns(String modelLineageId) {
        String lineage = resolveModelLineageId(modelLineageId);
        List<DiagnosticRun> runs = runRepository.findByModelNameOrderByCreatedAtAsc(lineage);
        if (runs.isEmpty()) {
            // Check if there is a single run with this ID
            Optional<DiagnosticRun> runOpt = runRepository.findById(lineage);
            runOpt.ifPresent(runs::add);
        }
        return runs;
    }

    public ModelLineageHistoryDto getModelLineageHistory(String modelLineageId, String window) {
        String lineage = resolveModelLineageId(modelLineageId);
        List<DiagnosticRun> allRuns = getLineageRuns(lineage);

        ModelLineageHistoryDto history = new ModelLineageHistoryDto();
        history.setModelLineageId(lineage);
        history.setWindow(window != null ? window : "ALL_AVAILABLE");

        if (allRuns.isEmpty()) {
            return history;
        }

        // Apply window filtering if specified (e.g. LAST_3, LAST_5, LAST_10)
        List<DiagnosticRun> activeRuns = filterRunsByWindow(allRuns, window);

        // Extract normalized temporal observations for all active runs
        List<DiagnosticTemporalObservation> observations = extractObservations(lineage, activeRuns);

        // Build metric histories
        Map<String, TemporalMetricHistoryDto> metricHistoryMap = buildMetricHistories(lineage, activeRuns, observations);
        List<TemporalMetricHistoryDto> metricHistories = new ArrayList<>(metricHistoryMap.values());
        history.setMetricHistories(metricHistories);

        // Collect change points across all metrics
        List<ChangePointDto> allChangePoints = new ArrayList<>();
        for (TemporalMetricHistoryDto mh : metricHistories) {
            allChangePoints.addAll(mh.getChangePoints());
        }
        history.setChangePoints(allChangePoints);

        // Build issue tracks
        List<IssueTrackDto> issueTracks = issueTrackAnalyzer.buildIssueTracks(lineage, observations);
        history.setIssueTracks(issueTracks);

        // Build durability assessments
        List<String> runIds = activeRuns.stream().map(DiagnosticRun::getId).toList();
        List<DiagnosticRemediation> remediations = remediationRepository.findByRunIdIn(runIds);
        List<DiagnosticExperiment> experiments = experimentRepository.findByBaselineRunIdIn(runIds);
        List<RemediationDurabilityDto> durabilityList = durabilityAnalyzer.evaluateDurability(lineage, activeRuns, remediations, experiments, observations);
        history.setRemediationDurability(durabilityList);

        // Link durability back into matching issue tracks
        for (IssueTrackDto track : issueTracks) {
            for (RemediationDurabilityDto dur : durabilityList) {
                if (track.getTargetKey().equalsIgnoreCase(dur.getTargetKey())) {
                    track.setDurabilityStatus(dur.getDurabilityStatus());
                    break;
                }
            }
        }

        // Generate temporal alerts
        List<TemporalAlertDto> alerts = alertAnalyzer.generateAlerts(lineage, activeRuns, issueTracks, allChangePoints, durabilityList);
        history.setAlerts(alerts);

        // Populate Run Summaries
        List<RunSummaryDto> orderedRunDtos = new ArrayList<>();
        int baselineCount = 0;
        int expCount = 0;

        for (DiagnosticRun r : activeRuns) {
            boolean isExp = "EXPERIMENT".equalsIgnoreCase(r.getRunType());
            if (isExp) expCount++;
            else baselineCount++;

            RunSummaryDto rSummary = new RunSummaryDto();
            rSummary.setRunId(r.getId());
            rSummary.setModelName(r.getModelName());
            rSummary.setStatus(r.getStatus() != null ? r.getStatus().name() : "COMPLETED");
            rSummary.setExecutionMode(r.getExecutionMode());
            rSummary.setCreatedAt(r.getCreatedAt());
            rSummary.setTotalModules(r.getModules().size());
            rSummary.setCompletedModules((int) r.getModules().stream().filter(m -> m.getStatus() == ModuleExecutionStatus.COMPLETED).count());
            orderedRunDtos.add(rSummary);
        }
        history.setOrderedRuns(orderedRunDtos);

        // Telemetry counts
        history.setTotalRunsCount(activeRuns.size());
        history.setBaselineRunsCount(baselineCount);
        history.setExperimentRunsCount(expCount);
        history.setFirstObservedAt(activeRuns.get(0).getCreatedAt());
        history.setLastObservedAt(activeRuns.get(activeRuns.size() - 1).getCreatedAt());

        int persistent = 0;
        int emerging = 0;
        int recurring = 0;
        int activeIssues = 0;
        for (IssueTrackDto it : issueTracks) {
            if ("PERSISTENT".equalsIgnoreCase(it.getStatus())) persistent++;
            if ("EMERGING".equalsIgnoreCase(it.getStatus())) emerging++;
            if ("RECURRING".equalsIgnoreCase(it.getStatus())) recurring++;
            if (!"RECOVERED".equalsIgnoreCase(it.getStatus()) && !"TRANSIENT".equalsIgnoreCase(it.getStatus())) activeIssues++;
        }
        history.setActiveIssuesCount(activeIssues);
        history.setPersistentIssuesCount(persistent);
        history.setEmergingIssuesCount(emerging);
        history.setRecurringIssuesCount(recurring);

        history.setActiveAlertsCount(alerts.size());
        history.setCriticalAlertsCount((int) alerts.stream().filter(a -> "CRITICAL".equalsIgnoreCase(a.getPriority())).count());
        history.setChangePointsCount(allChangePoints.size());

        return history;
    }

    @Transactional
    public TemporalRecalculateResponseDto recalculateTemporalIntelligence(String modelLineageId) {
        String lineage = resolveModelLineageId(modelLineageId);
        List<DiagnosticRun> runs = getLineageRuns(lineage);

        if (runs.isEmpty()) {
            return new TemporalRecalculateResponseDto(lineage, 0, 0, 0, 0, 0, false, "No diagnostic runs found for lineage: " + lineage);
        }

        // 1. Idempotently clean up previous derived records
        observationRepository.deleteByModelLineageId(lineage);
        issueTrackRepository.deleteByModelLineageId(lineage);
        changePointRepository.deleteByModelLineageId(lineage);
        alertRepository.deleteByModelLineageId(lineage);

        observationRepository.flush();
        issueTrackRepository.flush();
        changePointRepository.flush();
        alertRepository.flush();

        // 2. Extract and persist observations
        List<DiagnosticTemporalObservation> observations = extractObservations(lineage, runs);
        observationRepository.saveAll(observations);

        // 3. Build metric histories and change points
        Map<String, TemporalMetricHistoryDto> metricHistories = buildMetricHistories(lineage, runs, observations);
        List<DiagnosticChangePoint> changePointEntities = new ArrayList<>();

        for (TemporalMetricHistoryDto mh : metricHistories.values()) {
            for (ChangePointDto cpDto : mh.getChangePoints()) {
                DiagnosticChangePoint cp = new DiagnosticChangePoint();
                cp.setModelLineageId(lineage);
                cp.setMetricName(cpDto.getMetricName());
                cp.setTargetKey(cpDto.getTargetKey());
                cp.setChangeRunId(cpDto.getChangeRunId());
                cp.setChangeTimestamp(cpDto.getChangeTimestamp());
                cp.setBeforeMean(cpDto.getBeforeMean());
                cp.setAfterMean(cpDto.getAfterMean());
                cp.setAbsoluteShift(cpDto.getAbsoluteShift());
                cp.setRelativeShift(cpDto.getRelativeShift());
                cp.setConfidenceLevel(cpDto.getConfidenceLevel());
                try {
                    cp.setRunIdsBeforeJson(objectMapper.writeValueAsString(cpDto.getRunIdsBefore()));
                    cp.setRunIdsAfterJson(objectMapper.writeValueAsString(cpDto.getRunIdsAfter()));
                } catch (Exception ignored) {}
                changePointEntities.add(cp);
            }
        }
        changePointRepository.saveAll(changePointEntities);

        // 4. Build and persist issue tracks
        List<IssueTrackDto> issueTrackDtos = issueTrackAnalyzer.buildIssueTracks(lineage, observations);
        List<DiagnosticIssueTrack> trackEntities = new ArrayList<>();
        for (IssueTrackDto tDto : issueTrackDtos) {
            DiagnosticIssueTrack it = new DiagnosticIssueTrack();
            it.setModelLineageId(lineage);
            it.setTrackFingerprint(tDto.getTrackFingerprint());
            it.setTargetType(tDto.getTargetType());
            it.setTargetKey(tDto.getTargetKey());
            it.setFirstSeenAt(tDto.getFirstSeenAt());
            it.setLastSeenAt(tDto.getLastSeenAt());
            it.setFirstSeenRunId(tDto.getFirstSeenRunId());
            it.setLastSeenRunId(tDto.getLastSeenRunId());
            it.setObservationCount(tDto.getObservationCount());
            it.setConsecutiveCount(tDto.getConsecutiveCount());
            it.setCurrentSeverity(tDto.getCurrentSeverity());
            it.setPeakSeverity(tDto.getPeakSeverity());
            it.setStatus(tDto.getStatus());
            it.setHistoryJson(tDto.getHistoryJson());
            it.setRemediationHistoryJson(tDto.getRemediationHistoryJson());
            it.setDurabilityStatus(tDto.getDurabilityStatus());
            try {
                it.setModulesInvolvedJson(objectMapper.writeValueAsString(tDto.getModulesInvolved()));
                it.setMetricNamesJson(objectMapper.writeValueAsString(tDto.getMetricNames()));
                it.setRunIdsJson(objectMapper.writeValueAsString(tDto.getRunIds()));
            } catch (Exception ignored) {}
            trackEntities.add(it);
        }
        issueTrackRepository.saveAll(trackEntities);

        // 5. Durability and Alerts
        List<String> runIds = runs.stream().map(DiagnosticRun::getId).toList();
        List<DiagnosticRemediation> remediations = remediationRepository.findByRunIdIn(runIds);
        List<DiagnosticExperiment> experiments = experimentRepository.findByBaselineRunIdIn(runIds);
        List<ChangePointDto> changePointDtos = new ArrayList<>();
        for (TemporalMetricHistoryDto mh : metricHistories.values()) {
            changePointDtos.addAll(mh.getChangePoints());
        }
        List<RemediationDurabilityDto> durabilityList = durabilityAnalyzer.evaluateDurability(lineage, runs, remediations, experiments, observations);
        List<TemporalAlertDto> alertDtos = alertAnalyzer.generateAlerts(lineage, runs, issueTrackDtos, changePointDtos, durabilityList);

        List<DiagnosticTemporalAlert> alertEntities = new ArrayList<>();
        for (TemporalAlertDto aDto : alertDtos) {
            DiagnosticTemporalAlert a = new DiagnosticTemporalAlert();
            a.setModelLineageId(lineage);
            a.setRunId(aDto.getRunId());
            a.setAlertType(aDto.getAlertType());
            a.setPriority(aDto.getPriority());
            a.setTargetType(aDto.getTargetType());
            a.setTargetKey(aDto.getTargetKey());
            a.setMetricName(aDto.getMetricName());
            a.setCurrentValue(aDto.getCurrentValue());
            a.setReferenceValue(aDto.getReferenceValue());
            a.setTriggerDescription(aDto.getTriggerDescription());
            a.setConfidence(aDto.getConfidence());
            a.setCreatedAt(aDto.getCreatedAt());
            try {
                a.setRunIdsJson(objectMapper.writeValueAsString(aDto.getRunIds()));
            } catch (Exception ignored) {}
            alertEntities.add(a);
        }
        alertRepository.saveAll(alertEntities);

        return new TemporalRecalculateResponseDto(
                lineage,
                runs.size(),
                observations.size(),
                trackEntities.size(),
                alertEntities.size(),
                changePointEntities.size(),
                true,
                "Temporal intelligence recalculated and persisted successfully."
        );
    }

    private List<DiagnosticRun> filterRunsByWindow(List<DiagnosticRun> runs, String window) {
        if (window == null || window.equalsIgnoreCase("ALL_AVAILABLE") || runs.isEmpty()) {
            return runs;
        }

        int count = runs.size();
        int take = switch (window.toUpperCase()) {
            case "LAST_3" -> 3;
            case "LAST_5" -> 5;
            case "LAST_10" -> 10;
            default -> count;
        };

        if (take >= count) return runs;
        return runs.subList(count - take, count);
    }

    public List<DiagnosticTemporalObservation> extractObservations(String modelLineageId, List<DiagnosticRun> runs) {
        List<DiagnosticTemporalObservation> observations = new ArrayList<>();

        for (int idx = 0; idx < runs.size(); idx++) {
            DiagnosticRun run = runs.get(idx);
            int runIndex = idx + 1;
            Instant runTime = run.getCreatedAt() != null ? run.getCreatedAt() : Instant.now();
            String runType = run.getRunType() != null ? run.getRunType() : "BASELINE";

            List<DiagnosticResult> results = resultRepository.findByRunId(run.getId());
            if (results.isEmpty()) continue;

            NormalizedModuleData norm = resultNormalizer.normalize(run.getId(), results);

            // 1. Performance Summary Metrics
            if (norm.getAvailableModules().contains(DiagnosticModule.PERFORMANCE) && norm.getPerformanceSummary() != null) {
                Long resId = norm.getModuleResultIds().get(DiagnosticModule.PERFORMANCE);
                NormalizedModuleData.PerformanceSummary p = norm.getPerformanceSummary();
                addObs(observations, modelLineageId, run.getId(), runType, runIndex, runTime, DiagnosticModule.PERFORMANCE, "f1", "GLOBAL", "GLOBAL", p.f1, "score", resId);
                addObs(observations, modelLineageId, run.getId(), runType, runIndex, runTime, DiagnosticModule.PERFORMANCE, "roc_auc", "GLOBAL", "GLOBAL", p.rocAuc, "auc", resId);
                addObs(observations, modelLineageId, run.getId(), runType, runIndex, runTime, DiagnosticModule.PERFORMANCE, "pr_auc", "GLOBAL", "GLOBAL", p.prAuc, "auc", resId);
                addObs(observations, modelLineageId, run.getId(), runType, runIndex, runTime, DiagnosticModule.PERFORMANCE, "log_loss", "GLOBAL", "GLOBAL", p.logLoss, "loss", resId);
                addObs(observations, modelLineageId, run.getId(), runType, runIndex, runTime, DiagnosticModule.PERFORMANCE, "brier_score", "GLOBAL", "GLOBAL", p.brierScore, "score", resId);
                addObs(observations, modelLineageId, run.getId(), runType, runIndex, runTime, DiagnosticModule.PERFORMANCE, "expected_calibration_error", "GLOBAL", "GLOBAL", p.expectedCalibrationError, "ece", resId);
            }

            // 2. Drift Summary & Feature Metrics
            if (norm.getAvailableModules().contains(DiagnosticModule.DRIFT)) {
                Long resId = norm.getModuleResultIds().get(DiagnosticModule.DRIFT);
                if (norm.getDriftSummary() != null) {
                    addObs(observations, modelLineageId, run.getId(), runType, runIndex, runTime, DiagnosticModule.DRIFT, "max_psi", "GLOBAL", "GLOBAL", norm.getDriftSummary().maxPsi, "psi", resId);
                }
                for (NormalizedModuleData.FeatureDriftData fd : norm.getDriftByFeature().values()) {
                    String targetKey = "FEATURE::" + fd.feature;
                    addObs(observations, modelLineageId, run.getId(), runType, runIndex, runTime, DiagnosticModule.DRIFT, "psi", "FEATURE", targetKey, fd.psi, "psi", resId);
                    addObs(observations, modelLineageId, run.getId(), runType, runIndex, runTime, DiagnosticModule.DRIFT, "wasserstein", "FEATURE", targetKey, fd.wasserstein, "dist", resId);
                }
            }

            // 3. Error Forensics
            if (norm.getAvailableModules().contains(DiagnosticModule.ERROR_FORENSICS)) {
                Long resId = norm.getModuleResultIds().get(DiagnosticModule.ERROR_FORENSICS);
                if (norm.getErrorForensicsSummary() != null) {
                    NormalizedModuleData.ErrorForensicsSummary ef = norm.getErrorForensicsSummary();
                    addObs(observations, modelLineageId, run.getId(), runType, runIndex, runTime, DiagnosticModule.ERROR_FORENSICS, "high_confidence_error_rate", "GLOBAL", "GLOBAL", ef.highConfidenceErrorRate, "rate", resId);
                    addObs(observations, modelLineageId, run.getId(), runType, runIndex, runTime, DiagnosticModule.ERROR_FORENSICS, "overall_error_rate", "GLOBAL", "GLOBAL", ef.overallErrorRate, "rate", resId);
                }
                for (NormalizedModuleData.FeatureErrorData fe : norm.getErrorByFeature().values()) {
                    String targetKey = "FEATURE::" + fe.feature;
                    addObs(observations, modelLineageId, run.getId(), runType, runIndex, runTime, DiagnosticModule.ERROR_FORENSICS, "error_association", "FEATURE", targetKey, fe.absoluteAssociation, "r", resId);
                }
            }

            // 4. Bias / Fairness
            if (norm.getAvailableModules().contains(DiagnosticModule.BIAS) && norm.getBiasSummary() != null) {
                Long resId = norm.getModuleResultIds().get(DiagnosticModule.BIAS);
                NormalizedModuleData.BiasSummary bs = norm.getBiasSummary();
                addObs(observations, modelLineageId, run.getId(), runType, runIndex, runTime, DiagnosticModule.BIAS, "worst_disparate_impact_ratio", "GLOBAL", "GLOBAL", bs.worstDisparateImpactRatio, "ratio", resId);
                addObs(observations, modelLineageId, run.getId(), runType, runIndex, runTime, DiagnosticModule.BIAS, "demographic_parity_gap", "GLOBAL", "GLOBAL", bs.demographicParityGap, "gap", resId);
            }

            // 5. Robustness
            if (norm.getAvailableModules().contains(DiagnosticModule.ROBUSTNESS)) {
                Long resId = norm.getModuleResultIds().get(DiagnosticModule.ROBUSTNESS);
                if (norm.getRobustnessSummary() != null) {
                    addObs(observations, modelLineageId, run.getId(), runType, runIndex, runTime, DiagnosticModule.ROBUSTNESS, "gaussian_jitter_5pct_flip_rate", "GLOBAL", "GLOBAL", norm.getRobustnessSummary().gaussianJitter5PctFlipRate, "rate", resId);
                    addObs(observations, modelLineageId, run.getId(), runType, runIndex, runTime, DiagnosticModule.ROBUSTNESS, "boundary_flip_rate", "GLOBAL", "GLOBAL", norm.getRobustnessSummary().boundaryFlipRate, "rate", resId);
                }
                for (NormalizedModuleData.FeatureRobustnessData fr : norm.getRobustnessByFeature().values()) {
                    String targetKey = "FEATURE::" + fr.feature;
                    addObs(observations, modelLineageId, run.getId(), runType, runIndex, runTime, DiagnosticModule.ROBUSTNESS, "flip_rate", "FEATURE", targetKey, fr.flipRate, "rate", resId);
                    addObs(observations, modelLineageId, run.getId(), runType, runIndex, runTime, DiagnosticModule.ROBUSTNESS, "mean_probability_shift", "FEATURE", targetKey, fr.meanProbabilityShift, "shift", resId);
                }
            }

            // 6. Data Quality
            if (norm.getAvailableModules().contains(DiagnosticModule.DATA_QUALITY)) {
                Long resId = norm.getModuleResultIds().get(DiagnosticModule.DATA_QUALITY);
                for (NormalizedModuleData.FeatureQualityData fq : norm.getQualityByFeature().values()) {
                    String targetKey = "FEATURE::" + fq.feature;
                    addObs(observations, modelLineageId, run.getId(), runType, runIndex, runTime, DiagnosticModule.DATA_QUALITY, "null_rate", "FEATURE", targetKey, fq.nullRate, "rate", resId);
                    addObs(observations, modelLineageId, run.getId(), runType, runIndex, runTime, DiagnosticModule.DATA_QUALITY, "outlier_rate", "FEATURE", targetKey, fq.outlierRate, "rate", resId);
                }
            }

            // 7. Leakage
            if (norm.getAvailableModules().contains(DiagnosticModule.LEAKAGE)) {
                Long resId = norm.getModuleResultIds().get(DiagnosticModule.LEAKAGE);
                for (NormalizedModuleData.FeatureLeakageData fl : norm.getLeakageByFeature().values()) {
                    String targetKey = "FEATURE::" + fl.feature;
                    addObs(observations, modelLineageId, run.getId(), runType, runIndex, runTime, DiagnosticModule.LEAKAGE, "leakage_score", "FEATURE", targetKey, fl.leakageScore, "score", resId);
                }
            }
        }

        return observations;
    }

    private void addObs(List<DiagnosticTemporalObservation> obsList, String lineage, String runId, String runType,
                        int runIndex, Instant timestamp, DiagnosticModule module, String metricName,
                        String targetType, String targetKey, double value, String unit, Long sourceResultId) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return;

        DiagnosticTemporalObservation o = new DiagnosticTemporalObservation();
        o.setModelLineageId(lineage);
        o.setRunId(runId);
        o.setRunType(runType);
        o.setRunIndex(runIndex);
        o.setTimestamp(timestamp);
        o.setModule(module);
        o.setMetricName(metricName);
        o.setTargetType(targetType);
        o.setTargetKey(targetKey);
        o.setMetricValue(value);
        o.setUnit(unit);
        o.setSeverity(metricRegistry.computeSeverity(metricName, value));
        o.setSourceResultId(sourceResultId);

        metricRegistry.getDefinition(metricName).ifPresent(d -> o.setThreshold(d.getWarningThreshold()));

        obsList.add(o);
    }

    private Map<String, TemporalMetricHistoryDto> buildMetricHistories(
            String modelLineageId, List<DiagnosticRun> runs, List<DiagnosticTemporalObservation> observations) {

        Map<String, TemporalMetricHistoryDto> map = new LinkedHashMap<>();

        for (DiagnosticTemporalObservation o : observations) {
            String key = o.getModule().name() + "::" + o.getMetricName() + "::" + o.getTargetKey();
            TemporalMetricHistoryDto mh = map.computeIfAbsent(key, k -> {
                TemporalMetricHistoryDto h = new TemporalMetricHistoryDto();
                h.setMetricName(o.getMetricName());
                h.setModule(o.getModule());
                h.setTargetKey(o.getTargetKey());
                h.setTargetType(o.getTargetType());
                h.setUnit(o.getUnit());
                h.setHigherIsBetter(metricRegistry.isHigherIsBetter(o.getMetricName()));
                return h;
            });

            TemporalMetricPointDto pt = new TemporalMetricPointDto(
                    o.getRunId(),
                    o.getRunType(),
                    o.getRunIndex(),
                    o.getTimestamp(),
                    o.getMetricValue(),
                    o.getSeverity(),
                    o.getThreshold()
            );

            if ("EXPERIMENT".equalsIgnoreCase(o.getRunType())) {
                mh.getExperimentPoints().add(pt);
            } else {
                mh.getBaselinePoints().add(pt);
            }
        }

        // Analyze trends, severity transitions, and change points for each metric history
        for (TemporalMetricHistoryDto mh : map.values()) {
            trendAnalyzer.analyzeTrend(mh);

            // Compute severity transitions on baseline series
            List<String> sevSequence = mh.getBaselinePoints().stream().map(TemporalMetricPointDto::getSeverity).toList();
            PersistenceAnalyzer.PersistenceResult pRes = persistenceAnalyzer.evaluatePersistence(sevSequence);
            mh.setCurrentSeverity(pRes.currentSeverity);
            mh.setPreviousSeverity(pRes.previousSeverity);
            mh.setConsecutiveRunsAtSeverity(pRes.consecutiveCount);
            mh.setSeverityTransitions(pRes.severityTransitions);

            // Detect change points
            List<ChangePointDto> cps = changePointAnalyzer.detectChangePoints(modelLineageId, mh);
            mh.setChangePoints(cps);
        }

        return map;
    }
}
