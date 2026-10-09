package com.modeldoctor.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeldoctor.domain.*;
import com.modeldoctor.dto.*;
import com.modeldoctor.dto.ModelHealthDecisionDto.DataSufficiencyDto;
import com.modeldoctor.intelligence.monitoring.AlertLifecycleManager;
import com.modeldoctor.intelligence.monitoring.HealthDimensionEvaluator;
import com.modeldoctor.intelligence.monitoring.ModelHealthDecisionEngine;
import com.modeldoctor.intelligence.normalization.NormalizedModuleData;
import com.modeldoctor.intelligence.normalization.ResultNormalizer;
import com.modeldoctor.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@SuppressWarnings("null")
public class ContinuousMonitoringService {

    private final DiagnosticResultRepository resultRepository;
    private final DiagnosticMonitoringPolicyRepository policyRepository;
    private final DiagnosticOperationalAlertRepository alertRepository;
    private final DiagnosticAlertEventRepository eventRepository;
    private final DiagnosticHealthSnapshotRepository snapshotRepository;
    private final DiagnosticRemediationRepository remediationRepository;
    private final DiagnosticExperimentRepository experimentRepository;
    private final DiagnosticInvestigationRepository investigationRepository;

    private final TemporalAnalysisService temporalService;
    private final HealthDimensionEvaluator dimensionEvaluator;
    private final AlertLifecycleManager lifecycleManager;
    private final ModelHealthDecisionEngine decisionEngine;
    private final ResultNormalizer resultNormalizer;
    private final ObjectMapper objectMapper;

    public ContinuousMonitoringService(
            DiagnosticResultRepository resultRepository,
            DiagnosticMonitoringPolicyRepository policyRepository,
            DiagnosticOperationalAlertRepository alertRepository,
            DiagnosticAlertEventRepository eventRepository,
            DiagnosticHealthSnapshotRepository snapshotRepository,
            DiagnosticRemediationRepository remediationRepository,
            DiagnosticExperimentRepository experimentRepository,
            DiagnosticInvestigationRepository investigationRepository,
            TemporalAnalysisService temporalService,
            HealthDimensionEvaluator dimensionEvaluator,
            AlertLifecycleManager lifecycleManager,
            ModelHealthDecisionEngine decisionEngine,
            ResultNormalizer resultNormalizer,
            ObjectMapper objectMapper) {
        this.resultRepository = resultRepository;
        this.policyRepository = policyRepository;
        this.alertRepository = alertRepository;
        this.eventRepository = eventRepository;
        this.snapshotRepository = snapshotRepository;
        this.remediationRepository = remediationRepository;
        this.experimentRepository = experimentRepository;
        this.investigationRepository = investigationRepository;
        this.temporalService = temporalService;
        this.dimensionEvaluator = dimensionEvaluator;
        this.lifecycleManager = lifecycleManager;
        this.decisionEngine = decisionEngine;
        this.resultNormalizer = resultNormalizer;
        this.objectMapper = objectMapper;
    }

    public String resolveLineage(String identifier) {
        return temporalService.resolveModelLineageId(identifier);
    }

    @Transactional
    public DiagnosticMonitoringPolicy getOrCreatePolicyEntity(String modelLineageId) {
        String lineage = resolveLineage(modelLineageId);
        return policyRepository.findByModelLineageId(lineage).orElseGet(() -> {
            DiagnosticMonitoringPolicy p = new DiagnosticMonitoringPolicy(lineage);
            try {
                List<String> defDims = Arrays.stream(HealthDimension.values()).map(Enum::name).toList();
                p.setRequiredDimensionsJson(objectMapper.writeValueAsString(defDims));
            } catch (Exception ignored) {}
            return policyRepository.save(p);
        });
    }

    @Transactional(readOnly = true)
    public DiagnosticMonitoringPolicyDto getPolicy(String modelLineageId) {
        DiagnosticMonitoringPolicy entity = getOrCreatePolicyEntity(modelLineageId);
        return toPolicyDto(entity);
    }

