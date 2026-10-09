package com.modeldoctor;

import com.modeldoctor.domain.*;
import com.modeldoctor.dto.*;
import com.modeldoctor.intelligence.incident.EvidenceSynthesisEngine;
import com.modeldoctor.intelligence.incident.IncidentCorrelationEngine;
import com.modeldoctor.repository.*;
import com.modeldoctor.service.IncidentAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
@SuppressWarnings("null")
public class IncidentEngineAndCorrelationTest {

    @Autowired
    private DiagnosticIncidentRepository incidentRepository;

    @Autowired
    private DiagnosticIncidentAlertRepository incidentAlertRepository;

    @Autowired
    private DiagnosticIncidentEventRepository incidentEventRepository;

    @Autowired
    private DiagnosticOperationalAlertRepository alertRepository;

    @Autowired
    private DiagnosticAlertEventRepository alertEventRepository;

    @Autowired
    private DiagnosticRunRepository runRepository;

    @Autowired
    private IncidentAnalysisService incidentService;

    @Autowired
    private IncidentCorrelationEngine correlationEngine;

    @Autowired
    private EvidenceSynthesisEngine synthesisEngine;

    private final String lineage = "fraud_detection_xgb_prod";

    @BeforeEach
    public void setup() {
        incidentAlertRepository.deleteAll();
        incidentEventRepository.deleteAll();
        incidentRepository.deleteAll();
        alertEventRepository.deleteAll();
        alertRepository.deleteAll();
    }

    private DiagnosticOperationalAlert createAlert(
            String lineageId, String type, String sev, String targetType, String targetKey,
            String metric, Double val, Double ref, String mod, String runId) {
        DiagnosticOperationalAlert a = new DiagnosticOperationalAlert();
        a.setModelLineageId(lineageId);
        a.setAlertFingerprint(UUID.randomUUID().toString().replace("-", ""));
        a.setAlertType(type);
        a.setCurrentSeverity(sev);
        a.setTargetType(targetType);
        a.setTargetKey(targetKey);
        a.setMetricName(metric);
        a.setCurrentValue(val);
        a.setReferenceValue(ref);
        a.setSourceModule(mod);
        a.setTriggerDescription("Operational alert trigger for " + metric);
        a.setFirstSeenRunId(runId);
        a.setLastSeenRunId(runId);
        a.setFirstObservedAt(Instant.now().minus(2, ChronoUnit.HOURS));
        a.setLastObservedAt(Instant.now());
        a.setLifecycleState(AlertLifecycleState.OPEN);
        return alertRepository.save(a);
    }

    @Test
    @DisplayName("Correlation Engine: Deterministic scoring and explainable evidence reasons")
    public void testCorrelationScoringAndEvidence() {
        DiagnosticOperationalAlert a1 = createAlert(lineage, "FEATURE_DRIFT_CRITICAL", "CRITICAL",
                "FEATURE", "FEATURE::income", "psi", 0.45, 0.25, "DRIFT", "run_01");
        a1.setRelatedInvestigationTargetKey("FEATURE::income");
        a1.setRelatedIssueTrackId(42L);
        alertRepository.save(a1);

        DiagnosticOperationalAlert a2 = createAlert(lineage, "FEATURE_ERROR_ASSOCIATION", "HIGH",
                "FEATURE", "FEATURE::income", "error_rate", 0.32, 0.15, "ERROR_FORENSICS", "run_01");
        a2.setRelatedInvestigationTargetKey("FEATURE::income");
        a2.setRelatedIssueTrackId(42L);
        alertRepository.save(a2);

        var result = correlationEngine.evaluateCorrelation(a1, a2);
        assertTrue(result.isCorrelated(), "Alerts for same target and issue track should be strongly correlated");
        assertTrue(result.score() >= 80, "Score should be >= 80 (target +35, inv +25, track +20, run +20)");
        assertTrue(result.score() <= 100, "Score must be bounded by 100");
        assertFalse(result.reasons().isEmpty(), "Correlation must provide explicit explainable reasons");
    }

