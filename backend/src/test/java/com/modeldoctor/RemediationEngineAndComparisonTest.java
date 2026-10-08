package com.modeldoctor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeldoctor.domain.*;
import com.modeldoctor.dto.*;
import com.modeldoctor.repository.DiagnosticRemediationRepository;
import com.modeldoctor.repository.DiagnosticResultRepository;
import com.modeldoctor.repository.DiagnosticRunRepository;
import com.modeldoctor.service.CorrelationAnalysisService;
import com.modeldoctor.service.InvestigationAnalysisService;
import com.modeldoctor.service.RemediationAnalysisService;
import com.modeldoctor.service.RunComparisonService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
public class RemediationEngineAndComparisonTest {

    @Autowired
    private DiagnosticRunRepository runRepository;

    @Autowired
    private DiagnosticResultRepository resultRepository;

    @Autowired
    private DiagnosticRemediationRepository remediationRepository;

    @Autowired
    private CorrelationAnalysisService correlationService;

    @Autowired
    private InvestigationAnalysisService investigationService;

    @Autowired
    private RemediationAnalysisService remediationService;

    @Autowired
    private RunComparisonService comparisonService;

    @Autowired
    private ObjectMapper objectMapper;

    private String baselineRunId;
    private String candidateRunId;

    @BeforeEach
    public void setup() {
        baselineRunId = "run_test_base_" + UUID.randomUUID().toString().substring(0, 8);
        candidateRunId = "run_test_cand_" + UUID.randomUUID().toString().substring(0, 8);

        createTestRun(baselineRunId, "model_v1.json", "dataset_v1.csv");
        createTestRun(candidateRunId, "model_v2.json", "dataset_v2.csv");
    }

    private void createTestRun(String runId, String modelName, String evalDataset) {
        DiagnosticRun run = new DiagnosticRun();
        run.setId(runId);
        run.setModelName(modelName);
        run.setModelFramework("xgboost");
        run.setTaskType("binary_classification");
        run.setExecutionMode("REAL");
        run.setEvaluationDataset(evalDataset);
        run.setBaselineDataset("train_base.csv");
        run.setTargetColumn("is_fraud");
        run.setPredictionColumn("pred_prob");
        run.setProtectedAttribute("is_foreign_ip");
        run.setStatus(DiagnosticStatus.COMPLETED);
        run.setCreatedAt(Instant.now());
        run.setStartedAt(Instant.now().minusSeconds(15));
        run.setCompletedAt(Instant.now());
        run.setExecutionDurationMs(1500L);

        List<DiagnosticRunModule> modules = new ArrayList<>();
        DiagnosticModule[] allMods = {
            DiagnosticModule.DATA_QUALITY, DiagnosticModule.LEAKAGE, DiagnosticModule.DRIFT,
            DiagnosticModule.PERFORMANCE, DiagnosticModule.EXPLAINABILITY, DiagnosticModule.BIAS,
            DiagnosticModule.ROBUSTNESS, DiagnosticModule.ERROR_FORENSICS
        };
        for (DiagnosticModule mod : allMods) {
            DiagnosticRunModule m = new DiagnosticRunModule();
            m.setRun(run);
            m.setModule(mod);
            m.setStatus(ModuleExecutionStatus.COMPLETED);
            m.setStartedAt(Instant.now().minusSeconds(15));
            m.setCompletedAt(Instant.now());
            modules.add(m);
        }
        run.setModules(modules);
        runRepository.saveAndFlush(run);
    }