    @Transactional
    public DiagnosticMonitoringPolicyDto updatePolicy(String modelLineageId, DiagnosticMonitoringPolicyDto dto) {
        String lineage = resolveLineage(modelLineageId);
        DiagnosticMonitoringPolicy policy = getOrCreatePolicyEntity(lineage);

        policy.setPolicyVersion(policy.getPolicyVersion() + 1);
        policy.setEnabled(dto.isEnabled());
        if (dto.getObservationWindow() != null) policy.setObservationWindow(dto.getObservationWindow());
        if (dto.getMinBaselineRunsRequired() > 0) policy.setMinBaselineRunsRequired(dto.getMinBaselineRunsRequired());
        if (dto.getAlertPersistenceThreshold() > 0) policy.setAlertPersistenceThreshold(dto.getAlertPersistenceThreshold());
        if (dto.getRecoveryConsecutiveRuns() > 0) policy.setRecoveryConsecutiveRuns(dto.getRecoveryConsecutiveRuns());
        if (dto.getAlertCooldownRuns() >= 0) policy.setAlertCooldownRuns(dto.getAlertCooldownRuns());
        if (dto.getHysteresisMarginPct() >= 0) policy.setHysteresisMarginPct(dto.getHysteresisMarginPct());
        policy.setExperimentOverlayEnabled(dto.isExperimentOverlayEnabled());
        if (dto.getHealthEvaluationMode() != null) policy.setHealthEvaluationMode(dto.getHealthEvaluationMode());
        if (dto.getRequiredDimensions() != null && !dto.getRequiredDimensions().isEmpty()) {
            try {
                policy.setRequiredDimensionsJson(objectMapper.writeValueAsString(dto.getRequiredDimensions()));
            } catch (Exception ignored) {}
        }
        policy.setUpdatedAt(Instant.now());
        policy.setUpdatedBy(dto.getUpdatedBy() != null ? dto.getUpdatedBy() : "USER");

        policy = policyRepository.save(policy);
        return toPolicyDto(policy);
    }

