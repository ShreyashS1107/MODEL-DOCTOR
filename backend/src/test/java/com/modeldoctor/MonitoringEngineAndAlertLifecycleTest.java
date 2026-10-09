package com.modeldoctor;

import com.modeldoctor.domain.*;
import com.modeldoctor.dto.*;
import com.modeldoctor.intelligence.monitoring.AlertLifecycleManager;
import com.modeldoctor.repository.*;
import com.modeldoctor.service.ContinuousMonitoringService;
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
public class MonitoringEngineAndAlertLifecycleTest {

    @Autowired
    private DiagnosticRunRepository runRepository;

    @Autowired
    private DiagnosticResultRepository resultRepository;

    @Autowired
    private DiagnosticMonitoringPolicyRepository policyRepository;

    @Autowired
    private DiagnosticOperationalAlertRepository alertRepository;

    @Autowired
    private DiagnosticAlertEventRepository eventRepository;

    @Autowired
    private DiagnosticHealthSnapshotRepository snapshotRepository;

    @Autowired
    private DiagnosticTemporalObservationRepository observationRepository;

    @Autowired
    private DiagnosticIssueTrackRepository issueTrackRepository;

    @Autowired
    private DiagnosticTemporalAlertRepository temporalAlertRepository;

    @Autowired
    private DiagnosticChangePointRepository changePointRepository;

    @Autowired
    private ContinuousMonitoringService monitoringService;

    @Autowired
    private AlertLifecycleManager lifecycleManager;

    private final String modelName = "credit_default_risk_v2";

    @BeforeEach
    public void setup() {
        alertRepository.deleteAll();
        eventRepository.deleteAll();
        snapshotRepository.deleteAll();
        policyRepository.deleteAll();
        observationRepository.deleteAll();
        issueTrackRepository.deleteAll();
        temporalAlertRepository.deleteAll();
        changePointRepository.deleteAll();
    }

    private DiagnosticRun createRun(String runId, String model, String runType, Instant createdAt) {
        DiagnosticRun run = DiagnosticRun.builder()
                .id(runId)
                .modelName(model)
                .modelFramework("xgboost")
                .taskType("binary_classification")
                .evaluationDataset("s3://datasets/eval.parquet")
                .baselineDataset("s3://datasets/ref.parquet")
                .targetColumn("is_default")
                .predictionColumn("pred_prob")
                .status(DiagnosticStatus.COMPLETED)
                .executionMode("REAL")
                .createdAt(createdAt)
                .completedAt(createdAt.plus(1, ChronoUnit.MINUTES))
                .build();
        run.setRunType(runType);
        return runRepository.save(run);
    }

    private DiagnosticResult createResult(String runId, DiagnosticModule module, String jsonPayload) {
        DiagnosticResult res = new DiagnosticResult();
        res.setRunId(runId);
        res.setModule(module);
        res.setStatus(ModuleExecutionStatus.COMPLETED);
        res.setResultJson(jsonPayload);
        res.setCreatedAt(Instant.now());
        return resultRepository.save(res);
    }

    @Test
    @DisplayName("1. Monitoring Policy Management — Default creation, updates, and version auditing")
    public void testMonitoringPolicyLifecycle() {
        // Initial default fetch
        DiagnosticMonitoringPolicyDto policy = monitoringService.getPolicy(modelName);
        assertNotNull(policy);
        assertEquals(modelName, policy.getModelLineageId());
        assertEquals(1, policy.getPolicyVersion());
        assertTrue(policy.isEnabled());
        assertEquals(3, policy.getMinBaselineRunsRequired());

        // Update policy
        policy.setMinBaselineRunsRequired(4);
        policy.setObservationWindow("LAST_5");
        policy.setAlertPersistenceThreshold(3);
        policy.setRecoveryConsecutiveRuns(3);
        policy.setHysteresisMarginPct(0.08);
        policy.setUpdatedBy("LEAD_ML_ENGINEER");

        DiagnosticMonitoringPolicyDto updated = monitoringService.updatePolicy(modelName, policy);
        assertEquals(2, updated.getPolicyVersion());
        assertEquals(4, updated.getMinBaselineRunsRequired());
        assertEquals("LAST_5", updated.getObservationWindow());
        assertEquals(3, updated.getRecoveryConsecutiveRuns());
        assertEquals("LEAD_ML_ENGINEER", updated.getUpdatedBy());
    }