    private void seedCompleteBaselineResults(String runId) {
        // 1. DATA QUALITY - missing rate on account_age_months = 18%
        String dqJson = """
        {
            "summary": {"healthScore": 82.0, "rowCount": 2000, "columnCount": 6, "nullCount": 360, "passed": false},
            "columns": {
                "transaction_amount": {"nullRate": 0.01, "outlierRate": 0.08, "constant": false, "distinctCount": 1800},
                "account_age_months": {"nullRate": 0.18, "outlierRate": 0.02, "constant": false, "distinctCount": 120},
                "account_id": {"nullRate": 0.0, "outlierRate": 0.0, "constant": false, "distinctCount": 2000}
            }
        }
        """;
        resultRepository.save(new DiagnosticResult(null, runId, DiagnosticModule.DATA_QUALITY, ModuleExecutionStatus.COMPLETED, dqJson, Instant.now()));

        // 2. LEAKAGE - account_id has high leakage score 0.88 and identifier-like
        String leakJson = """
        {
            "summary": {"maxLeakageScore": 0.88, "leakageDetected": true, "healthScore": 55.0, "passed": false},
            "features": [
                {"feature": "account_id", "leakageScore": 0.88, "leakageType": "IDENTIFIER_LEAKAGE", "isIdentifier": true, "auc": 0.94},
                {"feature": "transaction_amount", "leakageScore": 0.12, "leakageType": "NONE", "isIdentifier": false, "auc": 0.61}
            ]
        }
        """;
        resultRepository.save(new DiagnosticResult(null, runId, DiagnosticModule.LEAKAGE, ModuleExecutionStatus.COMPLETED, leakJson, Instant.now()));

        // 3. DRIFT - transaction_amount PSI = 0.42
        String driftJson = """
        {
            "summary": {"maxPsi": 0.42, "driftDetected": true, "healthScore": 60.0, "driftedColumnsCount": 1, "passed": false},
            "columns": {
                "transaction_amount": {"psi": 0.42, "ksPValue": 0.0001, "wassersteinDistance": 45.2, "driftDetected": true, "driftSeverity": "CRITICAL"}
            }
        }
        """;
        resultRepository.save(new DiagnosticResult(null, runId, DiagnosticModule.DRIFT, ModuleExecutionStatus.COMPLETED, driftJson, Instant.now()));

        // 4. PERFORMANCE - ECE = 0.14, with threshold grid
        String perfJson = """
        {
            "summary": {
                "accuracy": 0.88, "precision": 0.72, "recall": 0.65, "f1": 0.683,
                "rocAuc": 0.85, "prAuc": 0.71, "logLoss": 0.38, "brierScore": 0.12,
                "expectedCalibrationError": 0.14, "maxCalibrationError": 0.28,
                "fpr": 0.05, "fnr": 0.35, "passed": false
            },
            "thresholdAnalysis": {
                "currentThreshold": 0.50,
                "grid": [
                    {"threshold": 0.35, "accuracy": 0.89, "precision": 0.70, "recall": 0.82, "f1": 0.755, "fpr": 0.07, "fnr": 0.18},
                    {"threshold": 0.50, "accuracy": 0.88, "precision": 0.72, "recall": 0.65, "f1": 0.683, "fpr": 0.05, "fnr": 0.35},
                    {"threshold": 0.65, "accuracy": 0.86, "precision": 0.79, "recall": 0.48, "f1": 0.597, "fpr": 0.03, "fnr": 0.52}
                ]
            }
        }
        """;
        resultRepository.save(new DiagnosticResult(null, runId, DiagnosticModule.PERFORMANCE, ModuleExecutionStatus.COMPLETED, perfJson, Instant.now()));

        // 5. EXPLAINABILITY - transaction_amount rank 1 (0.45), account_id rank 2 (0.35)
        String explJson = """
        {
            "summary": {"healthScore": 85.0, "topFeature": "transaction_amount", "method": "SHAP", "topAttributionShare": 0.45},
            "attributions": [
                {"feature": "transaction_amount", "meanAbsoluteShap": 0.45, "rank": 1, "importanceShare": 0.45},
                {"feature": "account_id", "meanAbsoluteShap": 0.35, "rank": 2, "importanceShare": 0.35},
                {"feature": "account_age_months", "meanAbsoluteShap": 0.20, "rank": 3, "importanceShare": 0.20}
            ]
        }
        """;
        resultRepository.save(new DiagnosticResult(null, runId, DiagnosticModule.EXPLAINABILITY, ModuleExecutionStatus.COMPLETED, explJson, Instant.now()));

        // 6. BIAS - disparate impact 0.62, TPR gap 0.14
        String biasJson = """
        {
            "summary": {
                "healthScore": 58.0, "protectedAttribute": "is_foreign_ip", "groupCount": 2,
                "worstDisparateImpactRatio": 0.62, "demographicParityGap": 0.14, "equalOpportunityGap": 0.14, "passed": false
            },
            "subgroups": [
                {"groupValue": "domestic", "selectionRate": 0.10, "truePositiveRate": 0.85, "falsePositiveRate": 0.03},
                {"groupValue": "foreign", "selectionRate": 0.24, "truePositiveRate": 0.71, "falsePositiveRate": 0.12}
            ]
        }
        """;
        resultRepository.save(new DiagnosticResult(null, runId, DiagnosticModule.BIAS, ModuleExecutionStatus.COMPLETED, biasJson, Instant.now()));

        // 7. ROBUSTNESS - flip rate 0.16 on transaction_amount
        String robJson = """
        {
            "summary": {"healthScore": 68.0, "topSensitiveFeature": "transaction_amount", "gaussianJitter5PctFlipRate": 0.16, "boundaryFlipRate": 0.30, "passed": false},
            "featureSensitivities": [
                {"feature": "transaction_amount", "sensitivityRank": 1, "predictionFlipRate": 0.16, "meanProbabilityShift": 0.11},
                {"feature": "account_age_months", "sensitivityRank": 2, "predictionFlipRate": 0.04, "meanProbabilityShift": 0.02}
            ]
        }
        """;
        resultRepository.save(new DiagnosticResult(null, runId, DiagnosticModule.ROBUSTNESS, ModuleExecutionStatus.COMPLETED, robJson, Instant.now()));

        // 8. ERROR FORENSICS - error correlation 0.42 on transaction_amount, high confidence errors 28%
        String errJson = """
        {
            "schemaVersion": 1,
            "module": "ERROR_FORENSICS",
            "sampleCount": 1000,
            "errorSummary": {
                "totalRecords": 1000,
                "totalErrors": 95,
                "errorRate": 0.095,
                "truePositive": {"count": 75, "rate": 0.075},
                "trueNegative": {"count": 830, "rate": 0.830},
                "falsePositive": {"count": 40, "rate": 0.040},
                "falseNegative": {"count": 55, "rate": 0.055}
            },
            "confidenceAnalysis": {
                "meanIncorrectConfidence": 0.84,
                "medianIncorrectConfidence": 0.86,
                "highConfidenceErrorCount": 27,
                "highConfidenceErrorRate": 0.284,
                "highConfidenceErrorShare": 0.027
            },
            "featureAssociations": [
                {
                    "feature": "transaction_amount",
                    "featureType": "numeric",
                    "statisticName": "point_biserial_r",
                    "statistic": 0.42,
                    "effectSize": 0.42,
                    "pValue": 0.00001,
                    "adjustedPValue": 0.0001,
                    "direction": "positive",
                    "rank": 1
                }
            ],
            "falsePositiveAnalysis": {
                "fpCount": 40,
                "topSeparations": [
                    {"feature": "transaction_amount", "standardizedMeanDifference": 1.15, "adjustedPValue": 0.0001}
                ]
            },
            "subgroupAnalysis": [
                {
                    "group": "foreign",
                    "sampleCount": 250,
                    "errorCount": 55,
                    "errorRate": 0.220,
                    "falsePositiveRate": 0.12,
                    "falseNegativeRate": 0.29,
                    "highConfidenceErrorRate": 0.11,
                    "disparityRatio": 2.44
                }
            ],
            "calibrationForensics": {
                "expectedCalibrationError": 0.14,
                "bins": []
            }
        }
        """;
        resultRepository.save(new DiagnosticResult(null, runId, DiagnosticModule.ERROR_FORENSICS, ModuleExecutionStatus.COMPLETED, errJson, Instant.now()));

        resultRepository.flush();
    }

