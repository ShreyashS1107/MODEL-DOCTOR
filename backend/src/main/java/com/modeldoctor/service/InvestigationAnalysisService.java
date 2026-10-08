package com.modeldoctor.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeldoctor.domain.*;
import com.modeldoctor.dto.*;
import com.modeldoctor.exception.ResourceNotFoundException;
import com.modeldoctor.intelligence.normalization.NormalizedModuleData;
import com.modeldoctor.intelligence.normalization.ResultNormalizer;
import com.modeldoctor.repository.DiagnosticCorrelationRepository;
import com.modeldoctor.repository.DiagnosticInvestigationRepository;
import com.modeldoctor.repository.DiagnosticResultRepository;
import com.modeldoctor.repository.DiagnosticRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class InvestigationAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(InvestigationAnalysisService.class);

    private final DiagnosticRunRepository runRepository;
    private final DiagnosticResultRepository resultRepository;
    private final DiagnosticCorrelationRepository correlationRepository;
    private final DiagnosticInvestigationRepository investigationRepository;
    private final CorrelationAnalysisService correlationAnalysisService;
    private final ResultNormalizer normalizer;
    private final ObjectMapper objectMapper;

    public InvestigationAnalysisService(
            DiagnosticRunRepository runRepository,
            DiagnosticResultRepository resultRepository,
            DiagnosticCorrelationRepository correlationRepository,
            DiagnosticInvestigationRepository investigationRepository,
            CorrelationAnalysisService correlationAnalysisService,
            ResultNormalizer normalizer,
            ObjectMapper objectMapper) {
        this.runRepository = runRepository;
        this.resultRepository = resultRepository;
        this.correlationRepository = correlationRepository;
        this.investigationRepository = investigationRepository;
        this.correlationAnalysisService = correlationAnalysisService;
        this.normalizer = normalizer;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<InvestigationTargetDto> analyzeAndPersist(String runId) {
        DiagnosticRun run = runRepository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("Diagnostic run not found: " + runId));

        List<DiagnosticResult> results = resultRepository.findByRunIdOrderByIdAsc(runId);
        if (results == null || results.isEmpty()) {
            logger.info("No diagnostic results found for run {}. Skipping investigation intelligence analysis.", runId);
            return Collections.emptyList();
        }

        NormalizedModuleData normalizedData = normalizer.normalize(runId, results);
        List<DiagnosticCorrelation> correlations = correlationRepository.findByRunIdOrderByPriorityScoreDesc(runId);
        if (correlations.isEmpty()) {
            // Lazy evaluate correlations if needed
            correlationAnalysisService.analyzeAndPersist(runId);
            correlations = correlationRepository.findByRunIdOrderByPriorityScoreDesc(runId);
        }

        List<DiagnosticInvestigation> investigations = generateInvestigationEntities(runId, normalizedData, correlations);

        // Delete existing investigations for this run to support clean, idempotent recalculation without duplicates
        investigationRepository.deleteByRunId(runId);
        investigationRepository.flush();

        List<DiagnosticInvestigation> saved = new ArrayList<>();
        for (DiagnosticInvestigation inv : investigations) {
            saved.add(investigationRepository.save(inv));
        }
        investigationRepository.flush();

        logger.info("Generated and persisted {} investigation targets for run {}", saved.size(), runId);
        return saved.stream().map(this::mapEntityToDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<InvestigationTargetDto> getInvestigations(String runId) {
        List<DiagnosticInvestigation> entities = investigationRepository.findByRunIdOrderByPriorityScoreDesc(runId);
        if (entities.isEmpty()) {
            List<DiagnosticResult> results = resultRepository.findByRunIdOrderByIdAsc(runId);
            if (!results.isEmpty()) {
                return analyzeAndPersist(runId);
            }
        }
        return entities.stream().map(this::mapEntityToDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public InvestigationDossierDto getInvestigationDossier(String runId, String targetKey) {
        List<DiagnosticInvestigation> targets = investigationRepository.findByRunIdOrderByPriorityScoreDesc(runId);
        if (targets.isEmpty()) {
            analyzeAndPersist(runId);
            targets = investigationRepository.findByRunIdOrderByPriorityScoreDesc(runId);
        }

        DiagnosticInvestigation entity = targets.stream()
                .filter(t -> t.getTargetKey().equalsIgnoreCase(targetKey) || t.getDisplayName().equalsIgnoreCase(targetKey))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Investigation target not found: " + targetKey + " in run " + runId));

        List<DiagnosticResult> results = resultRepository.findByRunIdOrderByIdAsc(runId);
        NormalizedModuleData norm = normalizer.normalize(runId, results);
        List<DiagnosticCorrelation> allCorrs = correlationRepository.findByRunIdOrderByPriorityScoreDesc(runId);

        return buildDossier(entity, norm, allCorrs);
    }

    @Transactional(readOnly = true)
    public EvidenceGraphDto getEvidenceGraph(String runId) {
        List<DiagnosticResult> results = resultRepository.findByRunIdOrderByIdAsc(runId);
        if (results.isEmpty()) {
            return emptyGraph(runId);
        }

        NormalizedModuleData norm = normalizer.normalize(runId, results);
        List<DiagnosticCorrelation> correlations = correlationRepository.findByRunIdOrderByPriorityScoreDesc(runId);
        if (correlations.isEmpty()) {
            correlationAnalysisService.analyzeAndPersist(runId);
            correlations = correlationRepository.findByRunIdOrderByPriorityScoreDesc(runId);
        }

        List<DiagnosticInvestigation> investigations = investigationRepository.findByRunIdOrderByPriorityScoreDesc(runId);
        if (investigations.isEmpty()) {
            analyzeAndPersist(runId);
            investigations = investigationRepository.findByRunIdOrderByPriorityScoreDesc(runId);
        }

        return buildEvidenceGraph(runId, norm, correlations, investigations);
    }

    private List<DiagnosticInvestigation> generateInvestigationEntities(
            String runId,
            NormalizedModuleData norm,
            List<DiagnosticCorrelation> correlations) {

        List<DiagnosticInvestigation> list = new ArrayList<>();
        Map<String, List<DiagnosticCorrelation>> corrsByFeature = new HashMap<>();
        Map<String, List<DiagnosticCorrelation>> corrsByRule = new HashMap<>();

        for (DiagnosticCorrelation c : correlations) {
            if (c.getFeature() != null && !c.getFeature().isBlank()) {
                corrsByFeature.computeIfAbsent(c.getFeature(), k -> new ArrayList<>()).add(c);
            }
            corrsByRule.computeIfAbsent(c.getRuleId(), k -> new ArrayList<>()).add(c);
        }

        // 1. Process Feature Targets
        for (String feature : norm.getAllKnownFeatures()) {
            List<DiagnosticCorrelation> featCorrs = corrsByFeature.getOrDefault(feature, Collections.emptyList());
            Set<DiagnosticModule> supportingModules = new LinkedHashSet<>();
            Map<String, Object> evidenceSummary = new LinkedHashMap<>();
            List<String> nextActions = new ArrayList<>();
            int evidenceItemCount = 0;

            // Collect Drift Evidence
            if (norm.getDriftByFeature().containsKey(feature)) {
                var d = norm.getDriftByFeature().get(feature);
                if (d.driftDetected || d.psi >= 0.05) {
                    supportingModules.add(DiagnosticModule.DRIFT);
                    evidenceSummary.put("driftPsi", d.psi);
                    evidenceSummary.put("driftSeverity", d.severity);
                    evidenceSummary.put("ksPValue", d.ksPValue);
                    evidenceItemCount++;
                }
            }

            // Collect Explainability Evidence
            if (norm.getImportanceByFeature().containsKey(feature)) {
                var imp = norm.getImportanceByFeature().get(feature);
                if (imp.rank <= 5 || imp.attributionShare >= 0.05 || imp.meanAbsShap > 0.05) {
                    supportingModules.add(DiagnosticModule.EXPLAINABILITY);
                    evidenceSummary.put("importanceRank", imp.rank);
                    evidenceSummary.put("meanAbsShap", imp.meanAbsShap);
                    evidenceSummary.put("attributionShare", imp.attributionShare);
                    evidenceItemCount++;
                }
            }

            // Collect Robustness Evidence
            if (norm.getRobustnessByFeature().containsKey(feature)) {
                var rob = norm.getRobustnessByFeature().get(feature);
                if (rob.flipRate >= 0.01 || rob.sensitivityRank <= 3) {
                    supportingModules.add(DiagnosticModule.ROBUSTNESS);
                    evidenceSummary.put("robustnessFlipRate", rob.flipRate);
                    evidenceSummary.put("robustnessSensitivityRank", rob.sensitivityRank);
                    evidenceItemCount++;
                }
            }

            // Collect Error Forensics Evidence
            if (norm.getErrorByFeature().containsKey(feature)) {
                var err = norm.getErrorByFeature().get(feature);
                if (err.isErrorEnriched || err.absoluteAssociation >= 0.10 || Math.abs(err.fpSeparation) >= 0.20 || Math.abs(err.fnSeparation) >= 0.20) {
                    supportingModules.add(DiagnosticModule.ERROR_FORENSICS);
                    evidenceSummary.put("errorCorrelation", err.correlation);
                    evidenceSummary.put("errorAbsoluteAssociation", err.absoluteAssociation);
                    evidenceSummary.put("errorAdjustedPValue", err.adjustedPValue);
                    evidenceSummary.put("fpSeparation", err.fpSeparation);
                    evidenceSummary.put("fnSeparation", err.fnSeparation);
                    evidenceItemCount++;
                }
            }

            // Collect Leakage Evidence
            if (norm.getLeakageByFeature().containsKey(feature)) {
                var leak = norm.getLeakageByFeature().get(feature);
                if (leak.isSuspicious || leak.leakageScore >= 0.50 || leak.mutualInfo >= 0.20) {
                    supportingModules.add(DiagnosticModule.LEAKAGE);
                    evidenceSummary.put("leakageMutualInfo", leak.mutualInfo);
                    evidenceSummary.put("leakageCorrelation", leak.correlation);
                    evidenceSummary.put("leakageScore", leak.leakageScore);
                    evidenceItemCount++;
                }
            }

            // Collect Data Quality Evidence
            if (norm.getQualityByFeature().containsKey(feature)) {
                var qual = norm.getQualityByFeature().get(feature);
                if (qual.nullRate >= 0.01 || qual.outlierRate >= 0.05 || qual.isConstant) {
                    supportingModules.add(DiagnosticModule.DATA_QUALITY);
                    evidenceSummary.put("qualityNullRate", qual.nullRate);
                    evidenceSummary.put("qualityOutlierRate", qual.outlierRate);
                    evidenceItemCount++;
                }
            }

            for (DiagnosticCorrelation c : featCorrs) {
                try {
                    List<String> srcMods = objectMapper.readValue(c.getSourceModulesJson(), new TypeReference<List<String>>() {});
                    for (String sm : srcMods) {
                        try {
                            supportingModules.add(DiagnosticModule.valueOf(sm));
                        } catch (Exception ignored) {}
                    }
                } catch (Exception ignored) {}
            }

            if (supportingModules.isEmpty() && featCorrs.isEmpty()) {
                continue;
            }

            // Calculate deterministic score
            double priorityScore = calculateFeaturePriorityScore(feature, norm, featCorrs, supportingModules.size());
            InvestigationPriority priority = derivePriorityLevel(priorityScore);
            EvidenceConfidence confidence = deriveEvidenceConfidence(supportingModules.size(), priorityScore, featCorrs);

            String hypothesis = generateFeatureHypothesis(feature, supportingModules, evidenceSummary, featCorrs);
            nextActions = generateFeatureNextActions(feature, supportingModules, evidenceSummary);
            List<InvestigationPathStepDto> pathSteps = generateFeaturePathSteps(feature, norm, supportingModules, featCorrs);

            DiagnosticInvestigation inv = new DiagnosticInvestigation();
            inv.setRunId(runId);
            inv.setTargetType("FEATURE");
            inv.setTargetKey("FEATURE::" + feature);
            inv.setDisplayName(feature);
            inv.setPriority(priority);
            inv.setPriorityScore(Math.round(priorityScore * 10.0) / 10.0);
            inv.setConfidence(confidence);
            inv.setSupportingModuleCount(supportingModules.size());
            inv.setSupportingFindingCount(featCorrs.size());
            inv.setSupportingEvidenceCount(evidenceItemCount);
            inv.setGraphDegree(supportingModules.size() + featCorrs.size() + evidenceItemCount);
            inv.setHypothesis(hypothesis);
            inv.setCreatedAt(Instant.now());
            inv.setUpdatedAt(Instant.now());

            try {
                inv.setSupportingModulesJson(objectMapper.writeValueAsString(supportingModules.stream().map(Enum::name).toList()));
                inv.setSupportingRuleIdsJson(objectMapper.writeValueAsString(featCorrs.stream().map(DiagnosticCorrelation::getRuleId).toList()));
                inv.setNextActionsJson(objectMapper.writeValueAsString(nextActions));
                inv.setEvidenceSummaryJson(objectMapper.writeValueAsString(evidenceSummary));
                inv.setInvestigationPathJson(objectMapper.writeValueAsString(pathSteps));

                Map<String, Long> resultIdMap = new LinkedHashMap<>();
                for (DiagnosticModule m : supportingModules) {
                    Long rid = norm.getModuleResultIds().get(m);
                    if (rid != null) resultIdMap.put(m.name(), rid);
                }
                inv.setProvenanceJson(objectMapper.writeValueAsString(resultIdMap));
            } catch (Exception ignored) {}

            list.add(inv);
        }

        // 2. Process Subgroup Targets
        if (norm.getBiasSummary() != null || norm.getErrorForensicsSummary() != null) {
            String protectedAttr = norm.getBiasSummary() != null && norm.getBiasSummary().protectedAttribute != null
                    ? norm.getBiasSummary().protectedAttribute : "";

            Set<String> subGroups = new LinkedHashSet<>();
            if (norm.getErrorForensicsSummary() != null && norm.getErrorForensicsSummary().subgroupErrorRates != null) {
                subGroups.addAll(norm.getErrorForensicsSummary().subgroupErrorRates.keySet());
            }

            for (String group : subGroups) {
                if (group.isBlank()) continue;
                Set<DiagnosticModule> supportingModules = new LinkedHashSet<>();
                Map<String, Object> evidenceSummary = new LinkedHashMap<>();
                List<DiagnosticCorrelation> subgroupCorrs = new ArrayList<>();

                supportingModules.add(DiagnosticModule.ERROR_FORENSICS);
                double errRate = norm.getErrorForensicsSummary().subgroupErrorRates.getOrDefault(group, 0.0);
                evidenceSummary.put("subgroupErrorRate", errRate);

                if (norm.getBiasSummary() != null) {
                    supportingModules.add(DiagnosticModule.BIAS);
                    evidenceSummary.put("protectedAttribute", protectedAttr);
                    evidenceSummary.put("disparateImpactRatio", norm.getBiasSummary().worstDisparateImpactRatio);
                    evidenceSummary.put("demographicParityGap", norm.getBiasSummary().demographicParityGap);
                }

                for (DiagnosticCorrelation c : correlations) {
                    if ("ERROR_BIAS_INTERACTION".equals(c.getRuleId()) || "BIAS_PERFORMANCE_INTERACTION".equals(c.getRuleId()) ||
                        "BIAS_DRIFT_INTERACTION".equals(c.getRuleId()) || "MULTI_FAIRNESS_SHIFT_RISK".equals(c.getRuleId())) {
                        subgroupCorrs.add(c);
                    }
                }

                double priorityScore = 60.0;
                if (errRate >= 0.15) priorityScore += 15.0;
                if (norm.getBiasSummary() != null && norm.getBiasSummary().worstDisparateImpactRatio <= 0.80) priorityScore += 12.0;
                if (!subgroupCorrs.isEmpty()) priorityScore += Math.min(subgroupCorrs.size() * 3.0, 10.0);
                priorityScore = Math.min(priorityScore, 98.0);

                InvestigationPriority priority = derivePriorityLevel(priorityScore);
                EvidenceConfidence confidence = deriveEvidenceConfidence(supportingModules.size(), priorityScore, subgroupCorrs);

                String canonicalKey = "SUBGROUP::" + (protectedAttr.isBlank() ? "subgroup" : protectedAttr) + "=" + group;
                String hypothesis = String.format(
                        "The '%s' subgroup shows elevated error rate (%.1f%%) and converging fairness disparities across %s, warranting subgroup-specific behavioral investigation.",
                        group, errRate * 100.0, String.join(", ", supportingModules.stream().map(Enum::name).toList())
                );

                List<String> nextActions = List.of(
                        String.format("Inspect subgroup confusion matrix and false positive/negative distribution for '%s'.", group),
                        "Compare subgroup calibration error and positive prediction density against baseline population.",
                        "Review fairness disparity metrics (Disparate Impact, Equal Opportunity Gap) across subgroup slices."
                );

                List<InvestigationPathStepDto> pathSteps = new ArrayList<>();
                pathSteps.add(new InvestigationPathStepDto(1, "TARGET_IDENTIFICATION", "ORCHESTRATOR", "Subgroup Target", canonicalKey, "Target", 1.0, "N/A", null));
                pathSteps.add(new InvestigationPathStepDto(2, "MODULE_OBSERVATION", "ERROR_FORENSICS", "Subgroup Error Concentration", String.format("Error rate = %.2f%%", errRate * 100.0), "errorRate", errRate, ">= 10.0%", norm.getModuleResultIds().get(DiagnosticModule.ERROR_FORENSICS)));
                if (norm.getBiasSummary() != null) {
                    pathSteps.add(new InvestigationPathStepDto(3, "MODULE_OBSERVATION", "BIAS", "Disparate Impact Signal", String.format("Worst Disparate Impact = %.3f", norm.getBiasSummary().worstDisparateImpactRatio), "disparateImpactRatio", norm.getBiasSummary().worstDisparateImpactRatio, "<= 0.80", norm.getModuleResultIds().get(DiagnosticModule.BIAS)));
                }

                DiagnosticInvestigation inv = new DiagnosticInvestigation();
                inv.setRunId(runId);
                inv.setTargetType("SUBGROUP");
                inv.setTargetKey(canonicalKey);
                inv.setDisplayName((protectedAttr.isBlank() ? "subgroup" : protectedAttr) + "=" + group);
                inv.setPriority(priority);
                inv.setPriorityScore(Math.round(priorityScore * 10.0) / 10.0);
                inv.setConfidence(confidence);
                inv.setSupportingModuleCount(supportingModules.size());
                inv.setSupportingFindingCount(subgroupCorrs.size());
                inv.setSupportingEvidenceCount(evidenceSummary.size());
                inv.setGraphDegree(supportingModules.size() + subgroupCorrs.size() + evidenceSummary.size());
                inv.setHypothesis(hypothesis);
                inv.setCreatedAt(Instant.now());
                inv.setUpdatedAt(Instant.now());

                try {
                    inv.setSupportingModulesJson(objectMapper.writeValueAsString(supportingModules.stream().map(Enum::name).toList()));
                    inv.setSupportingRuleIdsJson(objectMapper.writeValueAsString(subgroupCorrs.stream().map(DiagnosticCorrelation::getRuleId).toList()));
                    inv.setNextActionsJson(objectMapper.writeValueAsString(nextActions));
                    inv.setEvidenceSummaryJson(objectMapper.writeValueAsString(evidenceSummary));
                    inv.setInvestigationPathJson(objectMapper.writeValueAsString(pathSteps));

                    Map<String, Long> resultIdMap = new LinkedHashMap<>();
                    for (DiagnosticModule m : supportingModules) {
                        Long rid = norm.getModuleResultIds().get(m);
                        if (rid != null) resultIdMap.put(m.name(), rid);
                    }
                    inv.setProvenanceJson(objectMapper.writeValueAsString(resultIdMap));
                } catch (Exception ignored) {}

                list.add(inv);
            }
        }

        // 3. Process Model Behavior Targets (High Confidence Errors / Calibration Failures)
        if (norm.getErrorForensicsSummary() != null) {
            var errSum = norm.getErrorForensicsSummary();
            if (errSum.highConfidenceErrorCount > 0 || errSum.highConfidenceErrorRate >= 0.10) {
                Set<DiagnosticModule> supportingModules = new LinkedHashSet<>();
                supportingModules.add(DiagnosticModule.ERROR_FORENSICS);
                if (norm.getPerformanceSummary() != null) supportingModules.add(DiagnosticModule.PERFORMANCE);

                Map<String, Object> evidenceSummary = new LinkedHashMap<>();
                evidenceSummary.put("highConfidenceErrorCount", errSum.highConfidenceErrorCount);
                evidenceSummary.put("highConfidenceErrorRate", errSum.highConfidenceErrorRate);
                evidenceSummary.put("expectedCalibrationError", errSum.expectedCalibrationError);

                List<DiagnosticCorrelation> behaviorCorrs = correlations.stream()
                        .filter(c -> "CONFIDENCE_CALIBRATION_ERROR".equals(c.getRuleId()))
                        .toList();

                double score = 75.0 + Math.min(errSum.highConfidenceErrorRate * 40.0, 15.0);
                if (errSum.expectedCalibrationError >= 0.10) score += 6.0;
                score = Math.min(score, 98.0);

                InvestigationPriority priority = derivePriorityLevel(score);
                EvidenceConfidence confidence = deriveEvidenceConfidence(supportingModules.size(), score, behaviorCorrs);

                String hypothesis = String.format(
                        "A concentration of high-confidence mistakes (%d errors, %.1f%% rate) coincides with calibration gap (ECE = %.3f) and warrants threshold and calibration investigation.",
                        errSum.highConfidenceErrorCount, errSum.highConfidenceErrorRate * 100.0, errSum.expectedCalibrationError
                );

                List<String> nextActions = List.of(
                        "Review individual high-confidence mistake records in Error Forensics dossier.",
                        "Inspect calibration curve reliability diagram across upper confidence deciles.",
                        "Assess threshold tuning curve to evaluate precision/recall trade-offs around current decision boundary."
                );

                List<InvestigationPathStepDto> pathSteps = new ArrayList<>();
                pathSteps.add(new InvestigationPathStepDto(1, "TARGET_IDENTIFICATION", "ORCHESTRATOR", "Behavior Target", "BEHAVIOR::HIGH_CONFIDENCE_ERRORS", "Target", 1.0, "N/A", null));
                pathSteps.add(new InvestigationPathStepDto(2, "MODULE_OBSERVATION", "ERROR_FORENSICS", "High Confidence Misclassifications", String.format("Count = %d, Rate = %.1f%%", errSum.highConfidenceErrorCount, errSum.highConfidenceErrorRate * 100.0), "highConfidenceErrorRate", errSum.highConfidenceErrorRate, ">= 10.0%", norm.getModuleResultIds().get(DiagnosticModule.ERROR_FORENSICS)));
                if (norm.getPerformanceSummary() != null) {
                    pathSteps.add(new InvestigationPathStepDto(3, "MODULE_OBSERVATION", "PERFORMANCE", "Expected Calibration Error", String.format("ECE = %.4f", norm.getPerformanceSummary().expectedCalibrationError), "expectedCalibrationError", norm.getPerformanceSummary().expectedCalibrationError, ">= 0.05", norm.getModuleResultIds().get(DiagnosticModule.PERFORMANCE)));
                }

                DiagnosticInvestigation inv = new DiagnosticInvestigation();
                inv.setRunId(runId);
                inv.setTargetType("BEHAVIOR");
                inv.setTargetKey("BEHAVIOR::HIGH_CONFIDENCE_ERRORS");
                inv.setDisplayName("High-Confidence Misclassifications");
                inv.setPriority(priority);
                inv.setPriorityScore(Math.round(score * 10.0) / 10.0);
                inv.setConfidence(confidence);
                inv.setSupportingModuleCount(supportingModules.size());
                inv.setSupportingFindingCount(behaviorCorrs.size());
                inv.setSupportingEvidenceCount(evidenceSummary.size());
                inv.setGraphDegree(supportingModules.size() + behaviorCorrs.size() + evidenceSummary.size());
                inv.setHypothesis(hypothesis);
                inv.setCreatedAt(Instant.now());
                inv.setUpdatedAt(Instant.now());

                try {
                    inv.setSupportingModulesJson(objectMapper.writeValueAsString(supportingModules.stream().map(Enum::name).toList()));
                    inv.setSupportingRuleIdsJson(objectMapper.writeValueAsString(behaviorCorrs.stream().map(DiagnosticCorrelation::getRuleId).toList()));
                    inv.setNextActionsJson(objectMapper.writeValueAsString(nextActions));
                    inv.setEvidenceSummaryJson(objectMapper.writeValueAsString(evidenceSummary));
                    inv.setInvestigationPathJson(objectMapper.writeValueAsString(pathSteps));

                    Map<String, Long> resultIdMap = new LinkedHashMap<>();
                    for (DiagnosticModule m : supportingModules) {
                        Long rid = norm.getModuleResultIds().get(m);
                        if (rid != null) resultIdMap.put(m.name(), rid);
                    }
                    inv.setProvenanceJson(objectMapper.writeValueAsString(resultIdMap));
                } catch (Exception ignored) {}

                list.add(inv);
            }
        }

        // Sort all targets deterministically by Priority Score DESC, then Supporting Module Count DESC, then TargetKey ASC
        list.sort((a, b) -> {
            int scoreCmp = Double.compare(b.getPriorityScore(), a.getPriorityScore());
            if (scoreCmp != 0) return scoreCmp;
            int modCmp = Integer.compare(b.getSupportingModuleCount(), a.getSupportingModuleCount());
            if (modCmp != 0) return modCmp;
            return a.getTargetKey().compareTo(b.getTargetKey());
        });

        return list;
    }

    private double calculateFeaturePriorityScore(
            String feature,
            NormalizedModuleData norm,
            List<DiagnosticCorrelation> correlations,
            int supportingModuleCount) {

        double baseScore = 30.0;
        if (!correlations.isEmpty()) {
            baseScore = correlations.stream()
                    .mapToDouble(DiagnosticCorrelation::getPriorityScore)
                    .max()
                    .orElse(50.0);
        } else {
            // Estimate base from strongest module
            if (norm.getDriftByFeature().containsKey(feature) && norm.getDriftByFeature().get(feature).psi >= 0.25) {
                baseScore = 70.0;
            } else if (norm.getErrorByFeature().containsKey(feature) && norm.getErrorByFeature().get(feature).isErrorEnriched) {
                baseScore = 65.0;
            } else if (norm.getImportanceByFeature().containsKey(feature) && norm.getImportanceByFeature().get(feature).rank <= 2) {
                baseScore = 55.0;
            }
        }

        // Module convergence bonus: +3.5 per independent module beyond the 1st
        double moduleBonus = Math.min((supportingModuleCount - 1) * 3.5, 14.0);

        // Signal strength adjustments
        double signalBonus = 0.0;
        if (norm.getDriftByFeature().containsKey(feature)) {
            var d = norm.getDriftByFeature().get(feature);
            if (d.psi >= 0.25) signalBonus += 3.0;
            else if (d.psi >= 0.10) signalBonus += 1.5;
        }
        if (norm.getImportanceByFeature().containsKey(feature)) {
            var imp = norm.getImportanceByFeature().get(feature);
            if (imp.rank == 1) signalBonus += 3.0;
            else if (imp.rank <= 3) signalBonus += 1.5;
        }
        if (norm.getErrorByFeature().containsKey(feature)) {
            var err = norm.getErrorByFeature().get(feature);
            if (err.absoluteAssociation >= 0.25) signalBonus += 3.0;
            else if (err.absoluteAssociation >= 0.15) signalBonus += 1.5;
        }
        if (norm.getRobustnessByFeature().containsKey(feature)) {
            var rob = norm.getRobustnessByFeature().get(feature);
            if (rob.flipRate >= 0.10) signalBonus += 2.5;
            else if (rob.flipRate >= 0.03) signalBonus += 1.0;
        }

        double total = baseScore + moduleBonus + Math.min(signalBonus, 10.0);
        return Math.min(Math.max(total, 10.0), 99.5);
    }

    private InvestigationPriority derivePriorityLevel(double score) {
        if (score >= 85.0) return InvestigationPriority.CRITICAL;
        if (score >= 65.0) return InvestigationPriority.HIGH;
        if (score >= 45.0) return InvestigationPriority.MEDIUM;
        if (score >= 25.0) return InvestigationPriority.LOW;
        return InvestigationPriority.INFO;
    }

    private EvidenceConfidence deriveEvidenceConfidence(int supportingModuleCount, double priorityScore, List<DiagnosticCorrelation> correlations) {
        if (supportingModuleCount >= 3 || (supportingModuleCount >= 2 && priorityScore >= 80.0)) {
            return EvidenceConfidence.HIGH;
        }
        if (supportingModuleCount >= 2 || priorityScore >= 60.0) {
            return EvidenceConfidence.MEDIUM;
        }
        return EvidenceConfidence.LOW;
    }

    private String generateFeatureHypothesis(
            String feature,
            Set<DiagnosticModule> modules,
            Map<String, Object> evidenceSummary,
            List<DiagnosticCorrelation> correlations) {

        List<String> modNames = modules.stream().map(Enum::name).toList();
        List<String> highlights = new ArrayList<>();

        if (evidenceSummary.containsKey("driftPsi")) {
            highlights.add(String.format("distribution shift (PSI = %.3f)", (Double) evidenceSummary.get("driftPsi")));
        }
        if (evidenceSummary.containsKey("importanceRank")) {
            highlights.add(String.format("top model influence (SHAP rank #%d)", (Integer) evidenceSummary.get("importanceRank")));
        }
        if (evidenceSummary.containsKey("errorCorrelation")) {
            highlights.add(String.format("error association (r = %.3f)", (Double) evidenceSummary.get("errorCorrelation")));
        }
        if (evidenceSummary.containsKey("robustnessFlipRate")) {
            highlights.add(String.format("perturbation sensitivity (flip rate = %.1f%%)", ((Double) evidenceSummary.get("robustnessFlipRate")) * 100.0));
        }
        if (evidenceSummary.containsKey("leakageScore")) {
            highlights.add(String.format("elevated target association (leakage score = %.2f)", (Double) evidenceSummary.get("leakageScore")));
        }

        String highlightStr = highlights.isEmpty() ? "observed diagnostic signals" : String.join(", ", highlights);

        if (modules.size() >= 3) {
            return String.format("Feature '%s' is a high-priority investigation target because evidence from %s converges on %s.",
                    feature, String.join(", ", modNames), highlightStr);
        } else if (modules.size() == 2) {
            return String.format("Feature '%s' shows co-occurring signals across %s with %s.",
                    feature, String.join(" and ", modNames), highlightStr);
        } else {
            return String.format("Feature '%s' exhibits isolated elevated signal in %s with %s.",
                    feature, modNames.isEmpty() ? "diagnostics" : modNames.get(0), highlightStr);
        }
    }

    private List<String> generateFeatureNextActions(
            String feature,
            Set<DiagnosticModule> modules,
            Map<String, Object> evidenceSummary) {

        List<String> actions = new ArrayList<>();

        if (modules.contains(DiagnosticModule.DRIFT) && modules.contains(DiagnosticModule.ERROR_FORENSICS)) {
            actions.add(String.format("Inspect current evaluation distribution for '%s' against baseline; review error-rate-by-range bins.", feature));
        } else if (modules.contains(DiagnosticModule.DRIFT)) {
            actions.add(String.format("Compare evaluation vs baseline distribution quantiles for '%s' (PSI = %.3f).", feature, (Double) evidenceSummary.getOrDefault("driftPsi", 0.0)));
        }

        if (modules.contains(DiagnosticModule.EXPLAINABILITY) && modules.contains(DiagnosticModule.ERROR_FORENSICS)) {
            actions.add(String.format("Inspect SHAP dependence plot for '%s' against false positive and false negative error clusters.", feature));
        } else if (modules.contains(DiagnosticModule.EXPLAINABILITY)) {
            actions.add(String.format("Inspect SHAP summary contribution and top decision splits for '%s'.", feature));
        }

        if (modules.contains(DiagnosticModule.ROBUSTNESS)) {
            actions.add(String.format("Evaluate perturbation boundary sensitivity for '%s' to confirm resilience against input jitter.", feature));
        }

        if (modules.contains(DiagnosticModule.LEAKAGE)) {
            actions.add(String.format("Verify timestamp ordering and feature collection timing for '%s' to rule out target leakage proxy.", feature));
        }

        if (modules.contains(DiagnosticModule.DATA_QUALITY)) {
            actions.add(String.format("Check missingness and outlier distribution for '%s' across evaluation batches.", feature));
        }

        if (actions.isEmpty()) {
            actions.add(String.format("Inspect feature distribution and diagnostic probe outputs for '%s'.", feature));
        }

        return actions;
    }

    private List<InvestigationPathStepDto> generateFeaturePathSteps(
            String feature,
            NormalizedModuleData norm,
            Set<DiagnosticModule> modules,
            List<DiagnosticCorrelation> correlations) {

        List<InvestigationPathStepDto> steps = new ArrayList<>();
        int stepNum = 1;

        steps.add(new InvestigationPathStepDto(
                stepNum++,
                "TARGET_IDENTIFICATION",
                "ORCHESTRATOR",
                "Feature Target",
                "FEATURE::" + feature,
                "Target",
                1.0,
                "N/A",
                null
        ));

        if (norm.getDriftByFeature().containsKey(feature)) {
            var d = norm.getDriftByFeature().get(feature);
            steps.add(new InvestigationPathStepDto(
                    stepNum++,
                    "MODULE_OBSERVATION",
                    "DRIFT",
                    "Distribution Drift",
                    String.format("PSI = %.3f, Severity = %s", d.psi, d.severity),
                    "psi",
                    d.psi,
                    ">= 0.10",
                    norm.getModuleResultIds().get(DiagnosticModule.DRIFT)
            ));
        }

        if (norm.getImportanceByFeature().containsKey(feature)) {
            var imp = norm.getImportanceByFeature().get(feature);
            steps.add(new InvestigationPathStepDto(
                    stepNum++,
                    "MODULE_OBSERVATION",
                    "EXPLAINABILITY",
                    "Feature Importance",
                    String.format("SHAP Rank = #%d, Mean Abs SHAP = %.4f", imp.rank, imp.meanAbsShap),
                    "meanAbsShap",
                    imp.meanAbsShap,
                    "Top 5",
                    norm.getModuleResultIds().get(DiagnosticModule.EXPLAINABILITY)
            ));
        }

        if (norm.getErrorByFeature().containsKey(feature)) {
            var err = norm.getErrorByFeature().get(feature);
            steps.add(new InvestigationPathStepDto(
                    stepNum++,
                    "MODULE_OBSERVATION",
                    "ERROR_FORENSICS",
                    "Error Association",
                    String.format("Correlation = %.3f, Adjusted p-value = %.4f", err.correlation, err.adjustedPValue),
                    "errorCorrelation",
                    err.correlation,
                    "|r| >= 0.15",
                    norm.getModuleResultIds().get(DiagnosticModule.ERROR_FORENSICS)
            ));
        }

        if (norm.getRobustnessByFeature().containsKey(feature)) {
            var rob = norm.getRobustnessByFeature().get(feature);
            steps.add(new InvestigationPathStepDto(
                    stepNum++,
                    "MODULE_OBSERVATION",
                    "ROBUSTNESS",
                    "Robustness Sensitivity",
                    String.format("Prediction Flip Rate = %.2f%%", rob.flipRate * 100.0),
                    "flipRate",
                    rob.flipRate,
                    ">= 3.0%",
                    norm.getModuleResultIds().get(DiagnosticModule.ROBUSTNESS)
            ));
        }

        for (DiagnosticCorrelation c : correlations) {
            steps.add(new InvestigationPathStepDto(
                    stepNum++,
                    "CORRELATION_INTERACTION",
                    c.getFindingType(),
                    c.getTitle(),
                    c.getSummary(),
                    "priorityScore",
                    c.getPriorityScore(),
                    c.getPriority().name(),
                    null
            ));
        }

        return steps;
    }

    private InvestigationDossierDto buildDossier(
            DiagnosticInvestigation entity,
            NormalizedModuleData norm,
            List<DiagnosticCorrelation> allCorrelations) {

        InvestigationDossierDto dossier = new InvestigationDossierDto();
        dossier.setTarget(mapEntityToDto(entity));
        dossier.setHypothesis(entity.getHypothesis());
        dossier.setPriorityScore(entity.getPriorityScore());
        dossier.setPriorityLevel(entity.getPriority().name());
        dossier.setConfidence(entity.getConfidence().name());

        try {
            dossier.setSupportingModules(objectMapper.readValue(entity.getSupportingModulesJson(), new TypeReference<List<String>>() {}));
        } catch (Exception e) {
            dossier.setSupportingModules(Collections.emptyList());
        }

        try {
            dossier.setNextActions(objectMapper.readValue(entity.getNextActionsJson(), new TypeReference<List<String>>() {}));
        } catch (Exception e) {
            dossier.setNextActions(Collections.emptyList());
        }

        try {
            dossier.setInvestigationPath(objectMapper.readValue(entity.getInvestigationPathJson(), new TypeReference<List<InvestigationPathStepDto>>() {}));
        } catch (Exception e) {
            dossier.setInvestigationPath(Collections.emptyList());
        }

        // Filter supporting findings for this target
        List<DiagnosticCorrelationDto> relevantFindings = new ArrayList<>();
        for (DiagnosticCorrelation c : allCorrelations) {
            if ("FEATURE".equalsIgnoreCase(entity.getTargetType()) && entity.getDisplayName().equalsIgnoreCase(c.getFeature())) {
                relevantFindings.add(correlationAnalysisService.mapToDto(c));
            } else if ("SUBGROUP".equalsIgnoreCase(entity.getTargetType()) &&
                       (c.getRuleId().contains("BIAS") || c.getRuleId().contains("FAIRNESS"))) {
                relevantFindings.add(correlationAnalysisService.mapToDto(c));
            } else if ("BEHAVIOR".equalsIgnoreCase(entity.getTargetType()) &&
                       c.getRuleId().contains("CALIBRATION")) {
                relevantFindings.add(correlationAnalysisService.mapToDto(c));
            }
        }
        dossier.setSupportingFindings(relevantFindings);

        // Gather structured evidence metrics across modules
        Map<String, Map<String, Object>> metricsMap = new LinkedHashMap<>();
        String feat = entity.getDisplayName();
        if ("FEATURE".equalsIgnoreCase(entity.getTargetType())) {
            if (norm.getDriftByFeature().containsKey(feat)) {
                var d = norm.getDriftByFeature().get(feat);
                metricsMap.put("DRIFT", Map.of("psi", d.psi, "severity", d.severity, "ksPValue", d.ksPValue, "wasserstein", d.wasserstein));
            }
            if (norm.getImportanceByFeature().containsKey(feat)) {
                var imp = norm.getImportanceByFeature().get(feat);
                metricsMap.put("EXPLAINABILITY", Map.of("rank", imp.rank, "meanAbsShap", imp.meanAbsShap, "attributionShare", imp.attributionShare));
            }
            if (norm.getErrorByFeature().containsKey(feat)) {
                var err = norm.getErrorByFeature().get(feat);
                metricsMap.put("ERROR_FORENSICS", Map.of("correlation", err.correlation, "absoluteAssociation", err.absoluteAssociation, "adjustedPValue", err.adjustedPValue, "fpSeparation", err.fpSeparation, "fnSeparation", err.fnSeparation));
            }
            if (norm.getRobustnessByFeature().containsKey(feat)) {
                var rob = norm.getRobustnessByFeature().get(feat);
                metricsMap.put("ROBUSTNESS", Map.of("sensitivityRank", rob.sensitivityRank, "flipRate", rob.flipRate, "probabilityShift", rob.meanProbabilityShift));
            }
            if (norm.getLeakageByFeature().containsKey(feat)) {
                var leak = norm.getLeakageByFeature().get(feat);
                metricsMap.put("LEAKAGE", Map.of("mutualInfo", leak.mutualInfo, "correlation", leak.correlation, "leakageScore", leak.leakageScore));
            }
            if (norm.getQualityByFeature().containsKey(feat)) {
                var q = norm.getQualityByFeature().get(feat);
                metricsMap.put("DATA_QUALITY", Map.of("nullRate", q.nullRate, "outlierRate", q.outlierRate, "isConstant", q.isConstant));
            }
        }
        dossier.setEvidenceMetrics(metricsMap);

        // Provenance details
        InvestigationProvenanceDto prov = new InvestigationProvenanceDto();
        prov.setRunId(entity.getRunId());
        prov.setTargetKey(entity.getTargetKey());
        prov.setSourceModules(dossier.getSupportingModules());
        prov.setDataFreshness(entity.getUpdatedAt());
        prov.setImmutableResult(true);

        try {
            prov.setSourceResultIds(objectMapper.readValue(entity.getProvenanceJson(), new TypeReference<Map<String, Long>>() {}));
        } catch (Exception e) {
            prov.setSourceResultIds(Collections.emptyMap());
        }

        try {
            prov.setRulesEvaluated(objectMapper.readValue(entity.getSupportingRuleIdsJson(), new TypeReference<List<String>>() {}));
        } catch (Exception e) {
            prov.setRulesEvaluated(Collections.emptyList());
        }

        dossier.setProvenance(prov);

        List<String> connectedNodes = new ArrayList<>();
        connectedNodes.add(entity.getTargetKey());
        for (String m : dossier.getSupportingModules()) {
            connectedNodes.add("MODULE::" + m);
        }
        for (DiagnosticCorrelationDto f : relevantFindings) {
            connectedNodes.add("FINDING::" + f.getRuleId() + "_" + (f.getFeature() != null ? f.getFeature() : "GLOBAL"));
        }
        dossier.setConnectedNodeIds(connectedNodes);

        return dossier;
    }

    private EvidenceGraphDto buildEvidenceGraph(
            String runId,
            NormalizedModuleData norm,
            List<DiagnosticCorrelation> correlations,
            List<DiagnosticInvestigation> investigations) {

        EvidenceGraphDto graph = new EvidenceGraphDto();
        graph.setRunId(runId);
        graph.setAssociativeOnly(true);

        Map<String, EvidenceGraphNodeDto> nodeMap = new LinkedHashMap<>();
        List<EvidenceGraphEdgeDto> edges = new ArrayList<>();
        Map<String, Integer> degreeMap = new HashMap<>();

        // 1. Add Module Nodes
        for (DiagnosticModule mod : norm.getAvailableModules()) {
            String nodeId = "MODULE::" + mod.name();
            Long resId = norm.getModuleResultIds().get(mod);
            nodeMap.put(nodeId, new EvidenceGraphNodeDto(
                    nodeId,
                    mod.name(),
                    "MODULE",
                    "MODULE",
                    "INFO",
                    70.0,
                    0,
                    Map.of("module", mod.name(), "sourceResultId", resId != null ? resId : -1L)
            ));
        }

        // 2. Add Top Investigation Target Nodes (cap at top 30 for clean visual graph)
        List<DiagnosticInvestigation> topTargets = investigations.stream().limit(30).toList();
        for (DiagnosticInvestigation t : topTargets) {
            String nodeId = t.getTargetKey();
            nodeMap.put(nodeId, new EvidenceGraphNodeDto(
                    nodeId,
                    t.getDisplayName(),
                    t.getTargetType(),
                    "TARGET",
                    t.getPriority().name(),
                    t.getPriorityScore(),
                    0,
                    Map.of("targetType", t.getTargetType(), "confidence", t.getConfidence().name(), "hypothesis", t.getHypothesis())
            ));
        }

        // 3. Add Finding Nodes
        for (DiagnosticCorrelation c : correlations.stream().limit(25).toList()) {
            String findingNodeId = "FINDING::" + c.getRuleId() + "_" + (c.getFeature() != null ? c.getFeature() : "GLOBAL");
            nodeMap.put(findingNodeId, new EvidenceGraphNodeDto(
                    findingNodeId,
                    c.getTitle(),
                    "FINDING",
                    "FINDING",
                    c.getSeverity().name(),
                    c.getPriorityScore(),
                    0,
                    Map.of("ruleId", c.getRuleId(), "priority", c.getPriority().name(), "summary", c.getSummary())
            ));

            // Edge: MODULE -> PRODUCES -> FINDING
            try {
                List<String> srcMods = objectMapper.readValue(c.getSourceModulesJson(), new TypeReference<List<String>>() {});
                for (String sm : srcMods) {
                    String modNodeId = "MODULE::" + sm;
                    if (nodeMap.containsKey(modNodeId)) {
                        String edgeId = "edge_" + sm + "_PRODUCES_" + c.getRuleId() + "_" + (c.getFeature() != null ? c.getFeature() : "GLOBAL");
                        EvidenceGraphEdgeDto edge = new EvidenceGraphEdgeDto();
                        edge.setId(edgeId);
                        edge.setSource(modNodeId);
                        edge.setTarget(findingNodeId);
                        edge.setRelationship("PRODUCES");
                        edge.setSourceModule(sm);
                        edge.setSourceResultId(norm.getModuleResultIds().get(DiagnosticModule.valueOf(sm)));
                        edge.setRuleId(c.getRuleId());
                        edge.setMetricName("findingSeverity");
                        edge.setMetricValue(c.getPriorityScore());
                        edge.setEvidenceStrength(c.getConfidence().name());
                        edge.setWeight(0.85);
                        edge.setDescription("Module generated correlation finding " + c.getTitle());
                        edges.add(edge);

                        degreeMap.merge(modNodeId, 1, Integer::sum);
                        degreeMap.merge(findingNodeId, 1, Integer::sum);
                    }
                }
            } catch (Exception ignored) {}

            // Edge: FINDING -> IMPLICATES -> TARGET
            if (c.getFeature() != null && !c.getFeature().isBlank()) {
                String targetNodeId = "FEATURE::" + c.getFeature();
                if (nodeMap.containsKey(targetNodeId)) {
                    String edgeId = "edge_" + findingNodeId + "_IMPLICATES_" + c.getFeature();
                    EvidenceGraphEdgeDto edge = new EvidenceGraphEdgeDto();
                    edge.setId(edgeId);
                    edge.setSource(findingNodeId);
                    edge.setTarget(targetNodeId);
                    edge.setRelationship("IMPLICATES");
                    String primaryMod = "CORRELATION_ENGINE";
                    try {
                        List<String> srcMods = objectMapper.readValue(c.getSourceModulesJson(), new TypeReference<List<String>>() {});
                        if (srcMods != null && !srcMods.isEmpty()) primaryMod = srcMods.get(0);
                    } catch (Exception ignored) {}
                    edge.setSourceModule(primaryMod);
                    edge.setRuleId(c.getRuleId());
                    edge.setMetricName("priorityScore");
                    edge.setMetricValue(c.getPriorityScore());
                    edge.setEvidenceStrength(c.getConfidence().name());
                    edge.setWeight(0.90);
                    edge.setDescription(String.format("Finding '%s' implicates feature %s", c.getTitle(), c.getFeature()));
                    edges.add(edge);

                    degreeMap.merge(findingNodeId, 1, Integer::sum);
                    degreeMap.merge(targetNodeId, 1, Integer::sum);
                }
            }
        }

        // 4. Add Module -> Target observational evidence edges
        for (DiagnosticInvestigation t : topTargets) {
            String targetNodeId = t.getTargetKey();
            String feat = t.getDisplayName();

            if ("FEATURE".equalsIgnoreCase(t.getTargetType())) {
                // DRIFT edge
                if (norm.getDriftByFeature().containsKey(feat)) {
                    var d = norm.getDriftByFeature().get(feat);
                    if (d.psi >= 0.05 && nodeMap.containsKey("MODULE::DRIFT")) {
                        String edgeId = "edge_DRIFT_OBSERVES_" + feat;
                        EvidenceGraphEdgeDto edge = new EvidenceGraphEdgeDto();
                        edge.setId(edgeId);
                        edge.setSource("MODULE::DRIFT");
                        edge.setTarget(targetNodeId);
                        edge.setRelationship("OBSERVES");
                        edge.setSourceModule("DRIFT");
                        edge.setSourceResultId(norm.getModuleResultIds().get(DiagnosticModule.DRIFT));
                        edge.setMetricName("psi");
                        edge.setMetricValue(d.psi);
                        edge.setThreshold(0.10);
                        edge.setThresholdComparison(">= 0.10");
                        edge.setEvidenceStrength(d.psi >= 0.25 ? "HIGH" : "MEDIUM");
                        edge.setWeight(Math.min(d.psi, 1.0));
                        edge.setDescription(String.format("Observed distribution shift (PSI = %.3f, severity = %s)", d.psi, d.severity));
                        edges.add(edge);

                        degreeMap.merge("MODULE::DRIFT", 1, Integer::sum);
                        degreeMap.merge(targetNodeId, 1, Integer::sum);
                    }
                }

                // EXPLAINABILITY edge
                if (norm.getImportanceByFeature().containsKey(feat)) {
                    var imp = norm.getImportanceByFeature().get(feat);
                    if (imp.rank <= 5 && nodeMap.containsKey("MODULE::EXPLAINABILITY")) {
                        String edgeId = "edge_EXPLAINABILITY_OBSERVES_" + feat;
                        EvidenceGraphEdgeDto edge = new EvidenceGraphEdgeDto();
                        edge.setId(edgeId);
                        edge.setSource("MODULE::EXPLAINABILITY");
                        edge.setTarget(targetNodeId);
                        edge.setRelationship("INFLUENCES");
                        edge.setSourceModule("EXPLAINABILITY");
                        edge.setSourceResultId(norm.getModuleResultIds().get(DiagnosticModule.EXPLAINABILITY));
                        edge.setMetricName("meanAbsShap");
                        edge.setMetricValue(imp.meanAbsShap);
                        edge.setThresholdComparison("Rank #" + imp.rank);
                        edge.setEvidenceStrength(imp.rank <= 2 ? "HIGH" : "MEDIUM");
                        edge.setWeight(Math.min(imp.attributionShare * 2.0, 1.0));
                        edge.setDescription(String.format("Influences model predictions (SHAP rank #%d, attribution share = %.1f%%)", imp.rank, imp.attributionShare * 100.0));
                        edges.add(edge);

                        degreeMap.merge("MODULE::EXPLAINABILITY", 1, Integer::sum);
                        degreeMap.merge(targetNodeId, 1, Integer::sum);
                    }
                }

                // ERROR_FORENSICS edge
                if (norm.getErrorByFeature().containsKey(feat)) {
                    var err = norm.getErrorByFeature().get(feat);
                    if (err.absoluteAssociation >= 0.10 && nodeMap.containsKey("MODULE::ERROR_FORENSICS")) {
                        String edgeId = "edge_ERROR_FORENSICS_OBSERVES_" + feat;
                        EvidenceGraphEdgeDto edge = new EvidenceGraphEdgeDto();
                        edge.setId(edgeId);
                        edge.setSource("MODULE::ERROR_FORENSICS");
                        edge.setTarget(targetNodeId);
                        edge.setRelationship("ASSOCIATED_WITH");
                        edge.setSourceModule("ERROR_FORENSICS");
                        edge.setSourceResultId(norm.getModuleResultIds().get(DiagnosticModule.ERROR_FORENSICS));
                        edge.setMetricName("errorCorrelation");
                        edge.setMetricValue(err.correlation);
                        edge.setThreshold(0.15);
                        edge.setThresholdComparison("|r| >= 0.15, p < 0.05");
                        edge.setEvidenceStrength(err.isErrorEnriched ? "HIGH" : "MEDIUM");
                        edge.setWeight(Math.min(err.absoluteAssociation * 2.0, 1.0));
                        edge.setDescription(String.format("Associated with prediction errors (r = %.3f, p_adj = %.4f)", err.correlation, err.adjustedPValue));
                        edges.add(edge);

                        degreeMap.merge("MODULE::ERROR_FORENSICS", 1, Integer::sum);
                        degreeMap.merge(targetNodeId, 1, Integer::sum);
                    }
                }

                // ROBUSTNESS edge
                if (norm.getRobustnessByFeature().containsKey(feat)) {
                    var rob = norm.getRobustnessByFeature().get(feat);
                    if (rob.flipRate >= 0.01 && nodeMap.containsKey("MODULE::ROBUSTNESS")) {
                        String edgeId = "edge_ROBUSTNESS_OBSERVES_" + feat;
                        EvidenceGraphEdgeDto edge = new EvidenceGraphEdgeDto();
                        edge.setId(edgeId);
                        edge.setSource("MODULE::ROBUSTNESS");
                        edge.setTarget(targetNodeId);
                        edge.setRelationship("SENSITIVE_UNDER");
                        edge.setSourceModule("ROBUSTNESS");
                        edge.setSourceResultId(norm.getModuleResultIds().get(DiagnosticModule.ROBUSTNESS));
                        edge.setMetricName("flipRate");
                        edge.setMetricValue(rob.flipRate);
                        edge.setThreshold(0.03);
                        edge.setThresholdComparison(">= 3.0%");
                        edge.setEvidenceStrength(rob.flipRate >= 0.10 ? "HIGH" : "MEDIUM");
                        edge.setWeight(Math.min(rob.flipRate * 3.0, 1.0));
                        edge.setDescription(String.format("Sensitive to input perturbation (flip rate = %.1f%%, sensitivity rank #%d)", rob.flipRate * 100.0, rob.sensitivityRank));
                        edges.add(edge);

                        degreeMap.merge("MODULE::ROBUSTNESS", 1, Integer::sum);
                        degreeMap.merge(targetNodeId, 1, Integer::sum);
                    }
                }
            } else if ("SUBGROUP".equalsIgnoreCase(t.getTargetType())) {
                if (nodeMap.containsKey("MODULE::ERROR_FORENSICS")) {
                    String edgeId = "edge_ERROR_FORENSICS_OBSERVES_" + t.getTargetKey();
                    EvidenceGraphEdgeDto edge = new EvidenceGraphEdgeDto();
                    edge.setId(edgeId);
                    edge.setSource("MODULE::ERROR_FORENSICS");
                    edge.setTarget(targetNodeId);
                    edge.setRelationship("OBSERVES");
                    edge.setSourceModule("ERROR_FORENSICS");
                    edge.setSourceResultId(norm.getModuleResultIds().get(DiagnosticModule.ERROR_FORENSICS));
                    edge.setMetricName("subgroupErrorRate");
                    edge.setEvidenceStrength("HIGH");
                    edge.setWeight(0.85);
                    edge.setDescription("Observed elevated error rate in subgroup slice");
                    edges.add(edge);

                    degreeMap.merge("MODULE::ERROR_FORENSICS", 1, Integer::sum);
                    degreeMap.merge(targetNodeId, 1, Integer::sum);
                }
                if (nodeMap.containsKey("MODULE::BIAS")) {
                    String edgeId = "edge_BIAS_OBSERVES_" + t.getTargetKey();
                    EvidenceGraphEdgeDto edge = new EvidenceGraphEdgeDto();
                    edge.setId(edgeId);
                    edge.setSource("MODULE::BIAS");
                    edge.setTarget(targetNodeId);
                    edge.setRelationship("OBSERVES");
                    edge.setSourceModule("BIAS");
                    edge.setSourceResultId(norm.getModuleResultIds().get(DiagnosticModule.BIAS));
                    edge.setMetricName("disparateImpactRatio");
                    edge.setEvidenceStrength("HIGH");
                    edge.setWeight(0.80);
                    edge.setDescription("Observed disparate impact disparity in protected subgroup");
                    edges.add(edge);

                    degreeMap.merge("MODULE::BIAS", 1, Integer::sum);
                    degreeMap.merge(targetNodeId, 1, Integer::sum);
                }
            } else if ("BEHAVIOR".equalsIgnoreCase(t.getTargetType())) {
                if (nodeMap.containsKey("MODULE::ERROR_FORENSICS")) {
                    String edgeId = "edge_ERROR_FORENSICS_OBSERVES_" + t.getTargetKey();
                    EvidenceGraphEdgeDto edge = new EvidenceGraphEdgeDto();
                    edge.setId(edgeId);
                    edge.setSource("MODULE::ERROR_FORENSICS");
                    edge.setTarget(targetNodeId);
                    edge.setRelationship("OBSERVES");
                    edge.setSourceModule("ERROR_FORENSICS");
                    edge.setSourceResultId(norm.getModuleResultIds().get(DiagnosticModule.ERROR_FORENSICS));
                    edge.setMetricName("highConfidenceErrorRate");
                    edge.setEvidenceStrength("HIGH");
                    edge.setWeight(0.90);
                    edge.setDescription("Observed high-confidence mistake concentration");
                    edges.add(edge);

                    degreeMap.merge("MODULE::ERROR_FORENSICS", 1, Integer::sum);
                    degreeMap.merge(targetNodeId, 1, Integer::sum);
                }
            }
        }

        // Apply updated degrees
        for (EvidenceGraphNodeDto n : nodeMap.values()) {
            n.setDegree(degreeMap.getOrDefault(n.getId(), 0));
        }

        List<EvidenceGraphNodeDto> nodeList = new ArrayList<>(nodeMap.values());
        graph.setNodes(nodeList);
        graph.setEdges(edges);
        graph.setNodeCount(nodeList.size());
        graph.setEdgeCount(edges.size());
        graph.setTargetCount(topTargets.size());

        int maxPossibleEdges = nodeList.size() > 1 ? (nodeList.size() * (nodeList.size() - 1)) / 2 : 1;
        graph.setDensity(Math.round(((double) edges.size() / maxPossibleEdges) * 1000.0) / 1000.0);

        return graph;
    }

    private EvidenceGraphDto emptyGraph(String runId) {
        EvidenceGraphDto g = new EvidenceGraphDto();
        g.setRunId(runId);
        g.setNodes(Collections.emptyList());
        g.setEdges(Collections.emptyList());
        g.setNodeCount(0);
        g.setEdgeCount(0);
        g.setTargetCount(0);
        g.setDensity(0.0);
        return g;
    }

    private InvestigationTargetDto mapEntityToDto(DiagnosticInvestigation entity) {
        InvestigationTargetDto dto = new InvestigationTargetDto();
        dto.setId(entity.getId());
        dto.setRunId(entity.getRunId());
        dto.setTargetType(entity.getTargetType());
        dto.setTargetKey(entity.getTargetKey());
        dto.setDisplayName(entity.getDisplayName());
        dto.setPriority(entity.getPriority().name());
        dto.setPriorityScore(entity.getPriorityScore());
        dto.setConfidence(entity.getConfidence().name());
        dto.setSupportingModuleCount(entity.getSupportingModuleCount());
        dto.setSupportingFindingCount(entity.getSupportingFindingCount());
        dto.setSupportingEvidenceCount(entity.getSupportingEvidenceCount());
        dto.setGraphDegree(entity.getGraphDegree());
        dto.setHypothesis(entity.getHypothesis());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setAssociativeOnly(true);

        try {
            dto.setNextActions(objectMapper.readValue(entity.getNextActionsJson(), new TypeReference<List<String>>() {}));
        } catch (Exception e) {
            dto.setNextActions(Collections.emptyList());
        }

        try {
            dto.setSupportingModules(objectMapper.readValue(entity.getSupportingModulesJson(), new TypeReference<List<String>>() {}));
        } catch (Exception e) {
            dto.setSupportingModules(Collections.emptyList());
        }

        try {
            dto.setSupportingRuleIds(objectMapper.readValue(entity.getSupportingRuleIdsJson(), new TypeReference<List<String>>() {}));
        } catch (Exception e) {
            dto.setSupportingRuleIds(Collections.emptyList());
        }

        try {
            dto.setEvidenceSummary(objectMapper.readValue(entity.getEvidenceSummaryJson(), new TypeReference<Map<String, Object>>() {}));
        } catch (Exception e) {
            dto.setEvidenceSummary(Collections.emptyMap());
        }

        return dto;
    }
}
