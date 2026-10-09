package com.modeldoctor.controller;

import com.modeldoctor.dto.*;
import com.modeldoctor.service.DiagnosticService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/diagnostics")
@Tag(name = "Diagnostics Orchestrator", description = "Endpoints for diagnostic execution, run lifecycle, preflight validation, and results")
public class DiagnosticController {

    private final DiagnosticService diagnosticService;
    private final com.modeldoctor.service.ExperimentOrchestrationService experimentService;
    private final com.modeldoctor.service.TemporalAnalysisService temporalService;
    private final com.modeldoctor.service.ContinuousMonitoringService monitoringService;
    private final com.modeldoctor.service.IncidentAnalysisService incidentService;
    private final com.modeldoctor.service.ModelReliabilityService reliabilityService;

    @Autowired
    public DiagnosticController(
            DiagnosticService diagnosticService,
            com.modeldoctor.service.ExperimentOrchestrationService experimentService,
            com.modeldoctor.service.TemporalAnalysisService temporalService,
            com.modeldoctor.service.ContinuousMonitoringService monitoringService,
            com.modeldoctor.service.IncidentAnalysisService incidentService,
            com.modeldoctor.service.ModelReliabilityService reliabilityService) {
        this.diagnosticService = diagnosticService;
        this.experimentService = experimentService;
        this.temporalService = temporalService;
        this.monitoringService = monitoringService;
        this.incidentService = incidentService;
        this.reliabilityService = reliabilityService;
    }

    @PostMapping("/validate")
    @Operation(summary = "Preflight Run Configuration Validation", description = "Validates model/dataset compatibility, target cardinality, protected attributes, and module prerequisites without creating a run.")
    public ResponseEntity<ValidationResultDto> validateConfiguration(
            @RequestBody CreateDiagnosticRunRequestDto request) {
        ValidationResultDto result = diagnosticService.validateRunConfiguration(request);
        return ResponseEntity.ok(result);
    }