    private void seedCandidateResults(String runId) {
        // Performance improved (F1: 0.683 -> 0.770, ECE: 0.14 -> 0.06)
        String perfJson = """
        {
            "summary": {
                "accuracy": 0.93, "precision": 0.80, "recall": 0.74, "f1": 0.770,
                "rocAuc": 0.91, "prAuc": 0.80, "logLoss": 0.22, "brierScore": 0.07,
                "expectedCalibrationError": 0.06, "maxCalibrationError": 0.12,
                "fpr": 0.03, "fnr": 0.26, "passed": true
            }
        }
        """;
        resultRepository.save(new DiagnosticResult(null, runId, DiagnosticModule.PERFORMANCE, ModuleExecutionStatus.COMPLETED, perfJson, Instant.now()));

        // Drift reduced (PSI: 0.42 -> 0.08)
        String driftJson = """
        {
            "summary": {"maxPsi": 0.08, "driftDetected": false, "healthScore": 95.0, "driftedColumnsCount": 0, "passed": true},
            "columns": {
                "transaction_amount": {"psi": 0.08, "ksPValue": 0.45, "wassersteinDistance": 5.1, "driftDetected": false, "driftSeverity": "NONE"}
            }
        }
        """;
        resultRepository.save(new DiagnosticResult(null, runId, DiagnosticModule.DRIFT, ModuleExecutionStatus.COMPLETED, driftJson, Instant.now()));

        // Robustness improved (flipRate: 0.16 -> 0.04)
        String robJson = """
        {
            "summary": {"healthScore": 92.0, "topSensitiveFeature": "transaction_amount", "gaussianJitter5PctFlipRate": 0.04, "boundaryFlipRate": 0.08, "passed": true},
            "featureSensitivities": [
                {"feature": "transaction_amount", "sensitivityRank": 1, "predictionFlipRate": 0.04, "meanProbabilityShift": 0.02}
            ]
        }
        """;
        resultRepository.save(new DiagnosticResult(null, runId, DiagnosticModule.ROBUSTNESS, ModuleExecutionStatus.COMPLETED, robJson, Instant.now()));

        resultRepository.flush();
    }

