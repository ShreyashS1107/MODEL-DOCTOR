package com.modeldoctor.intelligence.incident;

import com.modeldoctor.domain.DiagnosticIncident;
import com.modeldoctor.domain.DiagnosticIncidentAlert;
import com.modeldoctor.domain.DiagnosticIncidentEvent;
import com.modeldoctor.domain.IncidentLifecycleState;
import com.modeldoctor.dto.DiagnosticIncidentDto;
import com.modeldoctor.dto.DiagnosticIncidentEventDto;
import com.modeldoctor.dto.IncidentAlertCorrelationDto;
import com.modeldoctor.dto.IncidentPriorityBreakdownDto;
import com.modeldoctor.exception.ResourceNotFoundException;
import com.modeldoctor.repository.DiagnosticIncidentAlertRepository;
import com.modeldoctor.repository.DiagnosticIncidentEventRepository;
import com.modeldoctor.repository.DiagnosticIncidentRepository;
import com.modeldoctor.repository.DiagnosticOperationalAlertRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * State machine and lifecycle audit manager for operational model diagnostic incidents.
 */
@Component
@SuppressWarnings("null")
public class IncidentLifecycleManager {

    private final DiagnosticIncidentRepository incidentRepository;
    private final DiagnosticIncidentAlertRepository incidentAlertRepository;
    private final DiagnosticIncidentEventRepository incidentEventRepository;
    private final DiagnosticOperationalAlertRepository operationalAlertRepository;
    private final ObjectMapper objectMapper;