    @PostMapping
    @Operation(summary = "Create Diagnostic Run", description = "Registers a new diagnostic run in CREATED state with target configuration after verifying validation rules.")
    public ResponseEntity<DiagnosticRunResponseDto> createDiagnosticRun(
            @Valid @RequestBody CreateDiagnosticRunRequestDto request) {
        DiagnosticRunResponseDto response = diagnosticService.createDiagnosticRun(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Diagnostic Run", description = "Fetches metadata, status, selected modules, and timestamps for a run.")
    public ResponseEntity<DiagnosticRunResponseDto> getDiagnosticRun(@PathVariable String id) {
        return ResponseEntity.ok(diagnosticService.getDiagnosticRun(id));
    }

    @PostMapping("/{id}/run")
    @Operation(summary = "Start Diagnostic Run", description = "Executes the diagnostic run across Python ML engines and updates lifecycle state.")
    public ResponseEntity<DiagnosticRunResponseDto> startDiagnosticRun(@PathVariable String id) {
        return ResponseEntity.ok(diagnosticService.startDiagnosticRun(id));
    }

    @PostMapping("/{id}/retry")
    @Operation(summary = "Retry Diagnostic Run", description = "Retries execution for a FAILED or PARTIAL diagnostic run, re-executing failed modules.")
    public ResponseEntity<DiagnosticRunResponseDto> retryDiagnosticRun(@PathVariable String id) {
        return ResponseEntity.ok(diagnosticService.retryDiagnosticRun(id));
    }

    @PostMapping("/{id}/retry-modules")
    @Operation(summary = "Retry Specific Modules", description = "Retries only specified failed modules for a FAILED or PARTIAL run.")
    public ResponseEntity<DiagnosticRunResponseDto> retryDiagnosticModules(
            @PathVariable String id,
            @RequestBody RetryModulesRequestDto request) {
        return ResponseEntity.ok(diagnosticService.retryDiagnosticModules(id, request.getModules()));
    }

    @GetMapping("/{id}/events")
    @Operation(summary = "Get Execution Events Timeline", description = "Retrieves timestamped audit events of the execution lifecycle.")
    public ResponseEntity<List<DiagnosticRunEventDto>> getDiagnosticRunEvents(@PathVariable String id) {
        return ResponseEntity.ok(diagnosticService.getDiagnosticRunEvents(id));
    }

    @GetMapping("/{id}/progress")
    @Operation(summary = "Get Execution Progress", description = "Calculates and returns real execution progress and module breakdown.")
    public ResponseEntity<DiagnosticProgressDto> getDiagnosticRunProgress(@PathVariable String id) {
        return ResponseEntity.ok(diagnosticService.getDiagnosticRunProgress(id));
    }

    @PostMapping("/recover-stale")
    @Operation(summary = "Recover Stale Executions", description = "Finds and marks stale RUNNING runs that have exceeded timeout threshold as FAILED (EXECUTION_STALE).")
    public ResponseEntity<List<String>> recoverStaleRuns(
            @RequestParam(required = false, defaultValue = "15") long thresholdMinutes) {
        return ResponseEntity.ok(diagnosticService.recoverStaleRuns(thresholdMinutes));
    }

    @GetMapping("/{id}/results")
    @Operation(summary = "Get Diagnostic Results", description = "Retrieves structured module-level diagnostic outputs.")
    public ResponseEntity<DiagnosticResultsResponseDto> getDiagnosticResults(@PathVariable String id) {
        return ResponseEntity.ok(diagnosticService.getDiagnosticResults(id));
    }

    @GetMapping("/health-score")
    @Operation(summary = "Model Health Score HUD", description = "Retrieves composite health score and diagnostic category breakdown.")
    public ResponseEntity<ModelHealthScoreDto> getModelHealthScore(
            @RequestParam(required = false, defaultValue = "MD-FRAUD-XGB-V4") String modelId) {
        return ResponseEntity.ok(diagnosticService.getModelHealthOverview(modelId));
    }

    @GetMapping("/{id}/correlations")
    @Operation(summary = "Get Cross-Module Diagnostic Correlations", description = "Retrieves prioritized cross-module investigation findings and multi-module failure risk patterns.")
    public ResponseEntity<List<DiagnosticCorrelationDto>> getDiagnosticCorrelations(@PathVariable String id) {
        return ResponseEntity.ok(diagnosticService.getCorrelations(id));
    }

    @GetMapping("/{id}/summary")
    @Operation(summary = "Get Run Diagnostic Summary", description = "Retrieves high-level diagnostic summary, critical finding counts, top investigation areas, and feature profiles.")
    public ResponseEntity<RunSummaryDto> getDiagnosticRunSummary(@PathVariable String id) {
        return ResponseEntity.ok(diagnosticService.getRunSummary(id));
    }

    @PostMapping("/{id}/correlations/recalculate")
    @Operation(summary = "Recalculate Correlations", description = "Explicitly re-runs cross-module correlation rules on persisted raw results.")
    public ResponseEntity<List<DiagnosticCorrelationDto>> recalculateDiagnosticCorrelations(@PathVariable String id) {
        return ResponseEntity.ok(diagnosticService.recalculateCorrelations(id));
    }

    @GetMapping("/{id}/investigations")
    @Operation(summary = "Get Ranked Investigation Targets", description = "Retrieves prioritized, evidence-backed root-cause investigation targets with deterministic priority scores and non-causal hypotheses.")
    public ResponseEntity<List<InvestigationTargetDto>> getDiagnosticInvestigations(@PathVariable String id) {
        return ResponseEntity.ok(diagnosticService.getInvestigations(id));
    }

    @GetMapping("/{id}/investigations/{targetKey}")
    @Operation(summary = "Get Investigation Dossier", description = "Retrieves the comprehensive evidence chain, ordered investigation path, next inspection actions, and provenance for a target.")
    public ResponseEntity<InvestigationDossierDto> getDiagnosticInvestigationDossier(
            @PathVariable String id,
            @PathVariable String targetKey) {
        return ResponseEntity.ok(diagnosticService.getInvestigationDossier(id, targetKey));
    }

    @GetMapping("/{id}/evidence-graph")
    @Operation(summary = "Get Diagnostic Evidence Graph", description = "Retrieves the deterministic, observational evidence graph nodes, edges, and provenance connections.")
    public ResponseEntity<EvidenceGraphDto> getDiagnosticEvidenceGraph(@PathVariable String id) {
        return ResponseEntity.ok(diagnosticService.getEvidenceGraph(id));
    }

    // =========================================================================
    // Phase 7: Remediation Decision Support & Run Comparison
    // =========================================================================

    @GetMapping("/{id}/remediations")
    @Operation(summary = "Get Ranked Remediation Candidates", description = "Retrieves prioritized, evidence-grounded remediation recommendations with validation hypotheses, acceptance criteria, and regression guards.")
    public ResponseEntity<List<DiagnosticRemediationDto>> getDiagnosticRemediations(@PathVariable String id) {
        return ResponseEntity.ok(diagnosticService.getRemediations(id));
    }

    @GetMapping("/{id}/remediations/{remediationId}")
    @Operation(summary = "Get Remediation Dossier", description = "Retrieves the comprehensive dossier for a specific remediation candidate by ID.")
    public ResponseEntity<DiagnosticRemediationDto> getDiagnosticRemediationById(
            @PathVariable String id,
            @PathVariable String remediationId) {
        return ResponseEntity.ok(diagnosticService.getRemediationById(remediationId));
    }

    @PostMapping("/{id}/remediations/recalculate")
    @Operation(summary = "Recalculate Remediations", description = "Explicitly re-runs deterministic remediation rules against persisted evidence and investigations.")
    public ResponseEntity<List<DiagnosticRemediationDto>> recalculateDiagnosticRemediations(@PathVariable String id) {
        return ResponseEntity.ok(diagnosticService.recalculateRemediations(id));
    }

    @PostMapping("/{id}/remediations/{remediationId}/select")
    @Operation(summary = "Select Remediation Candidate", description = "Marks a remediation candidate as SELECTED by the engineer for investigation.")
    public ResponseEntity<DiagnosticRemediationDto> selectRemediation(
            @PathVariable String id,
            @PathVariable String remediationId) {
        return ResponseEntity.ok(diagnosticService.selectRemediation(remediationId));
    }

    @PostMapping("/{id}/remediations/{remediationId}/reject")
    @Operation(summary = "Reject Remediation Candidate", description = "Marks a remediation candidate as REJECTED with an optional rationale.")
    public ResponseEntity<DiagnosticRemediationDto> rejectRemediation(
            @PathVariable String id,
            @PathVariable String remediationId,
            @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(diagnosticService.rejectRemediation(remediationId, reason));
    }

    @GetMapping("/{id}/comparison/{candidateRunId}")
    @Operation(summary = "Compare Diagnostic Runs", description = "Performs a deterministic before/after metric comparison between a baseline run and a candidate run.")
    public ResponseEntity<DiagnosticComparisonDto> compareDiagnosticRuns(
            @PathVariable String id,
            @PathVariable String candidateRunId) {
        return ResponseEntity.ok(diagnosticService.compareRuns(id, candidateRunId));
    }

    @GetMapping("/{id}/experiments")
    @Operation(summary = "List Experimental Validation Candidates", description = "Retrieves all experiments associated with a baseline diagnostic run.")
    public ResponseEntity<List<DiagnosticExperimentDto>> getExperiments(@PathVariable String id) {
        return ResponseEntity.ok(experimentService.getExperimentsForRun(id));
    }

    @GetMapping("/{id}/experiments/{experimentId}")
    @Operation(summary = "Get Experiment Dossier", description = "Retrieves detailed experimental evaluation dossier including interventions, provenance, metrics, and acceptance criteria.")
    public ResponseEntity<DiagnosticExperimentDto> getExperiment(
            @PathVariable String id,
            @PathVariable String experimentId) {
        return ResponseEntity.ok(experimentService.getExperiment(id, experimentId));
    }

    @PostMapping("/{id}/experiments")
    @Operation(summary = "Create Experimental Validation Candidate", description = "Registers a new controlled experiment against a baseline run and remediation hypothesis.")
    public ResponseEntity<DiagnosticExperimentDto> createExperiment(
            @PathVariable String id,
            @RequestBody CreateExperimentRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(experimentService.createExperiment(id, request));
    }

    @PostMapping("/{id}/experiments/{experimentId}/execute")
    @Operation(summary = "Execute Experimental Validation", description = "Executes the experimental strategy, evaluates candidate run, computes paired metrics, and assesses acceptance criteria.")
    public ResponseEntity<DiagnosticExperimentDto> executeExperiment(
            @PathVariable String id,
            @PathVariable String experimentId) {
        return ResponseEntity.ok(experimentService.executeExperiment(id, experimentId));
    }

    @PostMapping("/{id}/experiments/{experimentId}/cancel")
    @Operation(summary = "Cancel Experiment", description = "Cancels a proposed or running experiment.")
    public ResponseEntity<DiagnosticExperimentDto> cancelExperiment(
            @PathVariable String id,
            @PathVariable String experimentId) {
        return ResponseEntity.ok(experimentService.cancelExperiment(id, experimentId));
    }

    @GetMapping("/{id}/experiments/{experimentId}/comparison")
    @Operation(summary = "Get Experiment Comparison", description = "Retrieves the before/after metric comparison produced by an experiment.")
    public ResponseEntity<DiagnosticExperimentDto> getExperimentComparison(
            @PathVariable String id,
            @PathVariable String experimentId) {
        return ResponseEntity.ok(experimentService.getExperiment(id, experimentId));
    }

    // =========================================================================
    // Phase 9: Longitudinal Model Monitoring & Temporal Intelligence
    // =========================================================================

    @GetMapping("/{id}/temporal/history")
    @Operation(summary = "Get Model Lineage History", description = "Retrieves longitudinal model history, metric time-series, issue tracks, alerts, change points, and remediation durability.")
    public ResponseEntity<ModelLineageHistoryDto> getModelLineageHistory(
            @PathVariable String id,
            @RequestParam(required = false, defaultValue = "ALL_AVAILABLE") String window) {
        return ResponseEntity.ok(temporalService.getModelLineageHistory(id, window));
    }

    @GetMapping("/{id}/temporal/metrics")
    @Operation(summary = "Get Temporal Metric Time Series", description = "Retrieves historical metric series and trend models.")
    public ResponseEntity<List<TemporalMetricHistoryDto>> getTemporalMetrics(
            @PathVariable String id,
            @RequestParam(required = false, defaultValue = "ALL_AVAILABLE") String window) {
        return ResponseEntity.ok(temporalService.getModelLineageHistory(id, window).getMetricHistories());
    }

    @GetMapping("/{id}/temporal/issues")
    @Operation(summary = "Get Longitudinal Issue Tracks", description = "Retrieves issue tracks grouping historical findings by target with persistence states.")
    public ResponseEntity<List<IssueTrackDto>> getTemporalIssues(@PathVariable String id) {
        return ResponseEntity.ok(temporalService.getModelLineageHistory(id, "ALL_AVAILABLE").getIssueTracks());
    }

    @GetMapping("/{id}/temporal/alerts")
    @Operation(summary = "Get Temporal Alerts", description = "Retrieves prioritized longitudinal alerts (new degradations, persistence, change points).")
    public ResponseEntity<List<TemporalAlertDto>> getTemporalAlerts(@PathVariable String id) {
        return ResponseEntity.ok(temporalService.getModelLineageHistory(id, "ALL_AVAILABLE").getAlerts());
    }

    @GetMapping("/{id}/temporal/change-points")
    @Operation(summary = "Get Metric Change Points", description = "Retrieves detected statistical change points in model metrics.")
    public ResponseEntity<List<ChangePointDto>> getTemporalChangePoints(@PathVariable String id) {
        return ResponseEntity.ok(temporalService.getModelLineageHistory(id, "ALL_AVAILABLE").getChangePoints());
    }

    @GetMapping("/{id}/temporal/remediations")
    @Operation(summary = "Get Remediation Durability", description = "Retrieves durability evaluations tracking remediation effectiveness across subsequent baseline runs.")
    public ResponseEntity<List<RemediationDurabilityDto>> getTemporalRemediations(@PathVariable String id) {
        return ResponseEntity.ok(temporalService.getModelLineageHistory(id, "ALL_AVAILABLE").getRemediationDurability());
    }

    @GetMapping("/{id}/temporal/timeline")
    @Operation(summary = "Get Model Historical Timeline", description = "Retrieves chronological ordered run summaries.")
    public ResponseEntity<List<RunSummaryDto>> getTemporalTimeline(@PathVariable String id) {
        return ResponseEntity.ok(temporalService.getModelLineageHistory(id, "ALL_AVAILABLE").getOrderedRuns());
    }

    @PostMapping("/{id}/temporal/recalculate")
    @Operation(summary = "Recalculate Temporal Intelligence", description = "Idempotently rebuilds temporal observations, issue tracks, change points, and alerts from raw diagnostic results.")
    public ResponseEntity<TemporalRecalculateResponseDto> recalculateTemporalIntelligence(@PathVariable String id) {
        return ResponseEntity.ok(temporalService.recalculateTemporalIntelligence(id));
    }

    // =========================================================================
    // Phase 10: Continuous Monitoring & Model Health Decision Engine
    // =========================================================================

    @GetMapping("/{id}/monitoring/health")
    @Operation(summary = "Get Model Continuous Health Decision", description = "Retrieves current multidimensional health vector, active alerts, and deterministic health state for the run's model lineage.")
    public ResponseEntity<ModelHealthDecisionDto> getMonitoringHealth(@PathVariable String id) {
        return ResponseEntity.ok(monitoringService.getCurrentHealth(id));
    }

    @GetMapping("/{id}/monitoring/alerts")
    @Operation(summary = "Get Run Operational Monitoring Alerts", description = "Retrieves operational monitoring alerts for the run's model lineage.")
    public ResponseEntity<List<OperationalAlertDto>> getMonitoringAlerts(@PathVariable String id) {
        return ResponseEntity.ok(monitoringService.getAlerts(id));
    }

    @GetMapping("/{id}/monitoring/history")
    @Operation(summary = "Get Model Health History Snapshots", description = "Retrieves historical point-in-time health snapshots.")
    public ResponseEntity<List<DiagnosticHealthSnapshotDto>> getMonitoringHistory(@PathVariable String id) {
        return ResponseEntity.ok(monitoringService.getHealthHistory(id));
    }

    @GetMapping("/{id}/monitoring/policy")
    @Operation(summary = "Get Run Monitoring Policy", description = "Retrieves the monitoring contract configuration for the run's model lineage.")
    public ResponseEntity<DiagnosticMonitoringPolicyDto> getMonitoringPolicy(@PathVariable String id) {
        return ResponseEntity.ok(monitoringService.getPolicy(id));
    }

    @PutMapping("/{id}/monitoring/policy")
    @Operation(summary = "Update Run Monitoring Policy", description = "Updates the monitoring contract configuration.")
    public ResponseEntity<DiagnosticMonitoringPolicyDto> updateMonitoringPolicy(
            @PathVariable String id,
            @RequestBody DiagnosticMonitoringPolicyDto request) {
        return ResponseEntity.ok(monitoringService.updatePolicy(id, request));
    }

    @PostMapping("/{id}/monitoring/recalculate")
    @Operation(summary = "Recalculate Continuous Monitoring", description = "Idempotently executes full Phase 10 continuous monitoring and health decision pipeline.")
    public ResponseEntity<MonitoringRecalculateResponseDto> recalculateMonitoring(@PathVariable String id) {
        return ResponseEntity.ok(monitoringService.recalculateMonitoring(id));
    }

    // =========================================================================
    // Phase 11: Incident Correlation & Decision Workspace
    // =========================================================================

    @GetMapping("/{id}/incidents")
    @Operation(summary = "Get Run Operational Incidents", description = "Retrieves prioritized operational incidents for the run's model lineage.")
    public ResponseEntity<List<DiagnosticIncidentDto>> getRunIncidents(
            @PathVariable String id,
            @RequestParam(required = false, defaultValue = "true") boolean includeResolved) {
        return ResponseEntity.ok(incidentService.getIncidents(id, includeResolved));
    }

    @GetMapping("/{id}/incidents/{incidentId}")
    @Operation(summary = "Get Run Incident Dossier", description = "Retrieves complete evidence dossier, evidence matrix, contradictory signals, and operator decision for an incident.")
    public ResponseEntity<IncidentEvidenceDossierDto> getRunIncidentDossier(
            @PathVariable String id,
            @PathVariable Long incidentId) {
        return ResponseEntity.ok(incidentService.getIncidentDossier(id, incidentId));
    }

    @PostMapping("/{id}/incidents/recalculate")
    @Operation(summary = "Recalculate Run Incidents", description = "Idempotently correlates active alerts and recalculates incidents for the run's model lineage.")
    public ResponseEntity<IncidentRecalculateResponseDto> recalculateRunIncidents(@PathVariable String id) {
        return ResponseEntity.ok(incidentService.recalculateIncidents(id));
    }

    // =========================================================================
    // Phase 12: Model Reliability Governance & Fleet Intelligence
    // =========================================================================

    @GetMapping("/{id}/reliability")
    @Operation(summary = "Get Run Model Reliability Profile", description = "Retrieves operational reliability profile and dossier for the run's model lineage.")
    public ResponseEntity<ModelReliabilityProfileDto> getRunReliability(@PathVariable String id) {
        return ResponseEntity.ok(reliabilityService.getReliabilityProfile(id));
    }

    @PostMapping("/{id}/reliability/recalculate")
    @Operation(summary = "Recalculate Run Reliability", description = "Idempotently recalculates model reliability profile for the run's model lineage.")
    public ResponseEntity<ReliabilityRecalculateResponseDto> recalculateRunReliability(@PathVariable String id) {
        return ResponseEntity.ok(reliabilityService.recalculateReliability(id));
    }

    @GetMapping("/runs")
    @Operation(summary = "List Diagnostic Inspection Runs", description = "Retrieves recent diagnostic evaluation runs.")
    public ResponseEntity<List<DiagnosticRunDto>> getRecentRuns() {
        return ResponseEntity.ok(diagnosticService.getRecentRuns());
    }

    @GetMapping("/activities")
    @Operation(summary = "System Telemetry Stream", description = "Retrieves recent forensic probe logs and telemetry events.")
    public ResponseEntity<List<SystemActivityDto>> getRecentActivities() {
        return ResponseEntity.ok(diagnosticService.getRecentActivities());
    }
}


