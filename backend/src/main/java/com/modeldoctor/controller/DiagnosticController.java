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

    @Autowired
    public DiagnosticController(DiagnosticService diagnosticService) {
        this.diagnosticService = diagnosticService;
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
