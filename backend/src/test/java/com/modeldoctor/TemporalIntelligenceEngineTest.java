package com.modeldoctor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeldoctor.domain.*;
import com.modeldoctor.dto.*;
import com.modeldoctor.intelligence.normalization.NormalizedModuleData;
import com.modeldoctor.intelligence.normalization.ResultNormalizer;
import com.modeldoctor.intelligence.temporal.*;
import com.modeldoctor.repository.*;
import com.modeldoctor.service.TemporalAnalysisService;
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
public class TemporalIntelligenceEngineTest {

    @Autowired
    private DiagnosticRunRepository runRepository;

    @Autowired
    private DiagnosticResultRepository resultRepository;

    @Autowired
    private DiagnosticRemediationRepository remediationRepository;

    @Autowired
    private DiagnosticExperimentRepository experimentRepository;

    @Autowired
    private DiagnosticTemporalObservationRepository observationRepository;

    @Autowired
    private DiagnosticIssueTrackRepository issueTrackRepository;

    @Autowired
    private DiagnosticTemporalAlertRepository alertRepository;

    @Autowired
    private DiagnosticChangePointRepository changePointRepository;

    @Autowired
    private ResultNormalizer resultNormalizer;

    @Autowired
    private TemporalMetricRegistry metricRegistry;

    @Autowired
    private TemporalTrendAnalyzer trendAnalyzer;

    @Autowired
    private PersistenceAnalyzer persistenceAnalyzer;

    @Autowired
    private ChangePointAnalyzer changePointAnalyzer;

    @Autowired
    private IssueTrackAnalyzer issueTrackAnalyzer;

    @Autowired
    private DurabilityAnalyzer durabilityAnalyzer;

    @Autowired
    private TemporalAlertAnalyzer alertAnalyzer;

    @Autowired
    private TemporalAnalysisService temporalService;

    @Autowired
    private ObjectMapper objectMapper;

    private final String modelName = "test_fraud_model_v1";

    @BeforeEach
    public void setup() {
        observationRepository.deleteAll();
        issueTrackRepository.deleteAll();
        alertRepository.deleteAll();
        changePointRepository.deleteAll();
    }

    private DiagnosticRun createRun(String runId, String model, String runType, Instant createdAt) {
        DiagnosticRun run = DiagnosticRun.builder()
                .id(runId)
                .modelName(model)
                .modelFramework("xgboost")
                .taskType("binary_classification")
                .evaluationDataset("s3://datasets/eval.parquet")
                .targetColumn("is_fraud")
                .status(DiagnosticStatus.COMPLETED)
                .createdAt(createdAt)
                .build();
        run.setRunType(runType);
        return runRepository.save(run);
    }

    private void saveResult(String runId, DiagnosticModule module, Map<String, Object> payload) throws Exception {
        DiagnosticResult res = new DiagnosticResult();
        res.setRunId(runId);
        res.setModule(module);
        res.setStatus(ModuleExecutionStatus.COMPLETED);
        res.setResultJson(objectMapper.writeValueAsString(payload));
        res.setCreatedAt(Instant.now());
        resultRepository.save(res);
    }

    @Test
    @DisplayName("1. Metric Registry: Verify definitions and severity thresholds")
    public void testMetricRegistry() {
        assertTrue(metricRegistry.getDefinition("f1").isPresent());
        assertTrue(metricRegistry.isHigherIsBetter("f1"));
        assertEquals("CRITICAL", metricRegistry.computeSeverity("f1", 0.45));
        assertEquals("HIGH", metricRegistry.computeSeverity("f1", 0.65));
        assertEquals("LOW", metricRegistry.computeSeverity("f1", 0.85));

        assertFalse(metricRegistry.isHigherIsBetter("psi"));
        assertEquals("CRITICAL", metricRegistry.computeSeverity("psi", 0.35));
        assertEquals("HIGH", metricRegistry.computeSeverity("psi", 0.15));
        assertEquals("LOW", metricRegistry.computeSeverity("psi", 0.05));
    }

