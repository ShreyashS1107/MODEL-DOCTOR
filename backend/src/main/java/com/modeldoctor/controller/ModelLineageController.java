package com.modeldoctor.controller;

import com.modeldoctor.dto.*;
import com.modeldoctor.service.ContinuousMonitoringService;
import com.modeldoctor.service.IncidentAnalysisService;
import com.modeldoctor.service.TemporalAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/models")
@Tag(name = "Model Lineage, Incidents, Continuous Monitoring & Health Engine",
     description = "Endpoints for incident correlation, decision workspace, continuous monitoring, alert lifecycle, health decision vector, snapshots, and temporal intelligence")
public class ModelLineageController {

    private final TemporalAnalysisService temporalService;
    private final ContinuousMonitoringService monitoringService;
    private final IncidentAnalysisService incidentService;
    private final com.modeldoctor.service.ModelReliabilityService reliabilityService;

    @Autowired
    public ModelLineageController(
            TemporalAnalysisService temporalService,
            ContinuousMonitoringService monitoringService,
            IncidentAnalysisService incidentService,
            com.modeldoctor.service.ModelReliabilityService reliabilityService) {
        this.temporalService = temporalService;
        this.monitoringService = monitoringService;
        this.incidentService = incidentService;
        this.reliabilityService = reliabilityService;
    }

    // =========================================================================
    // Phase 12: Model Reliability Governance & Fleet Intelligence
    // =========================================================================

    @GetMapping("/reliability/fleet")
    @Operation(summary = "Get Fleet Reliability Overview", description = "Retrieves synthesized operational reliability overview, risk ranking, risk matrix, and recurring patterns across the entire model fleet.")
    public ResponseEntity<FleetOverviewDto> getFleetOverview() {
        return ResponseEntity.ok(reliabilityService.getFleetOverview());
    }

    @GetMapping("/reliability/fleet/risk")
    @Operation(summary = "Get Fleet Risk Ranking", description = "Retrieves prioritized operational risk rankings across all model lineages.")
    public ResponseEntity<List<FleetRiskRankDto>> getFleetRisk() {
        return ResponseEntity.ok(reliabilityService.getFleetRisk());
    }

    @GetMapping("/reliability/fleet/patterns")
    @Operation(summary = "Get Fleet Recurring Patterns", description = "Retrieves discovered cross-model operational risk patterns across multiple model lineages.")
    public ResponseEntity<List<FleetPatternDto>> getFleetPatterns() {
        return ResponseEntity.ok(reliabilityService.getFleetPatterns());
    }

    @GetMapping("/reliability/compare")
    @Operation(summary = "Compare Model Lineage Reliability", description = "Performs deterministic side-by-side reliability comparison between two model lineages.")
    public ResponseEntity<ModelComparisonDto> compareReliability(
            @RequestParam String left,
            @RequestParam String right) {
        return ResponseEntity.ok(reliabilityService.compareLineages(left, right));
    }

    @GetMapping("/{lineageId}/reliability")
    @Operation(summary = "Get Model Reliability Profile", description = "Retrieves comprehensive operational reliability dossier, bounded score (0-100), trajectory, recovery profile, durability summary, and governance recommendation.")
    public ResponseEntity<ModelReliabilityProfileDto> getReliability(@PathVariable String lineageId) {
        return ResponseEntity.ok(reliabilityService.getReliabilityProfile(lineageId));
    }

    @GetMapping("/{lineageId}/reliability/history")
    @Operation(summary = "Get Reliability History Trajectory", description = "Retrieves longitudinal reliability trajectory points across operational baseline runs.")
    public ResponseEntity<List<ReliabilityTrajectoryPointDto>> getReliabilityHistory(@PathVariable String lineageId) {
        return ResponseEntity.ok(reliabilityService.getReliabilityHistory(lineageId));
    }