    @Test
    @DisplayName("Incident Formation: Multi-alert correlated incident vs unrelated alert separation")
    public void testIncidentFormationAndClustering() {
        // Correlated cluster on FEATURE::income
        createAlert(lineage, "FEATURE_DRIFT_CRITICAL", "CRITICAL", "FEATURE", "FEATURE::income", "psi", 0.42, 0.25, "DRIFT", "run_01");
        createAlert(lineage, "ROBUSTNESS_DEGRADATION", "HIGH", "FEATURE", "FEATURE::income", "flip_rate", 0.18, 0.10, "ROBUSTNESS", "run_01");
        createAlert(lineage, "ERROR_CORRELATION", "HIGH", "FEATURE", "FEATURE::income", "r_score", 0.28, 0.15, "ERROR_FORENSICS", "run_01");

        // Unrelated alert on FEATURE::age
        createAlert(lineage, "FEATURE_DRIFT_WARNING", "LOW", "FEATURE", "FEATURE::age", "ks_stat", 0.12, 0.10, "DRIFT", "run_01");

        // Recalculate incidents
        IncidentRecalculateResponseDto resp = incidentService.recalculateIncidents(lineage);
        assertTrue(resp.isSuccess());

        List<DiagnosticIncidentDto> incidents = incidentService.getIncidents(lineage, true);
        assertEquals(2, incidents.size(), "Should form exactly 2 distinct incidents (income and age)");

        DiagnosticIncidentDto incomeIncident = incidents.stream()
                .filter(i -> "FEATURE::income".equals(i.getPrimaryTarget()))
                .findFirst().orElseThrow();
        assertEquals(3, incomeIncident.getRelatedAlertsCount(), "Income incident must aggregate all 3 correlated alerts");
        assertEquals(3, incomeIncident.getIndependentModuleCount(), "Must count 3 independent modules (DRIFT, ROBUSTNESS, ERROR)");
        assertEquals("CRITICAL", incomeIncident.getCurrentSeverity(), "Incident inherits highest alert severity");
        assertTrue(incomeIncident.getPriorityScore() >= 70, "Priority should be >= 70 for multi-module critical incident");

        DiagnosticIncidentDto ageIncident = incidents.stream()
                .filter(i -> "FEATURE::age".equals(i.getPrimaryTarget()))
                .findFirst().orElseThrow();
        assertEquals(1, ageIncident.getRelatedAlertsCount());
        assertEquals(1, ageIncident.getIndependentModuleCount());
    }

    @Test
    @DisplayName("Independent Module Counting: Multiple metrics in same module count as 1 independent source")
    public void testIndependentModuleCounting() {
        List<DiagnosticOperationalAlert> alerts = new ArrayList<>();
        // 3 drift alerts
        alerts.add(createAlert(lineage, "DRIFT_PSI", "HIGH", "FEATURE", "FEATURE::income", "psi", 0.35, 0.25, "DRIFT", "run_01"));
        alerts.add(createAlert(lineage, "DRIFT_KS", "HIGH", "FEATURE", "FEATURE::income", "ks", 0.22, 0.15, "DRIFT", "run_01"));
        alerts.add(createAlert(lineage, "DRIFT_WASSERSTEIN", "HIGH", "FEATURE", "FEATURE::income", "wd", 0.19, 0.10, "DRIFT", "run_01"));

        int independentCount = synthesisEngine.countIndependentModules(alerts);
        assertEquals(1, independentCount, "3 drift metric alerts must count as 1 independent module");

        // Add an error forensics alert
        alerts.add(createAlert(lineage, "ERROR_RATE", "HIGH", "FEATURE", "FEATURE::income", "err", 0.30, 0.15, "ERROR_FORENSICS", "run_01"));
        assertEquals(2, synthesisEngine.countIndependentModules(alerts), "Must count 2 independent modules");
    }

    @Test
    @DisplayName("Incident Priority: Deterministic calculation and explainable breakdown")
    public void testIncidentPriorityAndBreakdown() {
        IncidentPriorityBreakdownDto breakdown = synthesisEngine.calculatePriorityBreakdown(
                "CRITICAL", 2, 4, true, ModelHealthState.CRITICAL);
        assertNotNull(breakdown);
        assertEquals(40, breakdown.getSeverityContribution(), "CRITICAL severity contributes 40");
        assertEquals(20, breakdown.getIndependentEvidenceContribution(), "2 modules contribute 20");
        assertEquals(15, breakdown.getPersistenceContribution(), "Persistence and escalation contribute 15");
        assertEquals(10, breakdown.getHealthImpactContribution(), "Critical health impact contributes 10");
        assertEquals(85, breakdown.getTotalPriorityScore(), "Total priority is 40 + 20 + 15 + 10 = 85");
        assertEquals("CRITICAL", breakdown.getPriorityTier());
    }

