package com.modeldoctor.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeldoctor.domain.*;
import com.modeldoctor.dto.*;
import com.modeldoctor.intelligence.reliability.FleetIntelligenceEngine;
import com.modeldoctor.intelligence.reliability.ModelReliabilityEngine;
import com.modeldoctor.intelligence.reliability.ModelReliabilityEngine.EvaluationContext;
import com.modeldoctor.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@SuppressWarnings("null")
public class ModelReliabilityService {

    private static final Logger logger = LoggerFactory.getLogger(ModelReliabilityService.class);

    private final DiagnosticModelReliabilityRepository reliabilityRepository;
    private final DiagnosticReliabilityEventRepository eventRepository;
    private final DiagnosticFleetPatternRepository fleetPatternRepository;
    private final DiagnosticHealthSnapshotRepository snapshotRepository;
    private final DiagnosticIncidentRepository incidentRepository;
    private final DiagnosticRunRepository runRepository;
    private final ContinuousMonitoringService monitoringService;
    private final IncidentAnalysisService incidentService;
    private final TemporalAnalysisService temporalService;
    private final ModelReliabilityEngine reliabilityEngine;
    private final FleetIntelligenceEngine fleetEngine;
    private final ObjectMapper objectMapper;

    public ModelReliabilityService(
            DiagnosticModelReliabilityRepository reliabilityRepository,
            DiagnosticReliabilityEventRepository eventRepository,
            DiagnosticFleetPatternRepository fleetPatternRepository,
            DiagnosticHealthSnapshotRepository snapshotRepository,
            DiagnosticIncidentRepository incidentRepository,
            DiagnosticRunRepository runRepository,
            ContinuousMonitoringService monitoringService,
            IncidentAnalysisService incidentService,
            TemporalAnalysisService temporalService,
            ModelReliabilityEngine reliabilityEngine,
            FleetIntelligenceEngine fleetEngine,
            ObjectMapper objectMapper) {
        this.reliabilityRepository = reliabilityRepository;
        this.eventRepository = eventRepository;
        this.fleetPatternRepository = fleetPatternRepository;
        this.snapshotRepository = snapshotRepository;
        this.incidentRepository = incidentRepository;
        this.runRepository = runRepository;
        this.monitoringService = monitoringService;
        this.incidentService = incidentService;
        this.temporalService = temporalService;
        this.reliabilityEngine = reliabilityEngine;
        this.fleetEngine = fleetEngine;
        this.objectMapper = objectMapper;
    }

    public String resolveLineage(String identifier) {
        return temporalService.resolveModelLineageId(identifier);
    }

    @Transactional
    public ModelReliabilityProfileDto getReliabilityProfile(String identifier) {
        String lineage = resolveLineage(identifier);

        Optional<DiagnosticModelReliability> entityOpt = reliabilityRepository.findByModelLineageId(lineage);
        if (entityOpt.isEmpty()) {
            // Calculate and persist fresh reliability profile
            recalculateReliability(lineage);
            entityOpt = reliabilityRepository.findByModelLineageId(lineage);
        }

        if (entityOpt.isPresent()) {
            return toProfileDto(entityOpt.get(), lineage);
        }

        // Fallback calculation in-memory
        return computeProfileInMemory(lineage);
    }

