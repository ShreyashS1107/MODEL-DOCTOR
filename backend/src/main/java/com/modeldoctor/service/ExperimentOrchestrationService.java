package com.modeldoctor.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeldoctor.domain.*;
import com.modeldoctor.dto.*;
import com.modeldoctor.exception.ResourceNotFoundException;
import com.modeldoctor.intelligence.experiment.DiagnosticExperimentStrategy;
import com.modeldoctor.intelligence.experiment.ExperimentExecutionResult;
import com.modeldoctor.intelligence.experiment.ExperimentStrategyRegistry;
import com.modeldoctor.intelligence.experiment.PrerequisiteValidationResult;
import com.modeldoctor.repository.DiagnosticExperimentRepository;
import com.modeldoctor.repository.DiagnosticRemediationRepository;
import com.modeldoctor.repository.DiagnosticRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExperimentOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(ExperimentOrchestrationService.class);

    private final DiagnosticExperimentRepository experimentRepository;
    private final DiagnosticRunRepository runRepository;
    private final DiagnosticRemediationRepository remediationRepository;
    private final ExperimentStrategyRegistry strategyRegistry;
    private final AcceptanceEvaluatorService acceptanceEvaluatorService;
    private final ObjectMapper objectMapper;

    public ExperimentOrchestrationService(
            DiagnosticExperimentRepository experimentRepository,
            DiagnosticRunRepository runRepository,
            DiagnosticRemediationRepository remediationRepository,
            ExperimentStrategyRegistry strategyRegistry,
            AcceptanceEvaluatorService acceptanceEvaluatorService,
            ObjectMapper objectMapper) {
        this.experimentRepository = experimentRepository;
        this.runRepository = runRepository;
        this.remediationRepository = remediationRepository;
        this.strategyRegistry = strategyRegistry;
        this.acceptanceEvaluatorService = acceptanceEvaluatorService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DiagnosticExperimentDto createExperiment(String baselineRunId, CreateExperimentRequestDto req) {
        DiagnosticRun baselineRun = runRepository.findById(baselineRunId)
                .orElseThrow(() -> new ResourceNotFoundException("Baseline diagnostic run not found: " + baselineRunId));

        DiagnosticRemediation remediation = null;
        if (req.getRemediationId() != null) {
            remediation = remediationRepository.findById(req.getRemediationId()).orElse(null);
        }

        // Idempotency check: detect identical experiment configuration
        String targetKey = req.getTargetKey() != null ? req.getTargetKey() : (remediation != null ? remediation.getTargetKey() : null);
        List<DiagnosticExperiment> existing = experimentRepository.findByBaselineRunIdOrderByCreatedAtDesc(baselineRunId);
        for (DiagnosticExperiment e : existing) {
            if (e.getExperimentType() == req.getExperimentType()
                    && Objects.equals(e.getRemediationId(), req.getRemediationId())
                    && Objects.equals(e.getTargetKey(), targetKey)) {
                log.info("Found existing identical experiment {} for run {}", e.getId(), baselineRunId);
                return toDto(e);
            }
        }

        String expId = "exp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        DiagnosticExperiment experiment = new DiagnosticExperiment();
        experiment.setId(expId);
        experiment.setBaselineRunId(baselineRunId);
        experiment.setRemediationId(req.getRemediationId());
        experiment.setExperimentType(req.getExperimentType());
        experiment.setStatus(ExperimentStatus.PROPOSED);
        experiment.setDeterministicSeed(req.getDeterministicSeed() != null ? req.getDeterministicSeed() : 42);
        experiment.setCreatedAt(Instant.now());

        String title = req.getTitle();
        if (title == null || title.isBlank()) {
            title = "Experimental Validation: " + req.getExperimentType() +
                    (req.getTargetKey() != null ? " on " + req.getTargetKey() : "");
        }
        experiment.setTitle(title);

        String desc = req.getDescription();
        if (desc == null || desc.isBlank()) {
            desc = "Controlled experimental evaluation of candidate intervention against baseline run " + baselineRunId;
        }
        experiment.setDescription(desc);

        experiment.setTargetType(req.getTargetType() != null ? req.getTargetType() :
                (remediation != null ? remediation.getTargetType() : "FEATURE"));
        experiment.setTargetKey(req.getTargetKey() != null ? req.getTargetKey() :
                (remediation != null ? remediation.getTargetKey() : ""));

        try {
            if (req.getIntervention() != null) {
                experiment.setInterventionConfigJson(objectMapper.writeValueAsString(req.getIntervention()));
            } else {
                experiment.setInterventionConfigJson("{}");
            }

            if (req.getRequestedModules() != null && !req.getRequestedModules().isEmpty()) {
                experiment.setRequestedModulesJson(objectMapper.writeValueAsString(req.getRequestedModules()));
            } else if (remediation != null && remediation.getRequiredModulesJson() != null) {
                experiment.setRequestedModulesJson(remediation.getRequiredModulesJson());
            } else {
                experiment.setRequestedModulesJson("[\"PERFORMANCE\",\"ERROR_FORENSICS\"]");
            }

            // Set criteria and guards from request or fallback to remediation
            if (req.getAcceptanceCriteria() != null && !req.getAcceptanceCriteria().isEmpty()) {
                experiment.setAcceptanceCriteriaJson(objectMapper.writeValueAsString(req.getAcceptanceCriteria()));
            } else if (remediation != null) {
                experiment.setAcceptanceCriteriaJson(remediation.getAcceptanceCriteriaJson());
            }

            if (req.getRegressionGuards() != null && !req.getRegressionGuards().isEmpty()) {
                experiment.setRegressionGuardsJson(objectMapper.writeValueAsString(req.getRegressionGuards()));
            } else if (remediation != null) {
                experiment.setRegressionGuardsJson(remediation.getRegressionGuardsJson());
            }
        } catch (Exception e) {
            log.error("Failed to serialize experiment JSON metadata: {}", e.getMessage());
        }

        DiagnosticExperiment saved = experimentRepository.save(experiment);
        log.info("Created experimental validation candidate {} for run {}", saved.getId(), baselineRunId);
        return toDto(saved);
    }

    @Transactional
    public DiagnosticExperimentDto executeExperiment(String baselineRunId, String experimentId) {
        DiagnosticExperiment experiment = experimentRepository.findByIdAndBaselineRunId(experimentId, baselineRunId)
                .orElseThrow(() -> new ResourceNotFoundException("Diagnostic experiment not found: " + experimentId));

        DiagnosticRun baselineRun = runRepository.findById(baselineRunId)
                .orElseThrow(() -> new ResourceNotFoundException("Baseline run not found: " + baselineRunId));

        DiagnosticRemediation remediation = null;
        if (experiment.getRemediationId() != null) {
            remediation = remediationRepository.findById(experiment.getRemediationId()).orElse(null);
        }

        InterventionConfigDto interventionConfig = new InterventionConfigDto();
        if (experiment.getInterventionConfigJson() != null) {
            try {
                interventionConfig = objectMapper.readValue(experiment.getInterventionConfigJson(), InterventionConfigDto.class);
            } catch (Exception ignored) {}
        }
        if (interventionConfig.getDeterministicSeed() == null) {
            interventionConfig.setDeterministicSeed(experiment.getDeterministicSeed());
        }

        // 1. Resolve strategy
        Optional<DiagnosticExperimentStrategy> strategyOpt = strategyRegistry.getStrategy(experiment.getExperimentType());
        if (strategyOpt.isEmpty()) {
            experiment.setStatus(ExperimentStatus.NOT_EXECUTABLE);
            experiment.setConclusion(ExperimentConclusion.NOT_EXECUTABLE);
            experiment.setConclusionReason("No execution strategy registered for experiment type: " + experiment.getExperimentType());
            return toDto(experimentRepository.save(experiment));
        }

        DiagnosticExperimentStrategy strategy = strategyOpt.get();

        // 2. Validate prerequisites
        PrerequisiteValidationResult prereq = strategy.validatePrerequisites(baselineRun, remediation, interventionConfig);
        if (!prereq.isExecutable()) {
            experiment.setStatus(ExperimentStatus.NOT_EXECUTABLE);
            experiment.setConclusion(ExperimentConclusion.NOT_EXECUTABLE);
            experiment.setConclusionReason(prereq.getReason());
            experiment.setCompletedAt(Instant.now());
            return toDto(experimentRepository.save(experiment));
        }

        // 3. Mark RUNNING
        experiment.setStatus(ExperimentStatus.RUNNING);
        experiment.setStartedAt(Instant.now());
        experiment = experimentRepository.saveAndFlush(experiment);

        // 4. Execute strategy
        ExperimentExecutionResult execResult = strategy.execute(baselineRun, remediation, experiment, interventionConfig);

        if (!execResult.isSuccess()) {
            experiment.setStatus(ExperimentStatus.FAILED);
            experiment.setConclusion(ExperimentConclusion.FAILED);
            experiment.setErrorCode(execResult.getErrorCode());
            experiment.setErrorMessage(execResult.getErrorMessage());
            experiment.setConclusionReason("Experiment execution failed: " + execResult.getErrorMessage());
            experiment.setCompletedAt(Instant.now());
            return toDto(experimentRepository.save(experiment));
        }

        // 5. Evaluate Acceptance Criteria & Regression Guards
        List<String> criteria = readStringList(experiment.getAcceptanceCriteriaJson());
        List<String> guards = readStringList(experiment.getRegressionGuardsJson());

        AcceptanceEvaluatorService.EvaluationOutcome outcome = acceptanceEvaluatorService.evaluate(
                criteria,
                guards,
                execResult.getBaselineMetrics(),
                execResult.getCandidateMetrics(),
                execResult.getMetricDeltas()
        );

        // 6. Finalize Experiment Record
        experiment.setStatus(ExperimentStatus.COMPLETED);
        experiment.setCandidateRunId(execResult.getCandidateRunId());
        experiment.setConclusion(outcome.getConclusion());
        experiment.setConclusionReason(outcome.getConclusionReason());
        experiment.setCompletedAt(Instant.now());

        try {
            experiment.setBaselineMetricsJson(objectMapper.writeValueAsString(execResult.getBaselineMetrics()));
            experiment.setCandidateMetricsJson(objectMapper.writeValueAsString(execResult.getCandidateMetrics()));
            experiment.setMetricDeltasJson(objectMapper.writeValueAsString(execResult.getMetricDeltas()));
            experiment.setAcceptanceResultsJson(objectMapper.writeValueAsString(outcome.getAcceptanceResults()));
            experiment.setRegressionResultsJson(objectMapper.writeValueAsString(outcome.getRegressionResults()));
            if (execResult.getStatisticalEvidence() != null) {
                experiment.setStatisticalEvidenceJson(objectMapper.writeValueAsString(execResult.getStatisticalEvidence()));
            }
            experiment.setDatasetProvenanceJson(objectMapper.writeValueAsString(execResult.getDatasetProvenance()));
            experiment.setModelProvenanceJson(objectMapper.writeValueAsString(execResult.getModelProvenance()));
            experiment.setExecutedModulesJson(objectMapper.writeValueAsString(execResult.getExecutedModules()));
        } catch (Exception e) {
            log.error("Failed to serialize experiment evaluation results: {}", e.getMessage());
        }

        // Update remediation status if linked
        if (remediation != null && outcome.getConclusion() == ExperimentConclusion.VALIDATED) {
            remediation.setStatus(RemediationStatus.VALIDATED);
            remediationRepository.save(remediation);
        }

        DiagnosticExperiment finalSaved = experimentRepository.save(experiment);
        log.info("Experiment {} completed with conclusion: {}", finalSaved.getId(), finalSaved.getConclusion());
        return toDto(finalSaved);
    }

    @Transactional(readOnly = true)
    public List<DiagnosticExperimentDto> getExperimentsForRun(String baselineRunId) {
        return experimentRepository.findByBaselineRunIdOrderByCreatedAtDesc(baselineRunId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DiagnosticExperimentDto getExperiment(String baselineRunId, String experimentId) {
        DiagnosticExperiment experiment = experimentRepository.findByIdAndBaselineRunId(experimentId, baselineRunId)
                .orElseThrow(() -> new ResourceNotFoundException("Diagnostic experiment not found: " + experimentId));
        return toDto(experiment);
    }

    @Transactional
    public DiagnosticExperimentDto cancelExperiment(String baselineRunId, String experimentId) {
        DiagnosticExperiment experiment = experimentRepository.findByIdAndBaselineRunId(experimentId, baselineRunId)
                .orElseThrow(() -> new ResourceNotFoundException("Diagnostic experiment not found: " + experimentId));

        if (experiment.getStatus() == ExperimentStatus.COMPLETED || experiment.getStatus() == ExperimentStatus.CANCELLED) {
            return toDto(experiment);
        }

        experiment.setStatus(ExperimentStatus.CANCELLED);
        experiment.setConclusionReason("Experiment cancelled by user.");
        experiment.setCompletedAt(Instant.now());
        return toDto(experimentRepository.save(experiment));
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public DiagnosticExperimentDto toDto(DiagnosticExperiment entity) {
        if (entity == null) return null;
        DiagnosticExperimentDto dto = new DiagnosticExperimentDto();
        dto.setId(entity.getId());
        dto.setBaselineRunId(entity.getBaselineRunId());
        dto.setCandidateRunId(entity.getCandidateRunId());
        dto.setRemediationId(entity.getRemediationId());
        dto.setExperimentType(entity.getExperimentType());
        dto.setStatus(entity.getStatus());
        dto.setTitle(entity.getTitle());
        dto.setDescription(entity.getDescription());
        dto.setTargetType(entity.getTargetType());
        dto.setTargetKey(entity.getTargetKey());
        dto.setConclusion(entity.getConclusion());
        dto.setConclusionReason(entity.getConclusionReason());
        dto.setDeterministicSeed(entity.getDeterministicSeed());
        dto.setErrorCode(entity.getErrorCode());
        dto.setErrorMessage(entity.getErrorMessage());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setStartedAt(entity.getStartedAt());
        dto.setCompletedAt(entity.getCompletedAt());

        try {
            if (entity.getInterventionConfigJson() != null) {
                dto.setInterventionConfig(objectMapper.readValue(entity.getInterventionConfigJson(), InterventionConfigDto.class));
            }
            if (entity.getDatasetProvenanceJson() != null) {
                dto.setDatasetProvenance(objectMapper.readValue(entity.getDatasetProvenanceJson(), new TypeReference<Map<String, Object>>() {}));
            }
            if (entity.getModelProvenanceJson() != null) {
                dto.setModelProvenance(objectMapper.readValue(entity.getModelProvenanceJson(), new TypeReference<Map<String, Object>>() {}));
            }
            if (entity.getRequestedModulesJson() != null) {
                dto.setRequestedModules(objectMapper.readValue(entity.getRequestedModulesJson(), new TypeReference<List<DiagnosticModule>>() {}));
            }
            if (entity.getExecutedModulesJson() != null) {
                dto.setExecutedModules(objectMapper.readValue(entity.getExecutedModulesJson(), new TypeReference<List<DiagnosticModule>>() {}));
            }
            if (entity.getBaselineMetricsJson() != null) {
                dto.setBaselineMetrics(objectMapper.readValue(entity.getBaselineMetricsJson(), new TypeReference<Map<String, Object>>() {}));
            }
            if (entity.getCandidateMetricsJson() != null) {
                dto.setCandidateMetrics(objectMapper.readValue(entity.getCandidateMetricsJson(), new TypeReference<Map<String, Object>>() {}));
            }
            if (entity.getMetricDeltasJson() != null) {
                dto.setMetricDeltas(objectMapper.readValue(entity.getMetricDeltasJson(), new TypeReference<List<MetricComparisonDto>>() {}));
            }
            if (entity.getAcceptanceCriteriaJson() != null) {
                dto.setAcceptanceCriteria(objectMapper.readValue(entity.getAcceptanceCriteriaJson(), new TypeReference<List<String>>() {}));
            }
            if (entity.getAcceptanceResultsJson() != null) {
                dto.setAcceptanceResults(objectMapper.readValue(entity.getAcceptanceResultsJson(), new TypeReference<List<AcceptanceCriterionResultDto>>() {}));
            }
            if (entity.getRegressionGuardsJson() != null) {
                dto.setRegressionGuards(objectMapper.readValue(entity.getRegressionGuardsJson(), new TypeReference<List<String>>() {}));
            }
            if (entity.getRegressionResultsJson() != null) {
                dto.setRegressionResults(objectMapper.readValue(entity.getRegressionResultsJson(), new TypeReference<List<RegressionGuardResultDto>>() {}));
            }
            if (entity.getStatisticalEvidenceJson() != null) {
                dto.setStatisticalEvidence(objectMapper.readValue(entity.getStatisticalEvidenceJson(), StatisticalEvidenceDto.class));
            }
        } catch (Exception e) {
            log.warn("Failed to deserialize some experiment JSON fields for {}: {}", entity.getId(), e.getMessage());
        }

        return dto;
    }
}