    @Test
    @DisplayName("2. Trend Analyzer: Improving and Degrading linear slope and R²")
    public void testTrendAnalyzer() {
        TemporalMetricHistoryDto h = new TemporalMetricHistoryDto();
        h.setMetricName("f1");
        h.setHigherIsBetter(true);

        Instant now = Instant.now();
        h.getBaselinePoints().add(new TemporalMetricPointDto("r1", "BASELINE", 1, now.minus(5, ChronoUnit.DAYS), 0.85, "LOW", 0.70));
        h.getBaselinePoints().add(new TemporalMetricPointDto("r2", "BASELINE", 2, now.minus(4, ChronoUnit.DAYS), 0.82, "LOW", 0.70));
        h.getBaselinePoints().add(new TemporalMetricPointDto("r3", "BASELINE", 3, now.minus(3, ChronoUnit.DAYS), 0.79, "LOW", 0.70));
        h.getBaselinePoints().add(new TemporalMetricPointDto("r4", "BASELINE", 4, now.minus(2, ChronoUnit.DAYS), 0.75, "HIGH", 0.70));
        h.getBaselinePoints().add(new TemporalMetricPointDto("r5", "BASELINE", 5, now.minus(1, ChronoUnit.DAYS), 0.70, "HIGH", 0.70));

        trendAnalyzer.analyzeTrend(h);

        assertEquals(5, h.getObservationCount());
        assertEquals(0.70, h.getLatestValue());
        assertEquals(0.75, h.getPreviousValue());
        assertEquals(-0.05, h.getAbsoluteDelta());
        assertEquals("DEGRADING", h.getTrendDirection());
        assertTrue(h.getSlope() < 0);
        assertTrue(h.getrSquared() > 0.90);
        assertNotNull(h.getMannKendallTau());
    }

    @Test
    @DisplayName("3. Persistence Analyzer: Sequence state classification")
    public void testPersistenceAnalyzer() {
        // PERSISTENT: 3 consecutive HIGH/CRITICAL
        PersistenceAnalyzer.PersistenceResult p1 = persistenceAnalyzer.evaluatePersistence(
                List.of("LOW", "LOW", "HIGH", "HIGH", "CRITICAL")
        );
        assertEquals("PERSISTENT", p1.status);
        assertEquals("CRITICAL", p1.currentSeverity);
        assertEquals("CRITICAL", p1.peakSeverity);
        assertEquals(3, p1.consecutiveCount);

        // EMERGING: Lows followed by recent High
        PersistenceAnalyzer.PersistenceResult p2 = persistenceAnalyzer.evaluatePersistence(
                List.of("LOW", "LOW", "LOW", "HIGH")
        );
        assertEquals("EMERGING", p2.status);

        // RECURRING: High -> Low -> High
        PersistenceAnalyzer.PersistenceResult p3 = persistenceAnalyzer.evaluatePersistence(
                List.of("HIGH", "LOW", "LOW", "HIGH")
        );
        assertEquals("RECURRING", p3.status);

        // RECOVERED: High -> Low -> Low
        PersistenceAnalyzer.PersistenceResult p4 = persistenceAnalyzer.evaluatePersistence(
                List.of("HIGH", "HIGH", "LOW", "LOW")
        );
        assertEquals("RECOVERED", p4.status);
    }

    @Test
    @DisplayName("4. Change Point Analyzer: Step shift detection")
    public void testChangePointAnalyzer() {
        TemporalMetricHistoryDto h = new TemporalMetricHistoryDto();
        h.setMetricName("psi");
        h.setTargetKey("FEATURE::income");
        h.setHigherIsBetter(false);

        Instant now = Instant.now();
        h.getBaselinePoints().add(new TemporalMetricPointDto("r1", "BASELINE", 1, now.minus(5, ChronoUnit.DAYS), 0.04, "LOW", 0.10));
        h.getBaselinePoints().add(new TemporalMetricPointDto("r2", "BASELINE", 2, now.minus(4, ChronoUnit.DAYS), 0.05, "LOW", 0.10));
        h.getBaselinePoints().add(new TemporalMetricPointDto("r3", "BASELINE", 3, now.minus(3, ChronoUnit.DAYS), 0.35, "CRITICAL", 0.10));
        h.getBaselinePoints().add(new TemporalMetricPointDto("r4", "BASELINE", 4, now.minus(2, ChronoUnit.DAYS), 0.38, "CRITICAL", 0.10));
        h.getBaselinePoints().add(new TemporalMetricPointDto("r5", "BASELINE", 5, now.minus(1, ChronoUnit.DAYS), 0.36, "CRITICAL", 0.10));

        List<ChangePointDto> cps = changePointAnalyzer.detectChangePoints("lineage_1", h);
        assertEquals(1, cps.size());
        ChangePointDto cp = cps.get(0);
        assertEquals("r3", cp.getChangeRunId());
        assertTrue(cp.getAbsoluteShift() > 0.25);
        assertEquals("HIGH", cp.getConfidenceLevel());
    }