    @Transactional
    public ModelHealthDecisionDto getCurrentHealth(String modelLineageId) {
        String lineage = resolveLineage(modelLineageId);
        DiagnosticMonitoringPolicy policy = getOrCreatePolicyEntity(lineage);

        // Fetch all lineage runs
        List<DiagnosticRun> allRuns = temporalService.getLineageRuns(lineage);

        // Authoritative history: filter to BASELINE operational runs
        List<DiagnosticRun> baselineRuns = allRuns.stream()
                .filter(r -> !"EXPERIMENT".equalsIgnoreCase(r.getRunType()))
                .toList();

        ModelHealthDecisionDto decision = new ModelHealthDecisionDto();
        decision.setModelLineageId(lineage);
        decision.setPolicy(toPolicyDto(policy));

        if (baselineRuns.isEmpty()) {
            decision.setOverallState(ModelHealthState.UNKNOWN);
            decision.setDecisionReason("No operational baseline runs recorded for model lineage: " + lineage);
            decision.setDataSufficiency(new DataSufficiencyDto(0, policy.getMinBaselineRunsRequired(), false, 0, 9,
                    "No operational runs available for evaluation."));
            return decision;
        }

        DiagnosticRun latestBaselineRun = baselineRuns.get(baselineRuns.size() - 1);
        decision.setOperationalRunId(latestBaselineRun.getId());
        decision.setEvaluationTimestamp(Instant.now());

        // Get Phase 9 Temporal History
        ModelLineageHistoryDto temporalHistory = temporalService.getModelLineageHistory(lineage, policy.getObservationWindow());

        // Normalize latest baseline run results
        List<DiagnosticResult> latestResults = resultRepository.findByRunId(latestBaselineRun.getId());
        NormalizedModuleData normalized = latestResults.isEmpty() ? null : resultNormalizer.normalize(latestBaselineRun.getId(), latestResults);

        // Evaluate 9 Health Dimensions
        Map<HealthDimension, HealthDimensionEvaluationDto> dimensions = dimensionEvaluator.evaluateDimensions(
                normalized,
                latestResults,
                temporalHistory.getMetricHistories(),
                temporalHistory.getIssueTracks(),
                temporalHistory.getRemediationDurability(),
                policy
        );
        decision.setHealthVector(dimensions);

        // Synchronize & update operational alerts
        List<InvestigationTargetDto> investigations = getInvestigationsForRun(latestBaselineRun.getId());
        List<DiagnosticRemediationDto> remediations = getRemediationsForRun(latestBaselineRun.getId());
        List<DiagnosticExperimentDto> experiments = getExperimentsForRun(latestBaselineRun.getId());

        List<DiagnosticOperationalAlert> operationalAlerts = lifecycleManager.synchronizeAlerts(
                lineage,
                latestBaselineRun.getId(),
                baselineRuns.size(),
                temporalHistory.getAlerts() != null ? temporalHistory.getAlerts() : Collections.emptyList(),
                investigations,
                remediations,
                experiments,
                temporalHistory.getIssueTracks(),
                policy
        );

        List<OperationalAlertDto> alertDtos = operationalAlerts.stream().map(lifecycleManager::toDto).toList();
        decision.setAllAlerts(alertDtos);
        decision.setActiveAlerts(alertDtos.stream()
                .filter(a -> a.getLifecycleState() != AlertLifecycleState.RESOLVED && !a.isCurrentlySuppressed())
                .toList());

        // Fetch previous snapshot for recovery assessment
        Optional<DiagnosticHealthSnapshot> prevSnapOpt = snapshotRepository.findTopByModelLineageIdOrderByTimestampDesc(lineage);

        // Evaluate overall model health state & index
        ModelHealthDecisionEngine.DecisionResult result = decisionEngine.evaluateHealth(
                lineage,
                baselineRuns.size(),
                dimensions,
                operationalAlerts,
                policy,
                prevSnapOpt.orElse(null)
        );

        decision.setOverallState(result.overallState);
        decision.setDecisionReason(result.decisionReason);
        decision.setHealthIndex(result.indexBreakdown.getScore());
        decision.setHealthIndexBreakdown(result.indexBreakdown);

        // Populate Data Sufficiency
        int evaluatedCount = (int) dimensions.values().stream().filter(HealthDimensionEvaluationDto::isEvaluable).count();
        boolean isSufficient = baselineRuns.size() >= policy.getMinBaselineRunsRequired();
        decision.setDataSufficiency(new DataSufficiencyDto(
                baselineRuns.size(),
                policy.getMinBaselineRunsRequired(),
                isSufficient,
                evaluatedCount,
                dimensions.size(),
                isSufficient ? "Sufficient operational baseline observation history." : "Insufficient observation history."
        ));

        // Build Evidence Dossier
        EvidenceDossierDto dossier = new EvidenceDossierDto();
        dossier.setModelLineageId(lineage);
        dossier.setOverallState(result.overallState);
        dossier.setHealthIndex(result.indexBreakdown.getScore());
        dossier.setDecisionReason(result.decisionReason);
        dossier.setEvaluatedRunId(latestBaselineRun.getId());
        dossier.setEvaluatedAt(Instant.now());
        dossier.setActiveAlerts(decision.getActiveAlerts());
        dossier.setDimensionEvaluations(dimensions);
        dossier.setRelatedInvestigationTargets(investigations);
        dossier.setRelatedRemediations(remediations);
        dossier.setRelatedExperiments(experiments);
        dossier.setTemporalIssueTracks(temporalHistory.getIssueTracks());
        decision.setEvidenceDossier(dossier);

        // Recent Audit Events
        List<DiagnosticAlertEvent> events = eventRepository.findTop50ByModelLineageIdOrderByTimestampDesc(lineage);
        decision.setRecentEvents(events.stream().map(this::toEventDto).toList());

        // Health History Snapshots
        List<DiagnosticHealthSnapshot> snapshots = snapshotRepository.findByModelLineageIdOrderByTimestampDesc(lineage);
        decision.setHistory(snapshots.stream().map(this::toSnapshotDto).toList());

        return decision;
    }