    @Test
    public void testDistributionShiftRemediationGeneration() {
        seedCompleteBaselineResults(baselineRunId);

        List<DiagnosticRemediationDto> remediations = remediationService.analyzeAndPersist(baselineRunId);
        assertNotNull(remediations);
        assertFalse(remediations.isEmpty());

        // Find DISTRIBUTION_SHIFT_INVESTIGATION for transaction_amount
        DiagnosticRemediationDto shiftRemediation = remediations.stream()
                .filter(r -> RemediationType.DISTRIBUTION_SHIFT_INVESTIGATION.name().equals(r.getRemediationType())
                        && r.getTargetKey().equals("FEATURE::transaction_amount"))
                .findFirst()
                .orElse(null);

        assertNotNull(shiftRemediation, "Must generate DISTRIBUTION_SHIFT_INVESTIGATION for drifted influential feature");
        assertEquals("FEATURE", shiftRemediation.getTargetType());
        assertTrue("CRITICAL".equals(shiftRemediation.getPriority()) || "HIGH".equals(shiftRemediation.getPriority()));
        assertTrue(shiftRemediation.getPriorityScore() >= 75.0);
        assertTrue(shiftRemediation.getHypothesis().contains("transaction_amount"));
        assertTrue(shiftRemediation.getRequiredModules().contains("DRIFT"));
        assertTrue(shiftRemediation.getRequiredModules().contains("PERFORMANCE"));
        assertFalse(shiftRemediation.getAcceptanceCriteria().isEmpty());
    }

    @Test
    public void testFeatureEngineeringRemediationGeneration() {
        seedCompleteBaselineResults(baselineRunId);

        List<DiagnosticRemediationDto> remediations = remediationService.analyzeAndPersist(baselineRunId);
        DiagnosticRemediationDto featEng = remediations.stream()
                .filter(r -> RemediationType.FEATURE_ENGINEERING_REVIEW.name().equals(r.getRemediationType())
                        && r.getTargetKey().equals("FEATURE::transaction_amount"))
                .findFirst()
                .orElse(null);

        assertNotNull(featEng, "Must generate FEATURE_ENGINEERING_REVIEW for high SHAP + high error correlation");
        assertTrue(featEng.getExpectedImpact().stream().anyMatch(e -> e.getMetric().toLowerCase().contains("error")));
    }

    @Test
    public void testDataLeakageRemediationGeneration() {
        seedCompleteBaselineResults(baselineRunId);

        List<DiagnosticRemediationDto> remediations = remediationService.analyzeAndPersist(baselineRunId);
        DiagnosticRemediationDto leakRem = remediations.stream()
                .filter(r -> RemediationType.DATA_LEAKAGE_REVIEW.name().equals(r.getRemediationType())
                        && r.getTargetKey().equals("FEATURE::account_id"))
                .findFirst()
                .orElse(null);

        assertNotNull(leakRem, "Must generate DATA_LEAKAGE_REVIEW for high-leakage identifier feature");
        assertEquals("CRITICAL", leakRem.getPriority());
        assertTrue(leakRem.getHypothesis().contains("account_id"));
    }

