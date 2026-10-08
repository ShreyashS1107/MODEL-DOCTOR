package com.modeldoctor.controller;

import com.modeldoctor.dto.*;
import com.modeldoctor.service.TemporalAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/models")
@Tag(name = "Model Lineage & Temporal Intelligence", description = "Endpoints for model history, longitudinal monitoring, metric time-series, issue tracks, alerts, and durability")
public class ModelLineageController {

    private final TemporalAnalysisService temporalService;

    @Autowired
    public ModelLineageController(TemporalAnalysisService temporalService) {
        this.temporalService = temporalService;
    }

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
