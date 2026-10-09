package com.modeldoctor;

import com.modeldoctor.domain.*;
import com.modeldoctor.dto.*;
import com.modeldoctor.repository.*;
import com.modeldoctor.service.ModelReliabilityService;
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
public class ModelReliabilityAndFleetTest {

    @Autowired
    private DiagnosticModelReliabilityRepository reliabilityRepository;

    @Autowired
    private DiagnosticReliabilityEventRepository eventRepository;

    @Autowired
    private DiagnosticFleetPatternRepository fleetPatternRepository;

    @Autowired
    private DiagnosticHealthSnapshotRepository snapshotRepository;

    @Autowired
    private DiagnosticIncidentRepository incidentRepository;

    @Autowired
    private DiagnosticRunRepository runRepository;

    @Autowired
    private ModelReliabilityService reliabilityService;

    private final String LINEAGE_HEALTHY = "lineage-credit-risk-v1";
    private final String LINEAGE_DEGRADED = "lineage-fraud-detector-v2";
    private final String LINEAGE_CRITICAL = "lineage-default-risk-v3";

    @BeforeEach
    public void setup() {
        reliabilityRepository.deleteAll();
        eventRepository.deleteAll();
        fleetPatternRepository.deleteAll();
        snapshotRepository.deleteAll();
        incidentRepository.deleteAll();
    }

    private DiagnosticRun createRun(String runId, String lineageId, String runType, Instant createdAt) {
        DiagnosticRun run = new DiagnosticRun();
        run.setId(runId);
        run.setModelName(lineageId);
        run.setModelFramework("SCIKIT_LEARN");
        run.setEvaluationDataset("dataset.csv");
        run.setRunType(runType);
        run.setStatus(DiagnosticStatus.COMPLETED);
        run.setTargetColumn("target");
        run.setTaskType("CLASSIFICATION");
        run.setCreatedAt(createdAt);
        return runRepository.save(run);
    }

    private void createHealthSnapshot(String lineageId, String runId, ModelHealthState state, int healthIndex, Instant timestamp) {
        DiagnosticHealthSnapshot snap = new DiagnosticHealthSnapshot();
        snap.setModelLineageId(lineageId);
        snap.setRunId(runId);
        snap.setOverallState(state);
        snap.setHealthIndex(healthIndex);
        snap.setTimestamp(timestamp);
        snap.setDimensionStatesJson("{}");
        snapshotRepository.save(snap);
    }

    private DiagnosticIncident createIncident(String lineageId, String code, String title, SeverityLevel severity,
                                              IncidentLifecycleState state, int reopens, boolean recurring, String runId) {
        DiagnosticIncident inc = new DiagnosticIncident();
        inc.setModelLineageId(lineageId);
        inc.setIncidentCode(code);
        inc.setIncidentFingerprint("FP-" + code + "-" + lineageId);
        inc.setTitle(title);
        inc.setCategory(IncidentCategory.DRIFT_INCIDENT);
        inc.setCurrentSeverity(severity != null ? severity.name() : "MEDIUM");
        inc.setPriorityScore(75);
        inc.setLifecycleState(state);
        inc.setPrimaryTarget("GLOBAL");
        inc.setDecisionRecommendation(IncidentDecisionState.INVESTIGATE);
        inc.setDecisionConfidence(DecisionConfidence.HIGH);
        inc.setReopenedCount(reopens);
        inc.setRecurring(recurring);
        inc.setLastSeenRunId(runId);
        inc.setCreatedAt(Instant.now().minus(2, ChronoUnit.HOURS));
        inc.setLastObservedAt(Instant.now());
        return incidentRepository.save(inc);
    }

    @Test
    @DisplayName("Test 1: Healthy model lineage achieves high score, Grade A, and NORMAL_OPERATION")
    public void testHealthyModelReliability() {
        Instant now = Instant.now();
        for (int i = 1; i <= 6; i++) {
            String runId = "run_healthy_" + i;
            createRun(runId, LINEAGE_HEALTHY, "BASELINE", now.minus(7 - i, ChronoUnit.DAYS));
            createHealthSnapshot(LINEAGE_HEALTHY, runId, ModelHealthState.HEALTHY, 95, now.minus(7 - i, ChronoUnit.DAYS));
        }

        ModelReliabilityProfileDto profile = reliabilityService.getReliabilityProfile(LINEAGE_HEALTHY);

        assertNotNull(profile);
        assertEquals(LINEAGE_HEALTHY, profile.getModelLineageId());
        assertEquals(6, profile.getOperationalRunCount());
        assertTrue(profile.getReliabilityScore() >= 88, "Expected reliability score >= 88 for healthy model, was: " + profile.getReliabilityScore());
        assertEquals(ModelReliabilityState.RELIABILITY_HEALTHY, profile.getReliabilityState());
        assertEquals("A", profile.getGrade());
        assertEquals(GovernanceRecommendation.NORMAL_OPERATION, profile.getGovernanceRecommendation());
        assertEquals(0, profile.getActiveIncidentCount());
        assertNotNull(profile.getScoreBreakdown());
        assertTrue(profile.getScoreBreakdown().getNetScore() >= 88);
    }

