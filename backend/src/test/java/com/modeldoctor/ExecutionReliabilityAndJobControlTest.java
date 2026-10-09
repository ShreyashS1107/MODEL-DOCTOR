package com.modeldoctor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeldoctor.domain.DiagnosticModule;
import com.modeldoctor.domain.DiagnosticRun;
import com.modeldoctor.domain.DiagnosticStatus;
import com.modeldoctor.dto.*;
import com.modeldoctor.repository.DiagnosticRunRepository;
import com.modeldoctor.service.MlEngineClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@SuppressWarnings("null")
public class ExecutionReliabilityAndJobControlTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DiagnosticRunRepository runRepository;

    @MockBean
    private MlEngineClient mlEngineClient;

    private DiagnosticRunResponseDto createTestRun(List<DiagnosticModule> modules) throws Exception {
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
                .modules(modules)
                .build();

        MvcResult result = mockMvc.perform(post("/api/diagnostics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(), DiagnosticRunResponseDto.class);
    }

    @Test
    public void testExecutionEventLoggingAndTimeline() throws Exception {
        DiagnosticRunResponseDto runDto = createTestRun(List.of(DiagnosticModule.DATA_QUALITY, DiagnosticModule.LEAKAGE));
        String runId = runDto.getId();

        when(mlEngineClient.executeJob(any())).thenReturn(
                MlEngineJobResponseDto.builder()
                        .runId(runId)
                        .status("COMPLETED")
                        .executionTimeMs(180.0)
                        .modules(List.of(
                                MlEngineModuleResultDto.builder()
                                        .module("DATA_QUALITY")
                                        .status("COMPLETED")
                                        .message("Data Quality completed.")
                                        .result(Map.of("rowCount", 5000))
                                        .build(),
                                MlEngineModuleResultDto.builder()
                                        .module("LEAKAGE")
                                        .status("COMPLETED")
                                        .message("Leakage audit completed.")
                                        .result(Map.of("target", "is_fraud"))
                                        .build()
                        ))
                        .build()
        );

        // Start run
        mockMvc.perform(post("/api/diagnostics/" + runId + "/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // Fetch events timeline
        mockMvc.perform(get("/api/diagnostics/" + runId + "/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(5))))
                .andExpect(jsonPath("$[0].eventType").value("RUN_CREATED"))
                .andExpect(jsonPath("$[1].eventType").value("RUN_STARTED"))
                .andExpect(jsonPath("$[?(@.eventType == 'MODULE_STARTED')]", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$[?(@.eventType == 'MODULE_COMPLETED')]", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$[-1].eventType").value("RUN_COMPLETED"));
    }

    @Test
    public void testExecutionProgressCalculation() throws Exception {
        DiagnosticRunResponseDto runDto = createTestRun(List.of(
                DiagnosticModule.DATA_QUALITY,
                DiagnosticModule.LEAKAGE,
                DiagnosticModule.DRIFT,
                DiagnosticModule.PERFORMANCE
        ));
        String runId = runDto.getId();

        // Check progress before execution
        mockMvc.perform(get("/api/diagnostics/" + runId + "/progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runStatus").value("CREATED"))
                .andExpect(jsonPath("$.selectedModulesCount").value(4))
                .andExpect(jsonPath("$.completedModulesCount").value(0))
                .andExpect(jsonPath("$.progressPercent").value(0));

        when(mlEngineClient.executeJob(any())).thenReturn(
                MlEngineJobResponseDto.builder()
                        .runId(runId)
                        .status("COMPLETED")
                        .modules(List.of(
                                MlEngineModuleResultDto.builder().module("DATA_QUALITY").status("COMPLETED").result(Map.of("ok", true)).build(),
                                MlEngineModuleResultDto.builder().module("LEAKAGE").status("COMPLETED").result(Map.of("ok", true)).build(),
                                MlEngineModuleResultDto.builder().module("DRIFT").status("COMPLETED").result(Map.of("ok", true)).build(),
                                MlEngineModuleResultDto.builder().module("PERFORMANCE").status("COMPLETED").result(Map.of("ok", true)).build()
                        ))
                        .build()
        );

        mockMvc.perform(post("/api/diagnostics/" + runId + "/run"))
                .andExpect(status().isOk());

        // Check progress after completion
        mockMvc.perform(get("/api/diagnostics/" + runId + "/progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.completedModulesCount").value(4))
                .andExpect(jsonPath("$.failedModulesCount").value(0))
                .andExpect(jsonPath("$.progressPercent").value(100));
    }

    @Test
    public void testFailureIsolationAndPartialRunStatus() throws Exception {
        DiagnosticRunResponseDto runDto = createTestRun(List.of(
                DiagnosticModule.DATA_QUALITY,
                DiagnosticModule.LEAKAGE,
                DiagnosticModule.DRIFT
        ));
        String runId = runDto.getId();

        // DQ and LEAKAGE succeed, DRIFT fails
        when(mlEngineClient.executeJob(any())).thenReturn(
                MlEngineJobResponseDto.builder()
                        .runId(runId)
                        .status("PARTIAL")
                        .modules(List.of(
                                MlEngineModuleResultDto.builder().module("DATA_QUALITY").status("COMPLETED").result(Map.of("rowCount", 1000)).build(),
                                MlEngineModuleResultDto.builder().module("LEAKAGE").status("COMPLETED").result(Map.of("leaks", 0)).build(),
                                MlEngineModuleResultDto.builder().module("DRIFT").status("FAILED").message("Baseline column missing").build()
                        ))
                        .build()
        );

        mockMvc.perform(post("/api/diagnostics/" + runId + "/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIAL"));

        // Verify successful module results are strictly preserved
        mockMvc.perform(get("/api/diagnostics/" + runId + "/results"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIAL"))
                .andExpect(jsonPath("$.results", hasSize(3)))
                .andExpect(jsonPath("$.results[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.results[0].result.rowCount").value(1000))
                .andExpect(jsonPath("$.results[1].status").value("COMPLETED"))
                .andExpect(jsonPath("$.results[2].status").value("FAILED"));
    }

    @Test
    public void testFullRunRetryOnPartialRun() throws Exception {
        DiagnosticRunResponseDto runDto = createTestRun(List.of(
                DiagnosticModule.DATA_QUALITY,
                DiagnosticModule.LEAKAGE,
                DiagnosticModule.DRIFT
        ));
        String runId = runDto.getId();

        // 1. Initial execution: DQ succeeds, DRIFT & LEAKAGE fail
        when(mlEngineClient.executeJob(any())).thenReturn(
                MlEngineJobResponseDto.builder()
                        .runId(runId)
                        .status("PARTIAL")
                        .modules(List.of(
                                MlEngineModuleResultDto.builder().module("DATA_QUALITY").status("COMPLETED").result(Map.of("rowCount", 2000)).build(),
                                MlEngineModuleResultDto.builder().module("LEAKAGE").status("FAILED").message("Calc timeout").build(),
                                MlEngineModuleResultDto.builder().module("DRIFT").status("FAILED").message("Missing baseline").build()
                        ))
                        .build()
        );

        mockMvc.perform(post("/api/diagnostics/" + runId + "/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIAL"));

        // 2. Retry run: Only failed modules (LEAKAGE & DRIFT) re-execute and now succeed
        when(mlEngineClient.executeJob(any())).thenReturn(
                MlEngineJobResponseDto.builder()
                        .runId(runId)
                        .status("COMPLETED")
                        .modules(List.of(
                                MlEngineModuleResultDto.builder().module("LEAKAGE").status("COMPLETED").result(Map.of("leaks", 0)).build(),
                                MlEngineModuleResultDto.builder().module("DRIFT").status("COMPLETED").result(Map.of("maxPsi", 0.05)).build()
                        ))
                        .build()
        );

        mockMvc.perform(post("/api/diagnostics/" + runId + "/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.retryCount").value(1));

        // 3. Verify all 3 results now exist and are COMPLETED
        mockMvc.perform(get("/api/diagnostics/" + runId + "/results"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.results", hasSize(3)))
                .andExpect(jsonPath("$.results[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.results[0].result.rowCount").value(2000))
                .andExpect(jsonPath("$.results[1].status").value("COMPLETED"))
                .andExpect(jsonPath("$.results[2].status").value("COMPLETED"));
    }

    @Test
    public void testModuleLevelRetry() throws Exception {
        DiagnosticRunResponseDto runDto = createTestRun(List.of(
                DiagnosticModule.DATA_QUALITY,
                DiagnosticModule.LEAKAGE,
                DiagnosticModule.DRIFT
        ));
        String runId = runDto.getId();

        // 1. Initial execution: DQ succeeds, LEAKAGE & DRIFT fail
        when(mlEngineClient.executeJob(any())).thenReturn(
                MlEngineJobResponseDto.builder()
                        .runId(runId)
                        .status("PARTIAL")
                        .modules(List.of(
                                MlEngineModuleResultDto.builder().module("DATA_QUALITY").status("COMPLETED").result(Map.of("rowCount", 3000)).build(),
                                MlEngineModuleResultDto.builder().module("LEAKAGE").status("FAILED").message("Failed 1").build(),
                                MlEngineModuleResultDto.builder().module("DRIFT").status("FAILED").message("Failed 2").build()
                        ))
                        .build()
        );

        mockMvc.perform(post("/api/diagnostics/" + runId + "/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIAL"));

        // 2. Retry only LEAKAGE
        when(mlEngineClient.executeJob(any())).thenReturn(
                MlEngineJobResponseDto.builder()
                        .runId(runId)
                        .status("COMPLETED")
                        .modules(List.of(
                                MlEngineModuleResultDto.builder().module("LEAKAGE").status("COMPLETED").result(Map.of("target", "is_fraud")).build()
                        ))
                        .build()
        );

        RetryModulesRequestDto retryReq = new RetryModulesRequestDto(List.of(DiagnosticModule.LEAKAGE));
        mockMvc.perform(post("/api/diagnostics/" + runId + "/retry-modules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(retryReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIAL"))
                .andExpect(jsonPath("$.retryCount").value(1));

        // 3. Attempting to retry already completed DATA_QUALITY should be rejected with 400 Bad Request
        RetryModulesRequestDto invalidRetry = new RetryModulesRequestDto(List.of(DiagnosticModule.DATA_QUALITY));
        mockMvc.perform(post("/api/diagnostics/" + runId + "/retry-modules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRetry)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("already COMPLETED")));
    }

    @Test
    public void testCannotRetryCompletedRun() throws Exception {
        DiagnosticRunResponseDto runDto = createTestRun(List.of(DiagnosticModule.DATA_QUALITY));
        String runId = runDto.getId();

        when(mlEngineClient.executeJob(any())).thenReturn(
                MlEngineJobResponseDto.builder()
                        .runId(runId)
                        .status("COMPLETED")
                        .modules(List.of(
                                MlEngineModuleResultDto.builder().module("DATA_QUALITY").status("COMPLETED").result(Map.of("rowCount", 1000)).build()
                        ))
                        .build()
        );

        mockMvc.perform(post("/api/diagnostics/" + runId + "/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // Retry on COMPLETED run should fail with 409 Conflict
        mockMvc.perform(post("/api/diagnostics/" + runId + "/retry"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.statusCode").value(409))
                .andExpect(jsonPath("$.message", containsString("Only FAILED or PARTIAL runs can be retried")));
    }

    @Test
    public void testStaleExecutionRecovery() throws Exception {
        DiagnosticRunResponseDto runDto = createTestRun(List.of(DiagnosticModule.DATA_QUALITY));
        String runId = runDto.getId();

        // Simulate stuck RUNNING run by directly setting DB state with old timestamp
        DiagnosticRun entity = runRepository.findById(runId).orElseThrow();
        entity.setStatus(DiagnosticStatus.RUNNING);
        entity.setStartedAt(Instant.now().minus(30, ChronoUnit.MINUTES));
        runRepository.saveAndFlush(entity);

        // Call recover-stale with 15 minutes threshold
        mockMvc.perform(post("/api/diagnostics/recover-stale?thresholdMinutes=15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasItem(runId)));

        // Verify run is now marked FAILED with EXECUTION_STALE
        mockMvc.perform(get("/api/diagnostics/" + runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorMessage", containsString("EXECUTION_STALE")));
    }

    @Test
    public void testConcurrentExecutionProtection() throws Exception {
        DiagnosticRunResponseDto runDto = createTestRun(List.of(DiagnosticModule.DATA_QUALITY));
        String runId = runDto.getId();

        // Simulate Python delay
        when(mlEngineClient.executeJob(any())).thenAnswer(invocation -> {
            Thread.sleep(300);
            return MlEngineJobResponseDto.builder()
                    .runId(runId)
                    .status("COMPLETED")
                    .modules(List.of(
                            MlEngineModuleResultDto.builder().module("DATA_QUALITY").status("COMPLETED").result(Map.of("rowCount", 500)).build()
                    ))
                    .build();
        });

        int numThreads = 3;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        List<Future<?>> futures = new CopyOnWriteArrayList<>();
        for (int i = 0; i < numThreads; i++) {
            futures.add(executor.submit(() -> {
                try {
                    latch.await();
                    MvcResult res = mockMvc.perform(post("/api/diagnostics/" + runId + "/run")).andReturn();
                    int code = res.getResponse().getStatus();
                    if (code == 200) {
                        successCount.incrementAndGet();
                    } else if (code == 409) {
                        conflictCount.incrementAndGet();
                    }
                } catch (Exception ignored) {}
            }));
        }

        latch.countDown();
        for (Future<?> f : futures) {
            f.get(5, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // Exactly one should succeed, others must be 409 Conflict
        assertEquals(1, successCount.get(), "Exactly one execution request must succeed");
        assertEquals(2, conflictCount.get(), "Concurrent duplicate execution requests must return 409 Conflict");
    }
}
