package com.modeldoctor.service;

import com.modeldoctor.domain.*;
import com.modeldoctor.dto.*;
import com.modeldoctor.exception.ResourceNotFoundException;
import com.modeldoctor.intelligence.incident.EvidenceSynthesisEngine;
import com.modeldoctor.intelligence.incident.IncidentCorrelationEngine;
import com.modeldoctor.intelligence.incident.IncidentLifecycleManager;
import com.modeldoctor.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Service orchestrating incident formation, alert correlation, evidence synthesis,
 * cross-phase intelligence integration, and operator decision recommendations.
 */
@Service
@SuppressWarnings("null")
public class IncidentAnalysisService {

    private final DiagnosticIncidentRepository incidentRepository;
    private final DiagnosticIncidentAlertRepository incidentAlertRepository;
    private final DiagnosticIncidentEventRepository incidentEventRepository;
    private final DiagnosticOperationalAlertRepository operationalAlertRepository;
    private final DiagnosticInvestigationRepository investigationRepository;
    private final DiagnosticRemediationRepository remediationRepository;
    private final DiagnosticExperimentRepository experimentRepository;
    private final DiagnosticRunRepository runRepository;
    private final ContinuousMonitoringService monitoringService;
    private final TemporalAnalysisService temporalService;
    private final IncidentCorrelationEngine correlationEngine;
    private final EvidenceSynthesisEngine synthesisEngine;
    private final IncidentLifecycleManager lifecycleManager;
    private final ObjectMapper objectMapper;

    public IncidentAnalysisService(
            DiagnosticIncidentRepository incidentRepository,
            DiagnosticIncidentAlertRepository incidentAlertRepository,
            DiagnosticIncidentEventRepository incidentEventRepository,
            DiagnosticOperationalAlertRepository operationalAlertRepository,
            DiagnosticInvestigationRepository investigationRepository,
            DiagnosticRemediationRepository remediationRepository,
            DiagnosticExperimentRepository experimentRepository,
            DiagnosticRunRepository runRepository,
            ContinuousMonitoringService monitoringService,
            TemporalAnalysisService temporalService,
            IncidentCorrelationEngine correlationEngine,
            EvidenceSynthesisEngine synthesisEngine,
            IncidentLifecycleManager lifecycleManager,
            ObjectMapper objectMapper) {
        this.incidentRepository = incidentRepository;
        this.incidentAlertRepository = incidentAlertRepository;
        this.incidentEventRepository = incidentEventRepository;
        this.operationalAlertRepository = operationalAlertRepository;
        this.investigationRepository = investigationRepository;
        this.remediationRepository = remediationRepository;
        this.experimentRepository = experimentRepository;
        this.runRepository = runRepository;
        this.monitoringService = monitoringService;
        this.temporalService = temporalService;
        this.correlationEngine = correlationEngine;
        this.synthesisEngine = synthesisEngine;
        this.lifecycleManager = lifecycleManager;
        this.objectMapper = objectMapper;
    }

    public String resolveLineage(String identifier) {
        return temporalService.resolveModelLineageId(identifier);
    }

