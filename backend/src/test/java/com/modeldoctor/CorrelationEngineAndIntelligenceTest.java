package com.modeldoctor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeldoctor.domain.*;
import com.modeldoctor.dto.DiagnosticCorrelationDto;
import com.modeldoctor.dto.RunSummaryDto;
import com.modeldoctor.intelligence.normalization.NormalizedModuleData;
import com.modeldoctor.intelligence.normalization.ResultNormalizer;
import com.modeldoctor.intelligence.rules.*;
import com.modeldoctor.repository.DiagnosticCorrelationRepository;
import com.modeldoctor.repository.DiagnosticResultRepository;
import com.modeldoctor.repository.DiagnosticRunRepository;
import com.modeldoctor.service.CorrelationAnalysisService;
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
public class CorrelationEngineAndIntelligenceTest {

    @Autowired
    private DiagnosticRunRepository runRepository;

    @Autowired
    private DiagnosticResultRepository resultRepository;

    @Autowired
    private DiagnosticCorrelationRepository correlationRepository;

    @Autowired
    private CorrelationAnalysisService correlationService;

    @Autowired
    private ResultNormalizer normalizer;

    @Autowired
    private RuleRegistry ruleRegistry;

    @Autowired
    private ObjectMapper objectMapper;

    private String testRunId;

    @BeforeEach
    public void setup() {
        testRunId = "run_test_intelligence_" + UUID.randomUUID().toString().substring(0, 8);

        DiagnosticRun run = new DiagnosticRun();
        run.setId(testRunId);
        run.setModelName("test_model.json");
        run.setModelFramework("xgboost");
        run.setTaskType("binary_classification");
        run.setExecutionMode("REAL");
        run.setEvaluationDataset("test_eval.csv");
        run.setBaselineDataset("test_base.csv");
        run.setTargetColumn("is_fraud");
        run.setPredictionColumn("pred_prob");
        run.setProtectedAttribute("is_foreign_ip");
        run.setStatus(DiagnosticStatus.COMPLETED);
        run.setCreatedAt(Instant.now());
        run.setStartedAt(Instant.now().minusSeconds(10));
        run.setCompletedAt(Instant.now());
        run.setExecutionDurationMs(1200L);

        List<DiagnosticRunModule> modules = new ArrayList<>();
        DiagnosticModule[] coreMods = {
            DiagnosticModule.DATA_QUALITY, DiagnosticModule.LEAKAGE, DiagnosticModule.DRIFT,
            DiagnosticModule.PERFORMANCE, DiagnosticModule.EXPLAINABILITY, DiagnosticModule.BIAS,
            DiagnosticModule.ROBUSTNESS
        };
        for (DiagnosticModule mod : coreMods) {
            DiagnosticRunModule m = new DiagnosticRunModule();
            m.setRun(run);
            m.setModule(mod);
            m.setStatus(ModuleExecutionStatus.COMPLETED);
            m.setStartedAt(Instant.now().minusSeconds(10));
            m.setCompletedAt(Instant.now());
            modules.add(m);
        }
        run.setModules(modules);
        runRepository.saveAndFlush(run);
    }