    @Transactional
    public ReliabilityRecalculateResponseDto recalculateReliability(String identifier) {
        String lineage = resolveLineage(identifier);

        // 1. Recalculate monitoring and incidents first
        monitoringService.recalculateMonitoring(lineage);
        incidentService.recalculateIncidents(lineage);

        // 2. Compute fresh profile
        ModelReliabilityProfileDto profile = computeProfileInMemory(lineage);

        // 3. Persist / update DiagnosticModelReliability snapshot
        DiagnosticModelReliability entity = reliabilityRepository.findByModelLineageId(lineage)
                .orElseGet(DiagnosticModelReliability::new);

        entity.setModelLineageId(lineage);
        entity.setModelName(profile.getModelName());
        entity.setObservationWindow(profile.getObservationWindow());
        entity.setOperationalRunCount(profile.getOperationalRunCount());
        entity.setReliabilityScore(profile.getReliabilityScore());
        entity.setReliabilityState(profile.getReliabilityState());
        entity.setReliabilityConfidence(profile.getReliabilityConfidence());
        entity.setCurrentHealthState(profile.getCurrentHealthState());
        entity.setGrade(profile.getGrade());
        entity.setTrend(profile.getTrend());
        entity.setTrendSlope(profile.getTrendSlope());
        entity.setTrendR2(profile.getTrendR2());
        entity.setGovernanceRecommendation(profile.getGovernanceRecommendation());
        entity.setRecommendationReason(profile.getRecommendationReason());

        entity.setActiveIncidentCount(profile.getActiveIncidentCount());
        entity.setCriticalIncidentCount(profile.getCriticalIncidentCount());
        entity.setHistoricalIncidentCount(profile.getHistoricalIncidentCount());
        entity.setRecurringIncidentCount(profile.getRecurringIncidentCount());
        entity.setReopenedIncidentCount(profile.getReopenedIncidentCount());
        entity.setUnresolvedIncidentCount(profile.getUnresolvedIncidentCount());

        entity.setRemediationCount(profile.getRemediationCount());
        entity.setValidatedRemediationCount(profile.getValidatedRemediationCount());
        entity.setFailedRemediationCount(profile.getFailedRemediationCount());

        if (profile.getRemediationDurability() != null) {
            entity.setRemediationDurabilityRate(profile.getRemediationDurability().getDurabilityRate());
        }
        if (profile.getRecoveryProfile() != null) {
            entity.setRecoveryRate(profile.getRecoveryProfile().getRecoveryRate());
            entity.setRegressionRate(profile.getRecoveryProfile().getRegressionsAfterRecoveryCount() > 0 ?
                    (double) profile.getRecoveryProfile().getRegressionsAfterRecoveryCount() : 0.0);
        }

        entity.setLastHealthyRunId(profile.getLastHealthyRunId());
        entity.setLastDegradedRunId(profile.getLastDegradedRunId());
        entity.setLastCriticalRunId(profile.getLastCriticalRunId());
        entity.setLastIncidentCode(profile.getLastIncidentCode());

        try {
            entity.setScoreBreakdownJson(objectMapper.writeValueAsString(profile.getScoreBreakdown()));
            entity.setRiskFactorsJson(objectMapper.writeValueAsString(profile.getRiskFactors()));
            entity.setStrengthsJson(objectMapper.writeValueAsString(profile.getStrengths()));
            entity.setTrajectoryJson(objectMapper.writeValueAsString(profile.getTrajectory()));
        } catch (Exception e) {
            logger.warn("Failed to serialize reliability profile JSON for lineage {}: {}", lineage, e.getMessage());
        }

        entity.setUpdatedAt(Instant.now());
        reliabilityRepository.save(entity);

        // 4. Record Governance Events idempotently
        recordGovernanceEvents(lineage, profile);

        return new ReliabilityRecalculateResponseDto(
                lineage,
                profile.getOperationalRunCount(),
                profile.getReliabilityScore(),
                profile.getReliabilityState(),
                profile.getReliabilityConfidence(),
                profile.getTrend(),
                profile.getGovernanceRecommendation(),
                true,
                "Model reliability governance profile successfully recalculated."
        );
    }

    @Transactional(readOnly = true)
    public List<ReliabilityTrajectoryPointDto> getReliabilityHistory(String identifier) {
        String lineage = resolveLineage(identifier);
        ModelReliabilityProfileDto profile = getReliabilityProfile(lineage);
        return profile.getTrajectory();
    }

    @Transactional(readOnly = true)
    public List<DiagnosticReliabilityEventDto> getReliabilityEvents(String identifier) {
        String lineage = resolveLineage(identifier);
        return eventRepository.findByModelLineageIdOrderByTimestampDesc(lineage).stream()
                .map(this::toEventDto)
                .toList();
    }