    @Test
    @DisplayName("Test 2: Degraded model lineage with recurring incidents and degrading trend")
    public void testDegradedModelReliability() {
        Instant now = Instant.now();
        for (int i = 1; i <= 5; i++) {
            String runId = "run_deg_" + i;
            createRun(runId, LINEAGE_DEGRADED, "BASELINE", now.minus(6 - i, ChronoUnit.DAYS));
            ModelHealthState st = i < 3 ? ModelHealthState.HEALTHY : ModelHealthState.DEGRADED;
            int hIndex = i < 3 ? 90 : 65;
            createHealthSnapshot(LINEAGE_DEGRADED, runId, st, hIndex, now.minus(6 - i, ChronoUnit.DAYS));
        }

        createIncident(LINEAGE_DEGRADED, "INC-DEG-01", "Feature Distribution Drift", SeverityLevel.HIGH,
                IncidentLifecycleState.OPEN, 1, true, "run_deg_5");

        ModelReliabilityProfileDto profile = reliabilityService.getReliabilityProfile(LINEAGE_DEGRADED);

        assertNotNull(profile);
        assertEquals(LINEAGE_DEGRADED, profile.getModelLineageId());
        assertTrue(profile.getReliabilityScore() < 80, "Expected degraded reliability score < 80, was: " + profile.getReliabilityScore());
        assertTrue(profile.getReliabilityState() == ModelReliabilityState.RELIABILITY_DEGRADED ||
                   profile.getReliabilityState() == ModelReliabilityState.RELIABILITY_AT_RISK);
        assertEquals(1, profile.getActiveIncidentCount());
        assertEquals(1, profile.getReopenedIncidentCount());
        assertTrue(profile.getGovernanceRecommendation() == GovernanceRecommendation.REVIEW_REQUIRED ||
                   profile.getGovernanceRecommendation() == GovernanceRecommendation.PRIORITY_REVIEW);
    }

    @Test
    @DisplayName("Test 3: Critical model lineage triggers ESCALATE recommendation and Grade F")
    public void testCriticalModelReliability() {
        Instant now = Instant.now();
        for (int i = 1; i <= 4; i++) {
            String runId = "run_crit_" + i;
            createRun(runId, LINEAGE_CRITICAL, "BASELINE", now.minus(5 - i, ChronoUnit.DAYS));
            createHealthSnapshot(LINEAGE_CRITICAL, runId, ModelHealthState.CRITICAL, 40, now.minus(5 - i, ChronoUnit.DAYS));
        }

        createIncident(LINEAGE_CRITICAL, "INC-CRIT-01", "Severe Calibration Failure", SeverityLevel.CRITICAL,
                IncidentLifecycleState.OPEN, 2, true, "run_crit_4");

        ModelReliabilityProfileDto profile = reliabilityService.getReliabilityProfile(LINEAGE_CRITICAL);

        assertNotNull(profile);
        assertEquals(LINEAGE_CRITICAL, profile.getModelLineageId());
        assertTrue(profile.getReliabilityScore() < 60, "Expected critical reliability score < 60, was: " + profile.getReliabilityScore());
        assertEquals(ModelReliabilityState.RELIABILITY_CRITICAL, profile.getReliabilityState());
        assertEquals("F", profile.getGrade());
        assertEquals(GovernanceRecommendation.ESCALATE, profile.getGovernanceRecommendation());
        assertEquals(1, profile.getCriticalIncidentCount());
    }

    @Test
    @DisplayName("Test 4: Insufficient history produces UNKNOWN state and INSUFFICIENT_EVIDENCE recommendation")
    public void testInsufficientHistory() {
        String lineageEmpty = "lineage-new-v1";
        createRun("run_new_1", lineageEmpty, "BASELINE", Instant.now());

        ModelReliabilityProfileDto profile = reliabilityService.getReliabilityProfile(lineageEmpty);

        assertNotNull(profile);
        assertEquals(ModelReliabilityState.RELIABILITY_UNKNOWN, profile.getReliabilityState());
        assertEquals(DecisionConfidence.INSUFFICIENT, profile.getReliabilityConfidence());
        assertEquals(GovernanceRecommendation.INSUFFICIENT_EVIDENCE, profile.getGovernanceRecommendation());
        assertEquals(ReliabilityTrend.INSUFFICIENT_DATA, profile.getTrend());
    }