    @Test
    @DisplayName("5. Issue Track Analyzer: Grouping observations and tracking trajectories")
    public void testIssueTrackAnalyzer() {
        List<DiagnosticTemporalObservation> obsList = new ArrayList<>();
        Instant now = Instant.now();

        for (int i = 1; i <= 4; i++) {
            DiagnosticTemporalObservation o = new DiagnosticTemporalObservation();
            o.setModelLineageId("lineage_1");
            o.setRunId("run_" + i);
            o.setTimestamp(now.minus(5 - i, ChronoUnit.DAYS));
            o.setModule(DiagnosticModule.DRIFT);
            o.setMetricName("psi");
            o.setTargetType("FEATURE");
            o.setTargetKey("FEATURE::income");
            o.setMetricValue(i >= 3 ? 0.30 : 0.05);
            o.setSeverity(i >= 3 ? "HIGH" : "LOW");
            obsList.add(o);
        }

        List<IssueTrackDto> tracks = issueTrackAnalyzer.buildIssueTracks("lineage_1", obsList);
        assertEquals(1, tracks.size());
        IssueTrackDto t = tracks.get(0);
        assertEquals("FEATURE::income", t.getTargetKey());
        assertEquals(4, t.getObservationCount());
        assertEquals("HIGH", t.getCurrentSeverity());
    }

    @Test
    @DisplayName("6. Durability Analyzer: Validated remediation sustained across follow-up runs")
    public void testDurabilityAnalyzer() {
        Instant now = Instant.now();
        List<DiagnosticRun> runs = new ArrayList<>();
        runs.add(createRun("run_1", "model_a", "BASELINE", now.minus(4, ChronoUnit.DAYS)));
        runs.add(createRun("run_2", "model_a", "BASELINE", now.minus(3, ChronoUnit.DAYS)));
        runs.add(createRun("run_3", "model_a", "BASELINE", now.minus(2, ChronoUnit.DAYS)));

        DiagnosticExperiment exp = new DiagnosticExperiment();
        exp.setId("exp_101");
        exp.setBaselineRunId("run_1");
        exp.setCandidateRunId("run_cand_1");
        exp.setTargetKey("FEATURE::income");
        exp.setConclusion(ExperimentConclusion.VALIDATED);

        List<DiagnosticTemporalObservation> obs = new ArrayList<>();
        DiagnosticTemporalObservation o1 = new DiagnosticTemporalObservation();
        o1.setRunId("run_1");
        o1.setMetricName("psi");
        o1.setTargetKey("FEATURE::income");
        o1.setMetricValue(0.35);
        obs.add(o1);

        DiagnosticTemporalObservation o2 = new DiagnosticTemporalObservation();
        o2.setRunId("run_2");
        o2.setMetricName("psi");
        o2.setTargetKey("FEATURE::income");
        o2.setMetricValue(0.06); // Low PSI in follow-up 1
        obs.add(o2);

        DiagnosticTemporalObservation o3 = new DiagnosticTemporalObservation();
        o3.setRunId("run_3");
        o3.setMetricName("psi");
        o3.setTargetKey("FEATURE::income");
        o3.setMetricValue(0.07); // Low PSI in follow-up 2
        obs.add(o3);

        List<RemediationDurabilityDto> dList = durabilityAnalyzer.evaluateDurability(
                "model_a", runs, Collections.emptyList(), List.of(exp), obs);

        assertEquals(1, dList.size());
        assertEquals("SUSTAINED", dList.get(0).getDurabilityStatus());
        assertEquals(2, dList.get(0).getFollowUpValues().size());
    }

    @Test
    @DisplayName("7. Temporal Alert Analyzer: Generates prioritized alerts")
    public void testTemporalAlertAnalyzer() {
        Instant now = Instant.now();
        List<DiagnosticRun> runs = List.of(createRun("run_1", "model_x", "BASELINE", now));

        IssueTrackDto track = new IssueTrackDto();
        track.setTargetKey("FEATURE::income");
        track.setTargetType("FEATURE");
        track.setStatus("PERSISTENT");
        track.setCurrentSeverity("CRITICAL");
        track.setConsecutiveCount(4);
        track.setModulesInvolved(List.of("DRIFT", "ERROR_FORENSICS", "ROBUSTNESS"));

        List<TemporalAlertDto> alerts = alertAnalyzer.generateAlerts(
                "model_x", runs, List.of(track), Collections.emptyList(), Collections.emptyList());

        assertTrue(alerts.size() >= 1);
        assertEquals("CRITICAL", alerts.get(0).getPriority());
    }