    @Transactional(readOnly = true)
    public List<DiagnosticIncidentDto> getIncidents(String modelLineageId, boolean includeResolved) {
        String lineage = resolveLineage(modelLineageId);
        List<DiagnosticIncident> list;
        if (includeResolved) {
            list = incidentRepository.findByModelLineageIdOrderByPriorityScoreDesc(lineage);
        } else {
            list = incidentRepository.findByModelLineageIdAndLifecycleStateNotOrderByPriorityScoreDesc(
                    lineage, IncidentLifecycleState.RESOLVED);
        }
        return list.stream().map(lifecycleManager::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<DiagnosticIncidentDto> getIncidents(String modelLineageId) {
        return getIncidents(modelLineageId, true);
    }

    @Transactional(readOnly = true)
    public IncidentEvidenceDossierDto getIncidentDossier(String modelLineageId, Long incidentId) {
        String lineage = resolveLineage(modelLineageId);
        DiagnosticIncident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found with ID: " + incidentId));

        DiagnosticIncidentDto incDto = lifecycleManager.toDto(incident);

        IncidentEvidenceDossierDto dossier = new IncidentEvidenceDossierDto();
        dossier.setIncidentId(incident.getId());
        dossier.setIncidentCode(incident.getIncidentCode());
        dossier.setModelLineageId(lineage);
        dossier.setTitle(incident.getTitle());
        dossier.setCategory(incident.getCategory());
        dossier.setCurrentSeverity(incident.getCurrentSeverity());
        dossier.setPriorityScore(incident.getPriorityScore());
        dossier.setLifecycleState(incident.getLifecycleState());
        dossier.setPrimaryTarget(incident.getPrimaryTarget());
        dossier.setPrimaryMetric(incident.getPrimaryMetric());
        dossier.setEvidenceSummary(incident.getEvidenceSummary());
        dossier.setIndependentModuleCount(incident.getIndependentModuleCount());
        dossier.setRelatedAlertsCount(incident.getRelatedAlertsCount());
        dossier.setCurrentHealthState(incident.getCurrentHealthState());
        dossier.setFirstObservedAt(incident.getFirstObservedAt());
        dossier.setLastObservedAt(incident.getLastObservedAt());
        dossier.setResolvedAt(incident.getResolvedAt());
        dossier.setPriorityBreakdown(incDto.getPriorityBreakdown());
        dossier.setRelatedAlerts(incDto.getRelatedAlerts());
        dossier.setAuditEvents(incDto.getRecentEvents());

        // Fetch related alerts entities to construct evidence matrix
        List<DiagnosticOperationalAlert> alertEntities = new ArrayList<>();
        for (IncidentAlertCorrelationDto adto : incDto.getRelatedAlerts()) {
            operationalAlertRepository.findById(adto.getAlertId()).ifPresent(alertEntities::add);
        }
        dossier.setEvidenceMatrix(synthesisEngine.buildEvidenceMatrix(alertEntities));

        // Contradictory evidence
        ModelHealthDecisionDto health = monitoringService.getCurrentHealth(lineage);
        ContradictoryEvidenceDto conflict = synthesisEngine.evaluateContradictoryEvidence(
                alertEntities, health.getHealthVector(), false);
        dossier.setContradictoryEvidence(conflict);

        // Decision Recommendation
        boolean hasInvestigation = incident.getInvestigationTargetKey() != null && !incident.getInvestigationTargetKey().isBlank();
        boolean hasRemediation = incident.getRemediationId() != null;
        boolean hasValidatedExp = incident.getExperimentId() != null;
        IncidentDecisionDto decision = synthesisEngine.determineDecision(
                incident, hasInvestigation, hasRemediation, hasValidatedExp,
                conflict.isHasConflict(), incident.getIndependentModuleCount(), false);
        dossier.setDecision(decision);

        // Fetch linked Phase 6 Investigation
        if (hasInvestigation) {
            List<DiagnosticInvestigation> invs = investigationRepository.findByTargetKeyOrderByPriorityScoreDesc(incident.getInvestigationTargetKey());
            if (!invs.isEmpty()) {
                DiagnosticInvestigation inv = invs.get(0);
                InvestigationTargetDto idto = new InvestigationTargetDto();
                idto.setTargetType(inv.getTargetType());
                idto.setTargetKey(inv.getTargetKey());
                idto.setPriority(inv.getPriority() != null ? inv.getPriority().name() : "MEDIUM");
                idto.setPriorityScore(inv.getPriorityScore());
                idto.setHypothesis(inv.getHypothesis());
                dossier.setInvestigationTarget(idto);
            }
        }

        // Fetch linked Phase 7 Remediation
        if (incident.getRemediationId() != null) {
            remediationRepository.findById(incident.getRemediationId()).ifPresent(rem -> {
                DiagnosticRemediationDto rdto = new DiagnosticRemediationDto();
                rdto.setId(rem.getId());
                rdto.setTargetType(rem.getTargetType());
                rdto.setTargetKey(rem.getTargetKey());
                rdto.setRemediationType(rem.getRemediationType() != null ? rem.getRemediationType().name() : "REMEDIATION");
                rdto.setTitle(rem.getTitle());
                rdto.setDescription(rem.getDescription());
                rdto.setPriority(rem.getPriority() != null ? rem.getPriority().name() : "MEDIUM");
                rdto.setConfidence(rem.getConfidence() != null ? rem.getConfidence().name() : "HIGH");
                rdto.setStatus(rem.getStatus() != null ? rem.getStatus().name() : "PROPOSED");
                dossier.setRemediation(rdto);
            });
        }

        // Fetch linked Phase 8 Experiment
        if (incident.getExperimentId() != null) {
            experimentRepository.findById(incident.getExperimentId()).ifPresent(exp -> {
                DiagnosticExperimentDto edto = new DiagnosticExperimentDto();
                edto.setId(exp.getId());
                edto.setExperimentType(exp.getExperimentType());
                edto.setTitle(exp.getTitle());
                edto.setTargetType(exp.getTargetType());
                edto.setTargetKey(exp.getTargetKey());
                edto.setStatus(exp.getStatus());
                edto.setConclusion(exp.getConclusion());
                edto.setCandidateRunId(exp.getCandidateRunId());
                dossier.setExperiment(edto);
            });
        }

        return dossier;
    }

    /**
     * Idempotently recalculates incidents from active operational alerts, correlates evidence,
     * links investigations/remediations/experiments, and persists incident state.
     */
    @Transactional
    public IncidentRecalculateResponseDto recalculateIncidents(String modelLineageId) {
        String lineage = resolveLineage(modelLineageId);

        // 1. Fetch current operational health decision
        ModelHealthDecisionDto health = monitoringService.getCurrentHealth(lineage);

        // 2. Fetch all operational alerts for this lineage
        List<DiagnosticOperationalAlert> allAlerts = operationalAlertRepository.findByModelLineageIdOrderByLastObservedAtDesc(lineage);

        // Filter out alerts belonging to EXPERIMENT runs (operational baseline isolation)
        List<DiagnosticOperationalAlert> baselineAlerts = allAlerts.stream()
                .filter(a -> {
                    if (a.getLastSeenRunId() != null) {
                        Optional<DiagnosticRun> r = runRepository.findById(a.getLastSeenRunId());
                        if (r.isPresent() && "EXPERIMENT".equalsIgnoreCase(r.get().getRunType())) {
                            return false;
                        }
                    }
                    return true;
                })
                .toList();

        if (baselineAlerts.isEmpty()) {
            return new IncidentRecalculateResponseDto(lineage, 0, 0, 0, null, true,
                    "No active operational alerts found for lineage. No incidents formed.");
        }

        // 3. Cluster alerts into candidate incident groups
        List<List<DiagnosticOperationalAlert>> clusters = clusterAlerts(baselineAlerts);

        int formed = 0;
        int updated = 0;
        String topCode = null;
        int maxPriority = -1;

        for (List<DiagnosticOperationalAlert> cluster : clusters) {
            if (cluster.isEmpty()) continue;

            // Determine cluster primary target & metric
            DiagnosticOperationalAlert seed = cluster.get(0);
            String primaryTarget = seed.getTargetKey() != null ? seed.getTargetKey() : "GLOBAL";
            String primaryMetric = seed.getMetricName();

            // Highest severity among cluster alerts
            String highestSev = cluster.stream()
                    .map(DiagnosticOperationalAlert::getCurrentSeverity)
                    .filter(Objects::nonNull)
                    .min(Comparator.comparingInt(this::severityRank))
                    .orElse("MEDIUM");

            // Incident Category
            IncidentCategory category = correlationEngine.determineCategory(cluster);

            // Deterministic Fingerprint
            String fingerprint = correlationEngine.generateFingerprint(lineage, primaryTarget, category);

            // Independent modules count & persistence
            int independentModules = synthesisEngine.countIndependentModules(cluster);
            int maxPersistence = cluster.stream().mapToInt(DiagnosticOperationalAlert::getConsecutiveCount).max().orElse(1);
            boolean isEscalating = cluster.stream().anyMatch(a -> "ESCALATED".equalsIgnoreCase(a.getSeverityChange()) || a.getEscalationCount() > 0);

            // Priority Breakdown
            IncidentPriorityBreakdownDto pb = synthesisEngine.calculatePriorityBreakdown(
                    highestSev, independentModules, maxPersistence, isEscalating, health.getOverallState());

            // Contradictory evidence check
            ContradictoryEvidenceDto conflict = synthesisEngine.evaluateContradictoryEvidence(
                    cluster, health.getHealthVector(), false);

            // Evidence Summary
            String evidenceSummary = synthesisEngine.generateEvidenceSummary(primaryTarget, category, independentModules, cluster);
            String title = correlationEngine.generateTitle(highestSev, primaryTarget, category, cluster);

            // Find or create incident
            Optional<DiagnosticIncident> existingOpt = incidentRepository.findByModelLineageIdAndIncidentFingerprint(lineage, fingerprint);
            DiagnosticIncident incident;
            boolean isNew = false;
            boolean isReopened = false;

            if (existingOpt.isPresent()) {
                incident = existingOpt.get();
                if (incident.getLifecycleState() == IncidentLifecycleState.RESOLVED) {
                    isReopened = true;
                }
                updated++;
            } else {
                incident = new DiagnosticIncident();
                incident.setModelLineageId(lineage);
                incident.setIncidentFingerprint(fingerprint);
                incident.setIncidentCode(correlationEngine.generateIncidentCode(fingerprint, formed + 1));
                incident.setLifecycleState(IncidentLifecycleState.OPEN);
                incident.setFirstObservedAt(cluster.stream()
                        .map(DiagnosticOperationalAlert::getFirstObservedAt)
                        .filter(Objects::nonNull)
                        .min(Comparator.naturalOrder())
                        .orElse(Instant.now()));
                isNew = true;
                formed++;
            }

            incident.setTitle(title);
            incident.setCategory(category);
            incident.setCurrentSeverity(highestSev);
            incident.setPriorityScore(pb.getTotalPriorityScore());
            incident.setPrimaryTarget(primaryTarget);
            incident.setPrimaryMetric(primaryMetric);
            incident.setIndependentModuleCount(independentModules);
            incident.setRelatedAlertsCount(cluster.size());
            incident.setCurrentHealthState(health.getOverallState());
            incident.setHasContradictoryEvidence(conflict.isHasConflict());
            incident.setContradictoryEvidenceSummary(conflict.getSummary());
            incident.setEvidenceSummary(evidenceSummary);
            incident.setLastObservedAt(Instant.now());
            incident.setUpdatedAt(Instant.now());

            try {
                incident.setPriorityBreakdownJson(objectMapper.writeValueAsString(pb));
            } catch (Exception ignored) {}

            // Link Phase 6 Investigation target
            linkInvestigationTarget(incident, primaryTarget);

            // Link Phase 7 Remediation
            linkRemediation(incident, primaryTarget);

            // Link Phase 8 Experiment
            linkExperiment(incident, primaryTarget);

            // Determine Decision Recommendation
            boolean hasInvestigation = incident.getInvestigationTargetKey() != null;
            boolean hasRemediation = incident.getRemediationId() != null;
            boolean hasValidatedExp = incident.getExperimentId() != null;
            IncidentDecisionDto decision = synthesisEngine.determineDecision(
                    incident, hasInvestigation, hasRemediation, hasValidatedExp,
                    conflict.isHasConflict(), independentModules, isReopened);

            incident.setDecisionRecommendation(decision.getRecommendation());
            incident.setDecisionConfidence(decision.getConfidence());
            incident.setDecisionRationale(decision.getRationale());

            if (isReopened) {
                incident.setLifecycleState(IncidentLifecycleState.REOPENED);
                incident.setResolvedAt(null);
            }

            incident = incidentRepository.save(incident);

            if (isNew) {
                DiagnosticIncidentEvent createEvent = new DiagnosticIncidentEvent(
                        incident.getId(), lineage, "NONE", "OPEN", "SYSTEM", "CREATE",
                        "Incident created from correlated alerts", null);
                incidentEventRepository.save(createEvent);
            } else if (isReopened) {
                DiagnosticIncidentEvent reopenEvent = new DiagnosticIncidentEvent(
                        incident.getId(), lineage, "RESOLVED", "REOPENED", "SYSTEM", "REOPEN",
                        "Incident reopened: correlated alert condition recurred in operational monitoring", null);
                incidentEventRepository.save(reopenEvent);
            }

            // Sync DiagnosticIncidentAlert join records
            syncIncidentAlerts(incident, cluster);

            if (incident.getPriorityScore() > maxPriority) {
                maxPriority = incident.getPriorityScore();
                topCode = incident.getIncidentCode();
            }
        }

        return new IncidentRecalculateResponseDto(
                lineage, baselineAlerts.size(), formed, updated, topCode, true,
                String.format("Recalculation complete: %d incident(s) active across %d alerts. Top Incident: %s (Priority: %d).",
                        formed + updated, baselineAlerts.size(), topCode, maxPriority));
    }

    @Transactional
    public DiagnosticIncidentDto acknowledgeIncident(String modelLineageId, Long incidentId, AcknowledgeIncidentRequestDto request) {
        String lineage = resolveLineage(modelLineageId);
        DiagnosticIncident updated = lifecycleManager.acknowledge(
                lineage, incidentId, request.getActor(), request.getNote());
        return lifecycleManager.toDto(updated);
    }

    @Transactional
    public DiagnosticIncidentDto investigateIncident(String modelLineageId, Long incidentId, InvestigateIncidentRequestDto request) {
        String lineage = resolveLineage(modelLineageId);
        DiagnosticIncident updated = lifecycleManager.investigate(
                lineage, incidentId, request.getActor(), request.getNote());
        return lifecycleManager.toDto(updated);
    }

    @Transactional
    public DiagnosticIncidentDto planRemediation(String modelLineageId, Long incidentId, PlanRemediationRequestDto request) {
        String lineage = resolveLineage(modelLineageId);
        DiagnosticIncident updated = lifecycleManager.planRemediation(
                lineage, incidentId, request.getRemediationId(), request.getActor(), request.getPlanDetails());
        return lifecycleManager.toDto(updated);
    }

    @Transactional
    public DiagnosticIncidentDto startValidation(String modelLineageId, Long incidentId, StartValidationRequestDto request) {
        String lineage = resolveLineage(modelLineageId);
        DiagnosticIncident updated = lifecycleManager.startValidation(
                lineage, incidentId, request.getExperimentId(), request.getActor(), request.getValidationNotes());
        return lifecycleManager.toDto(updated);
    }

    @Transactional
    public DiagnosticIncidentDto resolveIncident(String modelLineageId, Long incidentId, ResolveIncidentRequestDto request) {
        String lineage = resolveLineage(modelLineageId);
        DiagnosticIncident updated = lifecycleManager.resolve(
                lineage, incidentId, request.getResolutionReason(), request.getActor());
        return lifecycleManager.toDto(updated);
    }

    @Transactional
    public DiagnosticIncidentDto suppressIncident(String modelLineageId, Long incidentId, SuppressIncidentRequestDto request) {
        String lineage = resolveLineage(modelLineageId);
        DiagnosticIncident updated = lifecycleManager.suppress(
                lineage, incidentId, request.getReason(), request.getDurationHours(), request.getActor());
        return lifecycleManager.toDto(updated);
    }

    /**
     * Clusters alerts into candidate incident groups based on target and correlation scoring.
     */
    private List<List<DiagnosticOperationalAlert>> clusterAlerts(List<DiagnosticOperationalAlert> alerts) {
        List<List<DiagnosticOperationalAlert>> clusters = new ArrayList<>();
        Set<Long> processed = new HashSet<>();

        for (int i = 0; i < alerts.size(); i++) {
            DiagnosticOperationalAlert a1 = alerts.get(i);
            if (processed.contains(a1.getId())) continue;

            List<DiagnosticOperationalAlert> currentCluster = new ArrayList<>();
            currentCluster.add(a1);
            processed.add(a1.getId());

            boolean addedNew;
            do {
                addedNew = false;
                for (DiagnosticOperationalAlert a2 : alerts) {
                    if (processed.contains(a2.getId())) continue;

                    boolean correlates = currentCluster.stream().anyMatch(existing -> {
                        String t1 = existing.getTargetKey();
                        String t2 = a2.getTargetKey();
                        if (t1 != null && !t1.equalsIgnoreCase("GLOBAL") && t1.equalsIgnoreCase(t2)) {
                            return true;
                        }
                        return correlationEngine.evaluateCorrelation(existing, a2).isCorrelated();
                    });

                    if (correlates) {
                        currentCluster.add(a2);
                        processed.add(a2.getId());
                        addedNew = true;
                    }
                }
            } while (addedNew);

            clusters.add(currentCluster);
        }

        return clusters;
    }

    private void syncIncidentAlerts(DiagnosticIncident incident, List<DiagnosticOperationalAlert> cluster) {
        incidentAlertRepository.deleteByIncidentId(incident.getId());
        for (DiagnosticOperationalAlert alert : cluster) {
            var corrRes = correlationEngine.evaluateCorrelation(cluster.get(0), alert);
            String reasonsJson = null;
            try {
                reasonsJson = objectMapper.writeValueAsString(corrRes.reasons());
            } catch (Exception ignored) {}

            DiagnosticIncidentAlert join = new DiagnosticIncidentAlert(
                    incident.getId(), alert.getId(), alert.getAlertFingerprint(),
                    corrRes.score(), reasonsJson);
            incidentAlertRepository.save(join);
        }
    }

    private void linkInvestigationTarget(DiagnosticIncident incident, String primaryTarget) {
        if (primaryTarget != null && !primaryTarget.isBlank()) {
            List<DiagnosticInvestigation> invs = investigationRepository.findByTargetKeyOrderByPriorityScoreDesc(primaryTarget);
            if (!invs.isEmpty()) {
                incident.setInvestigationTargetKey(invs.get(0).getTargetKey());
            }
        }
    }

    private void linkRemediation(DiagnosticIncident incident, String primaryTarget) {
        if (primaryTarget != null && !primaryTarget.isBlank()) {
            List<DiagnosticRemediation> rems = remediationRepository.findByTargetKeyOrderByPriorityScoreDesc(primaryTarget);
            if (!rems.isEmpty()) {
                incident.setRemediationId(rems.get(0).getId());
            }
        }
    }

    private void linkExperiment(DiagnosticIncident incident, String primaryTarget) {
        if (primaryTarget != null && !primaryTarget.isBlank()) {
            List<DiagnosticExperiment> exps = experimentRepository.findByTargetKeyOrderByCreatedAtDesc(primaryTarget);
            if (!exps.isEmpty()) {
                incident.setExperimentId(exps.get(0).getId());
            }
        }
    }

    private int severityRank(String sev) {
        if (sev == null) return 4;
        return switch (sev.toUpperCase()) {
            case "CRITICAL" -> 1;
            case "HIGH" -> 2;
            case "MEDIUM", "WARNING" -> 3;
            default -> 4;
        };
    }
}