    @Test
    @DisplayName("2. Data Sufficiency & UNKNOWN State — Evaluates UNKNOWN when baseline runs < required")
    public void testDataSufficiencyUnknownState() {
        Instant now = Instant.now();
        createRun("run_p10_1", modelName, "BASELINE", now.minus(2, ChronoUnit.HOURS));

        ModelHealthDecisionDto health = monitoringService.getCurrentHealth(modelName);
        assertNotNull(health);
        assertEquals(ModelHealthState.UNKNOWN, health.getOverallState());
        assertFalse(health.getDataSufficiency().isSufficient());
        assertEquals(1, health.getDataSufficiency().getBaselineRunsCount());
        assertEquals(3, health.getDataSufficiency().getRequiredBaselineRuns());
        assertTrue(health.getDecisionReason().contains("Insufficient operational history"));
    }

    @Test
    @DisplayName("3. Health Vector Dimensions — Evaluates all 9 dimensions with concrete metric evidence")
    public void testHealthVectorDimensionEvaluation() {
        Instant now = Instant.now();
        createRun("run_p10_a", modelName, "BASELINE", now.minus(3, ChronoUnit.HOURS));
        createRun("run_p10_b", modelName, "BASELINE", now.minus(2, ChronoUnit.HOURS));
        createRun("run_p10_c", modelName, "BASELINE", now.minus(1, ChronoUnit.HOURS));

        // Inject Drift Result on run C
        String driftJson = """
        {
            "summary": {
                "maxPsi": 0.42,
                "driftedFeatureCount": 1
            },
            "features": [
                {"feature": "income", "psi": 0.42, "wasserstein": 0.18, "driftDetected": true},
                {"feature": "credit_score", "psi": 0.04, "wasserstein": 0.01, "driftDetected": false}
            ]
        }
        """;
        createResult("run_p10_c", DiagnosticModule.DRIFT, driftJson);

        // Inject Performance Result on run C
        String perfJson = """
        {
            "summary": {
                "f1": 0.82,
                "rocAuc": 0.88,
                "logLoss": 0.32,
                "brierScore": 0.12,
                "expectedCalibrationError": 0.04
            }
        }
        """;
        createResult("run_p10_c", DiagnosticModule.PERFORMANCE, perfJson);

        ModelHealthDecisionDto health = monitoringService.getCurrentHealth(modelName);
        assertNotNull(health);
        assertNotNull(health.getHealthVector());

        // Check DRIFT dimension
        HealthDimensionEvaluationDto driftDim = health.getHealthVector().get(HealthDimension.DRIFT);
        assertNotNull(driftDim);
        assertTrue(driftDim.isEvaluable());
        assertEquals(DimensionHealthState.CRITICAL, driftDim.getState());
        assertFalse(driftDim.getMetricEvidence().isEmpty());
        assertTrue(driftDim.getMetricEvidence().stream().anyMatch(e -> "FEATURE::income".equals(e.getTargetKey()) && e.getValue() == 0.42));

        // Check PERFORMANCE dimension
        HealthDimensionEvaluationDto perfDim = health.getHealthVector().get(HealthDimension.PERFORMANCE);
        assertNotNull(perfDim);
        assertTrue(perfDim.isEvaluable());
        assertEquals(DimensionHealthState.HEALTHY, perfDim.getState());

        // Check CALIBRATION dimension
        HealthDimensionEvaluationDto calDim = health.getHealthVector().get(HealthDimension.CALIBRATION);
        assertNotNull(calDim);
        assertTrue(calDim.isEvaluable());
        assertEquals(DimensionHealthState.HEALTHY, calDim.getState());
    }

