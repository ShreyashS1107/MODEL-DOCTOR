package com.modeldoctor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeldoctor.domain.*;
import com.modeldoctor.dto.*;
import com.modeldoctor.intelligence.experiment.DiagnosticExperimentStrategy;
import com.modeldoctor.intelligence.experiment.ExperimentStrategyRegistry;
import com.modeldoctor.intelligence.experiment.PrerequisiteValidationResult;
import com.modeldoctor.repository.DiagnosticExperimentRepository;
import com.modeldoctor.repository.DiagnosticRemediationRepository;
import com.modeldoctor.repository.DiagnosticResultRepository;
import com.modeldoctor.repository.DiagnosticRunRepository;
import com.modeldoctor.service.AcceptanceEvaluatorService;
import com.modeldoctor.service.ExperimentOrchestrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileWriter;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
public class ExperimentEngineAndValidationTest {

    @Autowired
    private ExperimentOrchestrationService experimentService;

    @Autowired
    private ExperimentStrategyRegistry strategyRegistry;

    @Autowired
    private AcceptanceEvaluatorService acceptanceEvaluatorService;

    @Autowired
    private DiagnosticRunRepository runRepository;

    @Autowired
    private DiagnosticResultRepository resultRepository;

    @Autowired
    private DiagnosticRemediationRepository remediationRepository;

    @Autowired
    private DiagnosticExperimentRepository experimentRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private String baselineRunId;
    private Long remediationId;
    private String tempEvalDataset;

    @BeforeEach
    void setUp() throws Exception {
        baselineRunId = "run_test_base_" + UUID.randomUUID().toString().substring(0, 8);

        // Create temporary dataset file
        File tempFile = File.createTempFile("eval_dataset_", ".csv");
        tempFile.deleteOnExit();
        try (FileWriter fw = new FileWriter(tempFile)) {
            fw.write("customer_id,income,score,drifted_feature,target,prediction,protected_region\n");
            for (int i = 0; i < 50; i++) {
                fw.write(String.format("c_%d,%d,%.2f,%.2f,%d,%.2f,%s\n",
                        i, 30000 + i * 500, 0.5 + (i % 5) * 0.1, (i % 2 == 0 ? 10.0 : 1.0),
                        i % 2, 0.45 + (i % 2) * 0.3, (i % 3 == 0 ? "A" : "B")));
            }
        }
        tempEvalDataset = tempFile.getAbsolutePath();

        DiagnosticRun baseRun = DiagnosticRun.builder()
                .id(baselineRunId)
                .status(DiagnosticStatus.COMPLETED)
                .executionMode("TEST")
                .modelName("fraud_classifier_v17")
                .modelFramework("xgboost")
                .taskType("binary_classification")
                .evaluationDataset(tempEvalDataset)
                .baselineDataset(tempEvalDataset)
                .targetColumn("target")
                .predictionColumn("prediction")
                .protectedAttribute("protected_region")
                .createdAt(Instant.now())
                .modules(List.of(
                        new DiagnosticRunModule(DiagnosticModule.PERFORMANCE, ModuleExecutionStatus.COMPLETED),
                        new DiagnosticRunModule(DiagnosticModule.ERROR_FORENSICS, ModuleExecutionStatus.COMPLETED),
                        new DiagnosticRunModule(DiagnosticModule.BIAS, ModuleExecutionStatus.COMPLETED)
                ))
                .build();
        runRepository.save(baseRun);

        // Save raw mock performance payload
        Map<String, Object> perfPayload = Map.of(
                "metrics", Map.of(
                        "accuracy", 0.82,
                        "precision", 0.78,
                        "recall", 0.74,
                        "f1", 0.76,
                        "fpr", 0.12,
                        "fnr", 0.26,
                        "ece", 0.14,
                        "brierScore", 0.19
                )
        );
        DiagnosticResult perfRes = new DiagnosticResult(
                null,
                baselineRunId,
                DiagnosticModule.PERFORMANCE,
                ModuleExecutionStatus.COMPLETED,
                objectMapper.writeValueAsString(perfPayload),
                Instant.now()
        );
        resultRepository.save(perfRes);

        // Save mock bias payload
        Map<String, Object> biasPayload = Map.of(
                "metrics", Map.of(
                        "disparateImpact", 0.72,
                        "tprGap", 0.14,
                        "fprGap", 0.08
                )
        );
        DiagnosticResult biasRes = new DiagnosticResult(
                null,
                baselineRunId,
                DiagnosticModule.BIAS,
                ModuleExecutionStatus.COMPLETED,
                objectMapper.writeValueAsString(biasPayload),
                Instant.now()
        );
        resultRepository.save(biasRes);

        // Save mock remediation
        DiagnosticRemediation rem = new DiagnosticRemediation();
        rem.setRunId(baselineRunId);
        rem.setTargetType("FEATURE");
        rem.setTargetKey("FEATURE::drifted_feature");
        rem.setRemediationType(RemediationType.DISTRIBUTION_SHIFT_INVESTIGATION);
        rem.setTitle("Investigate Feature Drift on drifted_feature");
        rem.setDescription("Empirical shift detected.");
        rem.setPriority(InvestigationPriority.CRITICAL);
        rem.setPriorityScore(95.0);
        rem.setConfidence(EvidenceConfidence.HIGH);
        rem.setEvidenceStrength("STRONG");
        rem.setHypothesis("Remediating feature shift will improve calibration and stability.");
        rem.setExpectedEffect("PSI decreases below warning threshold");
        rem.setValidationStrategy("Re-run DRIFT, PERFORMANCE, ERROR_FORENSICS");
        rem.setAcceptanceCriteriaJson("[\"PSI decreases below 0.10 warning threshold\",\"F1 does not regress by > 0.01\"]");
        rem.setRegressionGuardsJson("[\"Accuracy does not drop by > 0.02\"]");
        rem.setRequiredModulesJson("[\"DRIFT\",\"PERFORMANCE\",\"ERROR_FORENSICS\"]");
        rem.setStatus(RemediationStatus.PROPOSED);
        rem.setCreatedAt(Instant.now());
        DiagnosticRemediation savedRem = remediationRepository.save(rem);
        remediationId = savedRem.getId();
    }