    private void seedRichModuleResults(String runId) {
        // 1. DATA_QUALITY
        String dqJson = """
        {
            "summary": {"healthScore": 92.0, "nullCount": 45, "constantColumnCount": 0, "duplicateRowCount": 0, "passed": true},
            "columns": {
                "transaction_amount": {"dataType": "NUMERIC", "nullRate": 0.04, "outlierRate": 0.12, "constant": false, "distinctCount": 850},
                "num_failed_logins": {"dataType": "NUMERIC", "nullRate": 0.0, "outlierRate": 0.02, "constant": false, "distinctCount": 10},
                "is_foreign_ip": {"dataType": "NUMERIC", "nullRate": 0.0, "outlierRate": 0.0, "constant": false, "distinctCount": 2}
            }
        }
        """;
        resultRepository.save(new DiagnosticResult(null, runId, DiagnosticModule.DATA_QUALITY, ModuleExecutionStatus.COMPLETED, dqJson, Instant.now()));

        // 2. LEAKAGE
        String leakageJson = """
        {
            "summary": {"healthScore": 75.0, "maxMutualInfo": 0.38, "maxCorrelation": 0.52, "suspiciousFeatureCount": 1},
            "features": [
                {"feature": "transaction_amount", "mutualInfo": 0.38, "pearsonCorrelation": 0.52, "leakageScore": 0.85, "isSuspicious": true},
                {"feature": "num_failed_logins", "mutualInfo": 0.12, "pearsonCorrelation": 0.18, "leakageScore": 0.20, "isSuspicious": false}
            ]
        }
        """;
        resultRepository.save(new DiagnosticResult(null, runId, DiagnosticModule.LEAKAGE, ModuleExecutionStatus.COMPLETED, leakageJson, Instant.now()));

        // 3. DRIFT
        String driftJson = """
        {
            "summary": {"healthScore": 68.0, "maxPsi": 0.32, "driftedFeatureCount": 2, "passed": false},
            "features": [
                {"feature": "transaction_amount", "psi": 0.32, "ksPValue": 0.0002, "wasserstein": 0.18, "driftDetected": true, "severity": "HIGH"},
                {"feature": "is_foreign_ip", "psi": 0.15, "ksPValue": 0.004, "wasserstein": 0.05, "driftDetected": true, "severity": "MEDIUM"}
            ]
        }
        """;
        resultRepository.save(new DiagnosticResult(null, runId, DiagnosticModule.DRIFT, ModuleExecutionStatus.COMPLETED, driftJson, Instant.now()));

        // 4. PERFORMANCE
        String perfJson = """
        {
            "summary": {
                "healthScore": 72.0, "rocAuc": 0.78, "prAuc": 0.55, "f1": 0.42, "precision": 0.60, "recall": 0.32,
                "falseNegativeRate": 0.68, "expectedCalibrationError": 0.082, "logLoss": 0.44, "brierScore": 0.14, "passed": false
            }
        }
        """;
        resultRepository.save(new DiagnosticResult(null, runId, DiagnosticModule.PERFORMANCE, ModuleExecutionStatus.COMPLETED, perfJson, Instant.now()));

        // 5. EXPLAINABILITY
        String expJson = """
        {
            "summary": {"healthScore": 90.0, "topFeature": "transaction_amount", "topFeatureMeanAbsShap": 0.48, "top1AttributionShare": 0.42, "passed": true},
            "globalImportance": [
                {"feature": "transaction_amount", "rank": 1, "meanAbsShap": 0.48, "attributionShare": 0.42},
                {"feature": "num_failed_logins", "rank": 2, "meanAbsShap": 0.28, "attributionShare": 0.25},
                {"feature": "is_foreign_ip", "rank": 3, "meanAbsShap": 0.16, "attributionShare": 0.14}
            ]
        }
        """;
        resultRepository.save(new DiagnosticResult(null, runId, DiagnosticModule.EXPLAINABILITY, ModuleExecutionStatus.COMPLETED, expJson, Instant.now()));

        // 6. BIAS
        String biasJson = """
        {
            "summary": {
                "healthScore": 65.0, "protectedAttribute": "is_foreign_ip", "groupCount": 2,
                "worstDisparateImpactRatio": 0.72, "demographicParityGap": 0.11, "equalOpportunityGap": 0.08, "passed": false
            }
        }
        """;
        resultRepository.save(new DiagnosticResult(null, runId, DiagnosticModule.BIAS, ModuleExecutionStatus.COMPLETED, biasJson, Instant.now()));

        // 7. ROBUSTNESS
        String robJson = """
        {
            "summary": {"healthScore": 80.0, "topSensitiveFeature": "transaction_amount", "gaussianJitter5PctFlipRate": 0.045, "boundaryFlipRate": 0.28, "passed": true},
            "featureSensitivity": {
                "features": [
                    {"feature": "transaction_amount", "sensitivityRank": 1, "flipRate": 0.045, "meanProbabilityShift": 0.06},
                    {"feature": "num_failed_logins", "sensitivityRank": 2, "flipRate": 0.025, "meanProbabilityShift": 0.03}
                ]
            }
        }
        """;
        resultRepository.save(new DiagnosticResult(null, runId, DiagnosticModule.ROBUSTNESS, ModuleExecutionStatus.COMPLETED, robJson, Instant.now()));

        resultRepository.flush();
    }

    @Test
    public void testResultNormalization() {
        seedRichModuleResults(testRunId);
        List<DiagnosticResult> results = resultRepository.findByRunIdOrderByIdAsc(testRunId);
        NormalizedModuleData norm = normalizer.normalize(testRunId, results);

        assertEquals(testRunId, norm.getRunId());
        assertEquals(7, norm.getAvailableModules().size());
        assertTrue(norm.getDriftByFeature().containsKey("transaction_amount"));
        assertEquals(0.32, norm.getDriftByFeature().get("transaction_amount").psi, 0.001);
        assertEquals(1, norm.getImportanceByFeature().get("transaction_amount").rank);
        assertEquals(0.42, norm.getPerformanceSummary().f1, 0.001);
        assertEquals("is_foreign_ip", norm.getBiasSummary().protectedAttribute);
    }