    @Transactional
    public FleetOverviewDto getFleetOverview() {
        List<String> distinctLineages = runRepository.findDistinctModelLineageIds();
        if (distinctLineages == null || distinctLineages.isEmpty()) {
            distinctLineages = new ArrayList<>();
        }

        List<ModelReliabilityProfileDto> profiles = new ArrayList<>();
        for (String lineage : distinctLineages) {
            if (lineage != null && !lineage.isBlank()) {
                profiles.add(getReliabilityProfile(lineage));
            }
        }

        FleetOverviewDto overview = fleetEngine.computeFleetOverview(profiles);

        // Synchronize and persist discovered Fleet Patterns
        for (FleetPatternDto p : overview.getRecurringPatterns()) {
            DiagnosticFleetPattern fp = fleetPatternRepository.findByPatternKey(p.getPatternKey())
                    .orElseGet(DiagnosticFleetPattern::new);

            fp.setPatternType(p.getPatternType());
            fp.setPatternKey(p.getPatternKey());
            fp.setPatternTitle(p.getPatternTitle());
            try {
                fp.setAffectedLineagesJson(objectMapper.writeValueAsString(p.getAffectedLineages()));
            } catch (Exception ignored) {}
            fp.setAffectedLineagesCount(p.getAffectedLineagesCount());
            fp.setTotalIncidentsCount(p.getTotalIncidentsCount());
            fp.setConfidence(p.getConfidence());
            fp.setDescription(p.getDescription());
            fp.setFirstObservedAt(p.getFirstObservedAt() != null ? p.getFirstObservedAt() : Instant.now());
            fp.setLastObservedAt(p.getLastObservedAt() != null ? p.getLastObservedAt() : Instant.now());
            fp.setUpdatedAt(Instant.now());

            fleetPatternRepository.save(fp);
        }

        return overview;
    }

    @Transactional(readOnly = true)
    public List<FleetRiskRankDto> getFleetRisk() {
        return getFleetOverview().getRankedLineages();
    }

    @Transactional(readOnly = true)
    public List<FleetPatternDto> getFleetPatterns() {
        return getFleetOverview().getRecurringPatterns();
    }

    @Transactional(readOnly = true)
    public ModelComparisonDto compareLineages(String leftIdentifier, String rightIdentifier) {
        String leftLineage = resolveLineage(leftIdentifier);
        String rightLineage = resolveLineage(rightIdentifier);

        ModelReliabilityProfileDto leftProfile = getReliabilityProfile(leftLineage);
        ModelReliabilityProfileDto rightProfile = getReliabilityProfile(rightLineage);

        return fleetEngine.compareLineages(leftProfile, rightProfile);
    }

    private ModelReliabilityProfileDto computeProfileInMemory(String lineage) {
        DiagnosticMonitoringPolicy policy = monitoringService.getOrCreatePolicyEntity(lineage);
        List<DiagnosticRun> allRuns = temporalService.getLineageRuns(lineage);
        List<DiagnosticRun> baselineRuns = allRuns.stream()
                .filter(r -> !"EXPERIMENT".equalsIgnoreCase(r.getRunType()) && !"EXPERIMENT".equalsIgnoreCase(r.getExecutionMode()))
                .toList();

        List<DiagnosticHealthSnapshot> allSnapshots = snapshotRepository.findByModelLineageIdOrderByTimestampDesc(lineage);
        Set<String> baselineRunIds = new HashSet<>();
        for (DiagnosticRun r : baselineRuns) {
            baselineRunIds.add(r.getId());
        }
        List<DiagnosticHealthSnapshot> snapshots = allSnapshots.stream()
                .filter(s -> s.getRunId() == null || baselineRunIds.contains(s.getRunId()))
                .toList();

        List<DiagnosticIncident> allIncidents = incidentRepository.findByModelLineageIdOrderByPriorityScoreDesc(lineage);
        List<DiagnosticIncident> incidents = allIncidents.stream()
                .filter(inc -> inc.getLastSeenRunId() == null || baselineRunIds.contains(inc.getLastSeenRunId()))
                .toList();

        ModelLineageHistoryDto temporalHistory = temporalService.getModelLineageHistory(lineage, policy.getObservationWindow());
        ModelHealthDecisionDto currentHealth = monitoringService.getCurrentHealth(lineage);

        String modelName = baselineRuns.stream()
                .map(DiagnosticRun::getModelName)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(lineage);

        EvaluationContext ctx = new EvaluationContext(
                lineage,
                modelName,
                baselineRuns,
                snapshots,
                incidents,
                temporalHistory.getIssueTracks(),
                temporalHistory.getRemediationDurability(),
                currentHealth,
                policy
        );

        ModelReliabilityProfileDto profile = reliabilityEngine.evaluateReliability(ctx);

        // Populate recent events
        List<DiagnosticReliabilityEvent> events = eventRepository.findTop50ByModelLineageIdOrderByTimestampDesc(lineage);
        profile.setRecentEvents(events.stream().map(this::toEventDto).toList());

        return profile;
    }

