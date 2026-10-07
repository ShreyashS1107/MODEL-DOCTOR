package com.modeldoctor.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeldoctor.domain.*;
import com.modeldoctor.dto.DiagnosticCorrelationDto;
import com.modeldoctor.dto.RunSummaryDto;
import com.modeldoctor.exception.ResourceNotFoundException;
import com.modeldoctor.intelligence.normalization.NormalizedModuleData;
import com.modeldoctor.intelligence.normalization.ResultNormalizer;
import com.modeldoctor.intelligence.rules.RuleRegistry;
import com.modeldoctor.repository.DiagnosticCorrelationRepository;
import com.modeldoctor.repository.DiagnosticResultRepository;
import com.modeldoctor.repository.DiagnosticRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class CorrelationAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(CorrelationAnalysisService.class);

    private final DiagnosticRunRepository runRepository;
    private final DiagnosticResultRepository resultRepository;
    private final DiagnosticCorrelationRepository correlationRepository;
    private final ResultNormalizer normalizer;
    private final RuleRegistry ruleRegistry;
    private final ObjectMapper objectMapper;

    public CorrelationAnalysisService(
            DiagnosticRunRepository runRepository,
            DiagnosticResultRepository resultRepository,
            DiagnosticCorrelationRepository correlationRepository,
            ResultNormalizer normalizer,
            RuleRegistry ruleRegistry,
            ObjectMapper objectMapper) {
        this.runRepository = runRepository;
        this.resultRepository = resultRepository;
        this.correlationRepository = correlationRepository;
        this.normalizer = normalizer;
        this.ruleRegistry = ruleRegistry;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<DiagnosticCorrelationDto> analyzeAndPersist(String runId) {
        DiagnosticRun run = runRepository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("Diagnostic run not found: " + runId));

        List<DiagnosticResult> results = resultRepository.findByRunIdOrderByIdAsc(runId);
        if (results == null || results.isEmpty()) {
            logger.info("No diagnostic results found for run {}. Skipping cross-module correlation analysis.", runId);
            return Collections.emptyList();
        }

        NormalizedModuleData normalizedData = normalizer.normalize(runId, results);
        List<DiagnosticCorrelation> evaluatedFindings = ruleRegistry.evaluateAll(normalizedData);

        // Delete existing correlations for this run to support clean, idempotent recalculation without duplicates
        correlationRepository.deleteByRunId(runId);

        List<DiagnosticCorrelation> savedEntities = new ArrayList<>();
        for (DiagnosticCorrelation corr : evaluatedFindings) {
            savedEntities.add(correlationRepository.save(corr));
        }

        logger.info("Generated and persisted {} cross-module correlation findings for run {}", savedEntities.size(), runId);
        return savedEntities.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DiagnosticCorrelationDto> getCorrelations(String runId) {
        List<DiagnosticCorrelation> correlations = correlationRepository.findByRunIdOrderByPriorityScoreDesc(runId);
        if (correlations.isEmpty()) {
            List<DiagnosticResult> results = resultRepository.findByRunIdOrderByIdAsc(runId);
            if (!results.isEmpty()) {
                // On-demand lazy generation if results exist but correlations haven't been generated yet
                return analyzeAndPersist(runId);
            }
        }
        return correlations.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RunSummaryDto getRunSummary(String runId) {
        DiagnosticRun run = runRepository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("Diagnostic run not found: " + runId));

        List<DiagnosticRunModule> modules = run.getModules() != null ? run.getModules() : Collections.emptyList();
        List<DiagnosticCorrelation> correlations = correlationRepository.findByRunIdOrderByPriorityScoreDesc(runId);
        if (correlations.isEmpty()) {
            List<DiagnosticResult> results = resultRepository.findByRunIdOrderByIdAsc(runId);
            if (!results.isEmpty()) {
                correlations = analyzeAndPersist(runId).stream()
                        .map(this::mapDtoToEntity)
                        .collect(Collectors.toList());
            }
        }

        RunSummaryDto summary = new RunSummaryDto();
        summary.setRunId(runId);
        summary.setStatus(run.getStatus().name());
        summary.setTotalModules(modules.size());

        int completed = 0;
        int failed = 0;
        for (DiagnosticRunModule m : modules) {
            if (m.getStatus() == ModuleExecutionStatus.COMPLETED) completed++;
            else if (m.getStatus() == ModuleExecutionStatus.FAILED) failed++;
        }
        summary.setCompletedModules(completed);
        summary.setFailedModules(failed);

        long criticalCount = 0;
        long highCount = 0;
        long medCount = 0;
        long lowCount = 0;

        Map<String, Double> featurePriorityMap = new HashMap<>();
        Set<String> investigationAreas = new LinkedHashSet<>();
        Map<String, Long> moduleCounts = new HashMap<>();

        for (DiagnosticCorrelation corr : correlations) {
            if (corr.getPriority() == InvestigationPriority.CRITICAL) criticalCount++;
            else if (corr.getPriority() == InvestigationPriority.HIGH) highCount++;
            else if (corr.getPriority() == InvestigationPriority.MEDIUM) medCount++;
            else if (corr.getPriority() == InvestigationPriority.LOW || corr.getPriority() == InvestigationPriority.INFO) lowCount++;

            if (corr.getFeature() != null && !corr.getFeature().isBlank()) {
                featurePriorityMap.merge(corr.getFeature(), corr.getPriorityScore(), Double::sum);
            }

            investigationAreas.add(corr.getTitle());

            try {
                List<String> srcMods = objectMapper.readValue(corr.getSourceModulesJson(), new TypeReference<List<String>>() {});
                for (String mod : srcMods) {
                    moduleCounts.merge(mod, 1L, Long::sum);
                }
            } catch (Exception ignored) {}
        }

        summary.setCriticalFindingsCount(criticalCount);
        summary.setHighPriorityFindingsCount(highCount);
        summary.setMediumPriorityFindingsCount(medCount);
        summary.setLowPriorityFindingsCount(lowCount);
        summary.setTotalCorrelationsCount(correlations.size());

        List<String> sortedFeatures = featurePriorityMap.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        summary.setTopFeatures(sortedFeatures);
        summary.setTopInvestigationAreas(new ArrayList<>(investigationAreas));
        summary.setModuleContributions(moduleCounts);

        // Build feature-centric investigation profiles across normalized modules
        List<DiagnosticResult> results = resultRepository.findByRunIdOrderByIdAsc(runId);
        NormalizedModuleData normalized = normalizer.normalize(runId, results);
        Map<String, Map<String, Object>> featureProfiles = new LinkedHashMap<>();

        for (String feat : normalized.getAllKnownFeatures()) {
            Map<String, Object> profile = new LinkedHashMap<>();
            if (normalized.getDriftByFeature().containsKey(feat)) {
                var d = normalized.getDriftByFeature().get(feat);
                profile.put("driftPsi", d.psi);
                profile.put("driftSeverity", d.severity);
                profile.put("driftDetected", d.driftDetected);
            }
            if (normalized.getImportanceByFeature().containsKey(feat)) {
                var imp = normalized.getImportanceByFeature().get(feat);
                profile.put("importanceRank", imp.rank);
                profile.put("meanAbsShap", imp.meanAbsShap);
            }
            if (normalized.getRobustnessByFeature().containsKey(feat)) {
                var r = normalized.getRobustnessByFeature().get(feat);
                profile.put("robustnessSensitivityRank", r.sensitivityRank);
                profile.put("robustnessFlipRate", r.flipRate);
            }
            if (normalized.getLeakageByFeature().containsKey(feat)) {
                var l = normalized.getLeakageByFeature().get(feat);
                profile.put("leakageMutualInfo", l.mutualInfo);
                profile.put("leakageCorrelation", l.correlation);
            }
            if (normalized.getQualityByFeature().containsKey(feat)) {
                var q = normalized.getQualityByFeature().get(feat);
                profile.put("qualityNullRate", q.nullRate);
                profile.put("qualityOutlierRate", q.outlierRate);
            }
            featureProfiles.put(feat, profile);
        }
        summary.setFeatureProfiles(featureProfiles);

        return summary;
    }

    public DiagnosticCorrelationDto mapToDto(DiagnosticCorrelation entity) {
        DiagnosticCorrelationDto dto = new DiagnosticCorrelationDto();
        dto.setId(entity.getId());
        dto.setRunId(entity.getRunId());
        dto.setRuleId(entity.getRuleId());
        dto.setCorrelationKey(entity.getCorrelationKey());
        dto.setFindingType(entity.getFindingType());
        dto.setSeverity(entity.getSeverity().name());
        dto.setPriority(entity.getPriority().name());
        dto.setPriorityScore(entity.getPriorityScore());
        dto.setConfidence(entity.getConfidence().name());
        dto.setFeature(entity.getFeature());
        dto.setTitle(entity.getTitle());
        dto.setSummary(entity.getSummary());
        dto.setWhyItMatters(entity.getWhyItMatters());
        dto.setInvestigationDirection(entity.getInvestigationDirection());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setAssociativeOnly(true);

        try {
            dto.setEvidence(objectMapper.readValue(entity.getEvidenceJson(), new TypeReference<Map<String, Object>>() {}));
        } catch (Exception e) {
            dto.setEvidence(Collections.emptyMap());
        }

        try {
            dto.setSourceModules(objectMapper.readValue(entity.getSourceModulesJson(), new TypeReference<List<String>>() {}));
        } catch (Exception e) {
            dto.setSourceModules(Collections.emptyList());
        }

        try {
            dto.setSourceResultIds(objectMapper.readValue(entity.getSourceResultIdsJson(), new TypeReference<List<String>>() {}));
        } catch (Exception e) {
            dto.setSourceResultIds(Collections.emptyList());
        }

        return dto;
    }

    private DiagnosticCorrelation mapDtoToEntity(DiagnosticCorrelationDto dto) {
        DiagnosticCorrelation e = new DiagnosticCorrelation();
        e.setId(dto.getId());
        e.setRunId(dto.getRunId());
        e.setRuleId(dto.getRuleId());
        e.setCorrelationKey(dto.getCorrelationKey());
        e.setFindingType(dto.getFindingType());
        e.setSeverity(SeverityLevel.valueOf(dto.getSeverity()));
        e.setPriority(InvestigationPriority.valueOf(dto.getPriority()));
        e.setPriorityScore(dto.getPriorityScore());
        e.setConfidence(EvidenceConfidence.valueOf(dto.getConfidence()));
        e.setFeature(dto.getFeature());
        e.setTitle(dto.getTitle());
        e.setSummary(dto.getSummary());
        e.setWhyItMatters(dto.getWhyItMatters());
        e.setInvestigationDirection(dto.getInvestigationDirection());
        e.setCreatedAt(dto.getCreatedAt());
        try {
            e.setEvidenceJson(objectMapper.writeValueAsString(dto.getEvidence()));
        } catch (Exception ignored) {}
        try {
            e.setSourceModulesJson(objectMapper.writeValueAsString(dto.getSourceModules()));
        } catch (Exception ignored) {}
        try {
            e.setSourceResultIdsJson(objectMapper.writeValueAsString(dto.getSourceResultIds()));
        } catch (Exception ignored) {}
        return e;
    }
}
