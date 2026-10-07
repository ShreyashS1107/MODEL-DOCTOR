package com.modeldoctor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeldoctor.domain.DiagnosticModule;
import com.modeldoctor.domain.DiagnosticStatus;
import com.modeldoctor.domain.ModuleExecutionStatus;
import com.modeldoctor.dto.*;
import com.modeldoctor.service.MlEngineClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class DiagnosticControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MlEngineClient mlEngineClient;

    @Test
    public void testCreateDiagnosticRunSuccess() throws Exception {
        CreateDiagnosticRunRequestDto request = CreateDiagnosticRunRequestDto.builder()
                .model(ModelInfoDto.builder()
                        .name("fraud_classifier_v17")
                        .framework("xgboost")
                        .taskType("binary_classification")
                        .build())
                .evaluationDataset("s3://datasets/fraud_eval.parquet")
                .baselineDataset("s3://datasets/fraud_train.parquet")
                .targetColumn("is_fraud")
                .predictionColumn("pred_prob")
                .protectedAttribute("is_foreign_ip")
                .modules(List.of(DiagnosticModule.DATA_QUALITY, DiagnosticModule.LEAKAGE, DiagnosticModule.DRIFT))
                .build();

        mockMvc.perform(post("/api/diagnostics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.model.name").value("fraud_classifier_v17"))
                .andExpect(jsonPath("$.selectedModules").isArray())
                .andExpect(jsonPath("$.selectedModules.length()").value(3));
    }

    @Test
    public void testCreateDiagnosticRunValidationFailure() throws Exception {
        CreateDiagnosticRunRequestDto invalidRequest = CreateDiagnosticRunRequestDto.builder()
                .evaluationDataset("") // blank -> invalid
                .targetColumn("")      // blank -> invalid
                .modules(List.of())    // empty -> invalid
                .build();

        mockMvc.perform(post("/api/diagnostics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value(400))
                .andExpect(jsonPath("$.validationDetails").isArray());
    }

    @Test
    public void testGetDiagnosticRunNotFound() throws Exception {
        mockMvc.perform(get("/api/diagnostics/non_existent_id"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.statusCode").value(404));
    }

    @Test
    public void testFullRunLifecycleAllCompleted() throws Exception {
        // 1. Create Run with DATA_QUALITY and LEAKAGE
        CreateDiagnosticRunRequestDto request = CreateDiagnosticRunRequestDto.builder()
                .model(ModelInfoDto.builder()
                        .name("fraud_classifier_v17")
                        .framework("xgboost")
                        .taskType("binary_classification")
                        .build())
                .evaluationDataset("s3://datasets/fraud_eval.parquet")
                .targetColumn("is_fraud")
                .modules(List.of(DiagnosticModule.DATA_QUALITY, DiagnosticModule.LEAKAGE))
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/diagnostics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        DiagnosticRunResponseDto createdDto = objectMapper.readValue(
                createResult.getResponse().getContentAsString(),
                DiagnosticRunResponseDto.class
        );
        String runId = createdDto.getId();

        // 2. Mock ML Engine response with both modules COMPLETED and structured result JSON
        when(mlEngineClient.executeJob(any())).thenReturn(
                MlEngineJobResponseDto.builder()
                        .runId(runId)
                        .status("COMPLETED")
                        .executionTimeMs(240.0)
                        .modules(List.of(
                                MlEngineModuleResultDto.builder()
                                        .module("DATA_QUALITY")
                                        .status("COMPLETED")
                                        .message("Data Quality audit completed.")
                                        .result(Map.of(
                                                "module", "DATA_QUALITY",
                                                "version", "1.0",
                                                "summary", Map.of("rowCount", 5000, "columnCount", 11, "duplicateRowCount", 23)
                                        ))
                                        .build(),
                                MlEngineModuleResultDto.builder()
                                        .module("LEAKAGE")
                                        .status("COMPLETED")
                                        .message("Data Leakage forensic audit completed.")
                                        .result(Map.of(
                                                "module", "LEAKAGE",
                                                "version", "1.0",
                                                "target", Map.of("column", "is_fraud", "taskType", "BINARY_CLASSIFICATION")
                                        ))
                                        .build()
                        ))
                        .build()
        );

        // 3. Start Run
        mockMvc.perform(post("/api/diagnostics/" + runId + "/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(runId))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // 4. Fetch Results
        mockMvc.perform(get("/api/diagnostics/" + runId + "/results"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(runId))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.results.length()").value(2))
                .andExpect(jsonPath("$.results[0].result.summary.rowCount").value(5000))
                .andExpect(jsonPath("$.results[1].result.target.column").value("is_fraud"));
    }

    @Test
    public void testFullRunLifecycleWithPerformanceCompleted() throws Exception {
        // 1. Create Run with DATA_QUALITY, LEAKAGE, DRIFT, and PERFORMANCE
        CreateDiagnosticRunRequestDto request = CreateDiagnosticRunRequestDto.builder()
                .model(ModelInfoDto.builder()
                        .name("fraud_classifier_v17")
                        .framework("xgboost")
                        .taskType("binary_classification")
                        .build())
                .evaluationDataset("s3://datasets/fraud_eval.parquet")
                .baselineDataset("s3://datasets/fraud_train.parquet")
                .targetColumn("is_fraud")
                .predictionColumn("pred_prob")
                .protectedAttribute("is_foreign_ip")
                .modules(List.of(DiagnosticModule.DATA_QUALITY, DiagnosticModule.LEAKAGE, DiagnosticModule.DRIFT, DiagnosticModule.PERFORMANCE))
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/diagnostics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        DiagnosticRunResponseDto createdDto = objectMapper.readValue(
                createResult.getResponse().getContentAsString(),
                DiagnosticRunResponseDto.class
        );
        String runId = createdDto.getId();

        // 2. Mock ML Engine response with 4 modules COMPLETED
        when(mlEngineClient.executeJob(any())).thenReturn(
                MlEngineJobResponseDto.builder()
                        .runId(runId)
                        .status("COMPLETED")
                        .executionTimeMs(450.0)
                        .modules(List.of(
                                MlEngineModuleResultDto.builder()
                                        .module("DATA_QUALITY")
                                        .status("COMPLETED")
                                        .message("Data Quality audit completed.")
                                        .result(Map.of("module", "DATA_QUALITY", "version", "1.0", "summary", Map.of("rowCount", 5000)))
                                        .build(),
                                MlEngineModuleResultDto.builder()
                                        .module("LEAKAGE")
                                        .status("COMPLETED")
                                        .message("Data Leakage forensic audit completed.")
                                        .result(Map.of("module", "LEAKAGE", "version", "1.0", "target", Map.of("column", "is_fraud")))
                                        .build(),
                                MlEngineModuleResultDto.builder()
                                        .module("DRIFT")
                                        .status("COMPLETED")
                                        .message("Distribution Drift audit completed.")
                                        .result(Map.of("module", "DRIFT", "version", "1.0", "summary", Map.of("maxPsi", 0.312, "healthScore", 85.0)))
                                        .build(),
                                MlEngineModuleResultDto.builder()
                                        .module("PERFORMANCE")
                                        .status("COMPLETED")
                                        .message("Model Performance audit completed.")
                                        .result(Map.of("module", "PERFORMANCE", "version", "1.0", "summary", Map.of("rocAuc", 0.892, "f1", 0.741, "expectedCalibrationError", 0.038)))
                                        .build()
                        ))
                        .build()
        );

        // 3. Start Run
        mockMvc.perform(post("/api/diagnostics/" + runId + "/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(runId))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // 4. Fetch Results
        mockMvc.perform(get("/api/diagnostics/" + runId + "/results"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(runId))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.results.length()").value(4))
                .andExpect(jsonPath("$.results[3].result.summary.rocAuc").value(0.892));
    }

    @Test
    public void testPartialRunLifecycleWithNotImplementedModules() throws Exception {
        // 1. Create Run with DATA_QUALITY, PERFORMANCE and EXPLAINABILITY (unimplemented)
        CreateDiagnosticRunRequestDto request = CreateDiagnosticRunRequestDto.builder()
                .model(ModelInfoDto.builder()
                        .name("credit_default_v2")
                        .framework("lightgbm")
                        .taskType("binary_classification")
                        .build())
                .evaluationDataset("s3://datasets/credit_eval.parquet")
                .baselineDataset("s3://datasets/credit_train.parquet")
                .targetColumn("default_flag")
                .predictionColumn("pred_prob")
                .modules(List.of(DiagnosticModule.DATA_QUALITY, DiagnosticModule.PERFORMANCE, DiagnosticModule.EXPLAINABILITY, DiagnosticModule.ROBUSTNESS))
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/diagnostics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        DiagnosticRunResponseDto createdDto = objectMapper.readValue(
                createResult.getResponse().getContentAsString(),
                DiagnosticRunResponseDto.class
        );
        String runId = createdDto.getId();

        // 2. Mock ML Engine response: DQ, PERF, EXPLAIN COMPLETED, ROBUSTNESS NOT_IMPLEMENTED
        when(mlEngineClient.executeJob(any())).thenReturn(
                MlEngineJobResponseDto.builder()
                        .runId(runId)
                        .status("PARTIAL")
                        .executionTimeMs(320.0)
                        .modules(List.of(
                                MlEngineModuleResultDto.builder()
                                        .module("DATA_QUALITY")
                                        .status("COMPLETED")
                                        .message("Data Quality audit completed.")
                                        .result(Map.of("module", "DATA_QUALITY", "summary", Map.of("rowCount", 1000)))
                                        .build(),
                                MlEngineModuleResultDto.builder()
                                        .module("PERFORMANCE")
                                        .status("COMPLETED")
                                        .message("Model Performance audit completed.")
                                        .result(Map.of("module", "PERFORMANCE", "summary", Map.of("rocAuc", 0.85)))
                                        .build(),
                                MlEngineModuleResultDto.builder()
                                        .module("EXPLAINABILITY")
                                        .status("COMPLETED")
                                        .message("Explainability audit completed.")
                                        .result(Map.of("module", "EXPLAINABILITY", "summary", Map.of("topFeature", "income", "method", "TREE_SHAP")))
                                        .build(),
                                MlEngineModuleResultDto.builder()
                                        .module("ROBUSTNESS")
                                        .status("NOT_IMPLEMENTED")
                                        .message("Robustness Engine verified; math deferred to subsequent phases.")
                                        .result(Map.of())
                                        .build()
                        ))
                        .build()
        );

        // 3. Start Run - should transition to PARTIAL
        mockMvc.perform(post("/api/diagnostics/" + runId + "/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(runId))
                .andExpect(jsonPath("$.status").value("PARTIAL"));

        // 4. Fetch Results
        mockMvc.perform(get("/api/diagnostics/" + runId + "/results"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(runId))
                .andExpect(jsonPath("$.status").value("PARTIAL"))
                .andExpect(jsonPath("$.results.length()").value(4));
    }

    @Test
    public void testFullRunLifecycleWithBiasCompleted() throws Exception {
        // 1. Create Run with all 6 ready modules: DQ, LEAKAGE, DRIFT, PERF, EXPLAIN, BIAS
        CreateDiagnosticRunRequestDto request = CreateDiagnosticRunRequestDto.builder()
                .model(ModelInfoDto.builder()
                        .name("fraud_classifier_v17")
                        .framework("xgboost")
                        .taskType("binary_classification")
                        .build())
                .evaluationDataset("s3://datasets/fraud_eval.parquet")
                .baselineDataset("s3://datasets/fraud_train.parquet")
                .targetColumn("is_fraud")
                .predictionColumn("pred_prob")
                .protectedAttribute("is_foreign_ip")
                .modules(List.of(
                        DiagnosticModule.DATA_QUALITY,
                        DiagnosticModule.LEAKAGE,
                        DiagnosticModule.DRIFT,
                        DiagnosticModule.PERFORMANCE,
                        DiagnosticModule.EXPLAINABILITY,
                        DiagnosticModule.BIAS
                ))
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/diagnostics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        DiagnosticRunResponseDto createdDto = objectMapper.readValue(
                createResult.getResponse().getContentAsString(),
                DiagnosticRunResponseDto.class
        );
        String runId = createdDto.getId();

        // 2. Mock ML Engine response with 6 modules COMPLETED
        when(mlEngineClient.executeJob(any())).thenReturn(
                MlEngineJobResponseDto.builder()
                        .runId(runId)
                        .status("COMPLETED")
                        .executionTimeMs(680.0)
                        .modules(List.of(
                                MlEngineModuleResultDto.builder()
                                        .module("DATA_QUALITY")
                                        .status("COMPLETED")
                                        .message("Data Quality audit completed.")
                                        .result(Map.of("module", "DATA_QUALITY", "summary", Map.of("rowCount", 5000)))
                                        .build(),
                                MlEngineModuleResultDto.builder()
                                        .module("LEAKAGE")
                                        .status("COMPLETED")
                                        .message("Data Leakage forensic audit completed.")
                                        .result(Map.of("module", "LEAKAGE", "target", Map.of("column", "is_fraud")))
                                        .build(),
                                MlEngineModuleResultDto.builder()
                                        .module("DRIFT")
                                        .status("COMPLETED")
                                        .message("Distribution Drift audit completed.")
                                        .result(Map.of("module", "DRIFT", "summary", Map.of("maxPsi", 0.12)))
                                        .build(),
                                MlEngineModuleResultDto.builder()
                                        .module("PERFORMANCE")
                                        .status("COMPLETED")
                                        .message("Model Performance audit completed.")
                                        .result(Map.of("module", "PERFORMANCE", "summary", Map.of("rocAuc", 0.89)))
                                        .build(),
                                MlEngineModuleResultDto.builder()
                                        .module("EXPLAINABILITY")
                                        .status("COMPLETED")
                                        .message("Model Explainability audit completed.")
                                        .result(Map.of("module", "EXPLAINABILITY", "summary", Map.of("topFeature", "amount")))
                                        .build(),
                                MlEngineModuleResultDto.builder()
                                        .module("BIAS")
                                        .status("COMPLETED")
                                        .message("Bias and Fairness audit completed.")
                                        .result(Map.of("module", "BIAS", "summary", Map.of("protectedAttribute", "is_foreign_ip", "worstDisparateImpactRatio", 0.76, "demographicParityGap", 0.08)))
                                        .build()
                        ))
                        .build()
        );

        // 3. Start Run
        mockMvc.perform(post("/api/diagnostics/" + runId + "/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(runId))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // 4. Fetch Results
        mockMvc.perform(get("/api/diagnostics/" + runId + "/results"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(runId))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.results.length()").value(6))
                .andExpect(jsonPath("$.results[5].result.summary.protectedAttribute").value("is_foreign_ip"))
                .andExpect(jsonPath("$.results[5].result.summary.worstDisparateImpactRatio").value(0.76));
    }

    @Test
    public void testFullRunLifecycleAllSevenModulesCompleted() throws Exception {
        // 1. Create Run with all 7 core modules: DQ, LEAKAGE, DRIFT, PERF, EXPLAIN, BIAS, ROBUSTNESS
        CreateDiagnosticRunRequestDto request = CreateDiagnosticRunRequestDto.builder()
                .model(ModelInfoDto.builder()
                        .name("fraud_classifier_v17")
                        .framework("xgboost")
                        .taskType("binary_classification")
                        .build())
                .evaluationDataset("s3://datasets/fraud_eval.parquet")
                .baselineDataset("s3://datasets/fraud_train.parquet")
                .targetColumn("is_fraud")
                .predictionColumn("pred_prob")
                .protectedAttribute("is_foreign_ip")
                .modules(List.of(
                        DiagnosticModule.DATA_QUALITY,
                        DiagnosticModule.LEAKAGE,
                        DiagnosticModule.DRIFT,
                        DiagnosticModule.PERFORMANCE,
                        DiagnosticModule.EXPLAINABILITY,
                        DiagnosticModule.BIAS,
                        DiagnosticModule.ROBUSTNESS
                ))
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/diagnostics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        DiagnosticRunResponseDto createdDto = objectMapper.readValue(
                createResult.getResponse().getContentAsString(),
                DiagnosticRunResponseDto.class
        );
        String runId = createdDto.getId();

        // 2. Mock ML Engine response with all 7 modules COMPLETED
        when(mlEngineClient.executeJob(any())).thenReturn(
                MlEngineJobResponseDto.builder()
                        .runId(runId)
                        .status("COMPLETED")
                        .executionTimeMs(820.0)
                        .modules(List.of(
                                MlEngineModuleResultDto.builder()
                                        .module("DATA_QUALITY")
                                        .status("COMPLETED")
                                        .message("Data Quality audit completed.")
                                        .result(Map.of("module", "DATA_QUALITY", "summary", Map.of("rowCount", 5000)))
                                        .build(),
                                MlEngineModuleResultDto.builder()
                                        .module("LEAKAGE")
                                        .status("COMPLETED")
                                        .message("Data Leakage forensic audit completed.")
                                        .result(Map.of("module", "LEAKAGE", "target", Map.of("column", "is_fraud")))
                                        .build(),
                                MlEngineModuleResultDto.builder()
                                        .module("DRIFT")
                                        .status("COMPLETED")
                                        .message("Distribution Drift audit completed.")
                                        .result(Map.of("module", "DRIFT", "summary", Map.of("maxPsi", 0.12)))
                                        .build(),
                                MlEngineModuleResultDto.builder()
                                        .module("PERFORMANCE")
                                        .status("COMPLETED")
                                        .message("Model Performance audit completed.")
                                        .result(Map.of("module", "PERFORMANCE", "summary", Map.of("rocAuc", 0.89)))
                                        .build(),
                                MlEngineModuleResultDto.builder()
                                        .module("EXPLAINABILITY")
                                        .status("COMPLETED")
                                        .message("Model Explainability audit completed.")
                                        .result(Map.of("module", "EXPLAINABILITY", "summary", Map.of("topFeature", "amount")))
                                        .build(),
                                MlEngineModuleResultDto.builder()
                                        .module("BIAS")
                                        .status("COMPLETED")
                                        .message("Bias and Fairness audit completed.")
                                        .result(Map.of("module", "BIAS", "summary", Map.of("protectedAttribute", "is_foreign_ip", "worstDisparateImpactRatio", 0.76)))
                                        .build(),
                                MlEngineModuleResultDto.builder()
                                        .module("ROBUSTNESS")
                                        .status("COMPLETED")
                                        .message("Adversarial Robustness audit completed.")
                                        .result(Map.of("module", "ROBUSTNESS", "summary", Map.of("gaussianJitter5PctFlipRate", 0.042, "boundaryFlipRate", 0.125, "topSensitiveFeature", "amount")))
                                        .build()
                        ))
                        .build()
        );

        // 3. Start Run
        mockMvc.perform(post("/api/diagnostics/" + runId + "/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(runId))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // 4. Fetch Results
        mockMvc.perform(get("/api/diagnostics/" + runId + "/results"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(runId))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.results.length()").value(7))
                .andExpect(jsonPath("$.results[6].module").value("ROBUSTNESS"))
                .andExpect(jsonPath("$.results[6].result.summary.gaussianJitter5PctFlipRate").value(0.042));
    }

    @Test
    public void testDuplicateExecutionRejected() throws Exception {
        // 1. Create Run
        CreateDiagnosticRunRequestDto request = CreateDiagnosticRunRequestDto.builder()
                .model(ModelInfoDto.builder()
                        .name("fraud_classifier_v17")
                        .framework("xgboost")
                        .taskType("binary_classification")
                        .build())
                .evaluationDataset("s3://datasets/fraud_eval.parquet")
                .targetColumn("is_fraud")
                .modules(List.of(DiagnosticModule.DATA_QUALITY))
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/diagnostics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        DiagnosticRunResponseDto createdDto = objectMapper.readValue(
                createResult.getResponse().getContentAsString(),
                DiagnosticRunResponseDto.class
        );
        String runId = createdDto.getId();

        // 2. Mock ML Engine response
        when(mlEngineClient.executeJob(any())).thenReturn(
                MlEngineJobResponseDto.builder()
                        .runId(runId)
                        .status("COMPLETED")
                        .executionTimeMs(150.0)
                        .modules(List.of(
                                MlEngineModuleResultDto.builder()
                                        .module("DATA_QUALITY")
                                        .status("COMPLETED")
                                        .message("Data Quality audit completed.")
                                        .result(Map.of("module", "DATA_QUALITY", "summary", Map.of("rowCount", 5000)))
                                        .build()
                        ))
                        .build()
        );

        // 3. First execution -> succeeds
        mockMvc.perform(post("/api/diagnostics/" + runId + "/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // 4. Duplicate execution -> rejected with 409 Conflict
        mockMvc.perform(post("/api/diagnostics/" + runId + "/run"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.statusCode").value(409))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Cannot execute diagnostic run")));
    }

    @Test
    public void testCreateDiagnosticRunWithDuplicateModulesFails() throws Exception {
        CreateDiagnosticRunRequestDto request = CreateDiagnosticRunRequestDto.builder()
                .model(ModelInfoDto.builder()
                        .name("fraud_classifier_v17")
                        .framework("xgboost")
                        .taskType("binary_classification")
                        .build())
                .evaluationDataset("s3://datasets/fraud_eval.parquet")
                .targetColumn("is_fraud")
                .modules(List.of(DiagnosticModule.DATA_QUALITY, DiagnosticModule.DATA_QUALITY))
                .build();

        mockMvc.perform(post("/api/diagnostics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Duplicate diagnostic modules")));
    }

    @Test
    public void testRunLifecycleMixedOutcomesBecomesPartial() throws Exception {
        CreateDiagnosticRunRequestDto request = CreateDiagnosticRunRequestDto.builder()
                .model(ModelInfoDto.builder()
                        .name("fraud_classifier_v17")
                        .framework("xgboost")
                        .taskType("binary_classification")
                        .build())
                .evaluationDataset("s3://datasets/fraud_eval.parquet")
                .targetColumn("is_fraud")
                .modules(List.of(DiagnosticModule.DATA_QUALITY, DiagnosticModule.LEAKAGE))
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/diagnostics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        DiagnosticRunResponseDto createdDto = objectMapper.readValue(
                createResult.getResponse().getContentAsString(),
                DiagnosticRunResponseDto.class
        );
        String runId = createdDto.getId();

        // 1 module COMPLETED, 1 module FAILED
        when(mlEngineClient.executeJob(any())).thenReturn(
                MlEngineJobResponseDto.builder()
                        .runId(runId)
                        .status("PARTIAL")
                        .executionTimeMs(200.0)
                        .modules(List.of(
                                MlEngineModuleResultDto.builder()
                                        .module("DATA_QUALITY")
                                        .status("COMPLETED")
                                        .message("Data Quality audit completed.")
                                        .result(Map.of("module", "DATA_QUALITY", "summary", Map.of("rowCount", 5000)))
                                        .build(),
                                MlEngineModuleResultDto.builder()
                                        .module("LEAKAGE")
                                        .status("FAILED")
                                        .message("Column calculation error in leakage detection.")
                                        .result(Map.of())
                                        .build()
                        ))
                        .build()
        );

        mockMvc.perform(post("/api/diagnostics/" + runId + "/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIAL"));
    }

    @Test
    public void testRunLifecyclePythonUnavailableMarksFailed() throws Exception {
        CreateDiagnosticRunRequestDto request = CreateDiagnosticRunRequestDto.builder()
                .model(ModelInfoDto.builder()
                        .name("fraud_classifier_v17")
                        .framework("xgboost")
                        .taskType("binary_classification")
                        .build())
                .evaluationDataset("s3://datasets/fraud_eval.parquet")
                .targetColumn("is_fraud")
                .modules(List.of(DiagnosticModule.DATA_QUALITY))
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/diagnostics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        DiagnosticRunResponseDto createdDto = objectMapper.readValue(
                createResult.getResponse().getContentAsString(),
                DiagnosticRunResponseDto.class
        );
        String runId = createdDto.getId();

        when(mlEngineClient.executeJob(any())).thenThrow(new RuntimeException("Connection refused: Python ML engine unavailable"));

        try {
            mockMvc.perform(post("/api/diagnostics/" + runId + "/run"));
        } catch (Exception ignored) {
        }

        // Verify the run in database was updated to FAILED
        mockMvc.perform(get("/api/diagnostics/" + runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorMessage").value(org.hamcrest.Matchers.containsString("Python ML engine unavailable")));
    }
}