    @Transactional
    public MonitoringRecalculateResponseDto recalculateMonitoring(String modelLineageId) {
        String lineage = resolveLineage(modelLineageId);
        DiagnosticMonitoringPolicy policy = getOrCreatePolicyEntity(lineage);

        List<DiagnosticRun> allRuns = temporalService.getLineageRuns(lineage);
        List<DiagnosticRun> baselineRuns = allRuns.stream()
                .filter(r -> !"EXPERIMENT".equalsIgnoreCase(r.getRunType()))
                .toList();

        if (baselineRuns.isEmpty()) {
            return new MonitoringRecalculateResponseDto(lineage, 0, 0, 0, false,
                    ModelHealthState.UNKNOWN, null, false, "No baseline diagnostic runs available for model lineage: " + lineage);
        }

        // 1. Recalculate temporal intelligence first
        temporalService.recalculateTemporalIntelligence(lineage);

        // 2. Perform fresh health evaluation
        ModelHealthDecisionDto decision = getCurrentHealth(lineage);

        // 3. Persist Immutable Point-in-Time Health Snapshot for latest baseline run
        DiagnosticRun latestBaseline = baselineRuns.get(baselineRuns.size() - 1);

        // Prevent duplicate snapshots for the exact same run and timestamp window
        Optional<DiagnosticHealthSnapshot> existingSnapshot = snapshotRepository.findByModelLineageIdAndRunId(lineage, latestBaseline.getId());
        DiagnosticHealthSnapshot snapshot = existingSnapshot.orElseGet(DiagnosticHealthSnapshot::new);

        snapshot.setModelLineageId(lineage);
        snapshot.setRunId(latestBaseline.getId());
        snapshot.setTimestamp(Instant.now());
        snapshot.setOverallState(decision.getOverallState());
        snapshot.setHealthIndex(decision.getHealthIndex());
        snapshot.setActiveAlertsCount(decision.getActiveAlerts().size());
        snapshot.setCriticalAlertsCount((int) decision.getActiveAlerts().stream().filter(a -> "CRITICAL".equalsIgnoreCase(a.getCurrentSeverity())).count());
        snapshot.setHighAlertsCount((int) decision.getActiveAlerts().stream().filter(a -> "HIGH".equalsIgnoreCase(a.getCurrentSeverity()) || "WARNING".equalsIgnoreCase(a.getCurrentSeverity())).count());
        snapshot.setDegradedDimensionsCount((int) decision.getHealthVector().values().stream().filter(d -> d.getState() == DimensionHealthState.DEGRADED || d.getState() == DimensionHealthState.CRITICAL).count());
        snapshot.setUnknownDimensionsCount((int) decision.getHealthVector().values().stream().filter(d -> d.getState() == DimensionHealthState.UNKNOWN).count());
        snapshot.setPolicyVersion(policy.getPolicyVersion());
        snapshot.setDecisionEngineVersion(policy.getHealthEvaluationMode());

        try {
            snapshot.setDimensionStatesJson(objectMapper.writeValueAsString(decision.getHealthVector()));
            snapshot.setEvidenceSummaryJson(decision.getDecisionReason());
        } catch (Exception ignored) {}

        snapshotRepository.save(snapshot);

        return new MonitoringRecalculateResponseDto(
                lineage,
                baselineRuns.size(),
                decision.getActiveAlerts().size(),
                decision.getAllAlerts().size(),
                true,
                decision.getOverallState(),
                decision.getHealthIndex(),
                true,
                "Continuous monitoring and health decision engine recalculated successfully."
        );
    }

