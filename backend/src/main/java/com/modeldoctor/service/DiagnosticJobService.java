package com.modeldoctor.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeldoctor.domain.*;
import com.modeldoctor.dto.*;
import com.modeldoctor.exception.DiagnosticExecutionException;
import com.modeldoctor.exception.InvalidStatusTransitionException;
import com.modeldoctor.exception.ResourceNotFoundException;
import com.modeldoctor.repository.DiagnosticResultRepository;
import com.modeldoctor.repository.DiagnosticRunEventRepository;
import com.modeldoctor.repository.DiagnosticRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DiagnosticJobService {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticJobService.class);

    private final DiagnosticRunRepository runRepository;
    private final DiagnosticResultRepository resultRepository;
    private final DiagnosticRunEventRepository eventRepository;
    private final MlEngineClient mlEngineClient;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final CorrelationAnalysisService correlationAnalysisService;

    public DiagnosticJobService(
            DiagnosticRunRepository runRepository,
            DiagnosticResultRepository resultRepository,
            DiagnosticRunEventRepository eventRepository,
            MlEngineClient mlEngineClient,
            ObjectMapper objectMapper,
            TransactionTemplate transactionTemplate,
            CorrelationAnalysisService correlationAnalysisService) {
        this.runRepository = runRepository;
        this.resultRepository = resultRepository;
        this.eventRepository = eventRepository;
        this.mlEngineClient = mlEngineClient;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.correlationAnalysisService = correlationAnalysisService;
    }

    /**
     * Executes a newly created or queued diagnostic run.
     */
    public DiagnosticRun executeRun(String runId) {
        // Phase 1: Atomically transition status to RUNNING in a dedicated transaction
        DiagnosticRun run = transactionTemplate.execute(status -> transitionToRunning(runId));
        if (run == null) {
            throw new ResourceNotFoundException("Diagnostic run not found with ID: " + runId);
        }

        return dispatchAndFinalize(runId, run.getModules().stream().map(DiagnosticRunModule::getModule).collect(Collectors.toList()));
    }

    /**
     * Retries all non-completed modules for a FAILED or PARTIAL run.
     */
    public DiagnosticRun retryRun(String runId) {
        // Phase 1: Atomically transition failed modules to RUNNING in a dedicated transaction
        List<DiagnosticModule> modulesToRetry = transactionTemplate.execute(status -> transitionToRetrying(runId, null));
        if (modulesToRetry == null || modulesToRetry.isEmpty()) {
            return runRepository.findById(runId).orElseThrow(() -> new ResourceNotFoundException("Diagnostic run not found: " + runId));
        }

        return dispatchAndFinalize(runId, modulesToRetry);
    }

    /**
     * Retries specific failed modules for a FAILED or PARTIAL run.
     */
    public DiagnosticRun retryModules(String runId, List<DiagnosticModule> targetModules) {
        if (targetModules == null || targetModules.isEmpty()) {
            throw new IllegalArgumentException("At least one diagnostic module must be selected for retry.");
        }

        // Phase 1: Atomically transition specified modules to RUNNING
        List<DiagnosticModule> modulesToRetry = transactionTemplate.execute(status -> transitionToRetrying(runId, targetModules));
        if (modulesToRetry == null || modulesToRetry.isEmpty()) {
            return runRepository.findById(runId).orElseThrow(() -> new ResourceNotFoundException("Diagnostic run not found: " + runId));
        }

        return dispatchAndFinalize(runId, modulesToRetry);
    }

    /**
     * Internal orchestration: Dispatches HTTP request to Python ML Engine and persists results atomically.
     */
    private DiagnosticRun dispatchAndFinalize(String runId, List<DiagnosticModule> modulesToExecute) {
        DiagnosticRun run = runRepository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("Diagnostic run not found with ID: " + runId));

        MlEngineJobRequestDto jobRequest = MlEngineJobRequestDto.builder()
                .runId(run.getId())
                .modelName(run.getModelName())
                .modelFramework(run.getModelFramework())
                .taskType(run.getTaskType())
                .modelStorageUri(run.getModelStorageUri())
                .modelArtifactId(run.getModelArtifactId())
                .executionMode(run.getExecutionMode())
                .evaluationDataset(run.getEvaluationDataset())
                .baselineDataset(run.getBaselineDataset())
                .targetColumn(run.getTargetColumn())
                .predictionColumn(run.getPredictionColumn())
                .protectedAttribute(run.getProtectedAttribute())
                .modules(modulesToExecute.stream().map(Enum::name).collect(Collectors.toList()))
                .build();

        try {
            // Phase 2: Downstream ML Engine execution (outside DB transaction)
            MlEngineJobResponseDto response = mlEngineClient.executeJob(jobRequest);

            // Phase 3: Atomically validate and persist structured results
            DiagnosticRun finalizedRun = transactionTemplate.execute(status -> persistResultsAndFinalize(runId, response, modulesToExecute));

            // Phase 4: Trigger Phase 4 Cross-Module Diagnostic Intelligence & Correlation Analysis
            try {
                correlationAnalysisService.analyzeAndPersist(runId);
            } catch (Exception corrEx) {
                log.warn("Failed to generate cross-module correlations for run {}: {}", runId, corrEx.getMessage(), corrEx);
            }

            return finalizedRun;

        } catch (InvalidStatusTransitionException | ResourceNotFoundException ex) {
            throw ex;
        } catch (Exception ex) {
            String sanitizedError = sanitizeErrorMessage(ex.getMessage());
            log.error("Execution failed for diagnostic run {}: {}", runId, sanitizedError, ex);

            // Phase 4: Record failure state in database within a dedicated transaction
            return transactionTemplate.execute(status -> recordExecutionFailure(runId, sanitizedError, modulesToExecute));
        }
    }

    private synchronized DiagnosticRun transitionToRunning(String runId) {
        DiagnosticRun run = runRepository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("Diagnostic run not found with ID: " + runId));

        if (run.getStatus() == DiagnosticStatus.RUNNING) {
            log.warn("Rejected execution for run {} because it is already RUNNING", runId);
            throw new InvalidStatusTransitionException(
                    String.format("Cannot execute diagnostic run '%s': Run is already currently running.", runId)
            );
        } else if (run.getStatus() == DiagnosticStatus.COMPLETED) {
            log.warn("Rejected execution for run {} because it has already COMPLETED", runId);
            throw new InvalidStatusTransitionException(
                    String.format("Cannot execute diagnostic run '%s': Run has already completed. Re-running is not permitted.", runId)
            );
        } else if (run.getStatus() == DiagnosticStatus.FAILED || run.getStatus() == DiagnosticStatus.PARTIAL) {
            log.warn("Rejected execution for run {} because it is in {} status (must use retry)", runId, run.getStatus());
            throw new InvalidStatusTransitionException(
                    String.format("Cannot execute diagnostic run '%s': Run is in %s state. Use the retry endpoint to re-execute.", runId, run.getStatus())
            );
        }

        Instant now = Instant.now();
        log.info("Transitioning diagnostic run {} from {} to RUNNING state", runId, run.getStatus());
        run.setStatus(DiagnosticStatus.RUNNING);
        run.setStartedAt(now);
        run.setCompletedAt(null);
        run.setExecutionDurationMs(null);
        run.setErrorMessage(null);

        // Record RUN_STARTED event
        eventRepository.save(DiagnosticRunEvent.builder()
                .runId(runId)
                .eventType("RUN_STARTED")
                .message("Diagnostic execution initiated across " + run.getModules().size() + " module(s).")
                .timestamp(now)
                .build());

        for (DiagnosticRunModule module : run.getModules()) {
            module.setStatus(ModuleExecutionStatus.RUNNING);
            module.setStartedAt(now);
            module.setCompletedAt(null);
            module.setExecutionDurationMs(null);
            module.setStatusMessage("Executing diagnostic probes...");

            eventRepository.save(DiagnosticRunEvent.builder()
                    .runId(runId)
                    .module(module.getModule().name())
                    .eventType("MODULE_STARTED")
                    .message("Diagnostic module " + module.getModule() + " started probe evaluation.")
                    .timestamp(now)
                    .build());
        }

        return runRepository.saveAndFlush(run);
    }

    private synchronized List<DiagnosticModule> transitionToRetrying(String runId, List<DiagnosticModule> specificModules) {
        DiagnosticRun run = runRepository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("Diagnostic run not found with ID: " + runId));

        if (run.getStatus() != DiagnosticStatus.FAILED && run.getStatus() != DiagnosticStatus.PARTIAL) {
            log.warn("Rejected retry for run {} with status {}", runId, run.getStatus());
            throw new InvalidStatusTransitionException(
                    String.format("Cannot retry diagnostic run '%s' with current status %s. Only FAILED or PARTIAL runs can be retried.",
                            runId, run.getStatus())
            );
        }

        Instant now = Instant.now();
        int newRetryCount = run.getRetryCount() + 1;
        run.setRetryCount(newRetryCount);
        run.setStatus(DiagnosticStatus.RUNNING);
        run.setStartedAt(now);
        run.setCompletedAt(null);
        run.setExecutionDurationMs(null);
        run.setErrorMessage(null);

        List<DiagnosticModule> modulesToRetry = new ArrayList<>();

        if (specificModules == null || specificModules.isEmpty()) {
            // Retry all modules that did not complete successfully
            for (DiagnosticRunModule m : run.getModules()) {
                if (m.getStatus() != ModuleExecutionStatus.COMPLETED) {
                    modulesToRetry.add(m.getModule());
                }
            }
        } else {
            // Validate specific requested modules
            Set<DiagnosticModule> runModules = run.getModules().stream().map(DiagnosticRunModule::getModule).collect(Collectors.toSet());
            for (DiagnosticModule targetMod : specificModules) {
                if (!runModules.contains(targetMod)) {
                    throw new IllegalArgumentException("Module " + targetMod + " is not configured for diagnostic run " + runId);
                }
                DiagnosticRunModule modEntity = run.getModules().stream().filter(m -> m.getModule() == targetMod).findFirst().orElseThrow();
                if (modEntity.getStatus() == ModuleExecutionStatus.COMPLETED) {
                    throw new IllegalArgumentException("Module " + targetMod + " has already COMPLETED successfully and cannot be retried.");
                }
                modulesToRetry.add(targetMod);
            }
        }

        if (modulesToRetry.isEmpty()) {
            log.info("No non-completed modules found to retry for run {}", runId);
            return Collections.emptyList();
        }

        // Record RUN_RETRIED event
        eventRepository.save(DiagnosticRunEvent.builder()
                .runId(runId)
                .eventType("RUN_RETRIED")
                .message("Diagnostic retry attempt #" + newRetryCount + " started for " + modulesToRetry.size() + " module(s): " + modulesToRetry)
                .timestamp(now)
                .build());

        for (DiagnosticRunModule module : run.getModules()) {
            if (modulesToRetry.contains(module.getModule())) {
                module.setStatus(ModuleExecutionStatus.RUNNING);
                module.setStartedAt(now);
                module.setCompletedAt(null);
                module.setExecutionDurationMs(null);
                module.setStatusMessage("Retrying diagnostic probe...");

                eventRepository.save(DiagnosticRunEvent.builder()
                        .runId(runId)
                        .module(module.getModule().name())
                        .eventType("MODULE_STARTED")
                        .message("Diagnostic module " + module.getModule() + " retrying probe execution.")
                        .timestamp(now)
                        .build());
            }
        }

        runRepository.saveAndFlush(run);
        return modulesToRetry;
    }

    private DiagnosticRun persistResultsAndFinalize(String runId, MlEngineJobResponseDto response, List<DiagnosticModule> executedModules) {
        DiagnosticRun run = runRepository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("Diagnostic run not found with ID: " + runId));

        Instant now = Instant.now();
        Set<DiagnosticModule> executedSet = new HashSet<>(executedModules);
        Set<DiagnosticModule> processedSet = new HashSet<>();

        // Process module results returned by downstream ML Engine
        if (response != null && response.getModules() != null) {
            for (MlEngineModuleResultDto modRes : response.getModules()) {
                DiagnosticModule moduleEnum;
                try {
                    moduleEnum = DiagnosticModule.valueOf(modRes.getModule());
                } catch (IllegalArgumentException e) {
                    log.warn("Unknown module identifier returned by ML engine: {}", modRes.getModule());
                    continue;
                }

                if (!executedSet.contains(moduleEnum)) {
                    log.warn("Ignoring unrequested module {} returned in ML engine response for run {}", moduleEnum, runId);
                    continue;
                }

                processedSet.add(moduleEnum);
                ModuleExecutionStatus moduleStatus = parseModuleStatus(modRes.getStatus());
                String statusMessage = modRes.getMessage();

                // Result validation: completed modules must contain non-empty structured result
                if (moduleStatus == ModuleExecutionStatus.COMPLETED) {
                    if (modRes.getResult() == null || modRes.getResult().isEmpty()) {
                        log.error("Module {} reported COMPLETED but returned empty result payload for run {}", moduleEnum, runId);
                        moduleStatus = ModuleExecutionStatus.FAILED;
                        statusMessage = "Validation error: Module reported COMPLETED but returned empty result payload.";
                    }
                } else if (moduleStatus == ModuleExecutionStatus.FAILED && (statusMessage == null || statusMessage.isBlank())) {
                    statusMessage = modRes.getError() != null && !modRes.getError().isBlank()
                            ? modRes.getError()
                            : "Module execution failed in downstream engine.";
                }

                // Update module entity in the run
                final ModuleExecutionStatus finalModStatus = moduleStatus;
                final String finalModMsg = statusMessage;
                Optional<DiagnosticRunModule> runModuleOpt = run.getModules().stream()
                        .filter(m -> m.getModule() == moduleEnum)
                        .findFirst();

                runModuleOpt.ifPresent(m -> {
                    m.setStatus(finalModStatus);
                    m.setStatusMessage(finalModMsg);
                    m.setCompletedAt(now);
                    if (m.getStartedAt() != null) {
                        m.setExecutionDurationMs(Duration.between(m.getStartedAt(), now).toMillis());
                    }
                });

                // Record module event
                eventRepository.save(DiagnosticRunEvent.builder()
                        .runId(runId)
                        .module(moduleEnum.name())
                        .eventType(moduleStatus == ModuleExecutionStatus.COMPLETED ? "MODULE_COMPLETED" : "MODULE_FAILED")
                        .message(statusMessage)
                        .timestamp(now)
                        .build());

                // Persist structured JSON result using upsert to avoid duplicate row violations
                String resultJson = "{}";
                if (modRes.getResult() != null && !modRes.getResult().isEmpty()) {
                    try {
                        resultJson = objectMapper.writeValueAsString(modRes.getResult());
                    } catch (JsonProcessingException e) {
                        log.error("Failed to serialize module result JSON for {}", moduleEnum, e);
                        resultJson = "{}";
                    }
                }

                saveOrUpdateDiagnosticResult(run.getId(), moduleEnum, moduleStatus, resultJson, now);
            }
        }

        // Handle any executed modules that were omitted from ML Engine response
        for (DiagnosticModule missingMod : executedModules) {
            if (!processedSet.contains(missingMod)) {
                log.error("Requested module {} was not returned by ML Engine for run {}", missingMod, runId);
                Optional<DiagnosticRunModule> modOpt = run.getModules().stream().filter(m -> m.getModule() == missingMod).findFirst();
                modOpt.ifPresent(m -> {
                    m.setStatus(ModuleExecutionStatus.FAILED);
                    m.setStatusMessage("Module was requested but not returned in diagnostic engine response.");
                    m.setCompletedAt(now);
                });

                eventRepository.save(DiagnosticRunEvent.builder()
                        .runId(runId)
                        .module(missingMod.name())
                        .eventType("MODULE_FAILED")
                        .message("Module omitted in ML engine response.")
                        .timestamp(now)
                        .build());

                saveOrUpdateDiagnosticResult(run.getId(), missingMod, ModuleExecutionStatus.FAILED, "{}", now);
            }
        }

        // Determine final run state based on all module outcomes
        int completedCount = 0;
        int failedCount = 0;
        int notImplementedCount = 0;
        int pendingOrRunningCount = 0;

        for (DiagnosticRunModule module : run.getModules()) {
            if (module.getStatus() == ModuleExecutionStatus.COMPLETED) {
                completedCount++;
            } else if (module.getStatus() == ModuleExecutionStatus.FAILED) {
                failedCount++;
            } else if (module.getStatus() == ModuleExecutionStatus.NOT_IMPLEMENTED) {
                notImplementedCount++;
            } else {
                pendingOrRunningCount++;
            }
        }

        run.setCompletedAt(now);
        if (run.getStartedAt() != null) {
            run.setExecutionDurationMs(Duration.between(run.getStartedAt(), now).toMillis());
        }

        if (failedCount == 0 && notImplementedCount == 0 && pendingOrRunningCount == 0 && completedCount > 0) {
            run.setStatus(DiagnosticStatus.COMPLETED);
            run.setErrorMessage(null);
            log.info("Diagnostic run {} completed successfully with all {} module(s) COMPLETED", run.getId(), completedCount);
            eventRepository.save(DiagnosticRunEvent.builder()
                    .runId(runId)
                    .eventType("RUN_COMPLETED")
                    .message("Diagnostic run completed successfully with all " + completedCount + " module(s) COMPLETED.")
                    .timestamp(now)
                    .build());
        } else if (completedCount == 0 && failedCount > 0 && pendingOrRunningCount == 0) {
            run.setStatus(DiagnosticStatus.FAILED);
            run.setErrorMessage(String.format("All %d module(s) failed execution.", failedCount));
            log.warn("Diagnostic run {} finished with all modules FAILED", run.getId());
            eventRepository.save(DiagnosticRunEvent.builder()
                    .runId(runId)
                    .eventType("RUN_FAILED")
                    .message("All " + failedCount + " module(s) failed execution.")
                    .timestamp(now)
                    .build());
        } else if (completedCount > 0 && (failedCount > 0 || notImplementedCount > 0)) {
            run.setStatus(DiagnosticStatus.PARTIAL);
            run.setErrorMessage(failedCount > 0 ? String.format("%d of %d module(s) failed execution.", failedCount, run.getModules().size()) : null);
            log.info("Diagnostic run {} finished with PARTIAL status (completed: {}, failed: {}, not_implemented: {})",
                    run.getId(), completedCount, failedCount, notImplementedCount);
            eventRepository.save(DiagnosticRunEvent.builder()
                    .runId(runId)
                    .eventType("RUN_PARTIAL")
                    .message(String.format("%d module(s) completed, %d failed.", completedCount, failedCount))
                    .timestamp(now)
                    .build());
        } else {
            run.setStatus(DiagnosticStatus.COMPLETED);
            run.setErrorMessage(null);
            eventRepository.save(DiagnosticRunEvent.builder()
                    .runId(runId)
                    .eventType("RUN_COMPLETED")
                    .message("Diagnostic run finalized.")
                    .timestamp(now)
                    .build());
        }

        return runRepository.saveAndFlush(run);
    }

    private DiagnosticRun recordExecutionFailure(String runId, String sanitizedError, List<DiagnosticModule> attemptedModules) {
        DiagnosticRun run = runRepository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("Diagnostic run not found with ID: " + runId));

        Instant now = Instant.now();
        run.setStatus(DiagnosticStatus.FAILED);
        run.setCompletedAt(now);
        if (run.getStartedAt() != null) {
            run.setExecutionDurationMs(Duration.between(run.getStartedAt(), now).toMillis());
        }
        run.setErrorMessage(sanitizedError);

        eventRepository.save(DiagnosticRunEvent.builder()
                .runId(runId)
                .eventType("RUN_FAILED")
                .message("Execution aborted: " + sanitizedError)
                .timestamp(now)
                .build());

        Set<DiagnosticModule> attemptedSet = new HashSet<>(attemptedModules);
        for (DiagnosticRunModule module : run.getModules()) {
            if (attemptedSet.contains(module.getModule())) {
                module.setStatus(ModuleExecutionStatus.FAILED);
                module.setCompletedAt(now);
                module.setStatusMessage("Execution failed: " + sanitizedError);

                eventRepository.save(DiagnosticRunEvent.builder()
                        .runId(runId)
                        .module(module.getModule().name())
                        .eventType("MODULE_FAILED")
                        .message("Execution failed: " + sanitizedError)
                        .timestamp(now)
                        .build());
            }
        }

        return runRepository.saveAndFlush(run);
    }

    private void saveOrUpdateDiagnosticResult(String runId, DiagnosticModule module, ModuleExecutionStatus status, String resultJson, Instant now) {
        Optional<DiagnosticResult> existingOpt = resultRepository.findByRunIdAndModule(runId, module);
        if (existingOpt.isPresent()) {
            DiagnosticResult existing = existingOpt.get();
            existing.setStatus(status);
            existing.setResultJson(resultJson != null ? resultJson : "{}");
            existing.setCreatedAt(now);
            resultRepository.save(existing);
        } else {
            DiagnosticResult newResult = DiagnosticResult.builder()
                    .runId(runId)
                    .module(module)
                    .status(status)
                    .resultJson(resultJson != null ? resultJson : "{}")
                    .createdAt(now)
                    .build();
            resultRepository.save(newResult);
        }
    }

    public void recordEvent(String runId, String module, String eventType, String message) {
        eventRepository.save(DiagnosticRunEvent.builder()
                .runId(runId)
                .module(module)
                .eventType(eventType)
                .message(message)
                .timestamp(Instant.now())
                .build());
    }

    public List<DiagnosticRunEventDto> getRunEvents(String runId) {
        List<DiagnosticRunEvent> events = eventRepository.findByRunIdOrderByTimestampAsc(runId);
        return events.stream().map(e -> DiagnosticRunEventDto.builder()
                .id(e.getId())
                .runId(e.getRunId())
                .module(e.getModule())
                .eventType(e.getEventType())
                .message(e.getMessage())
                .timestamp(e.getTimestamp())
                .build()).collect(Collectors.toList());
    }

    public DiagnosticProgressDto getRunProgress(String runId) {
        DiagnosticRun run = runRepository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("Diagnostic run not found with ID: " + runId));

        int selected = run.getModules().size();
        int completed = 0;
        int failed = 0;
        int running = 0;
        int pending = 0;
        int skipped = 0;

        for (DiagnosticRunModule m : run.getModules()) {
            if (m.getStatus() == ModuleExecutionStatus.COMPLETED) {
                completed++;
            } else if (m.getStatus() == ModuleExecutionStatus.FAILED) {
                failed++;
            } else if (m.getStatus() == ModuleExecutionStatus.RUNNING) {
                running++;
            } else if (m.getStatus() == ModuleExecutionStatus.SKIPPED) {
                skipped++;
            } else {
                pending++;
            }
        }

        int progressPercent = 0;
        if (selected > 0) {
            if (run.getStatus() == DiagnosticStatus.COMPLETED || run.getStatus() == DiagnosticStatus.PARTIAL || run.getStatus() == DiagnosticStatus.FAILED) {
                progressPercent = 100;
            } else {
                progressPercent = (int) Math.round(((double) (completed + failed + skipped) / selected) * 100.0);
            }
        }

        return DiagnosticProgressDto.builder()
                .runId(run.getId())
                .runStatus(run.getStatus())
                .selectedModulesCount(selected)
                .completedModulesCount(completed)
                .failedModulesCount(failed)
                .runningModulesCount(running)
                .pendingModulesCount(pending)
                .skippedModulesCount(skipped)
                .progressPercent(progressPercent)
                .build();
    }

    /**
     * Stale Execution Recovery: Identifies and recovers any diagnostic runs stuck in RUNNING state
     * beyond a configurable duration threshold in minutes.
     */
    public synchronized List<String> recoverStaleRuns(long thresholdMinutes) {
        Duration threshold = Duration.ofMinutes(thresholdMinutes > 0 ? thresholdMinutes : 15);
        Instant cutoff = Instant.now().minus(threshold);
        List<DiagnosticRun> runningRuns = runRepository.findByStatus(DiagnosticStatus.RUNNING);
        List<String> recoveredRunIds = new ArrayList<>();

        for (DiagnosticRun run : runningRuns) {
            if (run.getStartedAt() != null && run.getStartedAt().isBefore(cutoff)) {
                log.warn("Recovering stale diagnostic run {} stuck in RUNNING state since {}", run.getId(), run.getStartedAt());
                Instant now = Instant.now();
                run.setStatus(DiagnosticStatus.FAILED);
                run.setCompletedAt(now);
                run.setErrorMessage(String.format("[EXECUTION_STALE] Execution heartbeat timed out after %d minute(s) without completion. Run may be retried.",
                        threshold.toMinutes()));

                eventRepository.save(DiagnosticRunEvent.builder()
                        .runId(run.getId())
                        .eventType("EXECUTION_STALE_RECOVERED")
                        .message("Run marked FAILED by stale execution recovery mechanism.")
                        .timestamp(now)
                        .build());

                for (DiagnosticRunModule module : run.getModules()) {
                    if (module.getStatus() == ModuleExecutionStatus.RUNNING || module.getStatus() == ModuleExecutionStatus.PENDING) {
                        module.setStatus(ModuleExecutionStatus.FAILED);
                        module.setCompletedAt(now);
                        module.setStatusMessage("[EXECUTION_STALE] Module interrupted due to stale execution timeout.");
                    }
                }

                runRepository.saveAndFlush(run);
                recoveredRunIds.add(run.getId());
            }
        }

        if (!recoveredRunIds.isEmpty()) {
            log.info("Successfully recovered {} stale diagnostic run(s): {}", recoveredRunIds.size(), recoveredRunIds);
        }
        return recoveredRunIds;
    }

    private String sanitizeErrorMessage(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return "An unexpected error occurred during diagnostic job execution.";
        }
        String singleLine = rawMessage.split("\n")[0].trim();
        if (singleLine.length() > 500) {
            singleLine = singleLine.substring(0, 500) + "...";
        }
        return singleLine;
    }

    private ModuleExecutionStatus parseModuleStatus(String statusStr) {
        if (statusStr == null) return ModuleExecutionStatus.COMPLETED;
        try {
            return ModuleExecutionStatus.valueOf(statusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ModuleExecutionStatus.COMPLETED;
        }
    }
}