    @GetMapping("/{lineageId}/reliability/events")
    @Operation(summary = "Get Reliability Governance Events", description = "Retrieves historical reliability governance events and provenance timeline.")
    public ResponseEntity<List<DiagnosticReliabilityEventDto>> getReliabilityEvents(@PathVariable String lineageId) {
        return ResponseEntity.ok(reliabilityService.getReliabilityEvents(lineageId));
    }

    @PostMapping("/{lineageId}/reliability/recalculate")
    @Operation(summary = "Recalculate Model Reliability", description = "Idempotently recalculates model reliability profile, score breakdown, trajectory, recovery metrics, and fleet patterns.")
    public ResponseEntity<ReliabilityRecalculateResponseDto> recalculateReliability(@PathVariable String lineageId) {
        return ResponseEntity.ok(reliabilityService.recalculateReliability(lineageId));
    }

    // =========================================================================
    // Phase 11: Incident Correlation, Evidence Synthesis & Decision Workspace
    // =========================================================================

    @GetMapping("/{lineageId}/incidents")
    @Operation(summary = "Get Incident Queue", description = "Retrieves prioritized operational incidents formed by deterministic alert correlation.")
    public ResponseEntity<List<DiagnosticIncidentDto>> getIncidents(
            @PathVariable String lineageId,
            @RequestParam(required = false, defaultValue = "true") boolean includeResolved) {
        return ResponseEntity.ok(incidentService.getIncidents(lineageId, includeResolved));
    }

    @GetMapping("/{lineageId}/incidents/{incidentId}")
    @Operation(summary = "Get Incident Evidence Dossier", description = "Retrieves complete evidence dossier, evidence matrix, contradictory signals, linked investigations/remediations/experiments, and operator decision recommendation.")
    public ResponseEntity<IncidentEvidenceDossierDto> getIncidentDossier(
            @PathVariable String lineageId,
            @PathVariable Long incidentId) {
        return ResponseEntity.ok(incidentService.getIncidentDossier(lineageId, incidentId));
    }

    @PostMapping("/{lineageId}/incidents/recalculate")
    @Operation(summary = "Recalculate Incidents", description = "Idempotently correlates active operational alerts into deterministic incidents and updates decision state.")
    public ResponseEntity<IncidentRecalculateResponseDto> recalculateIncidents(@PathVariable String lineageId) {
        return ResponseEntity.ok(incidentService.recalculateIncidents(lineageId));
    }

    @PostMapping("/{lineageId}/incidents/{incidentId}/acknowledge")
    @Operation(summary = "Acknowledge Incident", description = "Transitions incident lifecycle state from OPEN/REOPENED to ACKNOWLEDGED.")
    public ResponseEntity<DiagnosticIncidentDto> acknowledgeIncident(
            @PathVariable String lineageId,
            @PathVariable Long incidentId,
            @RequestBody(required = false) AcknowledgeIncidentRequestDto request) {
        AcknowledgeIncidentRequestDto req = request != null ? request : new AcknowledgeIncidentRequestDto("USER", "Acknowledged by operator");
        return ResponseEntity.ok(incidentService.acknowledgeIncident(lineageId, incidentId, req));
    }

    @PostMapping("/{lineageId}/incidents/{incidentId}/investigate")
    @Operation(summary = "Investigate Incident", description = "Transitions incident lifecycle state to INVESTIGATING.")
    public ResponseEntity<DiagnosticIncidentDto> investigateIncident(
            @PathVariable String lineageId,
            @PathVariable Long incidentId,
            @RequestBody(required = false) InvestigateIncidentRequestDto request) {
        InvestigateIncidentRequestDto req = request != null ? request : new InvestigateIncidentRequestDto("USER", "Investigation initiated");
        return ResponseEntity.ok(incidentService.investigateIncident(lineageId, incidentId, req));
    }