    @Test
    public void testDataQualityRepairRemediationGeneration() {
        seedCompleteBaselineResults(baselineRunId);

        List<DiagnosticRemediationDto> remediations = remediationService.analyzeAndPersist(baselineRunId);
        DiagnosticRemediationDto dqRem = remediations.stream()
                .filter(r -> RemediationType.DATA_QUALITY_REPAIR.name().equals(r.getRemediationType())
                        && r.getTargetKey().equals("FEATURE::account_age_months"))
                .findFirst()
                .orElse(null);

        assertNotNull(dqRem, "Must generate DATA_QUALITY_REPAIR for feature with 18% null rate");
        assertTrue(dqRem.getRequiredModules().contains("DATA_QUALITY"));
    }

    @Test
    public void testCalibrationReviewRemediationGeneration() {
        seedCompleteBaselineResults(baselineRunId);

        List<DiagnosticRemediationDto> remediations = remediationService.analyzeAndPersist(baselineRunId);
        DiagnosticRemediationDto calRem = remediations.stream()
                .filter(r -> RemediationType.CALIBRATION_REVIEW.name().equals(r.getRemediationType()))
                .findFirst()
                .orElse(null);

        assertNotNull(calRem, "Must generate CALIBRATION_REVIEW for ECE = 0.14 >= 0.10");
        assertTrue(calRem.getExpectedImpact().stream().anyMatch(e -> e.getMetric().toLowerCase().contains("calibration") || e.getMetric().toLowerCase().contains("ece")));
    }

    @Test
    public void testThresholdReviewRemediationGeneration() {
        seedCompleteBaselineResults(baselineRunId);

        List<DiagnosticRemediationDto> remediations = remediationService.analyzeAndPersist(baselineRunId);
        DiagnosticRemediationDto threshRem = remediations.stream()
                .filter(r -> RemediationType.THRESHOLD_REVIEW.name().equals(r.getRemediationType()))
                .findFirst()
                .orElse(null);

        assertNotNull(threshRem, "Must generate THRESHOLD_REVIEW when alternative threshold cuts FNR from 0.35 to 0.18");
        assertTrue(threshRem.getDescription().toLowerCase().contains("threshold") || threshRem.getHypothesis().toLowerCase().contains("threshold"));
    }

    @Test
    public void testErrorSegmentReviewRemediationGeneration() {
        seedCompleteBaselineResults(baselineRunId);

        List<DiagnosticRemediationDto> remediations = remediationService.analyzeAndPersist(baselineRunId);
        DiagnosticRemediationDto errSegRem = remediations.stream()
                .filter(r -> RemediationType.ERROR_SEGMENT_REVIEW.name().equals(r.getRemediationType()))
                .findFirst()
                .orElse(null);

        assertNotNull(errSegRem, "Must generate ERROR_SEGMENT_REVIEW for high-confidence error rate 28.4%");
        assertTrue(errSegRem.getHypothesis().contains("high-confidence"));
    }

    @Test
    public void testFairnessReviewRemediationGeneration() {
        seedCompleteBaselineResults(baselineRunId);

        List<DiagnosticRemediationDto> remediations = remediationService.analyzeAndPersist(baselineRunId);
        DiagnosticRemediationDto fairRem = remediations.stream()
                .filter(r -> RemediationType.FAIRNESS_REVIEW.name().equals(r.getRemediationType()))
                .findFirst()
                .orElse(null);

        assertNotNull(fairRem, "Must generate FAIRNESS_REVIEW for disparate impact 0.62 < 0.80");
        assertEquals("SUBGROUP::is_foreign_ip", fairRem.getTargetKey());
    }

    @Test
    public void testRobustnessReviewRemediationGeneration() {
        seedCompleteBaselineResults(baselineRunId);

        List<DiagnosticRemediationDto> remediations = remediationService.analyzeAndPersist(baselineRunId);
        DiagnosticRemediationDto robRem = remediations.stream()
                .filter(r -> RemediationType.ROBUSTNESS_REVIEW.name().equals(r.getRemediationType())
                        && r.getTargetKey().equals("FEATURE::transaction_amount"))
                .findFirst()
                .orElse(null);

        assertNotNull(robRem, "Must generate ROBUSTNESS_REVIEW for flip rate 16% >= 10%");
    }