    @Test
    @DisplayName("Contradictory Evidence: Drift critical with healthy performance reduces confidence and recommends MONITOR")
    public void testContradictoryEvidenceHandling() {
        DiagnosticOperationalAlert driftAlert = createAlert(lineage, "FEATURE_DRIFT", "CRITICAL", "FEATURE", "FEATURE::income", "psi", 0.45, 0.25, "DRIFT", "run_01");
        List<DiagnosticOperationalAlert> alerts = List.of(driftAlert);

        Map<HealthDimension, HealthDimensionEvaluationDto> healthVector = new HashMap<>();
        HealthDimensionEvaluationDto driftEval = new HealthDimensionEvaluationDto(HealthDimension.DRIFT);
        driftEval.setState(DimensionHealthState.CRITICAL);
        healthVector.put(HealthDimension.DRIFT, driftEval);

        HealthDimensionEvaluationDto perfEval = new HealthDimensionEvaluationDto(HealthDimension.PERFORMANCE);
        perfEval.setState(DimensionHealthState.HEALTHY);
        healthVector.put(HealthDimension.PERFORMANCE, perfEval);

        ContradictoryEvidenceDto conflict = synthesisEngine.evaluateContradictoryEvidence(alerts, healthVector, false);
        assertTrue(conflict.isHasConflict(), "Should detect divergence between severe drift and healthy performance");
        assertNotNull(conflict.getConflictingSignals());

        DiagnosticIncident inc = new DiagnosticIncident();
        inc.setCategory(IncidentCategory.DRIFT_INCIDENT);
        inc.setCurrentSeverity("CRITICAL");
        inc.setLifecycleState(IncidentLifecycleState.OPEN);

        IncidentDecisionDto decision = synthesisEngine.determineDecision(
                inc, false, false, false, conflict.isHasConflict(), 1, false);

        assertEquals(IncidentDecisionState.MONITOR, decision.getRecommendation(), "Contradictory evidence recommends MONITOR");
        assertEquals(DecisionConfidence.MEDIUM, decision.getConfidence(), "Conflict reduces confidence to MEDIUM");
    }

    @Test
    @DisplayName("Incident Lifecycle: Deterministic transitions from OPEN through RESOLVED and audit events")
    public void testIncidentLifecycleTransitions() {
        createAlert(lineage, "DRIFT_CRITICAL", "CRITICAL", "FEATURE", "FEATURE::income", "psi", 0.45, 0.25, "DRIFT", "run_01");
        incidentService.recalculateIncidents(lineage);

        List<DiagnosticIncidentDto> incidents = incidentService.getIncidents(lineage, true);
        assertEquals(1, incidents.size());
        DiagnosticIncidentDto inc = incidents.get(0);
        assertEquals(IncidentLifecycleState.OPEN, inc.getLifecycleState());

        // Acknowledge
        DiagnosticIncidentDto ack = incidentService.acknowledgeIncident(
                lineage, inc.getId(), new AcknowledgeIncidentRequestDto("sec_eng", "Investigating distribution skew"));
        assertEquals(IncidentLifecycleState.ACKNOWLEDGED, ack.getLifecycleState());

        // Investigate
        DiagnosticIncidentDto inv = incidentService.investigateIncident(
                lineage, inc.getId(), new InvestigateIncidentRequestDto("ml_eng", "Drift correlated with upstream pipeline"));
        assertEquals(IncidentLifecycleState.INVESTIGATING, inv.getLifecycleState());

        // Plan Mitigation
        DiagnosticIncidentDto plan = incidentService.planRemediation(
                lineage, inc.getId(), new PlanRemediationRequestDto("ml_eng", 101L, "Target encoding transformation"));
        assertEquals(IncidentLifecycleState.MITIGATION_PLANNED, plan.getLifecycleState());
        assertEquals(101L, plan.getRemediationId());

        // Start Validation
        DiagnosticIncidentDto val = incidentService.startValidation(
                lineage, inc.getId(), new StartValidationRequestDto("ml_eng", "EXP-042", "Validation experiment running"));
        assertEquals(IncidentLifecycleState.VALIDATING, val.getLifecycleState());
        assertEquals("EXP-042", val.getExperimentId());

        // Resolve
        DiagnosticIncidentDto res = incidentService.resolveIncident(
                lineage, inc.getId(), new ResolveIncidentRequestDto("ml_eng", "Pipeline transformation verified"));
        assertEquals(IncidentLifecycleState.RESOLVED, res.getLifecycleState());
        assertNotNull(res.getResolvedAt());

        // Verify audit event log
        List<DiagnosticIncidentEvent> events = incidentEventRepository.findByIncidentIdOrderByTimestampDesc(inc.getId());
        assertEquals(6, events.size(), "All 6 lifecycle transitions must be audited");
    }