    @PostMapping("/{lineageId}/incidents/{incidentId}/plan-remediation")
    @Operation(summary = "Plan Mitigation", description = "Transitions incident lifecycle state to MITIGATION_PLANNED and links selected remediation.")
    public ResponseEntity<DiagnosticIncidentDto> planRemediation(
            @PathVariable String lineageId,
            @PathVariable Long incidentId,
            @RequestBody(required = false) PlanRemediationRequestDto request) {
        PlanRemediationRequestDto req = request != null ? request : new PlanRemediationRequestDto("USER", null, "Mitigation planned");
        return ResponseEntity.ok(incidentService.planRemediation(lineageId, incidentId, req));
    }

    @PostMapping("/{lineageId}/incidents/{incidentId}/start-validation")
    @Operation(summary = "Start Experimental Validation", description = "Transitions incident lifecycle state to VALIDATING and links Phase 8 experiment.")
    public ResponseEntity<DiagnosticIncidentDto> startValidation(
            @PathVariable String lineageId,
            @PathVariable Long incidentId,
            @RequestBody(required = false) StartValidationRequestDto request) {
        StartValidationRequestDto req = request != null ? request : new StartValidationRequestDto("USER", null, "Validation experiment running");
        return ResponseEntity.ok(incidentService.startValidation(lineageId, incidentId, req));
    }

    @PostMapping("/{lineageId}/incidents/{incidentId}/resolve")
    @Operation(summary = "Resolve Incident", description = "Transitions incident lifecycle state to RESOLVED with resolution reason.")
    public ResponseEntity<DiagnosticIncidentDto> resolveIncident(
            @PathVariable String lineageId,
            @PathVariable Long incidentId,
            @RequestBody(required = false) ResolveIncidentRequestDto request) {
        ResolveIncidentRequestDto req = request != null ? request : new ResolveIncidentRequestDto("USER", "Resolved by operator");
        return ResponseEntity.ok(incidentService.resolveIncident(lineageId, incidentId, req));
    }

    @PostMapping("/{lineageId}/incidents/{incidentId}/suppress")
    @Operation(summary = "Suppress Incident", description = "Temporarily suppresses incident notification while keeping underlying condition visible.")
    public ResponseEntity<DiagnosticIncidentDto> suppressIncident(
            @PathVariable String lineageId,
            @PathVariable Long incidentId,
            @RequestBody(required = false) SuppressIncidentRequestDto request) {
        SuppressIncidentRequestDto req = request != null ? request : new SuppressIncidentRequestDto("USER", "Suppressed by operator", 24);
        return ResponseEntity.ok(incidentService.suppressIncident(lineageId, incidentId, req));
    }

    // =========================================================================
    // Phase 10: Continuous Monitoring, Alert Lifecycle & Health Decision Engine
    // =========================================================================

    @GetMapping("/{lineageId}/monitoring-policy")
    @Operation(summary = "Get Monitoring Policy", description = "Retrieves the persistent monitoring contract and threshold configuration for a model lineage.")
    public ResponseEntity<DiagnosticMonitoringPolicyDto> getMonitoringPolicy(@PathVariable String lineageId) {
        return ResponseEntity.ok(monitoringService.getPolicy(lineageId));
    }

    @PutMapping("/{lineageId}/monitoring-policy")
    @Operation(summary = "Update Monitoring Policy", description = "Updates monitoring contract rules, observation windows, and hysteresis parameters with version audit.")
    public ResponseEntity<DiagnosticMonitoringPolicyDto> updateMonitoringPolicy(
            @PathVariable String lineageId,
            @RequestBody DiagnosticMonitoringPolicyDto request) {
        return ResponseEntity.ok(monitoringService.updatePolicy(lineageId, request));
    }

    @GetMapping("/{lineageId}/health")
    @Operation(summary = "Get Current Model Operational Health", description = "Evaluates multidimensional health vector, active operational alerts, and deterministic health state.")
    public ResponseEntity<ModelHealthDecisionDto> getCurrentHealth(@PathVariable String lineageId) {
        return ResponseEntity.ok(monitoringService.getCurrentHealth(lineageId));
    }