    @Test
    public void testFeatureRemovalReviewRequiresMultiSourceRisk() {
        seedCompleteBaselineResults(baselineRunId);

        List<DiagnosticRemediationDto> remediations = remediationService.analyzeAndPersist(baselineRunId);
        DiagnosticRemediationDto remRemoval = remediations.stream()
                .filter(r -> RemediationType.FEATURE_REMOVAL_REVIEW.name().equals(r.getRemediationType())
                        && r.getTargetKey().equals("FEATURE::account_id"))
                .findFirst()
                .orElse(null);

        assertNotNull(remRemoval, "Must generate FEATURE_REMOVAL_REVIEW for account_id with leakage + identifier + high SHAP");
        assertTrue(remRemoval.getHypothesis().contains("hypothesis to validate"));
    }

    @Test
    public void testRemediationPriorityRankingAndDeterminism() {
        seedCompleteBaselineResults(baselineRunId);

        List<DiagnosticRemediationDto> remediations = remediationService.analyzeAndPersist(baselineRunId);
        assertNotNull(remediations);
        assertTrue(remediations.size() >= 5);

        // Verify descending deterministic order by priority score
        for (int i = 0; i < remediations.size() - 1; i++) {
            assertTrue(remediations.get(i).getPriorityScore() >= remediations.get(i + 1).getPriorityScore(),
                    "Remediations must be ordered deterministically by priorityScore DESC");
        }
    }

    @Test
    public void testIdempotentRecalculationNoDuplicateRemediations() {
        seedCompleteBaselineResults(baselineRunId);

        List<DiagnosticRemediationDto> firstRun = remediationService.analyzeAndPersist(baselineRunId);
        int count1 = firstRun.size();

        List<DiagnosticRemediationDto> secondRun = remediationService.analyzeAndPersist(baselineRunId);
        int count2 = secondRun.size();

        assertEquals(count1, count2, "Recalculation must be idempotent without creating duplicate rows");
        assertEquals(count1, remediationRepository.countByRunId(baselineRunId));
    }

    @Test
    public void testProvenanceTraceability() {
        seedCompleteBaselineResults(baselineRunId);

        List<DiagnosticRemediationDto> remediations = remediationService.analyzeAndPersist(baselineRunId);
        for (DiagnosticRemediationDto rem : remediations) {
            assertNotNull(rem.getRunId());
            assertNotNull(rem.getTargetKey());
            assertNotNull(rem.getSourceResultIds());
            assertFalse(rem.getSourceResultIds().isEmpty(), "Must contain valid source result IDs");
            assertNotNull(rem.getHypothesis());
            assertNotNull(rem.getValidationStrategy());
            assertNotNull(rem.getAcceptanceCriteria());
            assertNotNull(rem.getRegressionGuards());
        }
    }

    @Test
    public void testRemediationLifecycleSelectAndReject() {
        seedCompleteBaselineResults(baselineRunId);

        List<DiagnosticRemediationDto> remediations = remediationService.analyzeAndPersist(baselineRunId);
        DiagnosticRemediationDto targetRem = remediations.get(0);
        assertEquals(RemediationStatus.PROPOSED.name(), targetRem.getStatus());

        // Test SELECT
        DiagnosticRemediationDto selected = remediationService.selectRemediation(targetRem.getId());
        assertEquals(RemediationStatus.SELECTED.name(), selected.getStatus());
        assertEquals(targetRem.getPriorityScore(), selected.getPriorityScore(), "Score should not change on selection");

        // Test REJECT
        DiagnosticRemediationDto rejected = remediationService.rejectRemediation(targetRem.getId(), "Risk unacceptable in production");
        assertEquals(RemediationStatus.REJECTED.name(), rejected.getStatus());
    }

    @Test
    public void testInsufficientEvidenceBehavior() {
        // Run with 0 diagnostic results
        List<DiagnosticRemediationDto> remediations = remediationService.analyzeAndPersist(baselineRunId);
        assertNotNull(remediations);
        assertTrue(remediations.isEmpty(), "Empty diagnostic results must produce zero remediations gracefully");
    }