    public IncidentLifecycleManager(
            DiagnosticIncidentRepository incidentRepository,
            DiagnosticIncidentAlertRepository incidentAlertRepository,
            DiagnosticIncidentEventRepository incidentEventRepository,
            DiagnosticOperationalAlertRepository operationalAlertRepository,
            ObjectMapper objectMapper) {
        this.incidentRepository = incidentRepository;
        this.incidentAlertRepository = incidentAlertRepository;
        this.incidentEventRepository = incidentEventRepository;
        this.operationalAlertRepository = operationalAlertRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DiagnosticIncident transition(Long incidentId, IncidentLifecycleState targetState, String actor, String action, String reason, String evidenceRef) {
        DiagnosticIncident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Diagnostic incident not found with ID: " + incidentId));

        IncidentLifecycleState currentState = incident.getLifecycleState();
        if (!currentState.canTransitionTo(targetState)) {
            throw new IllegalArgumentException(String.format(
                    "Invalid incident lifecycle transition from %s to %s for incident %s",
                    currentState, targetState, incident.getIncidentCode()));
        }

        incident.setLifecycleState(targetState);
        incident.setUpdatedAt(Instant.now());

        if (targetState == IncidentLifecycleState.RESOLVED) {
            incident.setResolvedAt(Instant.now());
        } else if (targetState == IncidentLifecycleState.REOPENED) {
            incident.setResolvedAt(null);
        }

        DiagnosticIncident saved = incidentRepository.save(incident);

        // Record audit event
        DiagnosticIncidentEvent event = new DiagnosticIncidentEvent(
                saved.getId(),
                saved.getModelLineageId(),
                currentState.name(),
                targetState.name(),
                actor != null ? actor : "USER",
                action != null ? action : "TRANSITION",
                reason,
                evidenceRef
        );
        incidentEventRepository.save(event);

        return saved;
    }

    @Transactional
    public DiagnosticIncident acknowledge(String modelLineageId, Long incidentId, String actor, String note) {
        return transition(incidentId, IncidentLifecycleState.ACKNOWLEDGED, actor, "ACKNOWLEDGE", note, null);
    }

    @Transactional
    public DiagnosticIncident investigate(String modelLineageId, Long incidentId, String actor, String note) {
        return transition(incidentId, IncidentLifecycleState.INVESTIGATING, actor, "INVESTIGATE", note, null);
    }

    @Transactional
    public DiagnosticIncident planRemediation(String modelLineageId, Long incidentId, Long remediationId, String actor, String planDetails) {
        DiagnosticIncident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Diagnostic incident not found: " + incidentId));
        if (remediationId != null) {
            incident.setRemediationId(remediationId);
            incidentRepository.save(incident);
        }
        return transition(incidentId, IncidentLifecycleState.MITIGATION_PLANNED, actor, "PLAN_REMEDIATION",
                planDetails, remediationId != null ? "REMEDIATION_ID::" + remediationId : null);
    }

    @Transactional
    public DiagnosticIncident startValidation(String modelLineageId, Long incidentId, String experimentId, String actor, String notes) {
        DiagnosticIncident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Diagnostic incident not found: " + incidentId));
        if (experimentId != null) {
            incident.setExperimentId(experimentId);
            incidentRepository.save(incident);
        }
        return transition(incidentId, IncidentLifecycleState.VALIDATING, actor, "START_VALIDATION",
                notes, experimentId != null ? "EXPERIMENT_ID::" + experimentId : null);
    }

    @Transactional
    public DiagnosticIncident resolve(String modelLineageId, Long incidentId, String reason, String actor) {
        return transition(incidentId, IncidentLifecycleState.RESOLVED, actor, "RESOLVE", reason, null);
    }

    @Transactional
    public DiagnosticIncident suppress(String modelLineageId, Long incidentId, String reason, int durationHours, String actor) {
        return transition(incidentId, IncidentLifecycleState.SUPPRESSED, actor, "SUPPRESS",
                String.format("Suppressed for %d hours: %s", durationHours, reason), null);
    }

    @Transactional
    public DiagnosticIncident reopen(DiagnosticIncident incident, String reason, String actor) {
        return transition(incident.getId(), IncidentLifecycleState.REOPENED, actor, "REOPEN", reason, "RECURRENCE_OBSERVED");
    }

    public DiagnosticIncidentDto toDto(DiagnosticIncident entity) {
        if (entity == null) return null;

        DiagnosticIncidentDto dto = new DiagnosticIncidentDto();
        dto.setId(entity.getId());
        dto.setIncidentCode(entity.getIncidentCode());
        dto.setModelLineageId(entity.getModelLineageId());
        dto.setIncidentFingerprint(entity.getIncidentFingerprint());
        dto.setTitle(entity.getTitle());
        dto.setCategory(entity.getCategory());
        dto.setCurrentSeverity(entity.getCurrentSeverity());
        dto.setPriorityScore(entity.getPriorityScore());
        dto.setLifecycleState(entity.getLifecycleState());
        dto.setPrimaryTarget(entity.getPrimaryTarget());
        dto.setPrimaryMetric(entity.getPrimaryMetric());
        dto.setDecisionRecommendation(entity.getDecisionRecommendation());
        dto.setDecisionConfidence(entity.getDecisionConfidence());
        dto.setDecisionRationale(entity.getDecisionRationale());
        dto.setEvidenceSummary(entity.getEvidenceSummary());
        dto.setIndependentModuleCount(entity.getIndependentModuleCount());
        dto.setRelatedAlertsCount(entity.getRelatedAlertsCount());
        dto.setInvestigationTargetKey(entity.getInvestigationTargetKey());
        dto.setRemediationId(entity.getRemediationId());
        dto.setExperimentId(entity.getExperimentId());
        dto.setCurrentHealthState(entity.getCurrentHealthState());
        dto.setHasContradictoryEvidence(entity.isHasContradictoryEvidence());
        dto.setContradictoryEvidenceSummary(entity.getContradictoryEvidenceSummary());
        dto.setFirstObservedAt(entity.getFirstObservedAt());
        dto.setLastObservedAt(entity.getLastObservedAt());
        dto.setResolvedAt(entity.getResolvedAt());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());

        // Parse priority breakdown
        if (entity.getPriorityBreakdownJson() != null && !entity.getPriorityBreakdownJson().isBlank()) {
            try {
                IncidentPriorityBreakdownDto pb = objectMapper.readValue(entity.getPriorityBreakdownJson(), IncidentPriorityBreakdownDto.class);
                dto.setPriorityBreakdown(pb);
            } catch (Exception ignored) {}
        }

        // Allowed lifecycle transitions
        List<String> allowed = new ArrayList<>();
        for (IncidentLifecycleState s : IncidentLifecycleState.values()) {
            if (s != entity.getLifecycleState() && entity.getLifecycleState().canTransitionTo(s)) {
                allowed.add(s.name());
            }
        }
        dto.setAllowedActions(allowed);

        // Fetch join alerts
        List<DiagnosticIncidentAlert> joinList = incidentAlertRepository.findByIncidentId(entity.getId());
        List<IncidentAlertCorrelationDto> alertDtos = new ArrayList<>();
        for (DiagnosticIncidentAlert ja : joinList) {
            operationalAlertRepository.findById(ja.getAlertId()).ifPresent(al -> {
                IncidentAlertCorrelationDto adto = new IncidentAlertCorrelationDto();
                adto.setAlertId(al.getId());
                adto.setAlertFingerprint(al.getAlertFingerprint());
                adto.setAlertType(al.getAlertType());
                adto.setTargetKey(al.getTargetKey());
                adto.setCurrentSeverity(al.getCurrentSeverity());
                adto.setLifecycleState(al.getLifecycleState());
                adto.setCorrelationScore(ja.getCorrelationScore());
                adto.setSourceModule(al.getSourceModule());
                adto.setMetricName(al.getMetricName());
                adto.setTriggerDescription(al.getTriggerDescription());
                adto.setFirstObservedAt(al.getFirstObservedAt());
                adto.setLastObservedAt(al.getLastObservedAt());

                if (ja.getCorrelationReasonsJson() != null) {
                    try {
                        List<String> reasons = objectMapper.readValue(ja.getCorrelationReasonsJson(), new TypeReference<List<String>>() {});
                        adto.setCorrelationReasons(reasons);
                    } catch (Exception ignored) {}
                }
                alertDtos.add(adto);
            });
        }
        dto.setRelatedAlerts(alertDtos);

        // Fetch recent events
        List<DiagnosticIncidentEvent> events = incidentEventRepository.findByIncidentIdOrderByTimestampDesc(entity.getId());
        List<DiagnosticIncidentEventDto> eventDtos = events.stream().map(this::toEventDto).toList();
        dto.setRecentEvents(eventDtos);

        return dto;
    }

    public DiagnosticIncidentEventDto toEventDto(DiagnosticIncidentEvent e) {
        DiagnosticIncidentEventDto dto = new DiagnosticIncidentEventDto();
        dto.setId(e.getId());
        dto.setIncidentId(e.getIncidentId());
        dto.setModelLineageId(e.getModelLineageId());
        dto.setPreviousState(e.getPreviousState());
        dto.setNewState(e.getNewState());
        dto.setActor(e.getActor());
        dto.setAction(e.getAction());
        dto.setReason(e.getReason());
        dto.setEvidenceReference(e.getEvidenceReference());
        dto.setTimestamp(e.getTimestamp());
        return dto;
    }
}