    @GetMapping("/{lineageId}/health/history")
    @Operation(summary = "Get Model Health History Snapshots", description = "Retrieves immutable point-in-time health evaluation snapshots over operational baseline runs.")
    public ResponseEntity<List<DiagnosticHealthSnapshotDto>> getHealthHistory(@PathVariable String lineageId) {
        return ResponseEntity.ok(monitoringService.getHealthHistory(lineageId));
    }

    @GetMapping("/{lineageId}/alerts")
    @Operation(summary = "Get Operational Monitoring Alerts", description = "Retrieves operational monitoring alerts with full lifecycle status, severity evolution, and provenance.")
    public ResponseEntity<List<OperationalAlertDto>> getOperationalAlerts(@PathVariable String lineageId) {
        return ResponseEntity.ok(monitoringService.getAlerts(lineageId));
    }

    @GetMapping("/{lineageId}/alerts/{alertId}")
    @Operation(summary = "Get Operational Alert by ID", description = "Retrieves a single operational monitoring alert by ID.")
    public ResponseEntity<OperationalAlertDto> getOperationalAlert(
            @PathVariable String lineageId,
            @PathVariable Long alertId) {
        return ResponseEntity.ok(monitoringService.getAlert(lineageId, alertId));
    }

    @PostMapping("/{lineageId}/alerts/{alertId}/acknowledge")
    @Operation(summary = "Acknowledge Alert", description = "Transitions alert lifecycle state from OPEN/REOPENED to ACKNOWLEDGED.")
    public ResponseEntity<OperationalAlertDto> acknowledgeAlert(
            @PathVariable String lineageId,
            @PathVariable Long alertId,
            @RequestBody(required = false) AcknowledgeAlertRequestDto request) {
        String actor = request != null ? request.getActor() : "USER";
        return ResponseEntity.ok(monitoringService.acknowledgeAlert(lineageId, alertId, actor));
    }

    @PostMapping("/{lineageId}/alerts/{alertId}/investigate")
    @Operation(summary = "Investigate Alert", description = "Transitions alert lifecycle state to INVESTIGATING for root-cause diagnosis.")
    public ResponseEntity<OperationalAlertDto> investigateAlert(
            @PathVariable String lineageId,
            @PathVariable Long alertId,
            @RequestBody(required = false) InvestigateAlertRequestDto request) {
        String actor = request != null ? request.getActor() : "USER";
        return ResponseEntity.ok(monitoringService.investigateAlert(lineageId, alertId, actor));
    }

    @PostMapping("/{lineageId}/alerts/{alertId}/suppress")
    @Operation(summary = "Suppress Alert", description = "Temporarily suppresses an alert with an explicit engineer reason and duration.")
    public ResponseEntity<OperationalAlertDto> suppressAlert(
            @PathVariable String lineageId,
            @PathVariable Long alertId,
            @RequestBody SuppressAlertRequestDto request) {
        String reason = request != null ? request.getReason() : "Suppressed by engineer";
        Integer duration = request != null ? request.getDurationHours() : 24;
        String actor = request != null ? request.getActor() : "USER";
        return ResponseEntity.ok(monitoringService.suppressAlert(lineageId, alertId, reason, duration, actor));
    }

    @PostMapping("/{lineageId}/alerts/{alertId}/resolve")
    @Operation(summary = "Resolve Alert", description = "Transitions alert lifecycle state to RESOLVED with explanation.")
    public ResponseEntity<OperationalAlertDto> resolveAlert(
            @PathVariable String lineageId,
            @PathVariable Long alertId,
            @RequestBody(required = false) ResolveAlertRequestDto request) {
        String reason = request != null ? request.getReason() : "Manually resolved";
        String actor = request != null ? request.getActor() : "USER";
        return ResponseEntity.ok(monitoringService.resolveAlert(lineageId, alertId, reason, actor));
    }

    @PostMapping("/{lineageId}/monitoring/recalculate")
    @Operation(summary = "Recalculate Model Monitoring & Health Decisions", description = "Idempotently rebuilds operational alerts, health vector, and snapshot history.")
    public ResponseEntity<MonitoringRecalculateResponseDto> recalculateMonitoring(@PathVariable String lineageId) {
        return ResponseEntity.ok(monitoringService.recalculateMonitoring(lineageId));
    }