    @Test
    @DisplayName("8. TemporalAnalysisService: Full End-to-End Lineage Evaluation and Idempotent Recalculation")
    public void testTemporalAnalysisServiceFullLifecycle() throws Exception {
        Instant now = Instant.now();
        String mName = "fraud_detection_lineage";

        // Create 3 historical runs
        DiagnosticRun r1 = createRun("run_h1", mName, "BASELINE", now.minus(3, ChronoUnit.DAYS));
        DiagnosticRun r2 = createRun("run_h2", mName, "BASELINE", now.minus(2, ChronoUnit.DAYS));
        DiagnosticRun r3 = createRun("run_h3", mName, "BASELINE", now.minus(1, ChronoUnit.DAYS));

        // Save diagnostic results for each run
        Map<String, Object> perf1 = Map.of("summary", Map.of("f1", 0.85, "rocAuc", 0.88, "precision", 0.84, "recall", 0.86));
        Map<String, Object> perf2 = Map.of("summary", Map.of("f1", 0.82, "rocAuc", 0.85, "precision", 0.80, "recall", 0.84));
        Map<String, Object> perf3 = Map.of("summary", Map.of("f1", 0.71, "rocAuc", 0.75, "precision", 0.70, "recall", 0.72));

        Map<String, Object> drift1 = Map.of("summary", Map.of("maxPsi", 0.04), "columns", Map.of("income", Map.of("psi", 0.04, "severity", "LOW")));
        Map<String, Object> drift2 = Map.of("summary", Map.of("maxPsi", 0.12), "columns", Map.of("income", Map.of("psi", 0.12, "severity", "MEDIUM")));
        Map<String, Object> drift3 = Map.of("summary", Map.of("maxPsi", 0.32), "columns", Map.of("income", Map.of("psi", 0.32, "severity", "CRITICAL")));

        saveResult(r1.getId(), DiagnosticModule.PERFORMANCE, perf1);
        saveResult(r1.getId(), DiagnosticModule.DRIFT, drift1);

        saveResult(r2.getId(), DiagnosticModule.PERFORMANCE, perf2);
        saveResult(r2.getId(), DiagnosticModule.DRIFT, drift2);

        saveResult(r3.getId(), DiagnosticModule.PERFORMANCE, perf3);
        saveResult(r3.getId(), DiagnosticModule.DRIFT, drift3);

        // Evaluate model lineage history
        ModelLineageHistoryDto history = temporalService.getModelLineageHistory(mName, "ALL_AVAILABLE");

        assertNotNull(history);
        assertEquals(mName, history.getModelLineageId());
        assertEquals(3, history.getTotalRunsCount());
        assertEquals(3, history.getBaselineRunsCount());
        assertEquals(0, history.getExperimentRunsCount());
        assertTrue(history.getMetricHistories().size() >= 2);

        // Check F1 trend: degrading
        Optional<TemporalMetricHistoryDto> f1History = history.getMetricHistories().stream()
                .filter(m -> "f1".equalsIgnoreCase(m.getMetricName()))
                .findFirst();
        assertTrue(f1History.isPresent());
        assertEquals("DEGRADING", f1History.get().getTrendDirection());
        assertEquals(0.71, f1History.get().getLatestValue());

        // Check issue track for income
        assertTrue(history.getIssueTracks().size() >= 1);
        IssueTrackDto incomeTrack = history.getIssueTracks().get(0);
        assertEquals("FEATURE::income", incomeTrack.getTargetKey());
        assertEquals("CRITICAL", incomeTrack.getCurrentSeverity());

        // Test Idempotent Recalculation
        TemporalRecalculateResponseDto recalcResp = temporalService.recalculateTemporalIntelligence(mName);
        assertTrue(recalcResp.isSuccess());
        assertEquals(3, recalcResp.getRunsProcessed());
        assertTrue(recalcResp.getObservationsExtracted() > 0);
        assertTrue(recalcResp.getIssueTracksBuilt() > 0);

        // Check database persistence
        List<DiagnosticTemporalObservation> savedObs = observationRepository.findByModelLineageIdOrderByTimestampAsc(mName);
        assertFalse(savedObs.isEmpty());
        List<DiagnosticIssueTrack> savedTracks = issueTrackRepository.findByModelLineageIdOrderByLastSeenAtDesc(mName);
        assertFalse(savedTracks.isEmpty());

        // Repeated recalculation should not duplicate records
        TemporalRecalculateResponseDto recalcResp2 = temporalService.recalculateTemporalIntelligence(mName);
        assertTrue(recalcResp2.isSuccess());
        assertEquals(savedObs.size(), observationRepository.findByModelLineageIdOrderByTimestampAsc(mName).size());
    }
}
