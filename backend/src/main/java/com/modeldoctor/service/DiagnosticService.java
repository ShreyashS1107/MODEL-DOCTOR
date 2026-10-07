package com.modeldoctor.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeldoctor.domain.*;
import com.modeldoctor.dto.*;
import com.modeldoctor.exception.ResourceNotFoundException;
import com.modeldoctor.repository.DiagnosticResultRepository;
import com.modeldoctor.repository.DiagnosticRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DiagnosticService {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticService.class);

    private final DiagnosticRunRepository runRepository;
    private final DiagnosticResultRepository resultRepository;
    private final DiagnosticJobService jobService;
    private final ArtifactStorageService artifactStorageService;
    private final CorrelationAnalysisService correlationAnalysisService;
    private final ObjectMapper objectMapper;

    public DiagnosticService(
            DiagnosticRunRepository runRepository,
            DiagnosticResultRepository resultRepository,
            DiagnosticJobService jobService,
            ArtifactStorageService artifactStorageService,
            CorrelationAnalysisService correlationAnalysisService,
            ObjectMapper objectMapper) {
        this.runRepository = runRepository;
        this.resultRepository = resultRepository;
        this.jobService = jobService;
        this.artifactStorageService = artifactStorageService;
        this.correlationAnalysisService = correlationAnalysisService;
        this.objectMapper = objectMapper;
    }

    /**
     * Performs pure preflight validation of a diagnostic run configuration.
     */
    @Transactional(readOnly = true)
    public ValidationResultDto validateRunConfiguration(CreateDiagnosticRunRequestDto request) {
        ValidationResultDto result = new ValidationResultDto();
        result.setValid(true);

        // 1. Resolve & Validate Model Artifact
        ModelArtifactEntity modelEntity = null;
        String modelName = null;
        String modelFramework = "xgboost";
        String taskType = "binary_classification";
        List<String> modelFeatures = null;

        if (StringUtils.hasText(request.getModelArtifactId())) {
            try {
                modelEntity = artifactStorageService.getModelArtifact(request.getModelArtifactId());
                if (Boolean.TRUE.equals(modelEntity.getIsDeleted())) {
                    result.addError("DELETED_ARTIFACT_SELECTED", "modelArtifactId", request.getModelArtifactId(),
                            "Model artifact '" + request.getModelArtifactId() + "' is soft-deleted and cannot be used for new diagnostic runs.");
                }
                modelName = modelEntity.getOriginalFilename();
                modelFramework = modelEntity.getFramework();
                taskType = modelEntity.getTaskType();

                if (StringUtils.hasText(modelEntity.getFeatureNamesJson())) {
                    try {
                        modelFeatures = objectMapper.readValue(modelEntity.getFeatureNamesJson(), new TypeReference<List<String>>() {});
                    } catch (Exception ignored) {}
                }
            } catch (ResourceNotFoundException e) {
                result.addError("RESOURCE_NOT_FOUND", "modelArtifactId", request.getModelArtifactId(),
                        "Model artifact not found with ID: " + request.getModelArtifactId());
            }
        } else if (request.getModel() != null && StringUtils.hasText(request.getModel().getName())) {
            modelName = request.getModel().getName();
            if (StringUtils.hasText(request.getModel().getFramework())) {
                modelFramework = request.getModel().getFramework();
            }
            if (StringUtils.hasText(request.getModel().getTaskType())) {
                taskType = request.getModel().getTaskType();
            }
        } else {
            result.addError("MODEL_REQUIRED", "model", null, "Either modelArtifactId or model metadata must be provided.");
        }

        // Validate Task Type (Strict Binary Classification)
        if (StringUtils.hasText(taskType) && !"binary_classification".equalsIgnoreCase(taskType.trim())) {
            String cleanTask = taskType.trim().toLowerCase();
            if (cleanTask.contains("multiclass")) {
                result.addError("MULTICLASS_NOT_SUPPORTED", "taskType", null,
                        "Multiclass classification task ('" + taskType + "') is not supported. Model Doctor requires binary_classification.");
            } else if (cleanTask.contains("regression")) {
                result.addError("REGRESSION_NOT_SUPPORTED", "taskType", null,
                        "Regression task ('" + taskType + "') is not supported. Model Doctor requires binary_classification.");
            } else {
                result.addError("UNSUPPORTED_TASK_TYPE", "taskType", null,
                        "Task type '" + taskType + "' is not supported. Model Doctor requires binary_classification.");
            }
        }

        // 2. Resolve & Validate Evaluation Dataset
        DatasetArtifactEntity evalEntity = null;
        List<String> evalColumns = new ArrayList<>();
        Map<String, String> evalDtypes = new HashMap<>();
        DatasetSchemaSummaryDto evalSummary = null;

        if (StringUtils.hasText(request.getEvaluationDatasetArtifactId())) {
            try {
                evalEntity = artifactStorageService.getDatasetArtifact(request.getEvaluationDatasetArtifactId());
                if (Boolean.TRUE.equals(evalEntity.getIsDeleted())) {
                    result.addError("DELETED_ARTIFACT_SELECTED", "evaluationDatasetArtifactId", request.getEvaluationDatasetArtifactId(),
                            "Evaluation dataset artifact '" + request.getEvaluationDatasetArtifactId() + "' is soft-deleted and cannot be used.");
                }
                if (StringUtils.hasText(evalEntity.getColumnNamesJson())) {
                    try {
                        evalColumns = objectMapper.readValue(evalEntity.getColumnNamesJson(), new TypeReference<List<String>>() {});
                    } catch (Exception ignored) {}
                }
                if (StringUtils.hasText(evalEntity.getDtypesJson())) {
                    try {
                        evalDtypes = objectMapper.readValue(evalEntity.getDtypesJson(), new TypeReference<Map<String, String>>() {});
                    } catch (Exception ignored) {}
                }
                if (StringUtils.hasText(evalEntity.getSchemaSummaryJson())) {
                    try {
                        evalSummary = objectMapper.readValue(evalEntity.getSchemaSummaryJson(), DatasetSchemaSummaryDto.class);
                    } catch (Exception ignored) {}
                }
            } catch (ResourceNotFoundException e) {
                result.addError("RESOURCE_NOT_FOUND", "evaluationDatasetArtifactId", request.getEvaluationDatasetArtifactId(),
                        "Evaluation dataset artifact not found with ID: " + request.getEvaluationDatasetArtifactId());
            }
        } else if (!StringUtils.hasText(request.getEvaluationDataset())) {
            result.addError("EVALUATION_DATASET_REQUIRED", "evaluationDataset", null,
                    "Either evaluationDatasetArtifactId or evaluationDataset path must be provided.");
        }

        // 3. Resolve & Validate Baseline Dataset
        DatasetArtifactEntity baseEntity = null;
        List<String> baseColumns = new ArrayList<>();
        Map<String, String> baseDtypes = new HashMap<>();

        if (StringUtils.hasText(request.getBaselineDatasetArtifactId())) {
            try {
                baseEntity = artifactStorageService.getDatasetArtifact(request.getBaselineDatasetArtifactId());
                if (Boolean.TRUE.equals(baseEntity.getIsDeleted())) {
                    result.addError("DELETED_ARTIFACT_SELECTED", "baselineDatasetArtifactId", request.getBaselineDatasetArtifactId(),
                            "Baseline dataset artifact '" + request.getBaselineDatasetArtifactId() + "' is soft-deleted and cannot be used.");
                }
                if (StringUtils.hasText(baseEntity.getColumnNamesJson())) {
                    try {
                        baseColumns = objectMapper.readValue(baseEntity.getColumnNamesJson(), new TypeReference<List<String>>() {});
                    } catch (Exception ignored) {}
                }
                if (StringUtils.hasText(baseEntity.getDtypesJson())) {
                    try {
                        baseDtypes = objectMapper.readValue(baseEntity.getDtypesJson(), new TypeReference<Map<String, String>>() {});
                    } catch (Exception ignored) {}
                }
            } catch (ResourceNotFoundException e) {
                result.addError("RESOURCE_NOT_FOUND", "baselineDatasetArtifactId", request.getBaselineDatasetArtifactId(),
                        "Baseline dataset artifact not found with ID: " + request.getBaselineDatasetArtifactId());
            }
        }

        // 4. Target Column Intelligence & Validation
        List<TargetSuggestionDto> targetSuggestions = computeTargetSuggestions(evalColumns, evalSummary, modelFeatures);
        result.setTargetSuggestions(targetSuggestions);

        if (!StringUtils.hasText(request.getTargetColumn())) {
            result.addError("TARGET_NOT_SPECIFIED", "targetColumn", null, "A ground-truth target column must be specified.");
        } else {
            String targetCol = request.getTargetColumn();
            if (!evalColumns.isEmpty() && !evalColumns.contains(targetCol)) {
                result.addError("TARGET_NOT_FOUND", "targetColumn", null,
                        "Target column '" + targetCol + "' not found in evaluation dataset columns: " + evalColumns);
            } else if (evalSummary != null && evalSummary.getColumns() != null) {
                ColumnProfileDto targetProfile = evalSummary.getColumns().stream()
                        .filter(c -> c.getName().equals(targetCol))
                        .findFirst()
                        .orElse(null);

                if (targetProfile != null && targetProfile.getUniqueCount() != null) {
                    if (targetProfile.getUniqueCount() != 2) {
                        result.addError("TARGET_HAS_INVALID_CARDINALITY", "targetColumn", null,
                                "Target column '" + targetCol + "' has " + targetProfile.getUniqueCount() +
                                        " unique values. Binary classification requires exactly 2 distinct classes.");
                    }
                }
            }
        }

        // 5. Prediction Column Intelligence & Validation
        List<PredictionSuggestionDto> predSuggestions = computePredictionSuggestions(evalColumns, evalSummary);
        result.setPredictionSuggestions(predSuggestions);

        if (StringUtils.hasText(request.getPredictionColumn()) && !evalColumns.isEmpty()) {
            if (!evalColumns.contains(request.getPredictionColumn())) {
                result.addWarning("PREDICTION_COLUMN_NOT_IN_DATASET", "predictionColumn", null,
                        "Configured prediction column '" + request.getPredictionColumn() +
                                "' was not found in evaluation dataset. Model inference will be used.");
            }
        }

        // 6. Protected Attribute Validation
        if (StringUtils.hasText(request.getProtectedAttribute())) {
            String prot = request.getProtectedAttribute();
            if (StringUtils.hasText(request.getTargetColumn()) && prot.equals(request.getTargetColumn())) {
                result.addError("PROTECTED_ATTRIBUTE_IS_TARGET", "protectedAttribute", null,
                        "Protected attribute cannot be the same as the target column ('" + prot + "').");
            }

            if (!evalColumns.isEmpty() && !evalColumns.contains(prot)) {
                result.addError("PROTECTED_ATTRIBUTE_NOT_FOUND", "protectedAttribute", null,
                        "Protected attribute '" + prot + "' not found in evaluation dataset columns: " + evalColumns);
            } else if (evalSummary != null && evalSummary.getColumns() != null) {
                ColumnProfileDto protProfile = evalSummary.getColumns().stream()
                        .filter(c -> c.getName().equals(prot))
                        .findFirst()
                        .orElse(null);

                if (protProfile != null && protProfile.getUniqueCount() != null && protProfile.getUniqueCount() < 2) {
                    result.addError("PROTECTED_ATTRIBUTE_INVALID_CARDINALITY", "protectedAttribute", null,
                            "Protected attribute '" + prot + "' has fewer than 2 distinct subgroups (" + protProfile.getUniqueCount() + ").");
                }
            }

            // Check if present in baseline dataset if baseline is provided
            if (baseEntity != null && !baseColumns.isEmpty() && !baseColumns.contains(prot)) {
                result.addError("BASELINE_PROTECTED_ATTRIBUTE_MISSING", "protectedAttribute", null,
                        "Protected attribute '" + prot + "' is present in evaluation dataset but missing from baseline dataset.");
            }
        }

        // 7. Model ↔ Dataset Feature Compatibility Validation
        Map<String, Object> compatibilityMap = new HashMap<>();
        List<String> missingFeatures = new ArrayList<>();
        List<String> extraFeatures = new ArrayList<>();

        if (modelFeatures != null && !modelFeatures.isEmpty() && !evalColumns.isEmpty()) {
            for (String feat : modelFeatures) {
                if (!evalColumns.contains(feat)) {
                    missingFeatures.add(feat);
                    result.addError("MISSING_MODEL_FEATURE", "evaluationDataset", null,
                            "Model requires feature '" + feat + "' but the evaluation dataset does not contain it.");
                }
            }

            if (StringUtils.hasText(request.getTargetColumn()) && modelFeatures.contains(request.getTargetColumn())) {
                result.addError("TARGET_IN_MODEL_FEATURES", "targetColumn", null,
                        "Target column '" + request.getTargetColumn() + "' is listed as an input feature for the model.");
            }

            if (StringUtils.hasText(request.getProtectedAttribute()) && modelFeatures.contains(request.getProtectedAttribute())) {
                result.addWarning("PROTECTED_ATTRIBUTE_IN_MODEL_FEATURES", "protectedAttribute", null,
                        "Protected attribute '" + request.getProtectedAttribute() + "' is included in model input features.");
            }

            for (String col : evalColumns) {
                if (!modelFeatures.contains(col) &&
                        !col.equals(request.getTargetColumn()) &&
                        !col.equals(request.getPredictionColumn()) &&
                        !col.equals(request.getProtectedAttribute())) {
                    extraFeatures.add(col);
                }
            }

            if (!extraFeatures.isEmpty()) {
                result.addWarning("EXTRA_DATASET_FEATURE", "evaluationDataset", null,
                        "Evaluation dataset contains " + extraFeatures.size() + " non-model feature(s): " + extraFeatures);
            }
        }

        compatibilityMap.put("missingModelFeatures", missingFeatures);
        compatibilityMap.put("extraDatasetFeatures", extraFeatures);
        compatibilityMap.put("isFeatureCompatible", missingFeatures.isEmpty());
        result.setCompatibility(compatibilityMap);

        // 8. Evaluation vs Baseline Compatibility Validation
        if (!evalColumns.isEmpty() && !baseColumns.isEmpty()) {
            List<String> requiredFeatures = modelFeatures != null ? modelFeatures : evalColumns;
            for (String feat : requiredFeatures) {
                if (evalColumns.contains(feat) && !baseColumns.contains(feat) && !feat.equals(request.getTargetColumn()) && !feat.equals(request.getPredictionColumn())) {
                    result.addError("BASELINE_FEATURE_MISMATCH", "baselineDataset", null,
                            "Feature '" + feat + "' present in evaluation dataset is missing from baseline dataset.");
                }
            }

            // Check dtypes
            for (String col : evalColumns) {
                if (baseDtypes.containsKey(col) && evalDtypes.containsKey(col)) {
                    String eDt = evalDtypes.get(col);
                    String bDt = baseDtypes.get(col);
                    if (eDt != null && bDt != null && !eDt.equalsIgnoreCase(bDt)) {
                        result.addWarning("BASELINE_DTYPE_MISMATCH", "baselineDataset", null,
                                "Column '" + col + "' has data type '" + eDt + "' in evaluation and '" + bDt + "' in baseline.");
                    }
                }
            }
        }

        // 9. Module Compatibility & Prerequisites Validation
        List<DiagnosticModule> requestedModules = request.getModules() != null && !request.getModules().isEmpty()
                ? request.getModules()
                : Arrays.asList(DiagnosticModule.values());

        // Check for duplicate modules
        Set<DiagnosticModule> uniqueModules = new LinkedHashSet<>(requestedModules);
        if (uniqueModules.size() != requestedModules.size()) {
            result.addError("DUPLICATE_MODULES", "modules", null, "Duplicate diagnostic modules requested.");
        }

        Map<String, ModuleValidationStatusDto> moduleValidationMap = new HashMap<>();

        for (DiagnosticModule mod : requestedModules) {
            ModuleValidationStatusDto modStatus = validateModulePrerequisites(mod, request, modelEntity, evalEntity, baseEntity);
            moduleValidationMap.put(mod.name(), modStatus);
            if (!Boolean.TRUE.equals(modStatus.getCompatible())) {
                for (String missing : modStatus.getMissingPrerequisites()) {
                    result.addError("MODULE_PREREQUISITE_UNMET", "modules", mod.name(),
                            "Module " + mod.name() + " is missing prerequisite: " + missing);
                }
            }
        }
        result.setModuleValidation(moduleValidationMap);

        return result;
    }

    private ModuleValidationStatusDto validateModulePrerequisites(
            DiagnosticModule module,
            CreateDiagnosticRunRequestDto request,
            ModelArtifactEntity modelEntity,
            DatasetArtifactEntity evalEntity,
            DatasetArtifactEntity baseEntity) {

        List<String> missing = new ArrayList<>();

        switch (module) {
            case DATA_QUALITY:
                if (!StringUtils.hasText(request.getEvaluationDatasetArtifactId()) && !StringUtils.hasText(request.getEvaluationDataset())) {
                    missing.add("Evaluation dataset");
                }
                break;

            case LEAKAGE:
                if (!StringUtils.hasText(request.getTargetColumn())) {
                    missing.add("Target column");
                }
                break;

            case DRIFT:
                if (!StringUtils.hasText(request.getBaselineDatasetArtifactId()) && !StringUtils.hasText(request.getBaselineDataset())) {
                    missing.add("Baseline dataset (Drift requires baseline for distribution comparison)");
                }
                break;

            case PERFORMANCE:
                if (!StringUtils.hasText(request.getTargetColumn())) {
                    missing.add("Target column (Performance requires ground truth labels)");
                }
                break;

            case EXPLAINABILITY:
                if (!StringUtils.hasText(request.getModelArtifactId()) && (request.getModel() == null || !StringUtils.hasText(request.getModel().getName()))) {
                    missing.add("Trained model artifact");
                }
                break;

            case BIAS:
                if (!StringUtils.hasText(request.getTargetColumn())) {
                    missing.add("Target column");
                }
                if (!StringUtils.hasText(request.getProtectedAttribute())) {
                    missing.add("Protected attribute (e.g. gender, age, is_foreign_ip)");
                }
                break;

            case ROBUSTNESS:
                if (!StringUtils.hasText(request.getModelArtifactId()) && (request.getModel() == null || !StringUtils.hasText(request.getModel().getName()))) {
                    missing.add("Trained model artifact");
                }
                break;
        }

        boolean compatible = missing.isEmpty();
        String message = compatible ? "Module prerequisites satisfied" : "Missing required prerequisites: " + String.join(", ", missing);

        return ModuleValidationStatusDto.builder()
                .module(module.name())
                .compatible(compatible)
                .missingPrerequisites(missing)
                .message(message)
                .build();
    }

    private List<TargetSuggestionDto> computeTargetSuggestions(
            List<String> columns,
            DatasetSchemaSummaryDto summary,
            List<String> modelFeatures) {

        if (columns == null || columns.isEmpty()) {
            return Collections.emptyList();
        }

        List<TargetSuggestionDto> suggestions = new ArrayList<>();
        int colCount = columns.size();

        for (int i = 0; i < colCount; i++) {
            String col = columns.get(i);
            String colLower = col.toLowerCase();
            double score = 0.0;
            List<String> reasons = new ArrayList<>();

            // Signal 1: Name match
            if (colLower.equals("target") || colLower.equals("label") || colLower.equals("y") ||
                    colLower.equals("is_fraud") || colLower.equals("fraud") || colLower.equals("default") ||
                    colLower.equals("churn") || colLower.equals("outcome") || colLower.equals("response") ||
                    colLower.equals("class")) {
                score += 0.45;
                reasons.add("Column name matches standard ground-truth naming convention ('" + col + "')");
            } else if (colLower.startsWith("is_") || colLower.startsWith("has_")) {
                score += 0.35;
                reasons.add("Column name is a boolean predicate");
            }

            // Signal 2: Schema Cardinality
            if (summary != null && summary.getColumns() != null) {
                ColumnProfileDto profile = summary.getColumns().stream()
                        .filter(c -> c.getName().equals(col))
                        .findFirst()
                        .orElse(null);

                if (profile != null && profile.getUniqueCount() != null) {
                    if (profile.getUniqueCount() == 2) {
                        score += 0.35;
                        reasons.add("Binary cardinality (exactly 2 unique values)");
                    } else if (profile.getUniqueCount() > 2 && profile.getUniqueCount() <= 5 && "categorical".equals(profile.getClassification())) {
                        score += 0.15;
                        reasons.add("Low-cardinality discrete values (" + profile.getUniqueCount() + ")");
                    }
                }
            }

            // Signal 3: Model Feature Absence
            if (modelFeatures != null && !modelFeatures.isEmpty() && !modelFeatures.contains(col)) {
                score += 0.15;
                reasons.add("Excluded from model's input feature set");
            }

            // Signal 4: Position (Last Column)
            if (i == colCount - 1) {
                score += 0.05;
                reasons.add("Positioned as terminal column in dataset schema");
            }

            if (score >= 0.30) {
                suggestions.add(new TargetSuggestionDto(col, Math.round(score * 100.0) / 100.0, reasons));
            }
        }

        suggestions.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return suggestions;
    }

    private List<PredictionSuggestionDto> computePredictionSuggestions(
            List<String> columns,
            DatasetSchemaSummaryDto summary) {

        if (columns == null || columns.isEmpty()) {
            return Collections.emptyList();
        }

        List<PredictionSuggestionDto> suggestions = new ArrayList<>();
        for (String col : columns) {
            String colLower = col.toLowerCase();
            double score = 0.0;
            List<String> reasons = new ArrayList<>();

            if (colLower.equals("pred_prob") || colLower.equals("prediction_probability") ||
                    colLower.equals("probability") || colLower.equals("score") ||
                    colLower.equals("y_pred") || colLower.equals("y_prob") || colLower.equals("pred")) {
                score += 0.60;
                reasons.add("Column name matches standard probability prediction identifier");
            } else if (colLower.contains("pred") || colLower.contains("prob")) {
                score += 0.40;
                reasons.add("Column name contains predictive keyword");
            }

            if (summary != null && summary.getColumns() != null) {
                ColumnProfileDto profile = summary.getColumns().stream()
                        .filter(c -> c.getName().equals(col))
                        .findFirst()
                        .orElse(null);

                if (profile != null && "numeric".equals(profile.getClassification()) &&
                        profile.getMin() != null && profile.getMax() != null &&
                        profile.getMin() >= 0.0 && profile.getMax() <= 1.0) {
                    score += 0.30;
                    reasons.add("Values are bounded in probability interval [0.0, 1.0]");
                }
            }

            if (score >= 0.30) {
                suggestions.add(new PredictionSuggestionDto(col, Math.round(score * 100.0) / 100.0, reasons));
            }
        }

        suggestions.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return suggestions;
    }

    /**
     * Creates a new diagnostic run record in CREATED state.
     * Enforces independent backend configuration validation before creating the run.
     */
    @Transactional
    public DiagnosticRunResponseDto createDiagnosticRun(CreateDiagnosticRunRequestDto request) {
        // Independent preflight validation
        ValidationResultDto validation = validateRunConfiguration(request);
        if (!validation.isValid()) {
            // Check if any error is RESOURCE_NOT_FOUND
            for (ValidationErrorDto err : validation.getErrors()) {
                if ("RESOURCE_NOT_FOUND".equals(err.getCode())) {
                    throw new ResourceNotFoundException(err.getMessage());
                }
            }
            String errorSummary = validation.getErrors().stream()
                    .map(e -> "[" + e.getCode() + "] " + e.getMessage())
                    .collect(Collectors.joining("; "));
            log.warn("Diagnostic run creation rejected due to validation failure: {}", errorSummary);
            throw new IllegalArgumentException("Invalid diagnostic run configuration: " + errorSummary);
        }

        String generatedId = "run_" + UUID.randomUUID().toString().substring(0, 8);
        Instant now = Instant.now();

        // 1. Resolve Model Artifact / Metadata
        String modelName;
        String modelFramework;
        String taskType;
        String modelStorageUri = null;
        String modelArtifactId = request.getModelArtifactId();

        if (StringUtils.hasText(modelArtifactId)) {
            ModelArtifactEntity modelEntity = artifactStorageService.getModelArtifact(modelArtifactId);
            modelName = modelEntity.getOriginalFilename();
            modelFramework = modelEntity.getFramework();
            taskType = modelEntity.getTaskType();
            modelStorageUri = modelEntity.getStoragePath();
        } else if (request.getModel() != null && StringUtils.hasText(request.getModel().getName())) {
            modelName = request.getModel().getName();
            modelFramework = StringUtils.hasText(request.getModel().getFramework()) ? request.getModel().getFramework() : "xgboost";
            taskType = StringUtils.hasText(request.getModel().getTaskType()) ? request.getModel().getTaskType() : "binary_classification";
            modelStorageUri = request.getModel().getStorageUri();
        } else {
            throw new IllegalArgumentException("Either modelArtifactId or model metadata must be provided.");
        }

        // 2. Resolve Evaluation Dataset Artifact / Path
        String evaluationDataset;
        String evaluationDatasetArtifactId = request.getEvaluationDatasetArtifactId();

        if (StringUtils.hasText(evaluationDatasetArtifactId)) {
            DatasetArtifactEntity evalEntity = artifactStorageService.getDatasetArtifact(evaluationDatasetArtifactId);
            evaluationDataset = evalEntity.getStoragePath();
        } else if (StringUtils.hasText(request.getEvaluationDataset())) {
            evaluationDataset = request.getEvaluationDataset();
        } else {
            throw new IllegalArgumentException("Either evaluationDatasetArtifactId or evaluationDataset path must be provided.");
        }

        // 3. Resolve Baseline Dataset Artifact / Path
        String baselineDataset = null;
        String baselineDatasetArtifactId = request.getBaselineDatasetArtifactId();

        if (StringUtils.hasText(baselineDatasetArtifactId)) {
            DatasetArtifactEntity baseEntity = artifactStorageService.getDatasetArtifact(baselineDatasetArtifactId);
            baselineDataset = baseEntity.getStoragePath();
        } else if (StringUtils.hasText(request.getBaselineDataset())) {
            baselineDataset = request.getBaselineDataset();
        }

        // 4. Determine Execution Mode
        String executionMode = request.getExecutionMode();
        if (!StringUtils.hasText(executionMode)) {
            if (StringUtils.hasText(modelArtifactId) || StringUtils.hasText(evaluationDatasetArtifactId)) {
                executionMode = "REAL";
            } else if (modelName.toLowerCase().contains("benchmark") || evaluationDataset.toLowerCase().contains("synthetic")) {
                executionMode = "BENCHMARK";
            } else {
                executionMode = "REAL";
            }
        }

        DiagnosticRun run = DiagnosticRun.builder()
                .id(generatedId)
                .status(DiagnosticStatus.CREATED)
                .modelArtifactId(modelArtifactId)
                .baselineDatasetArtifactId(baselineDatasetArtifactId)
                .evaluationDatasetArtifactId(evaluationDatasetArtifactId)
                .executionMode(executionMode)
                .modelName(modelName)
                .modelFramework(modelFramework)
                .taskType(taskType)
                .modelStorageUri(modelStorageUri)
                .evaluationDataset(evaluationDataset)
                .baselineDataset(baselineDataset)
                .targetColumn(request.getTargetColumn())
                .predictionColumn(request.getPredictionColumn())
                .protectedAttribute(request.getProtectedAttribute())
                .createdAt(now)
                .modules(new ArrayList<>())
                .build();

        List<DiagnosticModule> modulesToRun = request.getModules() != null && !request.getModules().isEmpty()
                ? request.getModules()
                : Arrays.asList(DiagnosticModule.values());

        for (DiagnosticModule module : modulesToRun) {
            DiagnosticRunModule runModule = DiagnosticRunModule.builder()
                    .run(run)
                    .module(module)
                    .status(ModuleExecutionStatus.PENDING)
                    .statusMessage("Queued for execution")
                    .build();
            run.addModule(runModule);
        }

        DiagnosticRun savedRun = runRepository.save(run);
        jobService.recordEvent(savedRun.getId(), null, "RUN_CREATED",
                "Diagnostic run created in " + savedRun.getExecutionMode() + " mode with " + run.getModules().size() + " module(s)");
        log.info("Created diagnostic run {} for model {} (mode: {})", savedRun.getId(), savedRun.getModelName(), savedRun.getExecutionMode());

        return mapToRunResponse(savedRun);
    }

    /**
     * Retrieves a diagnostic run by ID.
     */
    @Transactional(readOnly = true)
    public DiagnosticRunResponseDto getDiagnosticRun(String id) {
        DiagnosticRun run = runRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Diagnostic run not found with ID: " + id));

        return mapToRunResponse(run);
    }

    /**
     * Starts execution of a diagnostic run.
     */
    public DiagnosticRunResponseDto startDiagnosticRun(String id) {
        DiagnosticRun executedRun = jobService.executeRun(id);
        return mapToRunResponse(executedRun);
    }

    /**
     * Retries all failed modules or re-executes a failed/partial diagnostic run.
     */
    public DiagnosticRunResponseDto retryDiagnosticRun(String id) {
        DiagnosticRun retriedRun = jobService.retryRun(id);
        return mapToRunResponse(retriedRun);
    }

    /**
     * Retries specific modules for a diagnostic run.
     */
    public DiagnosticRunResponseDto retryDiagnosticModules(String id, List<DiagnosticModule> modules) {
        DiagnosticRun retriedRun = jobService.retryModules(id, modules);
        return mapToRunResponse(retriedRun);
    }

    /**
     * Retrieves real execution events timeline for a diagnostic run.
     */
    public List<DiagnosticRunEventDto> getDiagnosticRunEvents(String id) {
        return jobService.getRunEvents(id);
    }

    /**
     * Retrieves current calculated progress for a diagnostic run.
     */
    public DiagnosticProgressDto getDiagnosticRunProgress(String id) {
        return jobService.getRunProgress(id);
    }

    /**
     * Recovers stale executions that have been RUNNING longer than threshold.
     */
    public List<String> recoverStaleRuns(long thresholdMinutes) {
        return jobService.recoverStaleRuns(thresholdMinutes);
    }

    /**
     * Retrieves structured results for a diagnostic run.
     */
    @Transactional(readOnly = true)
    public DiagnosticResultsResponseDto getDiagnosticResults(String id) {
        DiagnosticRun run = runRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Diagnostic run not found with ID: " + id));

        List<DiagnosticResult> results = resultRepository.findByRunIdOrderByIdAsc(id);

        List<ModuleResultDto> moduleResultDtos = results.stream().map(r -> {
            Map<String, Object> resultMap = Collections.emptyMap();
            if (r.getResultJson() != null && !r.getResultJson().isBlank()) {
                try {
                    resultMap = objectMapper.readValue(r.getResultJson(), new TypeReference<Map<String, Object>>() {});
                } catch (Exception e) {
                    log.error("Failed to parse stored result JSON for run {} module {}", id, r.getModule(), e);
                }
            }

            return ModuleResultDto.builder()
                    .module(r.getModule())
                    .status(r.getStatus())
                    .statusMessage(r.getStatus().name())
                    .result(resultMap)
                    .build();
        }).collect(Collectors.toList());

        return DiagnosticResultsResponseDto.builder()
                .runId(run.getId())
                .status(run.getStatus())
                .completedAt(run.getCompletedAt())
                .results(moduleResultDtos)
                .errorMessage(run.getErrorMessage())
                .build();
    }

    private DiagnosticRunResponseDto mapToRunResponse(DiagnosticRun run) {
        Long durationMs = run.getExecutionDurationMs();
        if (durationMs == null && run.getStartedAt() != null && run.getCompletedAt() != null) {
            durationMs = Duration.between(run.getStartedAt(), run.getCompletedAt()).toMillis();
        }

        List<ModuleStatusDto> moduleDtos = run.getModules().stream()
                .map(m -> {
                    Long modDuration = m.getExecutionDurationMs();
                    if (modDuration == null && m.getStartedAt() != null && m.getCompletedAt() != null) {
                        modDuration = Duration.between(m.getStartedAt(), m.getCompletedAt()).toMillis();
                    }
                    return ModuleStatusDto.builder()
                            .module(m.getModule())
                            .status(m.getStatus())
                            .statusMessage(m.getStatusMessage())
                            .startedAt(m.getStartedAt())
                            .completedAt(m.getCompletedAt())
                            .executionDurationMs(modDuration)
                            .build();
                })
                .collect(Collectors.toList());

        DiagnosticProgressDto progress = jobService.getRunProgress(run.getId());

        ModelArtifactResponseDto modelArtifactDto = null;
        if (StringUtils.hasText(run.getModelArtifactId())) {
            try {
                modelArtifactDto = artifactStorageService.getModelArtifactDto(run.getModelArtifactId());
            } catch (Exception ignored) {}
        }

        DatasetArtifactResponseDto baselineArtifactDto = null;
        if (StringUtils.hasText(run.getBaselineDatasetArtifactId())) {
            try {
                baselineArtifactDto = artifactStorageService.getDatasetArtifactDto(run.getBaselineDatasetArtifactId());
            } catch (Exception ignored) {}
        }

        DatasetArtifactResponseDto evalArtifactDto = null;
        if (StringUtils.hasText(run.getEvaluationDatasetArtifactId())) {
            try {
                evalArtifactDto = artifactStorageService.getDatasetArtifactDto(run.getEvaluationDatasetArtifactId());
            } catch (Exception ignored) {}
        }

        return DiagnosticRunResponseDto.builder()
                .id(run.getId())
                .status(run.getStatus())
                .retryCount(run.getRetryCount())
                .modelArtifactId(run.getModelArtifactId())
                .baselineDatasetArtifactId(run.getBaselineDatasetArtifactId())
                .evaluationDatasetArtifactId(run.getEvaluationDatasetArtifactId())
                .executionMode(run.getExecutionMode())
                .model(ModelInfoDto.builder()
                        .name(run.getModelName())
                        .framework(run.getModelFramework())
                        .taskType(run.getTaskType())
                        .storageUri(run.getModelStorageUri())
                        .build())
                .modelArtifact(modelArtifactDto)
                .baselineDatasetArtifact(baselineArtifactDto)
                .evaluationDatasetArtifact(evalArtifactDto)
                .evaluationDataset(run.getEvaluationDataset())
                .baselineDataset(run.getBaselineDataset())
                .targetColumn(run.getTargetColumn())
                .predictionColumn(run.getPredictionColumn())
                .protectedAttribute(run.getProtectedAttribute())
                .selectedModules(moduleDtos)
                .progress(progress)
                .createdAt(run.getCreatedAt())
                .queuedAt(run.getQueuedAt())
                .startedAt(run.getStartedAt())
                .completedAt(run.getCompletedAt())
                .executionDurationMs(durationMs)
                .errorMessage(run.getErrorMessage())
                .build();
    }

    /**
     * Returns the overview model health score (for existing UI HUD integration).
     */
    public ModelHealthScoreDto getModelHealthOverview(String modelId) {
        return ModelHealthScoreDto.builder()
                .modelId(modelId != null ? modelId : "MD-FRAUD-XGB-V4")
                .modelName("Fraud-Detection-Sentinel-XGBoost")
                .overallScore(73.0)
                .statusCategory("CAUTION - POTENTIAL LEAKAGE")
                .categoryBreakdown(Map.of(
                        "dataQuality", 91.0,
                        "leakage", 42.0,
                        "drift", 86.5,
                        "performance", 88.0,
                        "fairness", 78.0,
                        "robustness", 62.5,
                        "calibration", 84.0
                ))
                .isMockData(true)
                .build();
    }

    /**
     * Returns historical runs list for workstation overview table.
     */
    public List<DiagnosticRunDto> getRecentRuns() {
        List<DiagnosticRun> dbRuns = runRepository.findAllByOrderByCreatedAtDesc();

        if (dbRuns.isEmpty()) {
            return List.of(
                    DiagnosticRunDto.builder()
                            .id("run_0042")
                            .modelId("fraud_classifier_v17")
                            .modelName("fraud_classifier_v17")
                            .modelArchitecture("XGBoost 2.0.3")
                            .status(DiagnosticStatus.COMPLETED)
                            .healthScore(73.0)
                            .sampleCount(250000)
                            .durationMs(1420L)
                            .createdAt(Instant.now().minus(12, ChronoUnit.MINUTES))
                            .flaggedAnomalies(List.of("Target Leakage: transaction_id_hash", "PSI Drift: user_velocity_6h"))
                            .isMockData(true)
                            .build()
            );
        }

        return dbRuns.stream().map(r -> DiagnosticRunDto.builder()
                .id(r.getId())
                .modelId(r.getModelName())
                .modelName(r.getModelName())
                .modelArchitecture(r.getModelFramework())
                .status(r.getStatus())
                .healthScore(73.0)
                .sampleCount(250000)
                .durationMs(r.getStartedAt() != null && r.getCompletedAt() != null
                        ? Duration.between(r.getStartedAt(), r.getCompletedAt()).toMillis() : null)
                .createdAt(r.getCreatedAt())
                .flaggedAnomalies(Collections.emptyList())
                .isMockData(false)
                .build()).collect(Collectors.toList());
    }

    /**
     * Returns system telemetry activity feed for the forensic workstation.
     */
    public List<SystemActivityDto> getRecentActivities() {
        return List.of(
                SystemActivityDto.builder()
                        .id("ACT-01")
                        .eventType("DIAGNOSTIC_PROBE")
                        .message("Leakage heuristic probe completed on feature [transaction_reference]")
                        .severity(SeverityLevel.HIGH)
                        .component("ML-ENGINE::LEAKAGE")
                        .timestamp(Instant.now().minus(2, ChronoUnit.MINUTES))
                        .isMockData(true)
                        .build()
        );
    }

    /**
     * Phase 4: Retrieves cross-module correlation findings for a run.
     */
    public List<DiagnosticCorrelationDto> getCorrelations(String runId) {
        return correlationAnalysisService.getCorrelations(runId);
    }

    /**
     * Phase 4: Retrieves run-level diagnostic summary for workstation.
     */
    public RunSummaryDto getRunSummary(String runId) {
        return correlationAnalysisService.getRunSummary(runId);
    }

    /**
     * Phase 4: Explicitly triggers cross-module correlation analysis and returns results.
     */
    public List<DiagnosticCorrelationDto> recalculateCorrelations(String runId) {
        return correlationAnalysisService.analyzeAndPersist(runId);
    }
}