    private void recordGovernanceEvents(String lineage, ModelReliabilityProfileDto profile) {
        // 1. Health state change or degradation events from trajectory
        if (profile.getTrajectory() != null) {
            for (ReliabilityTrajectoryPointDto pt : profile.getTrajectory()) {
                if (pt.getHealthState() == ModelHealthState.CRITICAL || pt.getHealthState() == ModelHealthState.DEGRADED) {
                    recordEventIfAbsent(lineage, ReliabilityEventType.DEGRADATION, pt.getRunId(),
                            "HEALTH_SNAPSHOT", pt.getRunId(), "HIGH",
                            String.format("Operational baseline run %s exhibited %s health state (Score: %d).",
                                    pt.getRunId(), pt.getHealthState(), pt.getReliabilityScore()),
                            pt.getTimestamp());
                } else if (pt.getHealthState() == ModelHealthState.RECOVERING) {
                    recordEventIfAbsent(lineage, ReliabilityEventType.RECOVERY, pt.getRunId(),
                            "HEALTH_SNAPSHOT", pt.getRunId(), "INFO",
                            String.format("Operational recovery detected in run %s.", pt.getRunId()),
                            pt.getTimestamp());
                }
            }
        }

        // 2. Incident events
        List<DiagnosticIncident> incidents = incidentRepository.findByModelLineageIdOrderByPriorityScoreDesc(lineage);
        for (DiagnosticIncident inc : incidents) {
            if ("CRITICAL".equalsIgnoreCase(String.valueOf(inc.getSeverity()))) {
                recordEventIfAbsent(lineage, ReliabilityEventType.INCIDENT_ESCALATION, inc.getLastSeenRunId(),
                        "INCIDENT", String.valueOf(inc.getId()), "CRITICAL",
                        String.format("Active CRITICAL incident %s: %s", inc.getIncidentCode(), inc.getTitle()),
                        inc.getCreatedAt());
            }
            if (inc.getReopenedCount() > 0) {
                recordEventIfAbsent(lineage, ReliabilityEventType.INCIDENT_REOPEN, inc.getLastSeenRunId(),
                        "INCIDENT", String.valueOf(inc.getId()), "HIGH",
                        String.format("Incident %s reopened (%d occurrences).", inc.getIncidentCode(), inc.getReopenedCount()),
                        inc.getLastObservedAt() != null ? inc.getLastObservedAt() : Instant.now());
            }
        }

        // 3. Remediation Durability events
        if (profile.getRemediationDurability() != null) {
            if (profile.getRemediationDurability().getSustainedCount() > 0) {
                recordEventIfAbsent(lineage, ReliabilityEventType.REMEDIATION_SUCCESS, profile.getLastHealthyRunId(),
                        "REMEDIATION", "SUSTAINED", "INFO",
                        String.format("%d remediation hypotheses sustained operational durability.", profile.getRemediationDurability().getSustainedCount()),
                        Instant.now());
            }
            if (profile.getRemediationDurability().getFailedCount() > 0) {
                recordEventIfAbsent(lineage, ReliabilityEventType.REMEDIATION_FAILURE, profile.getLastDegradedRunId(),
                        "REMEDIATION", "FAILED", "HIGH",
                        String.format("%d remediation hypotheses failed during experimental validation.", profile.getRemediationDurability().getFailedCount()),
                        Instant.now());
            }
        }
    }

    private void recordEventIfAbsent(String lineage, ReliabilityEventType type, String runId,
                                     String sourceType, String sourceId, String severity, String summary, Instant timestamp) {
        if (sourceType == null || sourceId == null) return;
        Optional<DiagnosticReliabilityEvent> existing = eventRepository
                .findByModelLineageIdAndEventTypeAndSourceTypeAndSourceId(lineage, type, sourceType, sourceId);
        if (existing.isEmpty()) {
            DiagnosticReliabilityEvent ev = new DiagnosticReliabilityEvent(
                    lineage, type, runId, sourceType, sourceId, severity, summary, timestamp
            );
            eventRepository.save(ev);
        }
    }

