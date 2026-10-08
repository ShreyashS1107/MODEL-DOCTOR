package com.modeldoctor.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeldoctor.domain.*;
import com.modeldoctor.dto.DiagnosticRemediationDto;
import com.modeldoctor.dto.ExpectedImpactDto;
import com.modeldoctor.exception.ResourceNotFoundException;
import com.modeldoctor.intelligence.normalization.NormalizedModuleData;
import com.modeldoctor.intelligence.normalization.ResultNormalizer;
import com.modeldoctor.intelligence.remediation.RemediationRuleRegistry;
import com.modeldoctor.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class RemediationAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(RemediationAnalysisService.class);

    private final DiagnosticRunRepository runRepository;
    private final DiagnosticResultRepository resultRepository;
    private final DiagnosticCorrelationRepository correlationRepository;
    private final DiagnosticInvestigationRepository investigationRepository;
    private final DiagnosticRemediationRepository remediationRepository;
    private final ResultNormalizer normalizer;
    private final RemediationRuleRegistry ruleRegistry;
    private final ObjectMapper objectMapper;

    public RemediationAnalysisService(
            DiagnosticRunRepository runRepository,
            DiagnosticResultRepository resultRepository,
            DiagnosticCorrelationRepository correlationRepository,
            DiagnosticInvestigationRepository investigationRepository,
            DiagnosticRemediationRepository remediationRepository,
            ResultNormalizer normalizer,
            RemediationRuleRegistry ruleRegistry,
            ObjectMapper objectMapper) {
        this.runRepository = runRepository;
        this.resultRepository = resultRepository;
        this.correlationRepository = correlationRepository;
        this.investigationRepository = investigationRepository;
        this.remediationRepository = remediationRepository;
        this.normalizer = normalizer;
        this.ruleRegistry = ruleRegistry;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<DiagnosticRemediationDto> analyzeAndPersist(String runId) {
        DiagnosticRun run = runRepository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("Diagnostic run not found: " + runId));

        List<DiagnosticResult> rawResults = resultRepository.findByRunIdOrderByIdAsc(runId);
        if (rawResults == null || rawResults.isEmpty()) {
            logger.info("No raw diagnostic results found for run {}. Skipping remediation generation.", runId);
            remediationRepository.deleteByRunId(runId);
            return Collections.emptyList();
        }

        NormalizedModuleData normalized = normalizer.normalize(runId, rawResults);
        List<DiagnosticCorrelation> correlations = correlationRepository.findByRunIdOrderByPriorityScoreDesc(runId);
        List<DiagnosticInvestigation> investigations = investigationRepository.findByRunIdOrderByPriorityScoreDesc(runId);

        List<DiagnosticRemediation> generated = ruleRegistry.evaluateAll(normalized, correlations, investigations, run);

        // Delete existing derived remediations for idempotency
        remediationRepository.deleteByRunId(runId);
        remediationRepository.flush();

        List<DiagnosticRemediation> saved = remediationRepository.saveAll(generated);
        logger.info("Generated and persisted {} remediation candidates for run {}", saved.size(), runId);

        return saved.stream().map(this::mapToDto).toList();
    }

    @Transactional(readOnly = true)
    public List<DiagnosticRemediationDto> getRemediations(String runId) {
        List<DiagnosticRemediation> entities = remediationRepository.findByRunIdOrderByPriorityScoreDesc(runId);
        return entities.stream().map(this::mapToDto).toList();
    }

    @Transactional(readOnly = true)
    public DiagnosticRemediationDto getRemediationById(Long remediationId) {
        DiagnosticRemediation entity = remediationRepository.findById(remediationId)
                .orElseThrow(() -> new ResourceNotFoundException("Remediation candidate not found with id: " + remediationId));
        return mapToDto(entity);
    }

    @Transactional(readOnly = true)
    public DiagnosticRemediationDto getRemediationById(String runId, Long remediationId) {
        DiagnosticRemediation entity = remediationRepository.findByRunIdAndId(runId, remediationId)
                .orElseThrow(() -> new ResourceNotFoundException("Remediation candidate not found with id: " + remediationId + " for run: " + runId));
        return mapToDto(entity);
    }

    @Transactional
    public DiagnosticRemediationDto selectRemediation(Long remediationId) {
        DiagnosticRemediation entity = remediationRepository.findById(remediationId)
                .orElseThrow(() -> new ResourceNotFoundException("Remediation candidate not found with id: " + remediationId));

        entity.setStatus(RemediationStatus.SELECTED);
        entity.setUpdatedAt(Instant.now());
        DiagnosticRemediation saved = remediationRepository.save(entity);
        return mapToDto(saved);
    }

    @Transactional
    public DiagnosticRemediationDto selectRemediation(String runId, Long remediationId) {
        DiagnosticRemediation entity = remediationRepository.findByRunIdAndId(runId, remediationId)
                .orElseThrow(() -> new ResourceNotFoundException("Remediation candidate not found with id: " + remediationId));

        entity.setStatus(RemediationStatus.SELECTED);
        entity.setUpdatedAt(Instant.now());
        DiagnosticRemediation saved = remediationRepository.save(entity);
        return mapToDto(saved);
    }

    @Transactional
    public DiagnosticRemediationDto rejectRemediation(Long remediationId, String reason) {
        DiagnosticRemediation entity = remediationRepository.findById(remediationId)
                .orElseThrow(() -> new ResourceNotFoundException("Remediation candidate not found with id: " + remediationId));

        entity.setStatus(RemediationStatus.REJECTED);
        entity.setRejectionReason(reason != null ? reason : "Rejected by engineer");
        entity.setUpdatedAt(Instant.now());
        DiagnosticRemediation saved = remediationRepository.save(entity);
        return mapToDto(saved);
    }

    @Transactional
    public DiagnosticRemediationDto rejectRemediation(String runId, Long remediationId, String reason) {
        DiagnosticRemediation entity = remediationRepository.findByRunIdAndId(runId, remediationId)
                .orElseThrow(() -> new ResourceNotFoundException("Remediation candidate not found with id: " + remediationId));

        entity.setStatus(RemediationStatus.REJECTED);
        entity.setRejectionReason(reason != null ? reason : "Rejected by engineer");
        entity.setUpdatedAt(Instant.now());
        DiagnosticRemediation saved = remediationRepository.save(entity);
        return mapToDto(saved);
    }

    public DiagnosticRemediationDto mapToDto(DiagnosticRemediation entity) {
        DiagnosticRemediationDto dto = new DiagnosticRemediationDto();
        dto.setId(entity.getId());
        dto.setRunId(entity.getRunId());
        dto.setTargetType(entity.getTargetType());
        dto.setTargetKey(entity.getTargetKey());
        dto.setRemediationType(entity.getRemediationType().name());
        dto.setTitle(entity.getTitle());
        dto.setDescription(entity.getDescription());
        dto.setPriority(entity.getPriority().name());
        dto.setPriorityScore(entity.getPriorityScore());
        dto.setConfidence(entity.getConfidence().name());
        dto.setEvidenceStrength(entity.getEvidenceStrength());
        dto.setHypothesis(entity.getHypothesis());
        dto.setExpectedEffect(entity.getExpectedEffect());
        dto.setValidationStrategy(entity.getValidationStrategy());
        dto.setSourceInvestigationTarget(entity.getSourceInvestigationTarget());
        dto.setStatus(entity.getStatus().name());
        dto.setRejectionReason(entity.getRejectionReason());
        dto.setValidationRunId(entity.getValidationRunId());
        dto.setAssociativeOnly(true);
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());

        try {
            if (entity.getAcceptanceCriteriaJson() != null) {
                dto.setAcceptanceCriteria(objectMapper.readValue(entity.getAcceptanceCriteriaJson(), new TypeReference<List<String>>() {}));
            } else {
                dto.setAcceptanceCriteria(Collections.emptyList());
            }
        } catch (Exception e) {
            dto.setAcceptanceCriteria(Collections.emptyList());
        }

        try {
            if (entity.getRequiredModulesJson() != null) {
                dto.setRequiredModules(objectMapper.readValue(entity.getRequiredModulesJson(), new TypeReference<List<String>>() {}));
            } else {
                dto.setRequiredModules(Collections.emptyList());
            }
        } catch (Exception e) {
            dto.setRequiredModules(Collections.emptyList());
        }

        try {
            if (entity.getExpectedImpactJson() != null) {
                dto.setExpectedImpact(objectMapper.readValue(entity.getExpectedImpactJson(), new TypeReference<List<ExpectedImpactDto>>() {}));
            } else {
                dto.setExpectedImpact(Collections.emptyList());
            }
        } catch (Exception e) {
            dto.setExpectedImpact(Collections.emptyList());
        }

        try {
            if (entity.getRegressionGuardsJson() != null) {
                dto.setRegressionGuards(objectMapper.readValue(entity.getRegressionGuardsJson(), new TypeReference<List<String>>() {}));
            } else {
                dto.setRegressionGuards(Collections.emptyList());
            }
        } catch (Exception e) {
            dto.setRegressionGuards(Collections.emptyList());
        }

        try {
            if (entity.getSourceCorrelationIdsJson() != null) {
                dto.setSourceCorrelationIds(objectMapper.readValue(entity.getSourceCorrelationIdsJson(), new TypeReference<List<String>>() {}));
            } else {
                dto.setSourceCorrelationIds(Collections.emptyList());
            }
        } catch (Exception e) {
            dto.setSourceCorrelationIds(Collections.emptyList());
        }

        try {
            if (entity.getSourceResultIdsJson() != null) {
                dto.setSourceResultIds(objectMapper.readValue(entity.getSourceResultIdsJson(), new TypeReference<Map<String, Long>>() {}));
            } else {
                dto.setSourceResultIds(Collections.emptyMap());
            }
        } catch (Exception e) {
            dto.setSourceResultIds(Collections.emptyMap());
        }

        return dto;
    }
}