    @Test
    public void testCrossModuleCorrelationEvaluation() {
        seedRichModuleResults(testRunId);
        List<DiagnosticCorrelationDto> correlations = correlationService.analyzeAndPersist(testRunId);

        assertNotNull(correlations);
        assertFalse(correlations.isEmpty());

        // Verify key rule evaluations
        List<String> ruleIds = correlations.stream().map(DiagnosticCorrelationDto::getRuleId).toList();
        assertTrue(ruleIds.contains("DRIFT_EXPLAINABILITY_INTERACTION"), "Must evaluate Drift-Explainability interaction");
        assertTrue(ruleIds.contains("DRIFT_PERFORMANCE_INTERACTION"), "Must evaluate Drift-Performance interaction");
        assertTrue(ruleIds.contains("LEAKAGE_EXPLAINABILITY_INTERACTION"), "Must evaluate Leakage-Explainability interaction");
        assertTrue(ruleIds.contains("BIAS_PERFORMANCE_INTERACTION"), "Must evaluate Bias-Performance interaction");
        assertTrue(ruleIds.contains("MULTI_DRIFT_PERFORMANCE_RISK"), "Must evaluate Multi-Module Drift-Performance risk");

        // Verify priority ordering
        for (int i = 0; i < correlations.size() - 1; i++) {
            assertTrue(correlations.get(i).getPriorityScore() >= correlations.get(i + 1).getPriorityScore(),
                    "Findings must be deterministically sorted by priority score descending");
        }

        // Verify non-causal disclaimer flag
        for (DiagnosticCorrelationDto c : correlations) {
            assertTrue(c.isAssociativeOnly(), "Every finding must be flagged as associative evidence");
            assertNotNull(c.getEvidence(), "Evidence map must be non-null");
            assertFalse(c.getSourceModules().isEmpty(), "Source modules list must be non-empty");
        }
    }

    @Test
    public void testIdempotentRecalculation() {
        seedRichModuleResults(testRunId);
        List<DiagnosticCorrelationDto> firstRun = correlationService.analyzeAndPersist(testRunId);
        int initialCount = firstRun.size();

        // Second analysis must not duplicate rows
        List<DiagnosticCorrelationDto> secondRun = correlationService.analyzeAndPersist(testRunId);
        assertEquals(initialCount, secondRun.size(), "Idempotent recalculation must not duplicate findings in DB");
        assertEquals(initialCount, correlationRepository.countByRunId(testRunId));
    }

    @Test
    public void testRunSummaryGeneration() {
        seedRichModuleResults(testRunId);
        RunSummaryDto summary = correlationService.getRunSummary(testRunId);

        assertNotNull(summary);
        assertEquals(testRunId, summary.getRunId());
        assertEquals(7, summary.getTotalModules());
        assertEquals(7, summary.getCompletedModules());
        assertEquals(0, summary.getFailedModules());
        assertTrue(summary.getCriticalFindingsCount() >= 1, "Must contain critical findings");
        assertTrue(summary.getHighPriorityFindingsCount() >= 1, "Must contain high priority findings");
        assertTrue(summary.getTopFeatures().contains("transaction_amount"), "transaction_amount must be among top features");
        assertNotNull(summary.getFeatureProfiles());
        assertTrue(summary.getFeatureProfiles().containsKey("transaction_amount"));
    }

    @Test
    public void testFailureIsolationOnMalformedResult() {
        // Seed only 2 modules, one valid and one malformed JSON
        resultRepository.save(new DiagnosticResult(null, testRunId, DiagnosticModule.DRIFT, ModuleExecutionStatus.COMPLETED,
                "{\"summary\": {\"maxPsi\": 0.35, \"healthScore\": 60.0}, \"features\": [{\"feature\": \"f1\", \"psi\": 0.35, \"driftDetected\": true}]}", Instant.now()));

        resultRepository.save(new DiagnosticResult(null, testRunId, DiagnosticModule.EXPLAINABILITY, ModuleExecutionStatus.COMPLETED,
                "{invalid_json_corrupted", Instant.now()));

        resultRepository.flush();

        // Must not crash; evaluates gracefully with whatever valid data exists
        List<DiagnosticCorrelationDto> correlations = correlationService.analyzeAndPersist(testRunId);
        assertNotNull(correlations);
    }
}
