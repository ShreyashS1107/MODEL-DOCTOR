package com.modeldoctor.controller;

import com.modeldoctor.domain.ModelArtifact;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/models")
@Tag(name = "Model Registry", description = "Endpoints for managing inspected ML models and artifacts")
public class ModelRegistryController {

    @GetMapping
    @Operation(summary = "List Registered Models", description = "Retrieves active registered models ready for diagnostics.")
    public ResponseEntity<List<ModelArtifact>> listRegisteredModels() {
        // Initial stub model list
        List<ModelArtifact> models = List.of(
                ModelArtifact.builder()
                        .id("MD-FRAUD-XGB-V4")
                        .name("Fraud-Detection-Sentinel-XGBoost")
                        .version("4.2.0")
                        .framework("xgboost")
                        .taskType("binary_classification")
                        .storageUri("s3://model-doctor-artifacts/models/fraud_xgb_v4.ubj")
                        .sizeBytes(14829100L)
                        .uploadedAt(Instant.now())
                        .metadata(Map.of("dataset_features", "48", "target", "is_fraud"))
                        .build(),
                ModelArtifact.builder()
                        .id("MD-CREDIT-LGBM-V2")
                        .name("Credit-Risk-Assessor")
                        .version("2.1.0")
                        .framework("lightgbm")
                        .taskType("binary_classification")
                        .storageUri("s3://model-doctor-artifacts/models/credit_lgbm_v2.txt")
                        .sizeBytes(8231000L)
                        .uploadedAt(Instant.now())
                        .metadata(Map.of("dataset_features", "64", "target", "default_status"))
                        .build()
        );
        return ResponseEntity.ok(models);
    }
}