    @Test
    @DisplayName("Test 1: Experiment creation attaches remediation metadata and initial PROPOSED state")
    void testExperimentCreation() {
        CreateExperimentRequestDto req = new CreateExperimentRequestDto();
        req.setRemediationId(remediationId);
        req.setExperimentType(ExperimentType.FEATURE_ABLATION);
        req.setTargetKey("FEATURE::drifted_feature");
        InterventionConfigDto cfg = new InterventionConfigDto();
        cfg.setFeature("drifted_feature");
        cfg.setStrategy("zero");
        req.setIntervention(cfg);

        DiagnosticExperimentDto exp = experimentService.createExperiment(baselineRunId, req);
        assertNotNull(exp.getId());
        assertEquals(baselineRunId, exp.getBaselineRunId());
        assertEquals(remediationId, exp.getRemediationId());
        assertEquals(ExperimentType.FEATURE_ABLATION, exp.getExperimentType());
        assertEquals(ExperimentStatus.PROPOSED, exp.getStatus());
        assertNotNull(exp.getAcceptanceCriteria());
        assertEquals(2, exp.getAcceptanceCriteria().size());
    }

    @Test
    @DisplayName("Test 2: Registry correctly resolves all 6 experiment strategies")
    void testRegistrySelection() {
        for (ExperimentType type : ExperimentType.values()) {
            Optional<DiagnosticExperimentStrategy> strat = strategyRegistry.getStrategy(type);
            assertTrue(strat.isPresent(), "Strategy should be registered for " + type);
            assertTrue(strat.get().supports(type));
        }
    }

    @Test
    @DisplayName("Test 3: Prerequisite validation catches missing feature for ablation")
    void testPrerequisiteValidationMissingFeature() {
        DiagnosticRun run = runRepository.findById(baselineRunId).orElseThrow();
        DiagnosticExperimentStrategy strat = strategyRegistry.getStrategy(ExperimentType.FEATURE_ABLATION).orElseThrow();

        InterventionConfigDto emptyConfig = new InterventionConfigDto();
        emptyConfig.setFeature(null);
        PrerequisiteValidationResult res = strat.validatePrerequisites(run, null, emptyConfig);
        assertFalse(res.isExecutable());
        assertTrue(res.getMissingPrerequisites().contains("feature"));
    }

    @Test
    @DisplayName("Test 4: Threshold counterfactual strategy computes valid metric tradeoffs")
    void testThresholdCounterfactualStrategy() {
        CreateExperimentRequestDto req = new CreateExperimentRequestDto();
        req.setExperimentType(ExperimentType.THRESHOLD_COUNTERFACTUAL);
        InterventionConfigDto cfg = new InterventionConfigDto();
        cfg.setBaselineThreshold(0.50);
        cfg.setCandidateThreshold(0.40);
        req.setIntervention(cfg);

        DiagnosticExperimentDto exp = experimentService.createExperiment(baselineRunId, req);
        DiagnosticExperimentDto executed = experimentService.executeExperiment(baselineRunId, exp.getId());

        assertEquals(ExperimentStatus.COMPLETED, executed.getStatus());
        assertNotNull(executed.getBaselineMetrics());
        assertNotNull(executed.getCandidateMetrics());
        assertNotNull(executed.getMetricDeltas());
        assertTrue(executed.getMetricDeltas().size() >= 4);
    }