    private ModelReliabilityProfileDto toProfileDto(DiagnosticModelReliability e, String lineage) {
        ModelReliabilityProfileDto dto = new ModelReliabilityProfileDto();
        dto.setModelLineageId(e.getModelLineageId());
        dto.setModelName(e.getModelName());
        dto.setObservationWindow(e.getObservationWindow());
        dto.setOperationalRunCount(e.getOperationalRunCount());
        dto.setReliabilityScore(e.getReliabilityScore());
        dto.setReliabilityState(e.getReliabilityState());
        dto.setReliabilityConfidence(e.getReliabilityConfidence());
        dto.setCurrentHealthState(e.getCurrentHealthState());
        dto.setGrade(e.getGrade());
        dto.setTrend(e.getTrend());
        dto.setTrendSlope(e.getTrendSlope());
        dto.setTrendR2(e.getTrendR2());
        dto.setGovernanceRecommendation(e.getGovernanceRecommendation());
        dto.setRecommendationReason(e.getRecommendationReason());

        dto.setActiveIncidentCount(e.getActiveIncidentCount());
        dto.setCriticalIncidentCount(e.getCriticalIncidentCount());
        dto.setHistoricalIncidentCount(e.getHistoricalIncidentCount());
        dto.setRecurringIncidentCount(e.getRecurringIncidentCount());
        dto.setReopenedIncidentCount(e.getReopenedIncidentCount());
        dto.setUnresolvedIncidentCount(e.getUnresolvedIncidentCount());

        dto.setRemediationCount(e.getRemediationCount());
        dto.setValidatedRemediationCount(e.getValidatedRemediationCount());
        dto.setFailedRemediationCount(e.getFailedRemediationCount());

        dto.setLastHealthyRunId(e.getLastHealthyRunId());
        dto.setLastDegradedRunId(e.getLastDegradedRunId());
        dto.setLastCriticalRunId(e.getLastCriticalRunId());
        dto.setLastIncidentCode(e.getLastIncidentCode());
        dto.setUpdatedAt(e.getUpdatedAt());

        try {
            if (e.getScoreBreakdownJson() != null) {
                dto.setScoreBreakdown(objectMapper.readValue(e.getScoreBreakdownJson(), ReliabilityScoreBreakdownDto.class));
            }
            if (e.getRiskFactorsJson() != null) {
                dto.setRiskFactors(objectMapper.readValue(e.getRiskFactorsJson(), new TypeReference<List<ReliabilityRiskFactorDto>>() {}));
            }
            if (e.getStrengthsJson() != null) {
                dto.setStrengths(objectMapper.readValue(e.getStrengthsJson(), new TypeReference<List<ReliabilityStrengthDto>>() {}));
            }
            if (e.getTrajectoryJson() != null) {
                dto.setTrajectory(objectMapper.readValue(e.getTrajectoryJson(), new TypeReference<List<ReliabilityTrajectoryPointDto>>() {}));
            }
        } catch (Exception ex) {
            logger.warn("Failed to deserialize profile JSON for lineage {}: {}", lineage, ex.getMessage());
        }

        // Recovery and Durability summaries
        dto.setRecoveryProfile(new RecoveryProfileDto(
                e.getHistoricalIncidentCount(),
                Math.max(0, e.getHistoricalIncidentCount() - e.getUnresolvedIncidentCount()),
                e.getUnresolvedIncidentCount(),
                e.getRecoveryRate() != null ? e.getRecoveryRate() : 100.0,
                e.getRegressionRate() != null ? e.getRegressionRate().intValue() : 0,
                e.getHistoricalIncidentCount(),
                String.format("Recovery rate: %.1f%%", e.getRecoveryRate() != null ? e.getRecoveryRate() : 100.0)
        ));

        dto.setRemediationDurability(new RemediationDurabilitySummaryDto(
                e.getRemediationCount(),
                e.getValidatedRemediationCount(),
                Math.max(0, e.getValidatedRemediationCount() - e.getFailedRemediationCount()),
                0,
                e.getFailedRemediationCount(),
                0,
                e.getRemediationDurabilityRate() != null ? e.getRemediationDurabilityRate() : 100.0,
                String.format("Remediation durability rate: %.1f%%", e.getRemediationDurabilityRate() != null ? e.getRemediationDurabilityRate() : 100.0)
        ));

        // Recent events
        List<DiagnosticReliabilityEvent> events = eventRepository.findTop50ByModelLineageIdOrderByTimestampDesc(lineage);
        dto.setRecentEvents(events.stream().map(this::toEventDto).toList());

        return dto;
    }

    private DiagnosticReliabilityEventDto toEventDto(DiagnosticReliabilityEvent e) {
        DiagnosticReliabilityEventDto dto = new DiagnosticReliabilityEventDto();
        dto.setId(e.getId());
        dto.setModelLineageId(e.getModelLineageId());
        dto.setEventType(e.getEventType());
        dto.setRunId(e.getRunId());
        dto.setSourceType(e.getSourceType());
        dto.setSourceId(e.getSourceId());
        dto.setSeverity(e.getSeverity());
        dto.setSummary(e.getSummary());
        dto.setTimestamp(e.getTimestamp());
        return dto;
    }
}