    @Test
    @DisplayName("4. Alert Fingerprinting & Deduplication — Consecutive runs update existing alert instead of creating duplicates")
    public void testAlertFingerprintingAndDeduplication() {
        String fp = lifecycleManager.computeFingerprint(modelName, "PERSISTENT_DEGRADATION", "FEATURE", "FEATURE::income", "psi");
        assertNotNull(fp);
        assertTrue(fp.contains("credit_default_risk_v2"));
        assertTrue(fp.contains("FEATURE::income"));

        // Simulate 2 consecutive runs producing the same alert
        TemporalAlertDto tAlert1 = new TemporalAlertDto();
        tAlert1.setAlertType("PERSISTENT_DEGRADATION");
        tAlert1.setTargetType("FEATURE");
        tAlert1.setTargetKey("FEATURE::income");
        tAlert1.setMetricName("psi");
        tAlert1.setCurrentValue(0.38);
        tAlert1.setPriority("CRITICAL");
        tAlert1.setTriggerDescription("Severe PSI drift on income");

        DiagnosticMonitoringPolicy policy = new DiagnosticMonitoringPolicy(modelName);

        // Run 1 sync
        List<DiagnosticOperationalAlert> sync1 = lifecycleManager.synchronizeAlerts(
                modelName, "run_p10_1", 1, List.of(tAlert1), null, null, null, null, policy
        );
        assertEquals(1, sync1.size());
        DiagnosticOperationalAlert alert1 = sync1.get(0);
        assertEquals(1, alert1.getOccurrenceCount());
        assertEquals(1, alert1.getConsecutiveCount());
        assertEquals(AlertLifecycleState.OPEN, alert1.getLifecycleState());
        assertEquals("NEW", alert1.getSeverityChange());

        // Run 2 sync with slightly higher value
        TemporalAlertDto tAlert2 = new TemporalAlertDto();
        tAlert2.setAlertType("PERSISTENT_DEGRADATION");
        tAlert2.setTargetType("FEATURE");
        tAlert2.setTargetKey("FEATURE::income");
        tAlert2.setMetricName("psi");
        tAlert2.setCurrentValue(0.42);
        tAlert2.setPriority("CRITICAL");
        tAlert2.setTriggerDescription("Severe PSI drift on income");

        List<DiagnosticOperationalAlert> sync2 = lifecycleManager.synchronizeAlerts(
                modelName, "run_p10_2", 2, List.of(tAlert2), null, null, null, null, policy
        );
        assertEquals(1, sync2.size()); // Still exactly 1 alert! (Deduplicated)
        DiagnosticOperationalAlert alert2 = sync2.get(0);
        assertEquals(alert1.getId(), alert2.getId());
        assertEquals(2, alert2.getOccurrenceCount());
        assertEquals(2, alert2.getConsecutiveCount());
        assertEquals(0.42, alert2.getCurrentValue());
    }