    @Transactional(readOnly = true)
    public List<DiagnosticHealthSnapshotDto> getHealthHistory(String modelLineageId) {
        String lineage = resolveLineage(modelLineageId);
        return snapshotRepository.findByModelLineageIdOrderByTimestampDesc(lineage).stream()
                .map(this::toSnapshotDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OperationalAlertDto> getAlerts(String modelLineageId) {
        String lineage = resolveLineage(modelLineageId);
        return alertRepository.findByModelLineageIdOrderByLastObservedAtDesc(lineage).stream()
                .map(lifecycleManager::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public OperationalAlertDto getAlert(String modelLineageId, Long alertId) {
        String lineage = resolveLineage(modelLineageId);
        DiagnosticOperationalAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found with ID: " + alertId));
        if (!alert.getModelLineageId().equalsIgnoreCase(lineage)) {
            throw new IllegalArgumentException(String.format("Alert %d does not belong to lineage '%s'", alertId, lineage));
        }
        return lifecycleManager.toDto(alert);
    }

    @Transactional
    public OperationalAlertDto acknowledgeAlert(String modelLineageId, Long alertId, String actor) {
        String lineage = resolveLineage(modelLineageId);
        DiagnosticOperationalAlert updated = lifecycleManager.acknowledge(lineage, alertId, actor);
        return lifecycleManager.toDto(updated);
    }

    @Transactional
    public OperationalAlertDto investigateAlert(String modelLineageId, Long alertId, String actor) {
        String lineage = resolveLineage(modelLineageId);
        DiagnosticOperationalAlert updated = lifecycleManager.investigate(lineage, alertId, actor);
        return lifecycleManager.toDto(updated);
    }

    @Transactional
    public OperationalAlertDto suppressAlert(String modelLineageId, Long alertId, String reason, Integer durationHours, String actor) {
        String lineage = resolveLineage(modelLineageId);
        DiagnosticOperationalAlert updated = lifecycleManager.suppress(lineage, alertId, reason, durationHours, actor);
        return lifecycleManager.toDto(updated);
    }

    @Transactional
    public OperationalAlertDto resolveAlert(String modelLineageId, Long alertId, String reason, String actor) {
        String lineage = resolveLineage(modelLineageId);
        DiagnosticOperationalAlert updated = lifecycleManager.resolve(lineage, alertId, reason, actor);
        return lifecycleManager.toDto(updated);
    }

    private List<InvestigationTargetDto> getInvestigationsForRun(String runId) {
        List<DiagnosticInvestigation> invs = investigationRepository.findByRunIdOrderByPriorityScoreDesc(runId);
        return invs.stream().map(i -> {
            InvestigationTargetDto dto = new InvestigationTargetDto();
            dto.setTargetType(i.getTargetType());
            dto.setTargetKey(i.getTargetKey());
            dto.setPriority(i.getPriority() != null ? i.getPriority().name() : "MEDIUM");
            dto.setPriorityScore(i.getPriorityScore());
            dto.setHypothesis(i.getHypothesis());
            return dto;
        }).toList();
    }

    private List<DiagnosticRemediationDto> getRemediationsForRun(String runId) {
        List<DiagnosticRemediation> rems = remediationRepository.findByRunIdOrderByPriorityScoreDesc(runId);
        return rems.stream().map(r -> {
            DiagnosticRemediationDto dto = new DiagnosticRemediationDto();
            dto.setId(r.getId());
            dto.setTargetType(r.getTargetType());
            dto.setTargetKey(r.getTargetKey());
            dto.setRemediationType(r.getRemediationType() != null ? r.getRemediationType().name() : "REMEDIATION");
            dto.setTitle(r.getTitle());
            dto.setDescription(r.getDescription());
            dto.setPriority(r.getPriority() != null ? r.getPriority().name() : "MEDIUM");
            dto.setConfidence(r.getConfidence() != null ? r.getConfidence().name() : "HIGH");
            dto.setStatus(r.getStatus() != null ? r.getStatus().name() : "PROPOSED");
            return dto;
        }).toList();
    }

    private List<DiagnosticExperimentDto> getExperimentsForRun(String runId) {
        List<DiagnosticExperiment> exps = experimentRepository.findByBaselineRunIdOrderByCreatedAtDesc(runId);
        return exps.stream().map(e -> {
            DiagnosticExperimentDto dto = new DiagnosticExperimentDto();
            dto.setId(e.getId());
            dto.setExperimentType(e.getExperimentType());
            dto.setTitle(e.getTitle());
            dto.setTargetType(e.getTargetType());
            dto.setTargetKey(e.getTargetKey());
            dto.setStatus(e.getStatus());
            dto.setConclusion(e.getConclusion());
            dto.setCandidateRunId(e.getCandidateRunId());
            return dto;
        }).toList();
    }

    private DiagnosticMonitoringPolicyDto toPolicyDto(DiagnosticMonitoringPolicy p) {
        DiagnosticMonitoringPolicyDto dto = new DiagnosticMonitoringPolicyDto();
        dto.setModelLineageId(p.getModelLineageId());
        dto.setPolicyVersion(p.getPolicyVersion());
        dto.setEnabled(p.isEnabled());
        dto.setObservationWindow(p.getObservationWindow());
        dto.setMinBaselineRunsRequired(p.getMinBaselineRunsRequired());
        dto.setAlertPersistenceThreshold(p.getAlertPersistenceThreshold());
        dto.setRecoveryConsecutiveRuns(p.getRecoveryConsecutiveRuns());
        dto.setAlertCooldownRuns(p.getAlertCooldownRuns());
        dto.setHysteresisMarginPct(p.getHysteresisMarginPct());
        dto.setExperimentOverlayEnabled(p.isExperimentOverlayEnabled());
        dto.setHealthEvaluationMode(p.getHealthEvaluationMode());
        dto.setCreatedAt(p.getCreatedAt());
        dto.setUpdatedAt(p.getUpdatedAt());
        dto.setUpdatedBy(p.getUpdatedBy());

        if (p.getRequiredDimensionsJson() != null) {
            try {
                List<String> list = objectMapper.readValue(p.getRequiredDimensionsJson(), new TypeReference<List<String>>() {});
                List<HealthDimension> dims = new ArrayList<>();
                for (String s : list) {
                    try { dims.add(HealthDimension.valueOf(s)); } catch (Exception ignored) {}
                }
                dto.setRequiredDimensions(dims);
            } catch (Exception ignored) {}
        }
        return dto;
    }

    private DiagnosticAlertEventDto toEventDto(DiagnosticAlertEvent e) {
        DiagnosticAlertEventDto dto = new DiagnosticAlertEventDto();
        dto.setId(e.getId());
        dto.setModelLineageId(e.getModelLineageId());
        dto.setAlertId(e.getAlertId());
        dto.setAlertFingerprint(e.getAlertFingerprint());
        dto.setPreviousState(e.getPreviousState());
        dto.setNewState(e.getNewState());
        dto.setActor(e.getActor());
        dto.setAction(e.getAction());
        dto.setReason(e.getReason());
        dto.setTimestamp(e.getTimestamp());
        return dto;
    }

    private DiagnosticHealthSnapshotDto toSnapshotDto(DiagnosticHealthSnapshot s) {
        DiagnosticHealthSnapshotDto dto = new DiagnosticHealthSnapshotDto();
        dto.setId(s.getId());
        dto.setModelLineageId(s.getModelLineageId());
        dto.setRunId(s.getRunId());
        dto.setTimestamp(s.getTimestamp());
        dto.setOverallState(s.getOverallState());
        dto.setHealthIndex(s.getHealthIndex());
        dto.setActiveAlertsCount(s.getActiveAlertsCount());
        dto.setCriticalAlertsCount(s.getCriticalAlertsCount());
        dto.setHighAlertsCount(s.getHighAlertsCount());
        dto.setDegradedDimensionsCount(s.getDegradedDimensionsCount());
        dto.setUnknownDimensionsCount(s.getUnknownDimensionsCount());
        dto.setEvidenceSummary(s.getEvidenceSummaryJson());
        dto.setPolicyVersion(s.getPolicyVersion());
        dto.setDecisionEngineVersion(s.getDecisionEngineVersion());
        return dto;
    }
}