    @Test
    @DisplayName("Incident Reopening: Resolved incident reopens to REOPENED on recurring operational alerts")
    public void testIncidentReopeningOnRecurrence() {
        createAlert(lineage, "DRIFT_CRITICAL", "CRITICAL", "FEATURE", "FEATURE::income", "psi", 0.45, 0.25, "DRIFT", "run_01");
        incidentService.recalculateIncidents(lineage);

        List<DiagnosticIncidentDto> incidents = incidentService.getIncidents(lineage, true);
        DiagnosticIncidentDto inc = incidents.get(0);

        // Resolve incident
        incidentService.resolveIncident(lineage, inc.getId(), new ResolveIncidentRequestDto("eng", "Resolved"));
        assertEquals(IncidentLifecycleState.RESOLVED, incidentRepository.findById(inc.getId()).orElseThrow().getLifecycleState());

        // Recalculate when active operational alerts still exist / reoccur
        incidentService.recalculateIncidents(lineage);

        DiagnosticIncident reopened = incidentRepository.findById(inc.getId()).orElseThrow();
        assertEquals(IncidentLifecycleState.REOPENED, reopened.getLifecycleState(), "Incident must transition to REOPENED");
    }

    @Test
    @DisplayName("Experiment Isolation: Experiment runs do not form authoritative operational incidents")
    public void testExperimentIsolationFromIncidents() {
        // Register an EXPERIMENT run
        DiagnosticRun expRun = new DiagnosticRun();
        expRun.setId("run_exp_999");
        expRun.setModelName(lineage);
        expRun.setModelFramework("xgboost");
        expRun.setTaskType("classification");
        expRun.setEvaluationDataset("s3://bucket/test.csv");
        expRun.setRunType("EXPERIMENT");
        expRun.setStatus(DiagnosticStatus.COMPLETED);
        expRun.setTargetColumn("target");
        expRun.setCreatedAt(Instant.now());
        runRepository.save(expRun);

        // Alert attached to EXPERIMENT run
        createAlert(lineage, "DRIFT_EXPERIMENT", "HIGH", "FEATURE", "FEATURE::exp_feature", "psi", 0.50, 0.25, "DRIFT", "run_exp_999");

        IncidentRecalculateResponseDto resp = incidentService.recalculateIncidents(lineage);
        assertEquals(0, resp.getIncidentsFormedCount(), "Experiment alerts must be excluded from forming operational incidents");
    }

    @Test
    @DisplayName("Idempotency: Repeated recalculations produce identical incidents with zero duplicates")
    public void testRecalculationIdempotency() {
        createAlert(lineage, "DRIFT_PSI", "CRITICAL", "FEATURE", "FEATURE::income", "psi", 0.45, 0.25, "DRIFT", "run_01");
        createAlert(lineage, "ERROR_RATE", "HIGH", "FEATURE", "FEATURE::income", "err", 0.30, 0.15, "ERROR_FORENSICS", "run_01");

        // Run 1
        incidentService.recalculateIncidents(lineage);
        long count1 = incidentRepository.count();
        long alertLinks1 = incidentAlertRepository.count();

        // Run 2
        incidentService.recalculateIncidents(lineage);
        long count2 = incidentRepository.count();
        long alertLinks2 = incidentAlertRepository.count();

        // Run 3
        incidentService.recalculateIncidents(lineage);
        long count3 = incidentRepository.count();
        long alertLinks3 = incidentAlertRepository.count();

        assertEquals(1, count1);
        assertEquals(count1, count2);
        assertEquals(count2, count3);
        assertEquals(2, alertLinks1);
        assertEquals(alertLinks1, alertLinks2);
        assertEquals(alertLinks2, alertLinks3);
    }
}