    @Test
    @DisplayName("Test 5: Calibration counterfactual requires independent calibration split")
    void testCalibrationPrerequisitesWithoutSplit() {
        // Run with null baseline dataset
        DiagnosticRun noBaseRun = DiagnosticRun.builder()
                .id("run_no_base_" + UUID.randomUUID().toString().substring(0, 6))
                .status(DiagnosticStatus.COMPLETED)
                .executionMode("TEST")
                .modelName("test_model")
                .modelFramework("xgboost")
                .taskType("binary_classification")
                .evaluationDataset(tempEvalDataset)
                .baselineDataset(null)
                .targetColumn("target")
                .createdAt(Instant.now())
                .build();
        runRepository.save(noBaseRun);

        CreateExperimentRequestDto req = new CreateExperimentRequestDto();
        req.setExperimentType(ExperimentType.CALIBRATION_COUNTERFACTUAL);
        DiagnosticExperimentDto exp = experimentService.createExperiment(noBaseRun.getId(), req);

        DiagnosticExperimentDto executed = experimentService.executeExperiment(noBaseRun.getId(), exp.getId());
        assertEquals(ExperimentStatus.NOT_EXECUTABLE, executed.getStatus());
        assertEquals(ExperimentConclusion.NOT_EXECUTABLE, executed.getConclusion());
        assertTrue(executed.getConclusionReason().contains("calibration"));
    }

    @Test
    @DisplayName("Test 6: Subgroup counterfactual evaluates subgroup metrics and sample sizes")
    void testSubgroupCounterfactualEvaluation() {
        CreateExperimentRequestDto req = new CreateExperimentRequestDto();
        req.setExperimentType(ExperimentType.SUBGROUP_COUNTERFACTUAL);
        InterventionConfigDto cfg = new InterventionConfigDto();
        cfg.setSubgroupAttribute("protected_region");
        req.setIntervention(cfg);

        DiagnosticExperimentDto exp = experimentService.createExperiment(baselineRunId, req);
        DiagnosticExperimentDto executed = experimentService.executeExperiment(baselineRunId, exp.getId());

        assertEquals(ExperimentStatus.COMPLETED, executed.getStatus());
        assertNotNull(executed.getStatisticalEvidence());
        assertNotNull(executed.getStatisticalEvidence().getSubgroups());
        assertFalse(executed.getStatisticalEvidence().getSubgroups().isEmpty());
    }

    @Test
    @DisplayName("Test 7: Acceptance Evaluator returns VALIDATED when all criteria pass and guards pass")
    void testAcceptanceCriteriaValidated() {
        List<String> criteria = List.of("PSI decreases below 0.10 warning threshold", "F1 does not regress by > 0.01");
        List<String> guards = List.of("Accuracy does not drop by > 0.02");

        Map<String, Object> base = Map.of("psi", 0.45, "f1", 0.80, "accuracy", 0.82);
        Map<String, Object> cand = Map.of("psi", 0.06, "f1", 0.81, "accuracy", 0.82);
        List<MetricComparisonDto> deltas = List.of(
                new MetricComparisonDto("psi", 0.45, 0.06, -0.39, -86.0, "DECREASED", "IMPROVED"),
                new MetricComparisonDto("f1", 0.80, 0.81, 0.01, 1.25, "INCREASED", "IMPROVED")
        );

        var outcome = acceptanceEvaluatorService.evaluate(criteria, guards, base, cand, deltas);
        assertEquals(ExperimentConclusion.VALIDATED, outcome.getConclusion());
        assertEquals(2, outcome.getAcceptanceResults().size());
        assertTrue(outcome.getAcceptanceResults().get(0).isPassed());
        assertTrue(outcome.getAcceptanceResults().get(1).isPassed());
        assertTrue(outcome.getRegressionResults().get(0).isPassed());
    }