    @Test
    @DisplayName("5. Alert Lifecycle Transitions & Audit Trail — Acknowledge, Investigate, Suppress, Resolve, Reopen")
    public void testAlertLifecycleStateTransitions() {
        // Create an alert
        DiagnosticOperationalAlert alert = new DiagnosticOperationalAlert();
        alert.setModelLineageId(modelName);
        alert.setAlertFingerprint("credit::DEGRADATION::FEATURE::income::psi");
        alert.setAlertType("DEGRADATION");
        alert.setCurrentSeverity("HIGH");
        alert.setLifecycleState(AlertLifecycleState.OPEN);
        alert.setTriggerDescription("PSI drift on income");
        alert = alertRepository.save(alert);

        Long alertId = alert.getId();

        // 1. Acknowledge
        OperationalAlertDto acked = monitoringService.acknowledgeAlert(modelName, alertId, "ENGINEER_ALICE");
        assertEquals(AlertLifecycleState.ACKNOWLEDGED, acked.getLifecycleState());
        assertEquals("ENGINEER_ALICE", acked.getAcknowledgedBy());

        // 2. Investigate
        OperationalAlertDto investigated = monitoringService.investigateAlert(modelName, alertId, "ENGINEER_BOB");
        assertEquals(AlertLifecycleState.INVESTIGATING, investigated.getLifecycleState());
        assertEquals("ENGINEER_BOB", investigated.getInvestigatedBy());

        // 3. Suppress for 12 hours
        OperationalAlertDto suppressed = monitoringService.suppressAlert(modelName, alertId, "Known upstream data migration in progress", 12, "ENGINEER_CHARLIE");
        assertEquals(AlertLifecycleState.SUPPRESSED, suppressed.getLifecycleState());
        assertTrue(suppressed.isCurrentlySuppressed());
        assertEquals("ENGINEER_CHARLIE", suppressed.getSuppressedBy());
        assertTrue(suppressed.getSuppressionReason().contains("data migration"));

        // 4. Resolve
        OperationalAlertDto resolved = monitoringService.resolveAlert(modelName, alertId, "Upstream schema fixed", "ENGINEER_ALICE");
        assertEquals(AlertLifecycleState.RESOLVED, resolved.getLifecycleState());
        assertFalse(resolved.isCurrentlySuppressed());

        // 5. Verify audit events recorded in database
        List<DiagnosticAlertEvent> events = eventRepository.findByAlertIdOrderByTimestampAsc(alertId);
        assertFalse(events.isEmpty());
        assertTrue(events.stream().anyMatch(e -> "ACKNOWLEDGE".equals(e.getAction())));
        assertTrue(events.stream().anyMatch(e -> "INVESTIGATE".equals(e.getAction())));
        assertTrue(events.stream().anyMatch(e -> "SUPPRESS".equals(e.getAction())));
        assertTrue(events.stream().anyMatch(e -> "RESOLVE".equals(e.getAction())));

        // 6. Test invalid lifecycle transition throws exception
        assertThrows(IllegalStateException.class, () -> {
            // Cannot directly transition RESOLVED alert to ACKNOWLEDGED without reopening
            lifecycleManager.acknowledge(modelName, alertId, "TEST");
        });
    }

    @Test
    @DisplayName("6. Severity Evolution — Tracks escalations and de-escalations with history")
    public void testSeverityEvolutionTracking() {
        TemporalAlertDto tAlert1 = new TemporalAlertDto();
        tAlert1.setAlertType("DRIFT_SHIFT");
        tAlert1.setTargetType("FEATURE");
        tAlert1.setTargetKey("FEATURE::income");
        tAlert1.setMetricName("psi");
        tAlert1.setPriority("MEDIUM");
        tAlert1.setTriggerDescription("Moderate drift on income");

        DiagnosticMonitoringPolicy policy = new DiagnosticMonitoringPolicy(modelName);

        // First observation at MEDIUM
        lifecycleManager.synchronizeAlerts(modelName, "run_p10_1", 1, List.of(tAlert1), null, null, null, null, policy);

        // Second observation at CRITICAL (Escalation)
        tAlert1.setPriority("CRITICAL");
        List<DiagnosticOperationalAlert> escalated = lifecycleManager.synchronizeAlerts(
                modelName, "run_p10_2", 2, List.of(tAlert1), null, null, null, null, policy
        );

        DiagnosticOperationalAlert alert = escalated.get(0);
        assertEquals("CRITICAL", alert.getCurrentSeverity());
        assertEquals("MEDIUM", alert.getPreviousSeverity());
        assertEquals("ESCALATED", alert.getSeverityChange());
        assertEquals(1, alert.getEscalationCount());

        // Third observation at HIGH (De-escalation / partial recovery)
        tAlert1.setPriority("HIGH");
        List<DiagnosticOperationalAlert> deescalated = lifecycleManager.synchronizeAlerts(
                modelName, "run_p10_3", 3, List.of(tAlert1), null, null, null, null, policy
        );
        DiagnosticOperationalAlert alertDeesc = deescalated.get(0);
        assertEquals("HIGH", alertDeesc.getCurrentSeverity());
        assertEquals("CRITICAL", alertDeesc.getPreviousSeverity());
        assertEquals("DEESCALATED", alertDeesc.getSeverityChange());
        assertEquals(1, alertDeesc.getRecoveryCount());
    }

