package com.modeldoctor.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeldoctor.domain.DatasetArtifactEntity;
import com.modeldoctor.domain.ModelArtifactEntity;
import com.modeldoctor.dto.ColumnProfileDto;
import com.modeldoctor.dto.DatasetArtifactResponseDto;
import com.modeldoctor.dto.DatasetSchemaSummaryDto;
import com.modeldoctor.dto.ModelArtifactResponseDto;
import com.modeldoctor.exception.ResourceNotFoundException;
import com.modeldoctor.repository.DatasetArtifactRepository;
import com.modeldoctor.repository.ModelArtifactRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ArtifactStorageService {

    private static final Logger logger = LoggerFactory.getLogger(ArtifactStorageService.class);

    private static final Set<String> ALLOWED_MODEL_EXTENSIONS = Set.of(".json", ".xgb", ".bin", ".joblib", ".pkl");
    private static final Set<String> ALLOWED_DATASET_EXTENSIONS = Set.of(".csv", ".parquet", ".pq", ".json");

    private final Path storageRoot;
    private final Path modelsDir;
    private final Path datasetsDir;
    private final long maxModelSizeBytes;
    private final long maxDatasetSizeBytes;

    private final ModelArtifactRepository modelArtifactRepository;
    private final DatasetArtifactRepository datasetArtifactRepository;
    private final ObjectMapper objectMapper;

    public ArtifactStorageService(
            @Value("${modeldoctor.storage.base-dir:storage}") String baseDir,
            @Value("${modeldoctor.storage.max-model-size-mb:50}") long maxModelSizeMb,
            @Value("${modeldoctor.storage.max-dataset-size-mb:100}") long maxDatasetSizeMb,
            ModelArtifactRepository modelArtifactRepository,
            DatasetArtifactRepository datasetArtifactRepository,
            ObjectMapper objectMapper) {

        this.storageRoot = Paths.get(baseDir).toAbsolutePath().normalize();
        this.modelsDir = this.storageRoot.resolve("models").normalize();
        this.datasetsDir = this.storageRoot.resolve("datasets").normalize();
        this.maxModelSizeBytes = maxModelSizeMb * 1024L * 1024L;
        this.maxDatasetSizeBytes = maxDatasetSizeMb * 1024L * 1024L;
        this.modelArtifactRepository = modelArtifactRepository;
        this.datasetArtifactRepository = datasetArtifactRepository;
        this.objectMapper = objectMapper;

        try {
            Files.createDirectories(this.modelsDir);
            Files.createDirectories(this.datasetsDir);
            logger.info("Initialized artifact storage at {}", this.storageRoot);
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize artifact storage directories at " + this.storageRoot, e);
        }
    }

    /**
     * Lists active (non-deleted) model artifacts in descending order of creation.
     */
    @Transactional(readOnly = true)
    public List<ModelArtifactResponseDto> listActiveModelArtifacts() {
        return modelArtifactRepository.findByIsDeletedFalseOrderByCreatedAtDesc().stream()
                .map(this::toModelResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Lists active (non-deleted) dataset artifacts in descending order of creation.
     */
    @Transactional(readOnly = true)
    public List<DatasetArtifactResponseDto> listActiveDatasetArtifacts() {
        return datasetArtifactRepository.findByIsDeletedFalseOrderByCreatedAtDesc().stream()
                .map(this::toDatasetResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Stores and registers a model artifact.
     */
    @Transactional
    public ModelArtifactResponseDto storeModelArtifact(MultipartFile file, String framework, String taskType) {
        validateFile(file, maxModelSizeBytes, ALLOWED_MODEL_EXTENSIONS, "model");

        String rawFilename = file.getOriginalFilename();
        String sanitizedFilename = sanitizeFilename(rawFilename);
        String extension = getExtension(sanitizedFilename);

        String cleanFramework = StringUtils.hasText(framework) ? framework.trim().toLowerCase() : inferFramework(extension);
        String cleanTaskType = StringUtils.hasText(taskType) ? taskType.trim().toLowerCase() : "binary_classification";

        String artifactId = "mdl_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Path targetDir = modelsDir.resolve(artifactId).normalize();
        validatePathContained(targetDir, modelsDir);

        try {
            Files.createDirectories(targetDir);
            Path targetFile = targetDir.resolve(sanitizedFilename).normalize();
            validatePathContained(targetFile, targetDir);

            // Stream file to disk while calculating SHA-256
            String sha256 = streamAndCalculateSha256(file, targetFile);
            long fileSize = Files.size(targetFile);

            // Check for duplicate artifact by SHA-256
            Optional<ModelArtifactEntity> existing = modelArtifactRepository.findBySha256(sha256);
            if (existing.isPresent()) {
                ModelArtifactEntity existingEntity = existing.get();
                if (existingEntity.getIsDeleted()) {
                    // Restore soft-deleted artifact
                    existingEntity.setIsDeleted(false);
                    existingEntity.setDeletedAt(null);
                    modelArtifactRepository.save(existingEntity);
                }
                logger.info("Found existing model artifact with SHA-256 {}, reusing record {}", sha256, existingEntity.getId());
                try {
                    Files.deleteIfExists(targetFile);
                    Files.deleteIfExists(targetDir);
                } catch (Exception ignored) {}
                return toModelResponseDto(existingEntity);
            }

            // Inspect metadata (e.g. feature names from XGBoost JSON)
            List<String> featureNames = inspectModelFeatureNames(targetFile, extension);
            String featureNamesJson = featureNames != null && !featureNames.isEmpty()
                    ? objectMapper.writeValueAsString(featureNames)
                    : null;

            ModelArtifactEntity entity = ModelArtifactEntity.builder()
                    .id(artifactId)
                    .originalFilename(sanitizedFilename)
                    .storagePath(targetFile.toString())
                    .modelFormat(extension.replace(".", ""))
                    .framework(cleanFramework)
                    .taskType(cleanTaskType)
                    .fileSize(fileSize)
                    .sha256(sha256)
                    .featureNamesJson(featureNamesJson)
                    .status("READY")
                    .createdAt(Instant.now())
                    .isDeleted(false)
                    .build();

            ModelArtifactEntity saved = modelArtifactRepository.save(entity);
            logger.info("Stored model artifact {} ({}) at {}", artifactId, sanitizedFilename, targetFile);
            return toModelResponseDto(saved);

        } catch (Exception e) {
            logger.error("Failed to store model artifact", e);
            throw new RuntimeException("Failed to store model artifact: " + e.getMessage(), e);
        }
    }

    /**
     * Stores and registers a dataset artifact with rich schema intelligence.
     */
    @Transactional
    public DatasetArtifactResponseDto storeDatasetArtifact(MultipartFile file) {
        validateFile(file, maxDatasetSizeBytes, ALLOWED_DATASET_EXTENSIONS, "dataset");

        String rawFilename = file.getOriginalFilename();
        String sanitizedFilename = sanitizeFilename(rawFilename);
        String extension = getExtension(sanitizedFilename);

        String artifactId = "ds_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Path targetDir = datasetsDir.resolve(artifactId).normalize();
        validatePathContained(targetDir, datasetsDir);

        try {
            Files.createDirectories(targetDir);
            Path targetFile = targetDir.resolve(sanitizedFilename).normalize();
            validatePathContained(targetFile, targetDir);

            // Stream file to disk while calculating SHA-256
            String sha256 = streamAndCalculateSha256(file, targetFile);
            long fileSize = Files.size(targetFile);

            // Check for duplicate artifact by SHA-256
            Optional<DatasetArtifactEntity> existing = datasetArtifactRepository.findBySha256(sha256);
            if (existing.isPresent()) {
                DatasetArtifactEntity existingEntity = existing.get();
                if (existingEntity.getIsDeleted()) {
                    // Restore soft-deleted artifact
                    existingEntity.setIsDeleted(false);
                    existingEntity.setDeletedAt(null);
                    datasetArtifactRepository.save(existingEntity);
                }
                logger.info("Found existing dataset artifact with SHA-256 {}, reusing record {}", sha256, existingEntity.getId());
                try {
                    Files.deleteIfExists(targetFile);
                    Files.deleteIfExists(targetDir);
                } catch (Exception ignored) {}
                return toDatasetResponseDto(existingEntity);
            }

            // Inspect dataset structure and rich column statistics
            DatasetInspectionResult inspection = inspectDataset(targetFile, extension);

            DatasetArtifactEntity entity = DatasetArtifactEntity.builder()
                    .id(artifactId)
                    .originalFilename(sanitizedFilename)
                    .storagePath(targetFile.toString())
                    .datasetFormat(extension.replace(".", ""))
                    .fileSize(fileSize)
                    .sha256(sha256)
                    .rowCount(inspection.rowCount)
                    .columnCount(inspection.columnCount)
                    .columnNamesJson(objectMapper.writeValueAsString(inspection.columnNames))
                    .dtypesJson(objectMapper.writeValueAsString(inspection.dtypes))
                    .schemaSummaryJson(inspection.schemaSummary != null ? objectMapper.writeValueAsString(inspection.schemaSummary) : null)
                    .status("READY")
                    .createdAt(Instant.now())
                    .isDeleted(false)
                    .build();

            DatasetArtifactEntity saved = datasetArtifactRepository.save(entity);
            logger.info("Stored dataset artifact {} ({}, {} rows, {} cols) at {}",
                    artifactId, sanitizedFilename, inspection.rowCount, inspection.columnCount, targetFile);
            return toDatasetResponseDto(saved);

        } catch (Exception e) {
            logger.error("Failed to store dataset artifact", e);
            throw new RuntimeException("Failed to store dataset artifact: " + e.getMessage(), e);
        }
    }

    /**
     * Soft-deletes a model artifact. File remains on disk for historical run provenance.
     */
    @Transactional
    public void softDeleteModelArtifact(String id) {
        ModelArtifactEntity entity = modelArtifactRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Model artifact not found with ID: " + id));

        if (Boolean.TRUE.equals(entity.getIsDeleted())) {
            return; // Already deleted
        }

        entity.setIsDeleted(true);
        entity.setDeletedAt(Instant.now());
        modelArtifactRepository.save(entity);
        logger.info("Soft-deleted model artifact {}", id);
    }

    /**
     * Soft-deletes a dataset artifact. File remains on disk for historical run provenance.
     */
    @Transactional
    public void softDeleteDatasetArtifact(String id) {
        DatasetArtifactEntity entity = datasetArtifactRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dataset artifact not found with ID: " + id));

        if (Boolean.TRUE.equals(entity.getIsDeleted())) {
            return; // Already deleted
        }

        entity.setIsDeleted(true);
        entity.setDeletedAt(Instant.now());
        datasetArtifactRepository.save(entity);
        logger.info("Soft-deleted dataset artifact {}", id);
    }

    public ModelArtifactResponseDto getModelArtifactDto(String id) {
        return toModelResponseDto(getModelArtifact(id));
    }

    public DatasetArtifactResponseDto getDatasetArtifactDto(String id) {
        return toDatasetResponseDto(getDatasetArtifact(id));
    }

    public ModelArtifactEntity getModelArtifact(String id) {
        return modelArtifactRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Model artifact not found with ID: " + id));
    }

    public DatasetArtifactEntity getDatasetArtifact(String id) {
        return datasetArtifactRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dataset artifact not found with ID: " + id));
    }

    public Path resolveModelPath(String id) {
        ModelArtifactEntity entity = getModelArtifact(id);
        Path p = Paths.get(entity.getStoragePath());
        if (!Files.exists(p)) {
            throw new ResourceNotFoundException("Model artifact file missing on disk for ID: " + id);
        }
        return p;
    }

    public Path resolveDatasetPath(String id) {
        DatasetArtifactEntity entity = getDatasetArtifact(id);
        Path p = Paths.get(entity.getStoragePath());
        if (!Files.exists(p)) {
            throw new ResourceNotFoundException("Dataset artifact file missing on disk for ID: " + id);
        }
        return p;
    }

    // --- Validation & Helpers ---

    private void validateFile(MultipartFile file, long maxSizeBytes, Set<String> allowedExtensions, String typeDesc) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded " + typeDesc + " file cannot be null or empty.");
        }
        if (file.getSize() > maxSizeBytes) {
            throw new IllegalArgumentException(String.format("Uploaded %s file (%d MB) exceeds maximum limit of %d MB.",
                    typeDesc, file.getSize() / (1024 * 1024), maxSizeBytes / (1024 * 1024)));
        }
        String originalFilename = file.getOriginalFilename();
        if (!StringUtils.hasText(originalFilename)) {
            throw new IllegalArgumentException("Filename cannot be blank.");
        }
        String extension = getExtension(originalFilename);
        if (!allowedExtensions.contains(extension)) {
            throw new IllegalArgumentException(String.format("Unsupported %s format '%s'. Supported formats: %s",
                    typeDesc, extension, allowedExtensions));
        }
    }

    private String sanitizeFilename(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "artifact.bin";
        }
        String clean = Paths.get(filename).getFileName().toString();
        clean = clean.replace("..", "").replace("/", "").replace("\\", "").trim();
        if (!StringUtils.hasText(clean)) {
            return "artifact.bin";
        }
        return clean;
    }

    private void validatePathContained(Path child, Path parent) {
        if (!child.normalize().startsWith(parent.normalize())) {
            throw new SecurityException("Path traversal attempt detected: " + child);
        }
    }

    private String getExtension(String filename) {
        int idx = filename.lastIndexOf('.');
        if (idx < 0) return "";
        return filename.substring(idx).toLowerCase();
    }

    private String inferFramework(String extension) {
        if (extension.equals(".json") || extension.equals(".xgb") || extension.equals(".bin")) {
            return "xgboost";
        } else if (extension.equals(".joblib") || extension.equals(".pkl")) {
            return "sklearn";
        }
        return "generic";
    }

    private String streamAndCalculateSha256(MultipartFile file, Path destination) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = file.getInputStream();
             DigestInputStream dis = new DigestInputStream(in, digest);
             OutputStream out = Files.newOutputStream(destination, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = dis.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
        byte[] hashBytes = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private List<String> inspectModelFeatureNames(Path modelPath, String extension) {
        if (extension.equals(".json")) {
            try {
                JsonNode root = objectMapper.readTree(modelPath.toFile());
                if (root.has("learner") && root.get("learner").has("feature_names")) {
                    List<String> names = new ArrayList<>();
                    for (JsonNode n : root.get("learner").get("feature_names")) {
                        names.add(n.asText());
                    }
                    return names;
                } else if (root.has("feature_names")) {
                    List<String> names = new ArrayList<>();
                    for (JsonNode n : root.get("feature_names")) {
                        names.add(n.asText());
                    }
                    return names;
                }
            } catch (Exception e) {
                logger.debug("Could not parse feature_names from model JSON: {}", e.getMessage());
            }
        }
        return null;
    }

    private DatasetInspectionResult inspectDataset(Path datasetPath, String extension) {
        if (extension.equals(".csv") || extension.equals(".txt")) {
            return inspectCsv(datasetPath);
        } else if (extension.equals(".json")) {
            return inspectJson(datasetPath);
        } else {
            return new DatasetInspectionResult(0L, 0, List.of(), Map.of(), null);
        }
    }

    private DatasetInspectionResult inspectCsv(Path csvPath) {
        List<String> columnNames = new ArrayList<>();
        Map<String, String> dtypes = new HashMap<>();

        try (BufferedReader reader = Files.newBufferedReader(csvPath)) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.trim().isEmpty()) {
                throw new IllegalArgumentException("CSV dataset file is empty or has no header row.");
            }

            String[] rawCols = headerLine.split(",");
            for (String col : rawCols) {
                columnNames.add(col.replace("\"", "").trim());
            }

            int colCount = columnNames.size();
            List<ColumnAccumulator> accumulators = new ArrayList<>();
            for (String col : columnNames) {
                accumulators.add(new ColumnAccumulator(col));
            }

            long rowCount = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    rowCount++;
                    String[] tokens = line.split(",", -1);
                    for (int i = 0; i < colCount; i++) {
                        String val = i < tokens.length ? tokens[i].replace("\"", "").trim() : "";
                        accumulators.get(i).addValue(val);
                    }
                }
            }

            // Build ColumnProfileDtos and Dtypes
            List<ColumnProfileDto> columnProfiles = new ArrayList<>();
            for (ColumnAccumulator acc : accumulators) {
                ColumnProfileDto profile = acc.buildProfile(rowCount);
                columnProfiles.add(profile);
                dtypes.put(profile.getName(), profile.getDtype());
            }

            // Potential candidates
            List<String> targetCandidates = new ArrayList<>();
            List<String> predCandidates = new ArrayList<>();
            List<String> protectedCandidates = new ArrayList<>();

            for (ColumnProfileDto cp : columnProfiles) {
                String nameLower = cp.getName().toLowerCase();
                // Target heuristic: name matches or binary target
                if (nameLower.equals("target") || nameLower.equals("label") || nameLower.equals("y") ||
                        nameLower.startsWith("is_") || nameLower.equals("class") || nameLower.equals("outcome") ||
                        nameLower.equals("response") || nameLower.equals("fraud") || nameLower.equals("default")) {
                    targetCandidates.add(cp.getName());
                } else if (cp.getUniqueCount() != null && cp.getUniqueCount() == 2 && ("numeric".equals(cp.getClassification()) || "boolean".equals(cp.getClassification()) || "categorical".equals(cp.getClassification()))) {
                    if (!targetCandidates.contains(cp.getName())) {
                        targetCandidates.add(cp.getName());
                    }
                }

                // Prediction heuristic: name matches
                if (nameLower.contains("pred") || nameLower.contains("prob") || nameLower.contains("score") || nameLower.equals("y_hat")) {
                    predCandidates.add(cp.getName());
                }

                // Protected attribute heuristic
                if (nameLower.contains("gender") || nameLower.contains("sex") || nameLower.contains("race") ||
                        nameLower.contains("ethnicity") || nameLower.contains("age") || nameLower.contains("foreign") ||
                        nameLower.contains("zip") || nameLower.contains("nationality") || nameLower.contains("disability")) {
                    protectedCandidates.add(cp.getName());
                }
            }

            DatasetSchemaSummaryDto schemaSummary = DatasetSchemaSummaryDto.builder()
                    .rowCount(rowCount)
                    .columnCount(colCount)
                    .columns(columnProfiles)
                    .potentialTargetColumns(targetCandidates)
                    .potentialPredictionColumns(predCandidates)
                    .potentialProtectedAttributes(protectedCandidates)
                    .build();

            return new DatasetInspectionResult(rowCount, colCount, columnNames, dtypes, schemaSummary);

        } catch (IOException e) {
            throw new RuntimeException("Failed to read and inspect CSV file: " + e.getMessage(), e);
        }
    }

    private DatasetInspectionResult inspectJson(Path jsonPath) {
        try {
            JsonNode root = objectMapper.readTree(jsonPath.toFile());
            if (root.isArray() && root.size() > 0) {
                long rowCount = root.size();
                JsonNode first = root.get(0);
                List<String> columnNames = new ArrayList<>();
                Map<String, String> dtypes = new HashMap<>();
                first.fieldNames().forEachRemaining(columnNames::add);

                List<ColumnAccumulator> accumulators = new ArrayList<>();
                for (String col : columnNames) {
                    accumulators.add(new ColumnAccumulator(col));
                }

                for (JsonNode rowNode : root) {
                    for (int i = 0; i < columnNames.size(); i++) {
                        String col = columnNames.get(i);
                        JsonNode valNode = rowNode.get(col);
                        String val = valNode != null && !valNode.isNull() ? valNode.asText() : "";
                        accumulators.get(i).addValue(val);
                    }
                }

                List<ColumnProfileDto> columnProfiles = new ArrayList<>();
                for (ColumnAccumulator acc : accumulators) {
                    ColumnProfileDto profile = acc.buildProfile(rowCount);
                    columnProfiles.add(profile);
                    dtypes.put(profile.getName(), profile.getDtype());
                }

                DatasetSchemaSummaryDto schemaSummary = DatasetSchemaSummaryDto.builder()
                        .rowCount(rowCount)
                        .columnCount(columnNames.size())
                        .columns(columnProfiles)
                        .potentialTargetColumns(List.of())
                        .potentialPredictionColumns(List.of())
                        .potentialProtectedAttributes(List.of())
                        .build();

                return new DatasetInspectionResult(rowCount, columnNames.size(), columnNames, dtypes, schemaSummary);
            }
            return new DatasetInspectionResult(0L, 0, List.of(), Map.of(), null);
        } catch (Exception e) {
            return new DatasetInspectionResult(0L, 0, List.of(), Map.of(), null);
        }
    }

    public ModelArtifactResponseDto toModelResponseDto(ModelArtifactEntity entity) {
        List<String> featureNames = null;
        if (StringUtils.hasText(entity.getFeatureNamesJson())) {
            try {
                featureNames = objectMapper.readValue(entity.getFeatureNamesJson(), new TypeReference<List<String>>() {});
            } catch (Exception ignored) {}
        }
        int featureCount = featureNames != null ? featureNames.size() : 0;

        return ModelArtifactResponseDto.builder()
                .id(entity.getId())
                .filename(entity.getOriginalFilename())
                .modelFormat(entity.getModelFormat())
                .framework(entity.getFramework())
                .taskType(entity.getTaskType())
                .sizeBytes(entity.getFileSize())
                .sha256(entity.getSha256())
                .featureCount(featureCount)
                .featureNames(featureNames)
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .isDeleted(entity.getIsDeleted())
                .deletedAt(entity.getDeletedAt())
                .build();
    }

    public DatasetArtifactResponseDto toDatasetResponseDto(DatasetArtifactEntity entity) {
        List<String> columnNames = null;
        Map<String, String> dtypes = null;
        DatasetSchemaSummaryDto schemaSummary = null;

        if (StringUtils.hasText(entity.getColumnNamesJson())) {
            try {
                columnNames = objectMapper.readValue(entity.getColumnNamesJson(), new TypeReference<List<String>>() {});
            } catch (Exception ignored) {}
        }
        if (StringUtils.hasText(entity.getDtypesJson())) {
            try {
                dtypes = objectMapper.readValue(entity.getDtypesJson(), new TypeReference<Map<String, String>>() {});
            } catch (Exception ignored) {}
        }
        if (StringUtils.hasText(entity.getSchemaSummaryJson())) {
            try {
                schemaSummary = objectMapper.readValue(entity.getSchemaSummaryJson(), DatasetSchemaSummaryDto.class);
            } catch (Exception ignored) {}
        }

        return DatasetArtifactResponseDto.builder()
                .id(entity.getId())
                .filename(entity.getOriginalFilename())
                .datasetFormat(entity.getDatasetFormat())
                .sizeBytes(entity.getFileSize())
                .sha256(entity.getSha256())
                .rowCount(entity.getRowCount())
                .columnCount(entity.getColumnCount())
                .columnNames(columnNames)
                .dtypes(dtypes)
                .schemaSummary(schemaSummary)
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .isDeleted(entity.getIsDeleted())
                .deletedAt(entity.getDeletedAt())
                .build();
    }

    private static class DatasetInspectionResult {
        final long rowCount;
        final int columnCount;
        final List<String> columnNames;
        final Map<String, String> dtypes;
        final DatasetSchemaSummaryDto schemaSummary;

        DatasetInspectionResult(long rowCount, int columnCount, List<String> columnNames, Map<String, String> dtypes, DatasetSchemaSummaryDto schemaSummary) {
            this.rowCount = rowCount;
            this.columnCount = columnCount;
            this.columnNames = columnNames;
            this.dtypes = dtypes;
            this.schemaSummary = schemaSummary;
        }
    }

    /**
     * Bounded column accumulator for streaming statistical calculation during ingestion.
     */
    private static class ColumnAccumulator {
        private final String name;
        private long nonNullCount = 0;
        private long nullCount = 0;
        private final Set<String> distinctValues = new HashSet<>();
        private final Map<String, Integer> topFrequencyMap = new HashMap<>();
        private final List<String> exampleValues = new ArrayList<>();
        private boolean isNumericCandidate = true;
        private boolean isIntegerCandidate = true;
        private boolean isBooleanCandidate = true;
        private double min = Double.MAX_VALUE;
        private double max = -Double.MAX_VALUE;
        private double sum = 0.0;
        private double sumSq = 0.0;
        private final List<Double> sampledNumericValues = new ArrayList<>();

        ColumnAccumulator(String name) {
            this.name = name;
        }

        void addValue(String val) {
            if (val == null || val.isEmpty() || val.equalsIgnoreCase("nan") || val.equalsIgnoreCase("null") || val.equalsIgnoreCase("none")) {
                nullCount++;
                return;
            }

            nonNullCount++;
            if (distinctValues.size() < 500) {
                distinctValues.add(val);
            }
            if (topFrequencyMap.size() < 500) {
                topFrequencyMap.put(val, topFrequencyMap.getOrDefault(val, 0) + 1);
            }
            if (exampleValues.size() < 5 && !exampleValues.contains(val)) {
                exampleValues.add(val);
            }

            // Check boolean
            if (isBooleanCandidate) {
                if (!val.equalsIgnoreCase("true") && !val.equalsIgnoreCase("false") &&
                        !val.equals("0") && !val.equals("1") &&
                        !val.equalsIgnoreCase("yes") && !val.equalsIgnoreCase("no")) {
                    isBooleanCandidate = false;
                }
            }

            // Check numeric
            if (isNumericCandidate) {
                try {
                    double d = Double.parseDouble(val);
                    if (d < min) min = d;
                    if (d > max) max = d;
                    sum += d;
                    sumSq += d * d;
                    if (sampledNumericValues.size() < 2000) {
                        sampledNumericValues.add(d);
                    }
                    if (isIntegerCandidate && !Pattern.matches("^-?\\d+$", val)) {
                        isIntegerCandidate = false;
                    }
                } catch (NumberFormatException e) {
                    isNumericCandidate = false;
                    isIntegerCandidate = false;
                }
            }
        }

        ColumnProfileDto buildProfile(long totalRows) {
            double nullPct = totalRows > 0 ? (double) nullCount / totalRows * 100.0 : 0.0;
            long uCount = distinctValues.size();
            double uPct = totalRows > 0 ? (double) uCount / totalRows * 100.0 : 0.0;

            String classification;
            String dtype;
            Double computedMin = null;
            Double computedMax = null;
            Double mean = null;
            Double std = null;
            Map<String, Double> quantiles = null;

            if (isBooleanCandidate && (uCount <= 2 || totalRows == 0)) {
                classification = "boolean";
                dtype = "bool";
            } else if (isNumericCandidate && nonNullCount > 0) {
                classification = "numeric";
                dtype = isIntegerCandidate ? "int64" : "float64";
                computedMin = min;
                computedMax = max;
                mean = sum / nonNullCount;
                if (nonNullCount > 1) {
                    double var = (sumSq - (sum * sum / nonNullCount)) / (nonNullCount - 1);
                    std = Math.sqrt(Math.max(0.0, var));
                } else {
                    std = 0.0;
                }
                if (!sampledNumericValues.isEmpty()) {
                    Collections.sort(sampledNumericValues);
                    quantiles = new HashMap<>();
                    quantiles.put("p25", getPercentile(sampledNumericValues, 25));
                    quantiles.put("p50", getPercentile(sampledNumericValues, 50));
                    quantiles.put("p75", getPercentile(sampledNumericValues, 75));
                }
            } else {
                dtype = "object";
                classification = uCount < 30 ? "categorical" : "text";
            }

            boolean isConstant = uCount == 1 && totalRows > 0;
            boolean isNearConstant = false;
            if (!topFrequencyMap.isEmpty() && nonNullCount > 0) {
                int maxFreq = Collections.max(topFrequencyMap.values());
                if ((double) maxFreq / nonNullCount >= 0.99 && nonNullCount >= 10) {
                    isNearConstant = true;
                }
            }

            String lowerName = name.toLowerCase();
            boolean isIdLike = (uCount == totalRows && totalRows > 0 && !isNumericCandidate) ||
                    lowerName.endsWith("_id") || lowerName.startsWith("id_") || lowerName.equals("id") ||
                    lowerName.contains("_id_") || lowerName.contains("uuid") || lowerName.contains("guid") ||
                    lowerName.contains("hash") || lowerName.contains("identifier");

            boolean isHighCard = !isNumericCandidate && (uCount > 50 || (totalRows > 10 && uPct > 50.0));

            return ColumnProfileDto.builder()
                    .name(name)
                    .dtype(dtype)
                    .classification(classification)
                    .nullCount(nullCount)
                    .nullPercentage(Math.round(nullPct * 100.0) / 100.0)
                    .uniqueCount(uCount)
                    .uniquePercentage(Math.round(uPct * 100.0) / 100.0)
                    .isConstant(isConstant)
                    .isNearConstant(isNearConstant)
                    .min(computedMin)
                    .max(computedMax)
                    .mean(mean != null ? Math.round(mean * 10000.0) / 10000.0 : null)
                    .std(std != null ? Math.round(std * 10000.0) / 10000.0 : null)
                    .quantiles(quantiles)
                    .exampleValues(exampleValues)
                    .isIdentifierLike(isIdLike)
                    .isHighCardinality(isHighCard)
                    .build();
        }

        private double getPercentile(List<Double> sorted, double percentile) {
            if (sorted.isEmpty()) return 0.0;
            int index = (int) Math.ceil((percentile / 100.0) * sorted.size()) - 1;
            index = Math.max(0, Math.min(index, sorted.size() - 1));
            return sorted.get(index);
        }
    }
}