    // =========================================================================
    // Phase 9: Longitudinal Model Monitoring & Temporal Intelligence
    // =========================================================================

    @GetMapping("/{lineageId}/history")
    @Operation(summary = "Get Model Lineage History", description = "Retrieves complete longitudinal model intelligence dossier.")
    public ResponseEntity<ModelLineageHistoryDto> getModelLineageHistory(
            @PathVariable String lineageId,
            @RequestParam(required = false, defaultValue = "ALL_AVAILABLE") String window) {
        return ResponseEntity.ok(temporalService.getModelLineageHistory(lineageId, window));
    }

    @GetMapping("/{lineageId}/temporal/metrics")
    @Operation(summary = "Get Temporal Metrics", description = "Retrieves metric time-series histories and trend calculations for a model lineage.")
    public ResponseEntity<List<TemporalMetricHistoryDto>> getTemporalMetrics(
            @PathVariable String lineageId,
            @RequestParam(required = false, defaultValue = "ALL_AVAILABLE") String window) {
        return ResponseEntity.ok(temporalService.getModelLineageHistory(lineageId, window).getMetricHistories());
    }

    @GetMapping("/{lineageId}/temporal/issues")
    @Operation(summary = "Get Model Issue Tracks", description = "Retrieves longitudinal issue tracks for a model lineage.")
    public ResponseEntity<List<IssueTrackDto>> getTemporalIssues(@PathVariable String lineageId) {
        return ResponseEntity.ok(temporalService.getModelLineageHistory(lineageId, "ALL_AVAILABLE").getIssueTracks());
    }

    @GetMapping("/{lineageId}/temporal/alerts")
    @Operation(summary = "Get Model Temporal Alerts", description = "Retrieves active longitudinal alerts for a model lineage.")
    public ResponseEntity<List<TemporalAlertDto>> getTemporalAlerts(@PathVariable String lineageId) {
        return ResponseEntity.ok(temporalService.getModelLineageHistory(lineageId, "ALL_AVAILABLE").getAlerts());
    }

    @GetMapping("/{lineageId}/temporal/change-points")
    @Operation(summary = "Get Metric Change Points", description = "Retrieves detected statistical change points for a model lineage.")
    public ResponseEntity<List<ChangePointDto>> getTemporalChangePoints(@PathVariable String lineageId) {
        return ResponseEntity.ok(temporalService.getModelLineageHistory(lineageId, "ALL_AVAILABLE").getChangePoints());
    }

    @GetMapping("/{lineageId}/temporal/remediations")
    @Operation(summary = "Get Remediation Durability", description = "Retrieves durability evaluations for a model lineage.")
    public ResponseEntity<List<RemediationDurabilityDto>> getTemporalRemediations(@PathVariable String lineageId) {
        return ResponseEntity.ok(temporalService.getModelLineageHistory(lineageId, "ALL_AVAILABLE").getRemediationDurability());
    }

    @GetMapping("/{lineageId}/temporal/timeline")
    @Operation(summary = "Get Model History Timeline", description = "Retrieves ordered run summaries for a model lineage.")
    public ResponseEntity<List<RunSummaryDto>> getTemporalTimeline(@PathVariable String lineageId) {
        return ResponseEntity.ok(temporalService.getModelLineageHistory(lineageId, "ALL_AVAILABLE").getOrderedRuns());
    }

    @PostMapping("/{lineageId}/temporal/recalculate")
    @Operation(summary = "Recalculate Model Temporal Intelligence", description = "Idempotently recalculates temporal intelligence for a model lineage.")
    public ResponseEntity<TemporalRecalculateResponseDto> recalculateTemporalIntelligence(@PathVariable String lineageId) {
        return ResponseEntity.ok(temporalService.recalculateTemporalIntelligence(lineageId));
    }
}