    @Test
    public void testPartialRunBehavior() {
        // Only seed DRIFT and PERFORMANCE
        String driftJson = """
        {"summary": {"maxPsi": 0.35, "driftDetected": true, "healthScore": 65.0, "passed": false}, "columns": {"amount": {"psi": 0.35, "driftDetected": true}}}
        """;
        resultRepository.save(new DiagnosticResult(null, baselineRunId, DiagnosticModule.DRIFT, ModuleExecutionStatus.COMPLETED, driftJson, Instant.now()));

        String perfJson = """
        {"summary": {"rocAuc": 0.82, "expectedCalibrationError": 0.05, "passed": true}}
        """;
        resultRepository.save(new DiagnosticResult(null, baselineRunId, DiagnosticModule.PERFORMANCE, ModuleExecutionStatus.COMPLETED, perfJson, Instant.now()));
        resultRepository.flush();

        List<DiagnosticRemediationDto> remediations = remediationService.analyzeAndPersist(baselineRunId);
        assertNotNull(remediations);
        // Only DRIFT-supported remediation can be produced
        for (DiagnosticRemediationDto rem : remediations) {
            assertTrue(rem.getRequiredModules().contains("DRIFT") || rem.getRequiredModules().contains("PERFORMANCE"));
        }
    }

    @Test
    public void testRunComparisonImprovedMetrics() {
        seedCompleteBaselineResults(baselineRunId);
        seedCandidateResults(candidateRunId);

        DiagnosticComparisonDto comparison = comparisonService.compareRuns(baselineRunId, candidateRunId);
        assertNotNull(comparison);
        assertEquals(baselineRunId, comparison.getBaselineRunId());
        assertEquals(candidateRunId, comparison.getCandidateRunId());

        assertNotNull(comparison.getMetricComparisons());
        assertFalse(comparison.getMetricComparisons().isEmpty());

        // Check F1 improved (+0.087)
        MetricComparisonDto f1Comp = comparison.getMetricComparisons().stream()
                .filter(m -> m.getMetricName().toLowerCase().contains("f1"))
                .findFirst()
                .orElse(null);
        assertNotNull(f1Comp);
        assertEquals("IMPROVED", f1Comp.getAssessment());
        assertTrue(f1Comp.getDelta() > 0.05);

        // Check ECE improved (decreased from 0.14 to 0.06 -> delta -0.08)
        MetricComparisonDto eceComp = comparison.getMetricComparisons().stream()
                .filter(m -> m.getMetricName().toLowerCase().contains("calibration"))
                .findFirst()
                .orElse(null);
        assertNotNull(eceComp);
        assertEquals("IMPROVED", eceComp.getAssessment());
        assertTrue(eceComp.getDelta() < -0.05);

        // Check overall assessment
        assertEquals("IMPROVED", comparison.getOverallAssessment());
        assertTrue(comparison.getImprovedMetricCount() > 0);
    }

    @Test
    public void testRunComparisonNoMaterialChange() {
        seedCompleteBaselineResults(baselineRunId);
        // Compare baseline against baseline
        DiagnosticComparisonDto comparison = comparisonService.compareRuns(baselineRunId, baselineRunId);
        assertNotNull(comparison);
        assertEquals("NO_MATERIAL_CHANGE", comparison.getOverallAssessment());
        assertEquals(0, comparison.getImprovedMetricCount());
        assertEquals(0, comparison.getRegressedMetricCount());
    }

    @Test
    public void testRunComparisonMissingMetricsRepresentation() {
        // Baseline has full results, candidate has only PERFORMANCE
        seedCompleteBaselineResults(baselineRunId);
        String perfJson = """
        {"summary": {"f1": 0.70, "expectedCalibrationError": 0.12}}
        """;
        resultRepository.save(new DiagnosticResult(null, candidateRunId, DiagnosticModule.PERFORMANCE, ModuleExecutionStatus.COMPLETED, perfJson, Instant.now()));
        resultRepository.flush();

        DiagnosticComparisonDto comparison = comparisonService.compareRuns(baselineRunId, candidateRunId);
        assertNotNull(comparison);
        assertNotNull(comparison.getMissingMetrics());
        assertFalse(comparison.getMissingMetrics().isEmpty(), "Metrics missing in candidate run must be explicitly listed");
    }
}
