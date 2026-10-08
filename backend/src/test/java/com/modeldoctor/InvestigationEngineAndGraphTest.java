package com.modeldoctor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeldoctor.domain.*;
import com.modeldoctor.dto.*;
import com.modeldoctor.repository.DiagnosticCorrelationRepository;
import com.modeldoctor.repository.DiagnosticInvestigationRepository;
import com.modeldoctor.repository.DiagnosticResultRepository;
import com.modeldoctor.repository.DiagnosticRunRepository;
import com.modeldoctor.service.CorrelationAnalysisService;
import com.modeldoctor.service.InvestigationAnalysisService;
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
public class InvestigationEngineAndGraphTest {

    @Autowired
    private DiagnosticRunRepository runRepository;

    @Autowired
    private DiagnosticResultRepository resultRepository;

    @Autowired
    private DiagnosticCorrelationRepository correlationRepository;

    @Autowired
    private DiagnosticInvestigationRepository investigationRepository;

    @Autowired
    private CorrelationAnalysisService correlationService;

    @Autowired
    private InvestigationAnalysisService investigationService;

    @Autowired
    private ObjectMapper objectMapper;

    private String testRunId;

    @BeforeEach
    public void setup() {
        testRunId = "run_test_phase6_" + UUID.randomUUID().toString().substring(0, 8);

        DiagnosticRun run = new DiagnosticRun();
        run.setId(testRunId);
        run.setModelName("test_fraud_model_v6.json");
        run.setModelFramework("xgboost");
        run.setTaskType("binary_classification");
        run.setExecutionMode("REAL");
        run.setEvaluationDataset("test_eval_v6.csv");
        run.setBaselineDataset("test_base_v6.csv");
        run.setTargetColumn("is_fraud");
        run.setPredictionColumn("pred_prob");
        run.setProtectedAttribute("is_foreign_ip");
        run.setStatus(DiagnosticStatus.COMPLETED);
        run.setCreatedAt(Instant.now());
        run.setStartedAt(Instant.now().minusSeconds(15));
        run.setCompletedAt(Instant.now());
        run.setExecutionDurationMs(1800L);

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

    private void seedCompleteDiagnosticResults(String runId) {
        // 1. DATA QUALITY
        String dqJson = """
        {
            "summary": {"healthScore": 90.0, "rowCount": 2000, "columnCount": 6, "nullCount": 12, "passed": true},
            "columns": {
                "transaction_amount": {"nullRate": 0.02, "outlierRate": 0.08, "constant": false, "distinctCount": 1800},
                "num_failed_logins": {"nullRate": 0.0, "outlierRate": 0.01, "constant": false, "distinctCount": 8},
                "is_foreign_ip": {"nullRate": 0.0, "outlierRate": 0.0, "constant": false, "distinctCount": 2}
            }
        }
        """;
        resultRepository.save(new DiagnosticResult(null, runId, DiagnosticModule.DATA_QUALITY, ModuleExecutionStatus.COMPLETED, dqJson, Instant.now()));

        // 2. LEAKAGE
        String leakageJson = """
        {
            "summary": {"healthScore": 82.0, "maxMutualInfo": 0.22, "suspiciousFeatureCount": 0},
            "features": [
                {"feature": "transaction_amount", "mutualInfo": 0.18, "pearsonCorrelation": 0.32, "leakageScore": 0.35, "isSuspicious": false},
                {"feature": "num_failed_logins", "mutualInfo": 0.08, "pearsonCorrelation": 0.12, "leakageScore": 0.10, "isSuspicious": false}
            ]
        }
        """;
        resultRepository.save(new DiagnosticResult(null, runId, DiagnosticModule.LEAKAGE, ModuleExecutionStatus.COMPLETED, leakageJson, Instant.now()));

        // 3. DRIFT
        String driftJson = """
        {
            "summary": {"healthScore": 62.0, "maxPsi": 0.38, "driftedFeatureCount": 1, "passed": false},
            "columns": {
                "transaction_amount": {"psi": 0.38, "ksPValue": 0.0001, "wasserstein": 0.24, "driftDetected": true, "driftSeverity": "CRITICAL", "method": "psi"}
            }
        }
        """;
        resultRepository.save(new DiagnosticResult(null, runId, DiagnosticModule.DRIFT, ModuleExecutionStatus.COMPLETED, driftJson, Instant.now()));

        // 4. PERFORMANCE
        String perfJson = """
        {
            "summary": {
                "healthScore": 70.0, "rocAuc": 0.86, "prAuc": 0.62, "f1": 0.68, "precision": 0.72, "recall": 0.64,
                "falseNegativeRate": 0.36, "falsePositiveRate": 0.04, "expectedCalibrationError": 0.145, "logLoss": 0.38, "brierScore": 0.12, "passed": false
            },
            "confusionMatrix": {"truePositives": 64, "trueNegatives": 850, "falsePositives": 35, "falseNegatives": 36}
        }
        """;
        resultRepository.save(new DiagnosticResult(null, runId, DiagnosticModule.PERFORMANCE, ModuleExecutionStatus.COMPLETED, perfJson, Instant.now()));

        // 5. EXPLAINABILITY
        String explJson = """
        {
            "summary": {"healthScore": 88.0, "topFeature": "transaction_amount", "topFeatureImportance": 0.52, "top1AttributionShare": 0.46, "passed": true},
            "features": [
                {"feature": "transaction_amount", "meanAbsoluteShap": 0.52, "rank": 1, "attributionShare": 0.46},
                {"feature": "num_failed_logins", "meanAbsoluteShap": 0.24, "rank": 2, "attributionShare": 0.22}
            ]
        }
        """;
        resultRepository.save(new DiagnosticResult(null, runId, DiagnosticModule.EXPLAINABILITY, ModuleExecutionStatus.COMPLETED, explJson, Instant.now()));

        // 6. BIAS
        String biasJson = """
        {
            "summary": {
                "healthScore": 60.0, "protectedAttribute": "is_foreign_ip", "groupCount": 2,
                "worstDisparateImpactRatio": 0.62, "demographicParityGap": 0.14, "equalOpportunityGap": 0.10, "passed": false
            },
            "subgroups": [
                {"groupValue": "domestic", "selectionRate": 0.08, "truePositiveRate": 0.88, "falsePositiveRate": 0.03},
                {"groupValue": "foreign", "selectionRate": 0.24, "truePositiveRate": 0.48, "falsePositiveRate": 0.16}
            ]
        }
        """;
        resultRepository.save(new DiagnosticResult(null, runId, DiagnosticModule.BIAS, ModuleExecutionStatus.COMPLETED, biasJson, Instant.now()));

        // 7. ROBUSTNESS
        String robJson = """
        {
            "summary": {"healthScore": 72.0, "topSensitiveFeature": "transaction_amount", "gaussianJitter5PctFlipRate": 0.16, "boundaryFlipRate": 0.32, "passed": false},
            "featureSensitivities": [
                {"feature": "transaction_amount", "sensitivityRank": 1, "predictionFlipRate": 0.16, "meanProbabilityShift": 0.12},
                {"feature": "num_failed_logins", "sensitivityRank": 2, "predictionFlipRate": 0.04, "meanProbabilityShift": 0.03}
            ]
        }
        """;
        resultRepository.save(new DiagnosticResult(null, runId, DiagnosticModule.ROBUSTNESS, ModuleExecutionStatus.COMPLETED, robJson, Instant.now()));

        // 8. ERROR FORENSICS
        String errJson = """
        {
            "schemaVersion": 1,
            "module": "ERROR_FORENSICS",
            "sampleCount": 985,
            "errorSummary": {
                "totalRecords": 985,
                "totalErrors": 71,
                "errorRate": 0.0721,
                "truePositive": {"count": 64, "rate": 0.065},
                "trueNegative": {"count": 850, "rate": 0.863},
                "falsePositive": {"count": 35, "rate": 0.0355},
                "falseNegative": {"count": 36, "rate": 0.0365}
            },
            "confidenceAnalysis": {
                "meanIncorrectConfidence": 0.82,
                "medianIncorrectConfidence": 0.85,
                "highConfidenceErrorCount": 24,
                "highConfidenceErrorRate": 0.338,
                "highConfidenceErrorShare": 0.0243
            },
            "featureAssociations": [
                {
                    "feature": "transaction_amount",
                    "featureType": "numeric",
                    "statisticName": "point_biserial_r",
                    "statistic": 0.412,
                    "effectSize": 0.412,
                    "pValue": 0.00001,
                    "adjustedPValue": 0.0001,
                    "direction": "positive",
                    "rank": 1
                }
            ],
            "falsePositiveAnalysis": {
                "fpCount": 35,
                "topSeparations": [
                    {"feature": "transaction_amount", "standardizedMeanDifference": 1.08, "adjustedPValue": 0.0001}
                ]
            },
            "subgroupAnalysis": [
                {
                    "group": "foreign",
                    "sampleCount": 200,
                    "errorCount": 42,
                    "errorRate": 0.210,
                    "falsePositiveRate": 0.16,
                    "falseNegativeRate": 0.26,
                    "highConfidenceErrorRate": 0.10,
                    "disparityRatio": 2.45
                },
                {
                    "group": "domestic",
                    "sampleCount": 785,
                    "errorCount": 29,
                    "errorRate": 0.0369,
                    "falsePositiveRate": 0.02,
                    "falseNegativeRate": 0.05,
                    "highConfidenceErrorRate": 0.01,
                    "disparityRatio": 1.0
                }
            ],
            "calibrationForensics": {
                "expectedCalibrationError": 0.145,
                "bins": [
                    {"binIndex": 8, "lowerBound": 0.8, "upperBound": 0.9, "sampleCount": 120, "isHighError": true, "isSevereGap": true}
                ]
            }
        }
        """;
        resultRepository.save(new DiagnosticResult(null, runId, DiagnosticModule.ERROR_FORENSICS, ModuleExecutionStatus.COMPLETED, errJson, Instant.now()));

        resultRepository.flush();
    }

    @Test
    public void testInvestigationTargetGenerationAndRanking() {
        seedCompleteDiagnosticResults(testRunId);

        List<InvestigationTargetDto> targets = investigationService.analyzeAndPersist(testRunId);
        assertNotNull(targets);
        assertFalse(targets.isEmpty(), "Must generate ranked investigation targets from real diagnostic evidence");

        // Verify top target is transaction_amount due to multi-module convergence (DRIFT + EXPLAINABILITY + ERROR_FORENSICS + ROBUSTNESS)
        InvestigationTargetDto topTarget = targets.get(0);
        assertEquals("FEATURE", topTarget.getTargetType());
        assertEquals("FEATURE::transaction_amount", topTarget.getTargetKey());
        assertEquals("transaction_amount", topTarget.getDisplayName());
        assertEquals("CRITICAL", topTarget.getPriority());
        assertTrue(topTarget.getPriorityScore() >= 85.0, "Top converging feature must have critical priority score >= 85");
        assertTrue(topTarget.getSupportingModuleCount() >= 4, "transaction_amount must be supported by at least 4 independent modules");

        // Verify deterministic descending sort
        for (int i = 0; i < targets.size() - 1; i++) {
            assertTrue(targets.get(i).getPriorityScore() >= targets.get(i + 1).getPriorityScore(),
                    "Investigation targets must be sorted in deterministic descending order by priority score");
        }
    }

    @Test
    public void testEvidenceDeduplicationAndModuleIndependence() {
        seedCompleteDiagnosticResults(testRunId);

        List<InvestigationTargetDto> targets = investigationService.analyzeAndPersist(testRunId);
        InvestigationTargetDto topTarget = targets.stream()
                .filter(t -> t.getTargetKey().equals("FEATURE::transaction_amount"))
                .findFirst()
                .orElseThrow();

        // 4 distinct modules: DRIFT, EXPLAINABILITY, ROBUSTNESS, ERROR_FORENSICS
        List<String> modules = topTarget.getSupportingModules();
        Set<String> distinctModules = new HashSet<>(modules);
        assertEquals(distinctModules.size(), modules.size(), "Supporting module list must contain deduplicated distinct modules");
        assertTrue(distinctModules.contains("DRIFT"));
        assertTrue(distinctModules.contains("EXPLAINABILITY"));
        assertTrue(distinctModules.contains("ERROR_FORENSICS"));
        assertTrue(distinctModules.contains("ROBUSTNESS"));
    }

    @Test
    public void testHypothesisAndNextActionsAreEvidenceDrivenAndNonCausal() {
        seedCompleteDiagnosticResults(testRunId);

        List<InvestigationTargetDto> targets = investigationService.analyzeAndPersist(testRunId);
        InvestigationTargetDto topTarget = targets.get(0);

        // Verify hypothesis does NOT use forbidden causal words
        String hyp = topTarget.getHypothesis().toLowerCase();
        assertFalse(hyp.contains("causes"), "Hypothesis must not claim causality");
        assertFalse(hyp.contains("caused"), "Hypothesis must not claim causality");
        assertFalse(hyp.contains("proves"), "Hypothesis must not claim causality");
        assertFalse(hyp.contains("responsible for"), "Hypothesis must not claim causality");

        // Verify hypothesis contains real evidence metrics
        assertTrue(topTarget.getHypothesis().contains("transaction_amount"));
        assertTrue(topTarget.getHypothesis().contains("PSI") || topTarget.getHypothesis().contains("shift"));

        // Verify next actions exist and are evidence-driven
        assertNotNull(topTarget.getNextActions());
        assertFalse(topTarget.getNextActions().isEmpty());
        assertTrue(topTarget.getNextActions().stream().anyMatch(a -> a.contains("transaction_amount")));
        assertTrue(topTarget.isAssociativeOnly(), "Must be explicitly flagged as associative only");
    }

    @Test
    public void testEvidenceGraphGeneration() {
        seedCompleteDiagnosticResults(testRunId);

        EvidenceGraphDto graph = investigationService.getEvidenceGraph(testRunId);
        assertNotNull(graph);
        assertEquals(testRunId, graph.getRunId());
        assertTrue(graph.getNodeCount() > 0, "Graph must contain nodes");
        assertTrue(graph.getEdgeCount() > 0, "Graph must contain edges");
        assertTrue(graph.isAssociativeOnly(), "Graph must be marked associativeOnly");
        assertNotNull(graph.getCausalityDisclaimer(), "Graph must include causality disclaimer");

        // Verify nodes have proper categorization
        Set<String> nodeTypes = new HashSet<>();
        for (EvidenceGraphNodeDto node : graph.getNodes()) {
            nodeTypes.add(node.getNodeType());
            assertNotNull(node.getId());
            assertNotNull(node.getLabel());
        }
        assertTrue(nodeTypes.contains("MODULE"));
        assertTrue(nodeTypes.contains("FEATURE"));

        // Verify edges have non-causal relationships and sourceResultIds
        for (EvidenceGraphEdgeDto edge : graph.getEdges()) {
            assertNotNull(edge.getId());
            assertNotNull(edge.getSource());
            assertNotNull(edge.getTarget());
            assertNotNull(edge.getRelationship());
            assertFalse(edge.getRelationship().equalsIgnoreCase("CAUSES"), "Graph edge must not be CAUSES");
            assertFalse(edge.getRelationship().equalsIgnoreCase("PROVES"), "Graph edge must not be PROVES");
            assertTrue(edge.isAssociativeOnly());
        }
    }

    @Test
    public void testInvestigationDossierRetrievalAndProvenance() {
        seedCompleteDiagnosticResults(testRunId);

        InvestigationDossierDto dossier = investigationService.getInvestigationDossier(testRunId, "FEATURE::transaction_amount");
        assertNotNull(dossier);
        assertEquals("FEATURE::transaction_amount", dossier.getTarget().getTargetKey());
        assertEquals("CRITICAL", dossier.getPriorityLevel());
        assertNotNull(dossier.getHypothesis());
        assertNotNull(dossier.getSupportingModules());
        assertNotNull(dossier.getSupportingFindings());
        assertNotNull(dossier.getInvestigationPath());
        assertFalse(dossier.getInvestigationPath().isEmpty(), "Investigation path must contain ordered evidence steps");

        // Verify step numbers are ordered
        for (int i = 0; i < dossier.getInvestigationPath().size(); i++) {
            assertEquals(i + 1, dossier.getInvestigationPath().get(i).getStepNumber());
        }

        // Verify provenance traceability
        assertNotNull(dossier.getProvenance());
        assertEquals(testRunId, dossier.getProvenance().getRunId());
        assertNotNull(dossier.getProvenance().getSourceResultIds());
        assertFalse(dossier.getProvenance().getSourceResultIds().isEmpty(), "Provenance must expose real result IDs");
    }

    @Test
    public void testIdempotentRecalculationNoDuplicateInvestigations() {
        seedCompleteDiagnosticResults(testRunId);

        List<InvestigationTargetDto> firstRun = investigationService.analyzeAndPersist(testRunId);
        int count1 = firstRun.size();

        List<InvestigationTargetDto> secondRun = investigationService.analyzeAndPersist(testRunId);
        int count2 = secondRun.size();

        assertEquals(count1, count2, "Recalculation must be idempotent without creating duplicate records");
        assertEquals(count1, investigationRepository.countByRunId(testRunId));
    }

    @Test
    public void testPartialRunGracefulBehavior() {
        // Only seed 2 modules: DRIFT and PERFORMANCE
        String driftJson = """
        {"summary": {"maxPsi": 0.32, "healthScore": 70.0}, "columns": {"amount": {"psi": 0.32, "driftDetected": true, "driftSeverity": "HIGH"}}}
        """;
        resultRepository.save(new DiagnosticResult(null, testRunId, DiagnosticModule.DRIFT, ModuleExecutionStatus.COMPLETED, driftJson, Instant.now()));

        String perfJson = """
        {"summary": {"rocAuc": 0.82, "healthScore": 80.0, "expectedCalibrationError": 0.05}}
        """;
        resultRepository.save(new DiagnosticResult(null, testRunId, DiagnosticModule.PERFORMANCE, ModuleExecutionStatus.COMPLETED, perfJson, Instant.now()));
        resultRepository.flush();

        List<InvestigationTargetDto> targets = investigationService.analyzeAndPersist(testRunId);
        assertNotNull(targets);
        assertFalse(targets.isEmpty());

        EvidenceGraphDto graph = investigationService.getEvidenceGraph(testRunId);
        assertNotNull(graph);
        assertEquals(testRunId, graph.getRunId());
    }

    @Test
    public void testInsufficientEvidenceBehavior() {
        // Empty results
        List<InvestigationTargetDto> targets = investigationService.analyzeAndPersist(testRunId);
        assertNotNull(targets);
        assertTrue(targets.isEmpty(), "Empty results must produce zero investigation targets gracefully without error");

        EvidenceGraphDto graph = investigationService.getEvidenceGraph(testRunId);
        assertNotNull(graph);
        assertEquals(0, graph.getNodeCount());
        assertEquals(0, graph.getEdgeCount());
    }
}
