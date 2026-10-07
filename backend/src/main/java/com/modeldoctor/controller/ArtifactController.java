package com.modeldoctor.controller;

import com.modeldoctor.dto.DatasetArtifactResponseDto;
import com.modeldoctor.dto.ModelArtifactResponseDto;
import com.modeldoctor.service.ArtifactStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/artifacts")
@Tag(name = "Artifacts Management", description = "Endpoints for uploading, listing, validating, deleting, and retrieving model and dataset artifacts")
public class ArtifactController {

    private final ArtifactStorageService artifactStorageService;

    public ArtifactController(ArtifactStorageService artifactStorageService) {
        this.artifactStorageService = artifactStorageService;
    }

    @PostMapping(value = "/models", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload Model Artifact", description = "Uploads and registers an ML model artifact with checksum calculation and format validation.")
    public ResponseEntity<ModelArtifactResponseDto> uploadModel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "framework", required = false) String framework,
            @RequestParam(value = "taskType", required = false) String taskType) {

        ModelArtifactResponseDto response = artifactStorageService.storeModelArtifact(file, framework, taskType);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/models")
    @Operation(summary = "List Active Model Artifacts", description = "Lists non-deleted model artifacts available for diagnostic inspection.")
    public ResponseEntity<List<ModelArtifactResponseDto>> listModels() {
        List<ModelArtifactResponseDto> response = artifactStorageService.listActiveModelArtifacts();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/models/{id}")
    @Operation(summary = "Get Model Artifact Metadata", description = "Retrieves stored metadata and provenance for a model artifact.")
    public ResponseEntity<ModelArtifactResponseDto> getModel(@PathVariable("id") String id) {
        ModelArtifactResponseDto response = artifactStorageService.getModelArtifactDto(id);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/models/{id}")
    @Operation(summary = "Soft Delete Model Artifact", description = "Soft deletes a model artifact while preserving historical run integrity.")
    public ResponseEntity<Void> deleteModel(@PathVariable("id") String id) {
        artifactStorageService.softDeleteModelArtifact(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/datasets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload Dataset Artifact", description = "Uploads and registers a tabular dataset artifact (CSV/Parquet) with checksum and rich schema inspection.")
    public ResponseEntity<DatasetArtifactResponseDto> uploadDataset(
            @RequestParam("file") MultipartFile file) {

        DatasetArtifactResponseDto response = artifactStorageService.storeDatasetArtifact(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/datasets")
    @Operation(summary = "List Active Dataset Artifacts", description = "Lists non-deleted dataset artifacts with rich schema metadata.")
    public ResponseEntity<List<DatasetArtifactResponseDto>> listDatasets() {
        List<DatasetArtifactResponseDto> response = artifactStorageService.listActiveDatasetArtifacts();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/datasets/{id}")
    @Operation(summary = "Get Dataset Artifact Metadata", description = "Retrieves stored metadata and schema provenance for a dataset artifact.")
    public ResponseEntity<DatasetArtifactResponseDto> getDataset(@PathVariable("id") String id) {
        DatasetArtifactResponseDto response = artifactStorageService.getDatasetArtifactDto(id);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/datasets/{id}")
    @Operation(summary = "Soft Delete Dataset Artifact", description = "Soft deletes a dataset artifact while preserving historical run integrity.")
    public ResponseEntity<Void> deleteDataset(@PathVariable("id") String id) {
        artifactStorageService.softDeleteDatasetArtifact(id);
        return ResponseEntity.noContent().build();
    }
}
