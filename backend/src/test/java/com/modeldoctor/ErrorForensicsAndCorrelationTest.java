package com.modeldoctor;

import com.modeldoctor.domain.*;
import com.modeldoctor.dto.DiagnosticCorrelationDto;
import com.modeldoctor.dto.RunSummaryDto;
import com.modeldoctor.intelligence.normalization.NormalizedModuleData;
import com.modeldoctor.intelligence.normalization.ResultNormalizer;
import com.modeldoctor.intelligence.rules.RuleRegistry;
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
public class ErrorForensicsAndCorrelationTest {

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

    private String testRunId;

    @BeforeEach
    public void setup() {
        testRunId = "run_test_error_forensics_" + UUID.randomUUID().toString().substring(0, 8);

        DiagnosticRun run = new DiagnosticRun();
        run.setId(testRunId);
        run.setModelName("test_fraud_model.json");
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
            m.setStartedAt(Instant.now().minusSeconds(10));
            m.setCompletedAt(Instant.now());
            modules.add(m);
        }
        run.setModules(modules);
        runRepository.saveAndFlush(run);
    }

    private void seedDiagnosticResults(String runId) {
        // DRIFT
        String driftJson = """
        {
            "summary": {"overallDriftDetected": true, "maxPsi": 0.35, "driftedColumnCount": 1},
            "columns": {
                "transaction_amount": {"psi": 0.35, "driftDetected": true, "driftSeverity": "CRITICAL", "method": "psi"}
            }
        }
        """;
        resultRepository.save(new DiagnosticResult(null, runId, DiagnosticModule.DRIFT, ModuleExecutionStatus.COMPLETED, driftJson, Instant.now()));

        // PERFORMANCE
        String perfJson = """
        {
            "summary": {"accuracy": 0.88, "precision": 0.75, "recall": 0.70, "f1": 0.724, "rocAuc": 0.89, "logLoss": 0.32, "brierScore": 0.10, "expectedCalibrationError": 0.22},
            "confusionMatrix": {"truePositives": 70, "trueNegatives": 810, "falsePositives": 25, "falseNegatives": 30}
        }
        """;
        resultRepository.save(new DiagnosticResult(null, runId, DiagnosticModule.PERFORMANCE, ModuleExecutionStatus.COMPLETED, perfJson, Instant.now()));

        // EXPLAINABILITY
        String explJson = """
        {
            "summary": {"topFeature": "transaction_amount", "topFeatureImportance": 0.48},
            "features": [
                {"feature": "transaction_amount", "meanAbsoluteShap": 0.48, "rank": 1},
                {"feature": "num_failed_logins", "meanAbsoluteShap": 0.22, "rank": 2}
            ]
        }
        """;
        resultRepository.save(new DiagnosticResult(null, runId, DiagnosticModule.EXPLAINABILITY, ModuleExecutionStatus.COMPLETED, explJson, Instant.now()));

        // BIAS
        String biasJson = """
        {
            "summary": {"protectedAttribute": "is_foreign_ip", "biasDetected": true, "maxDisparity": 2.2, "disparateImpactRatio": 0.45},
            "subgroups": [
                {"groupValue": "domestic", "selectionRate": 0.10, "truePositiveRate": 0.85, "falsePositiveRate": 0.05},
                {"groupValue": "foreign", "selectionRate": 0.22, "truePositiveRate": 0.50, "falsePositiveRate": 0.15}
            ]
        }
        """;
        resultRepository.save(new DiagnosticResult(null, runId, DiagnosticModule.BIAS, ModuleExecutionStatus.COMPLETED, biasJson, Instant.now()));

        // ROBUSTNESS
        String robJson = """
        {
            "summary": {"vulnerabilityScore": 0.65, "mostSensitiveFeature": "transaction_amount"},
            "featureSensitivities": [
                {"feature": "transaction_amount", "sensitivityRank": 1, "predictionFlipRate": 0.28, "vulnerabilityLevel": "HIGH"}
            ]
        }
        """;
        resultRepository.save(new DiagnosticResult(null, runId, DiagnosticModule.ROBUSTNESS, ModuleExecutionStatus.COMPLETED, robJson, Instant.now()));

        // ERROR_FORENSICS
        String errJson = """
        {
            "schemaVersion": 1,
            "module": "ERROR_FORENSICS",
            "sampleCount": 935,
            "errorSummary": {
                "totalRecords": 935,
                "totalErrors": 55,
                "errorRate": 0.0588,
                "truePositive": {"count": 70, "rate": 0.0749},
                "trueNegative": {"count": 810, "rate": 0.8663},
                "falsePositive": {"count": 25, "rate": 0.0267},
                "falseNegative": {"count": 30, "rate": 0.0321}
            },
            "confidenceAnalysis": {
                "meanIncorrectConfidence": 0.78,
                "medianIncorrectConfidence": 0.82,
                "highConfidenceErrorCount": 18,
                "highConfidenceErrorRate": 0.327,
                "highConfidenceErrorShare": 0.0192
            },
            "featureAssociations": [
                {
                    "feature": "transaction_amount",
                    "featureType": "numeric",
                    "statisticName": "point_biserial_r",
                    "statistic": 0.385,
                    "effectSize": 0.385,
                    "pValue": 0.0001,
                    "adjustedPValue": 0.0005,
                    "direction": "positive",
                    "rank": 1
                },
                {
                    "feature": "num_failed_logins",
                    "featureType": "numeric",
                    "statisticName": "point_biserial_r",
                    "statistic": 0.12,
                    "effectSize": 0.12,
                    "pValue": 0.04,
                    "adjustedPValue": 0.08,
                    "direction": "positive",
                    "rank": 2
                }
            ],
            "falsePositiveAnalysis": {
                "fpCount": 25,
                "topSeparations": [
                    {"feature": "transaction_amount", "standardizedMeanDifference": 0.95, "adjustedPValue": 0.0002}
                ]
            },
            "falseNegativeAnalysis": {
                "fnCount": 30,
                "topSeparations": [
                    {"feature": "transaction_amount", "standardizedMeanDifference": -0.85, "adjustedPValue": 0.0004}
                ]
            },
            "subgroupAnalysis": [
                {
                    "group": "foreign",
                    "sampleCount": 150,
                    "errorCount": 25,
                    "errorRate": 0.1667,
                    "falsePositiveRate": 0.15,
                    "falseNegativeRate": 0.20,
                    "highConfidenceErrorRate": 0.08
                },
                {
                    "group": "domestic",
                    "sampleCount": 785,
                    "errorCount": 30,
                    "errorRate": 0.0382,
                    "falsePositiveRate": 0.02,
                    "falseNegativeRate": 0.04,
                    "highConfidenceErrorRate": 0.01
                }
            ],
            "highConfidenceErrors": [
                {
                    "stableRowIndex": 42,
                    "actualClass": 0,
                    "predictedClass": 1,
                    "predictedProbability": 0.94,
                    "confidence": 0.94,
                    "errorType": "FALSE_POSITIVE",
                    "forensicPriority": 0.96
                }
            ],
            "findings": [
                {
                    "findingId": "ERR_HIGH_CONF_CONCENTRATION",
                    "title": "High-Confidence Error Concentration",
                    "severity": "HIGH",
                    "evidence": {"highConfidenceErrorRate": 0.327}
                }
            ]
        }
        """;
        resultRepository.save(new DiagnosticResult(null, runId, DiagnosticModule.ERROR_FORENSICS, ModuleExecutionStatus.COMPLETED, errJson, Instant.now()));
        resultRepository.flush();
    }

    @Test
    public void testRuleRegistryIncludesPhase5Rules() {
        assertEquals(17, ruleRegistry.getRules().size(), "RuleRegistry should contain all 12 Phase 4 rules + 5 Phase 5 rules");
    }

    @Test
    public void testNormalizeErrorForensics() {
        seedDiagnosticResults(testRunId);
        List<DiagnosticResult> results = resultRepository.findByRunIdOrderByIdAsc(testRunId);

        NormalizedModuleData normalized = normalizer.normalize(testRunId, results);

        assertNotNull(normalized.getErrorForensicsSummary());
        assertEquals(55, normalized.getErrorForensicsSummary().totalErrors);
        assertEquals(25, normalized.getErrorForensicsSummary().falsePositiveCount);
        assertEquals(30, normalized.getErrorForensicsSummary().falseNegativeCount);
        assertEquals(0.327, normalized.getErrorForensicsSummary().highConfidenceErrorRate, 0.001);

        assertTrue(normalized.getErrorByFeature().containsKey("transaction_amount"));
        var errFeat = normalized.getErrorByFeature().get("transaction_amount");
        assertEquals(0.385, errFeat.correlation, 0.001);
        assertEquals(0.385, errFeat.absoluteAssociation, 0.001);

        assertTrue(normalized.getErrorForensicsSummary().subgroupErrorRates.containsKey("foreign"));
        assertEquals(0.1667, normalized.getErrorForensicsSummary().subgroupErrorRates.get("foreign"), 0.001);
    }

    @Test
    public void testCrossModuleErrorForensicRulesEvaluation() {
        seedDiagnosticResults(testRunId);

        List<DiagnosticCorrelationDto> correlations = correlationService.analyzeAndPersist(testRunId);
        assertNotNull(correlations);
        assertFalse(correlations.isEmpty());

        Set<String> ruleIds = new HashSet<>();
        for (DiagnosticCorrelationDto c : correlations) {
            ruleIds.add(c.getRuleId());
            assertTrue(c.isAssociativeOnly(), "All findings must be marked associativeOnly");
            assertNotNull(c.getSourceResultIds(), "Source result IDs must be traceable");
            assertFalse(c.getSourceResultIds().isEmpty(), "Source result IDs must not be empty");
        }

        // Check Phase 5 Interaction Rules triggered
        assertTrue(ruleIds.contains("ERROR_DRIFT_INTERACTION"), "Should trigger ERROR_DRIFT_INTERACTION for transaction_amount");
        assertTrue(ruleIds.contains("ERROR_EXPLAINABILITY_INTERACTION"), "Should trigger ERROR_EXPLAINABILITY_INTERACTION for transaction_amount");
        assertTrue(ruleIds.contains("ERROR_ROBUSTNESS_INTERACTION"), "Should trigger ERROR_ROBUSTNESS_INTERACTION for transaction_amount");
        assertTrue(ruleIds.contains("ERROR_BIAS_INTERACTION"), "Should trigger ERROR_BIAS_INTERACTION for foreign subgroup");
        assertTrue(ruleIds.contains("CONFIDENCE_CALIBRATION_ERROR"), "Should trigger CONFIDENCE_CALIBRATION_ERROR due to high confidence errors + ECE");
    }

    @Test
    public void testRunSummaryFeatureProfilesContainErrorData() {
        seedDiagnosticResults(testRunId);

        RunSummaryDto summary = correlationService.getRunSummary(testRunId);
        assertNotNull(summary);
        assertNotNull(summary.getFeatureProfiles());

        Map<String, Object> transProfile = summary.getFeatureProfiles().get("transaction_amount");
        assertNotNull(transProfile);
        assertTrue(transProfile.containsKey("errorCorrelation"));
        assertEquals(0.385, (Double) transProfile.get("errorCorrelation"), 0.001);
        assertTrue(transProfile.containsKey("errorFpSeparation"));
        assertTrue(transProfile.containsKey("driftPsi"));
        assertTrue(transProfile.containsKey("importanceRank"));
    }

    @Test
    public void testIdempotentRecalculation() {
        seedDiagnosticResults(testRunId);

        List<DiagnosticCorrelationDto> firstRun = correlationService.analyzeAndPersist(testRunId);
        int count1 = firstRun.size();

        List<DiagnosticCorrelationDto> secondRun = correlationService.analyzeAndPersist(testRunId);
        int count2 = secondRun.size();

        assertEquals(count1, count2, "Repeated execution must produce identical finding counts");
        assertEquals(count1, correlationRepository.findByRunIdOrderByPriorityScoreDesc(testRunId).size());
    }
}