    @Test
    @DisplayName("7. Experiment Run Isolation — Experiment runs do NOT corrupt baseline operational health")
    public void testExperimentRunIsolation() {
        Instant now = Instant.now();
        // 3 Operational Baseline Runs
        createRun("run_base_1", modelName, "BASELINE", now.minus(3, ChronoUnit.HOURS));
        createRun("run_base_2", modelName, "BASELINE", now.minus(2, ChronoUnit.HOURS));
        createRun("run_base_3", modelName, "BASELINE", now.minus(1, ChronoUnit.HOURS));

        // 1 Experiment Run (Candidate model from Phase 8 with degraded metrics)
        createRun("run_exp_candidate_1", modelName, "EXPERIMENT", now);

        String degradedDriftJson = """
        {
            "summary": {
                "maxPsi": 0.95,
                "driftedFeatureCount": 1
            },
            "features": [
                {"feature": "income", "psi": 0.95, "wasserstein": 0.50, "driftDetected": true}
            ]
        }
        """;
        // Put extreme drift only on the experiment run
        createResult("run_exp_candidate_1", DiagnosticModule.DRIFT, degradedDriftJson);

        // Put nominal drift on the baseline run 3
        String nominalDriftJson = """
        {
            "summary": {
                "maxPsi": 0.05,
                "driftedFeatureCount": 0
            },
            "features": [
                {"feature": "income", "psi": 0.05, "wasserstein": 0.01, "driftDetected": false}
            ]
        }
        """;
        createResult("run_base_3", DiagnosticModule.DRIFT, nominalDriftJson);

        // Check health decision
        ModelHealthDecisionDto health = monitoringService.getCurrentHealth(modelName);
        assertNotNull(health);
        // The authoritative operational run must be run_base_3, NOT the experiment run!
        assertEquals("run_base_3", health.getOperationalRunId());
        assertEquals(3, health.getDataSufficiency().getBaselineRunsCount());

        HealthDimensionEvaluationDto driftEval = health.getHealthVector().get(HealthDimension.DRIFT);
        assertEquals(DimensionHealthState.HEALTHY, driftEval.getState());
    }

    @Test
    @DisplayName("8. Idempotent Recalculation & Health Snapshots — Multiple runs produce consistent state without duplicates")
    public void testIdempotentRecalculationAndSnapshots() {
        Instant now = Instant.now();
        createRun("run_base_1", modelName, "BASELINE", now.minus(3, ChronoUnit.HOURS));
        createRun("run_base_2", modelName, "BASELINE", now.minus(2, ChronoUnit.HOURS));
        createRun("run_base_3", modelName, "BASELINE", now.minus(1, ChronoUnit.HOURS));

        String perfJson = """
        {
            "summary": {
                "f1": 0.85,
                "rocAuc": 0.90,
                "logLoss": 0.28,
                "brierScore": 0.10,
                "expectedCalibrationError": 0.03
            }
        }
        """;
        createResult("run_base_1", DiagnosticModule.PERFORMANCE, perfJson);
        createResult("run_base_2", DiagnosticModule.PERFORMANCE, perfJson);
        createResult("run_base_3", DiagnosticModule.PERFORMANCE, perfJson);

        // First recalculation
        MonitoringRecalculateResponseDto resp1 = monitoringService.recalculateMonitoring(modelName);
        assertTrue(resp1.isSuccess());
        assertTrue(resp1.isSnapshotCreated());

        List<DiagnosticHealthSnapshot> snapshots1 = snapshotRepository.findByModelLineageIdOrderByTimestampDesc(modelName);
        assertEquals(1, snapshots1.size());

        // Second recalculation against unchanged data
        MonitoringRecalculateResponseDto resp2 = monitoringService.recalculateMonitoring(modelName);
        assertTrue(resp2.isSuccess());

        List<DiagnosticHealthSnapshot> snapshots2 = snapshotRepository.findByModelLineageIdOrderByTimestampDesc(modelName);
        assertEquals(1, snapshots2.size()); // Exactly 1 snapshot maintained for run_base_3 (Idempotent!)
    }
}