    @Test
    @DisplayName("Test 5: Score breakdown deductions and bonuses are deterministic and bounded [0, 100]")
    public void testScoreBreakdownDeterministic() {
        Instant now = Instant.now();
        for (int i = 1; i <= 8; i++) {
            String runId = "run_bnd_" + i;
            createRun(runId, "lineage-bounded-test", "BASELINE", now.minus(9 - i, ChronoUnit.DAYS));
            createHealthSnapshot("lineage-bounded-test", runId, ModelHealthState.HEALTHY, 92, now.minus(9 - i, ChronoUnit.DAYS));
        }

        ModelReliabilityProfileDto profile = reliabilityService.getReliabilityProfile("lineage-bounded-test");
        ReliabilityScoreBreakdownDto breakdown = profile.getScoreBreakdown();

        assertNotNull(breakdown);
        assertEquals(100, breakdown.getBaseScore());
        assertTrue(breakdown.getNetScore() >= 0 && breakdown.getNetScore() <= 100);
        assertFalse(breakdown.getItems().isEmpty());
    }

    @Test
    @DisplayName("Test 6: Experiment run isolation — EXPERIMENT runs do not alter authoritative reliability")
    public void testExperimentRunIsolation() {
        Instant now = Instant.now();
        for (int i = 1; i <= 4; i++) {
            String runId = "run_base_" + i;
            createRun(runId, "lineage-exp-iso", "BASELINE", now.minus(5 - i, ChronoUnit.DAYS));
            createHealthSnapshot("lineage-exp-iso", runId, ModelHealthState.HEALTHY, 90, now.minus(5 - i, ChronoUnit.DAYS));
        }

        ModelReliabilityProfileDto before = reliabilityService.getReliabilityProfile("lineage-exp-iso");

        // Add 5 EXPERIMENT runs with varying synthetic states
        for (int i = 1; i <= 5; i++) {
            String runId = "run_exp_" + i;
            createRun(runId, "lineage-exp-iso", "EXPERIMENT", now.plus(i, ChronoUnit.HOURS));
            createHealthSnapshot("lineage-exp-iso", runId, ModelHealthState.CRITICAL, 20, now.plus(i, ChronoUnit.HOURS));
        }

        reliabilityService.recalculateReliability("lineage-exp-iso");
        ModelReliabilityProfileDto after = reliabilityService.getReliabilityProfile("lineage-exp-iso");

        assertEquals(4, after.getOperationalRunCount(), "Operational run count should only count BASELINE runs");
        assertEquals(before.getReliabilityScore(), after.getReliabilityScore(), "Score should not be degraded by EXPERIMENT runs");
        assertEquals(before.getReliabilityState(), after.getReliabilityState());
    }

    @Test
    @DisplayName("Test 7: Fleet Risk Ranking prioritizes critical models over stable models")
    public void testFleetRiskRanking() {
        Instant now = Instant.now();

        // 1. Lineage Healthy
        for (int i = 1; i <= 4; i++) {
            createRun("h_run_" + i, LINEAGE_HEALTHY, "BASELINE", now.minus(5 - i, ChronoUnit.DAYS));
            createHealthSnapshot(LINEAGE_HEALTHY, "h_run_" + i, ModelHealthState.HEALTHY, 95, now.minus(5 - i, ChronoUnit.DAYS));
        }

        // 2. Lineage Critical
        for (int i = 1; i <= 4; i++) {
            createRun("c_run_" + i, LINEAGE_CRITICAL, "BASELINE", now.minus(5 - i, ChronoUnit.DAYS));
            createHealthSnapshot(LINEAGE_CRITICAL, "c_run_" + i, ModelHealthState.CRITICAL, 35, now.minus(5 - i, ChronoUnit.DAYS));
        }
        createIncident(LINEAGE_CRITICAL, "INC-C-1", "Data Leakage Incident", SeverityLevel.CRITICAL,
                IncidentLifecycleState.OPEN, 1, false, "c_run_4");

        FleetOverviewDto fleet = reliabilityService.getFleetOverview();

        assertNotNull(fleet);
        assertTrue(fleet.getTotalLineagesCount() >= 2);
        assertNotNull(fleet.getRankedLineages());
        assertFalse(fleet.getRankedLineages().isEmpty());

        FleetRiskRankDto topRank = fleet.getRankedLineages().get(0);
        assertEquals(LINEAGE_CRITICAL, topRank.getModelLineageId(), "Top risk lineage should be the critical model");
        assertEquals("CRITICAL", topRank.getRiskTier());
        assertFalse(topRank.getRankingReasons().isEmpty());
    }