    @Test
    @DisplayName("Test 8: Acceptance Evaluator returns REJECTED when regression guard fails")
    void testAcceptanceCriteriaRejectedOnGuardFailure() {
        List<String> criteria = List.of("PSI decreases below 0.10 warning threshold");
        List<String> guards = List.of("Accuracy does not drop by > 0.02");

        Map<String, Object> base = Map.of("psi", 0.45, "accuracy", 0.85);
        Map<String, Object> cand = Map.of("psi", 0.06, "accuracy", 0.70); // drops 0.15 > 0.02

        var outcome = acceptanceEvaluatorService.evaluate(criteria, guards, base, cand, Collections.emptyList());
        assertEquals(ExperimentConclusion.REJECTED, outcome.getConclusion());
        assertFalse(outcome.getRegressionResults().get(0).isPassed());
    }

    @Test
    @DisplayName("Test 9: Inconclusive conclusion when no metrics are available")
    void testInconclusiveConclusionOnEmptyMetrics() {
        var outcome = acceptanceEvaluatorService.evaluate(
                List.of("F1 does not regress"), List.of("Accuracy invariant"),
                Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList()
        );
        assertEquals(ExperimentConclusion.INCONCLUSIVE, outcome.getConclusion());
    }

    @Test
    @DisplayName("Test 10: Cancel experiment transitions to CANCELLED state")
    void testCancelExperiment() {
        CreateExperimentRequestDto req = new CreateExperimentRequestDto();
        req.setExperimentType(ExperimentType.FEATURE_ABLATION);
        req.setTargetKey("FEATURE::income");
        DiagnosticExperimentDto exp = experimentService.createExperiment(baselineRunId, req);

        DiagnosticExperimentDto cancelled = experimentService.cancelExperiment(baselineRunId, exp.getId());
        assertEquals(ExperimentStatus.CANCELLED, cancelled.getStatus());
    }

    @Test
    @DisplayName("Test 11: Feature ablation with non-existent dataset marks NOT_EXECUTABLE")
    void testFeatureAblationMissingDatasetFile() {
        DiagnosticRun missingFileRun = DiagnosticRun.builder()
                .id("run_no_file_" + UUID.randomUUID().toString().substring(0, 6))
                .status(DiagnosticStatus.COMPLETED)
                .executionMode("TEST")
                .modelName("test_model")
                .modelFramework("xgboost")
                .taskType("binary_classification")
                .evaluationDataset("/non/existent/path/eval.csv")
                .targetColumn("target")
                .createdAt(Instant.now())
                .build();
        runRepository.save(missingFileRun);

        CreateExperimentRequestDto req = new CreateExperimentRequestDto();
        req.setExperimentType(ExperimentType.FEATURE_ABLATION);
        req.setTargetKey("FEATURE::income");
        InterventionConfigDto cfg = new InterventionConfigDto();
        cfg.setFeature("income");
        req.setIntervention(cfg);

        DiagnosticExperimentDto exp = experimentService.createExperiment(missingFileRun.getId(), req);
        DiagnosticExperimentDto executed = experimentService.executeExperiment(missingFileRun.getId(), exp.getId());

        assertEquals(ExperimentStatus.NOT_EXECUTABLE, executed.getStatus());
        assertEquals(ExperimentConclusion.NOT_EXECUTABLE, executed.getConclusion());
    }

    @Test
    @DisplayName("Test 12: Provenance fields persist dataset and model origins")
    void testProvenancePersistence() {
        CreateExperimentRequestDto req = new CreateExperimentRequestDto();
        req.setExperimentType(ExperimentType.THRESHOLD_COUNTERFACTUAL);
        InterventionConfigDto cfg = new InterventionConfigDto();
        cfg.setBaselineThreshold(0.50);
        cfg.setCandidateThreshold(0.35);
        req.setIntervention(cfg);

        DiagnosticExperimentDto exp = experimentService.createExperiment(baselineRunId, req);
        DiagnosticExperimentDto executed = experimentService.executeExperiment(baselineRunId, exp.getId());

        assertNotNull(executed.getModelProvenance());
        assertNotNull(executed.getDatasetProvenance());
        assertEquals("fraud_classifier_v17", executed.getModelProvenance().get("modelName"));
    }

    @Test
    @DisplayName("Test 13: Retrieval of experiments by baseline run ID returns descending created order")
    void testExperimentListRetrieval() {
        CreateExperimentRequestDto req1 = new CreateExperimentRequestDto();
        req1.setExperimentType(ExperimentType.THRESHOLD_COUNTERFACTUAL);
        experimentService.createExperiment(baselineRunId, req1);

        CreateExperimentRequestDto req2 = new CreateExperimentRequestDto();
        req2.setExperimentType(ExperimentType.SUBGROUP_COUNTERFACTUAL);
        experimentService.createExperiment(baselineRunId, req2);

        List<DiagnosticExperimentDto> list = experimentService.getExperimentsForRun(baselineRunId);
        assertTrue(list.size() >= 2);
    }
}