    @Test
    @DisplayName("Test 8: Cross-model recurring pattern detection and non-causal disclaimer")
    public void testFleetRecurringPatterns() {
        Instant now = Instant.now();

        // Create 2 lineages with feature drift incidents
        String l1 = "lineage-alpha-drift";
        String l2 = "lineage-beta-drift";

        for (int i = 1; i <= 3; i++) {
            createRun("a_run_" + i, l1, "BASELINE", now.minus(4 - i, ChronoUnit.DAYS));
            createHealthSnapshot(l1, "a_run_" + i, ModelHealthState.DEGRADED, 70, now.minus(4 - i, ChronoUnit.DAYS));

            createRun("b_run_" + i, l2, "BASELINE", now.minus(4 - i, ChronoUnit.DAYS));
            createHealthSnapshot(l2, "b_run_" + i, ModelHealthState.DEGRADED, 68, now.minus(4 - i, ChronoUnit.DAYS));
        }

        createIncident(l1, "INC-A-DRIFT", "Feature income drift", SeverityLevel.HIGH,
                IncidentLifecycleState.OPEN, 1, true, "a_run_3");
        createIncident(l2, "INC-B-DRIFT", "Feature age drift", SeverityLevel.HIGH,
                IncidentLifecycleState.OPEN, 2, true, "b_run_3");

        FleetOverviewDto fleet = reliabilityService.getFleetOverview();
        List<FleetPatternDto> patterns = fleet.getRecurringPatterns();

        assertNotNull(patterns);
        assertFalse(patterns.isEmpty(), "Expected at least one recurring pattern across lineages");

        for (FleetPatternDto p : patterns) {
            assertNotNull(p.getNonCausalDisclaimer());
            assertTrue(p.getNonCausalDisclaimer().contains("Causal relationship has not been established"));
            assertTrue(p.getAffectedLineagesCount() >= 2);
        }
    }

    @Test
    @DisplayName("Test 9: Side-by-side lineage comparison computes explicit delta without bias")
    public void testLineageComparison() {
        Instant now = Instant.now();

        for (int i = 1; i <= 4; i++) {
            createRun("h_cmp_" + i, LINEAGE_HEALTHY, "BASELINE", now.minus(5 - i, ChronoUnit.DAYS));
            createHealthSnapshot(LINEAGE_HEALTHY, "h_cmp_" + i, ModelHealthState.HEALTHY, 95, now.minus(5 - i, ChronoUnit.DAYS));

            createRun("c_cmp_" + i, LINEAGE_CRITICAL, "BASELINE", now.minus(5 - i, ChronoUnit.DAYS));
            createHealthSnapshot(LINEAGE_CRITICAL, "c_cmp_" + i, ModelHealthState.CRITICAL, 40, now.minus(5 - i, ChronoUnit.DAYS));
        }

        ModelComparisonDto comparison = reliabilityService.compareLineages(LINEAGE_HEALTHY, LINEAGE_CRITICAL);

        assertNotNull(comparison);
        assertNotNull(comparison.getLeft());
        assertNotNull(comparison.getRight());
        assertTrue(comparison.getScoreDelta() > 0, "Healthy model should have positive score delta over Critical model");
        assertFalse(comparison.getKeyDifferences().isEmpty());
        assertTrue(comparison.getGovernanceComparisonSummary().contains("GOVERNANCE COMPARISON"));
    }

    @Test
    @DisplayName("Test 10: Idempotency — repeated recalculations produce identical results without duplicate events")
    public void testRecalculateIdempotency() {
        Instant now = Instant.now();
        for (int i = 1; i <= 4; i++) {
            String runId = "run_idem_" + i;
            createRun(runId, "lineage-idempotency", "BASELINE", now.minus(5 - i, ChronoUnit.DAYS));
            createHealthSnapshot("lineage-idempotency", runId, ModelHealthState.HEALTHY, 92, now.minus(5 - i, ChronoUnit.DAYS));
        }

        ReliabilityRecalculateResponseDto res1 = reliabilityService.recalculateReliability("lineage-idempotency");
        long eventsCount1 = eventRepository.count();

        ReliabilityRecalculateResponseDto res2 = reliabilityService.recalculateReliability("lineage-idempotency");
        long eventsCount2 = eventRepository.count();

        ReliabilityRecalculateResponseDto res3 = reliabilityService.recalculateReliability("lineage-idempotency");
        long eventsCount3 = eventRepository.count();

        assertEquals(res1.getReliabilityScore(), res2.getReliabilityScore());
        assertEquals(res2.getReliabilityScore(), res3.getReliabilityScore());
        assertEquals(res1.getReliabilityState(), res3.getReliabilityState());
        assertEquals(eventsCount1, eventsCount2, "Event repository count must remain constant on repeated recalculation");
        assertEquals(eventsCount2, eventsCount3);
    }
}
