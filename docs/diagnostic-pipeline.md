# Model Doctor — Diagnostic Pipeline Architecture & Hardening Guide

## 1. System Overview

The Model Doctor Diagnostic Pipeline orchestrates end-to-end audits across Machine Learning models and datasets. The pipeline integrates three primary tiers:

```text
Next.js Frontend (/diagnose, /diagnostic/[id])
                      ↓ HTTP POST /api/diagnostics
              Spring Boot Orchestrator (Port 8080)
                      ↓ JPA / Hibernate
           Relational Database (PostgreSQL / H2)
                      ↓ HTTP POST /api/v1/diagnostics/run
               Python FastAPI ML Engine (Port 8000)
                      ↓ Registry & Engine Dispatch
 ┌─────────────────────────────────────────────────────────────┐
 │ 8 Core Diagnostic Engines:                                  │
 │ 1. DATA_QUALITY      (Nulls, Duplicates, Types, Schema)     │
 │ 2. LEAKAGE           (Target/Feature Correlation Heuristics)│
 │ 3. DRIFT             (PSI, KS-Test, Jensen-Shannon)         │
 │ 4. PERFORMANCE       (ROC-AUC, PR-AUC, F1, LogLoss, Brier)  │
 │ 5. EXPLAINABILITY    (TreeSHAP, LinearSHAP, KernelSHAP)     │
 │ 6. BIAS              (Disparate Impact, Demographic Parity) │
 │ 7. ROBUSTNESS        (Gaussian Noise, Boundary Flip, Rank)  │
 │ 8. ERROR_FORENSICS   (Residuals, Disparities, Flip Rates)   │
 └─────────────────────────────────────────────────────────────┘
                      ↓ JSON DiagnosticReport
              Spring Boot Persistence & Status Resolution
                      ↓
              RAW DIAGNOSTICS
                      ↓
               CORRELATIONS
                      ↓
               INVESTIGATION
                      ↓
              EVIDENCE GRAPH
                      ↓
               REMEDIATION
                      ↓
         EXPERIMENTAL VALIDATION
                      ↓
            BEFORE / AFTER EVIDENCE
                      ↓ HTTP GET /api/diagnostics/{id}/...
             Next.js Workstation (/diagnostic/[id])
```

---

## 2. End-to-End Pipeline Walkthrough

### A. Frontend Entry Point (`/diagnose`)
- **User Inputs:**
  - Model Name (`fraud_classifier_v17`), Framework (`xgboost`, `lightgbm`, `sklearn`), Task Type (`binary_classification`).
  - Evaluation Dataset Path (e.g., `s3://datasets/fraud_eval.parquet` or local test path).
  - Baseline Dataset Path (e.g., `s3://datasets/fraud_train.parquet` for Drift).
  - Target Column Name (`is_fraud`), Prediction Column (`pred_prob`), Protected Attribute (`is_foreign_ip` for Bias/Fairness).
  - Module Selection: Checkboxes for `DATA_QUALITY`, `LEAKAGE`, `DRIFT`, `PERFORMANCE`, `EXPLAINABILITY`, `BIAS`, `ROBUSTNESS`.
- **Action:** Clicking "Launch Diagnostics" sends `POST /api/diagnostics` with `CreateDiagnosticRunRequestDto`.
- **Response:** Receives `DiagnosticRunResponseDto` with unique `runId` (e.g., `run_a9b1c2d3`) in `CREATED` status.
- **Trigger Execution:** Client immediately dispatches `POST /api/diagnostics/{runId}/run` and navigates to `/diagnostic/{runId}`.

### B. Spring Boot Creation Flow (`POST /api/diagnostics`)
- **Controller:** `DiagnosticController.createDiagnosticRun(@Valid @RequestBody CreateDiagnosticRunRequestDto)`
- **Validation:**
  - `@NotBlank` constraints on `evaluationDataset` and `targetColumn`.
  - `@NotEmpty` constraint on `modules`.
  - Duplicate module detection in `DiagnosticService.createDiagnosticRun`: Throws HTTP 400 (`IllegalArgumentException`) if duplicates exist.
- **Persistence:**
  - Inserts row into `diagnostic_runs` (`status = CREATED`).
  - Inserts 1 row per selected module into `diagnostic_run_modules` (`status = REQUESTED`).
- **Response:** Returns `DiagnosticRunResponseDto` with `HTTP 201 Created`.

### C. Execution Flow (`POST /api/diagnostics/{id}/run`)
- **Controller:** `DiagnosticController.startDiagnosticRun(id)` -> `DiagnosticService.startDiagnosticRun(id)` -> `DiagnosticJobService.executeDiagnosticJob(id)`.
- **Lifecycle Transition Validation:**
  - Checks if `run.getStatus() == CREATED`.
  - If status is `RUNNING`, `COMPLETED`, `FAILED`, or `PARTIAL`, execution is rejected with `InvalidStatusTransitionException` (HTTP 409 Conflict), preventing duplicate concurrent or re-entrant executions.
- **Execution Step:**
  1. Sets `run.status = RUNNING`, `run.startedAt = Instant.now()`.
  2. Sets each module in `diagnostic_run_modules` to `status = RUNNING`.
  3. Builds `MlEngineJobRequestDto` containing run ID, model info, dataset paths, column mappings, and requested module strings.
  4. Calls Python ML Engine via `MlEngineClient.executeJob(jobRequest)`.
- **Result Processing & Validation:**
  - Validates that returned module results belong to requested modules.
  - Rejects/flags malformed module outputs.
  - Updates `diagnostic_run_modules` status to `COMPLETED`, `FAILED`, or `NOT_IMPLEMENTED`.
  - Persists structured JSON into `diagnostic_results`.
- **Run State Resolution:**
  - All requested modules `COMPLETED` -> Run `COMPLETED`.
  - All requested modules `FAILED` -> Run `FAILED`.
  - Any module `FAILED` or `NOT_IMPLEMENTED` alongside `COMPLETED` -> Run `PARTIAL`.
- **Error Handling:**
  - If Python is down, times out, or throws an unhandled exception, `DiagnosticJobService` catches the exception, marks `run.status = FAILED`, marks pending modules `FAILED`, stores a sanitized error message, and saves to database.

### D. Python ML Engine Flow (`POST /api/v1/diagnostics/run`)
- **FastAPI Router:** `app/api/v1/diagnostics.py`.
- **Validation:** Pydantic schema validation for `DiagnosticRunRequest` (`run_id`, `model`, `evaluation_dataset`, `target_column`, `modules`).
- **Data & Model Resolution:**
  - `DatasetLoader`: Resolves and loads pandas DataFrame from Parquet, CSV, or synthetic test generators.
  - `ModelLoader` & `SklearnModelAdapter`: Loads serialized model weights or falls back to synthetic mock predictor for end-to-end integration tests.
- **Engine Dispatch via `DiagnosticRegistry`:**
  - Iterates over requested modules:
    - `DATA_QUALITY`: Dispatches to `DataQualityEngine.run()`.
    - `LEAKAGE`: Dispatches to `LeakageEngine.run()`.
    - `DRIFT`: Dispatches to `DriftEngine.run()`.
    - `PERFORMANCE`: Dispatches to `PerformanceEngine.run()`.
    - `EXPLAINABILITY`: Dispatches to `ExplainabilityEngine.run()`.
    - `BIAS`: Dispatches to `FairnessEngine.run()`.
    - `ROBUSTNESS`: Dispatches to `RobustnessEngine.run()`.
    - Deferred modules (e.g. `EXPERIMENTS`): Returned with `status = NOT_IMPLEMENTED`.
    - Unknown modules: Returned with `status = FAILED`, `message = "Unknown diagnostic module"`.
- **Error Isolation:** Each engine runs in an isolated `try/except` block. An uncaught engine exception marks only that module `FAILED` while remaining engines proceed.
- **Response:** Returns `DiagnosticRunResponse` with execution duration, module array, and overall status (`COMPLETED`, `PARTIAL`, or `FAILED`).

### E. Result Retrieval & Workstation Display
- **API Endpoint:** `GET /api/diagnostics/{id}/results`.
- **Database Query:** `diagnosticResultRepository.findByRunIdOrderByIdAsc(id)`.
- **DTO Mapping:** `DiagnosticResultsResponseDto` with serialized module result dictionaries.
- **Next.js Workstation (`/diagnostic/[id]`):**
  - Fetches run status and module results.
  - Maps module results to specialized workstation views:
    - Data Quality View: Missingness, schema types, duplicates.
    - Leakage View: High feature-target mutual info, proxy IDs.
    - Drift View: PSI values, KS p-values, feature distribution divergence.
    - Performance View: ROC-AUC curve, PR curve, calibration error, Brier score.
    - Explainability View: TreeSHAP / KernelSHAP top feature importance.
    - Fairness View: Disparate impact ratio, demographic parity gap per sensitive slice.
    - Robustness View: Gaussian noise flip rates, decision boundary vulnerability.

---

## 3. Database Schema & Integrity Constraints

The relational schema is defined by JPA entities and mapped into H2/PostgreSQL:

### `diagnostic_runs`
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | VARCHAR(64) | PRIMARY KEY | Unique run identifier (e.g., `run_1a2b3c4d`) |
| `model_name` | VARCHAR(255) | NOT NULL | Name of evaluated model |
| `model_framework` | VARCHAR(64) | NULLABLE | xgboost, lightgbm, sklearn, pytorch |
| `task_type` | VARCHAR(64) | NULLABLE | binary_classification, regression, etc. |
| `model_storage_uri` | VARCHAR(512) | NULLABLE | S3 or local URI to model artifact |
| `evaluation_dataset` | VARCHAR(512) | NOT NULL | URI to evaluation dataset |
| `baseline_dataset` | VARCHAR(512) | NULLABLE | URI to baseline dataset (for drift) |
| `target_column` | VARCHAR(128) | NOT NULL | Ground truth target column name |
| `prediction_column` | VARCHAR(128) | NULLABLE | Prediction / score column name |
| `protected_attribute`| VARCHAR(128) | NULLABLE | Sensitive feature name for fairness |
| `status` | VARCHAR(32) | NOT NULL | CREATED, RUNNING, COMPLETED, FAILED, PARTIAL |
| `created_at` | TIMESTAMP | NOT NULL | Creation timestamp |
| `started_at` | TIMESTAMP | NULLABLE | Execution start timestamp |
| `completed_at` | TIMESTAMP | NULLABLE | Completion / termination timestamp |
| `error_message` | VARCHAR(1024) | NULLABLE | Sanitized error message if failed |

### `diagnostic_run_modules`
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT (Identity) | PRIMARY KEY | Module row ID |
| `run_id` | VARCHAR(64) | FOREIGN KEY -> `diagnostic_runs.id` | Associated run |
| `module` | VARCHAR(64) | NOT NULL | Module enum name |
| `status` | VARCHAR(32) | NOT NULL | REQUESTED, RUNNING, COMPLETED, FAILED, NOT_IMPLEMENTED |
| `status_message` | VARCHAR(512) | NULLABLE | Diagnostic status detail message |
| **Constraint** | `uk_run_module` | **UNIQUE (`run_id`, `module`)** | Prevents duplicate module entries per run |

### `diagnostic_results`
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT (Identity) | PRIMARY KEY | Result row ID |
| `run_id` | VARCHAR(64) | FOREIGN KEY -> `diagnostic_runs.id` | Associated run |
| `module` | VARCHAR(64) | NOT NULL | Module enum name |
| `status` | VARCHAR(32) | NOT NULL | Module execution status |
| `result_json` | TEXT | NOT NULL | Structured diagnostic JSON output |
| `created_at` | TIMESTAMP | NOT NULL | Persistence timestamp |
| **Constraint** | `uk_result_run_module`| **UNIQUE (`run_id`, `module`)**| Authoritative uniqueness: at most 1 result per module per run |

---

## 4. Status Machine & Invariant Transitions

### Run Lifecycle States:
- `CREATED`: Run registered; inputs validated; awaiting execution.
- `RUNNING`: ML engine processing requested modules.
- `COMPLETED`: All requested modules executed successfully.
- `PARTIAL`: One or more modules failed or were deferred, while at least one module completed.
- `FAILED`: Execution could not start, Python engine was unreachable, or all modules failed.

### Valid State Transitions:
```text
[Initial] ──> CREATED ──> RUNNING ──┬──> COMPLETED (all modules ok)
                                    ├──> PARTIAL   (mixed or deferred)
                                    └──> FAILED    (engine/all failed)
```

### Prohibited / Enforced Invariants:
- `COMPLETED` -> `RUNNING` ❌ (Rejected with HTTP 409 Conflict)
- `FAILED` -> `RUNNING` ❌ (Rejected with HTTP 409 Conflict)
- `PARTIAL` -> `RUNNING` ❌ (Rejected with HTTP 409 Conflict)
- `RUNNING` -> `RUNNING` ❌ (Rejected with HTTP 409 Conflict)

---

## 5. Failure Semantics & Error Hardening

1. **Python ML Engine Unreachable / Timeout:**
   - Spring Boot catches `RestClientException` / `HttpServerErrorException`.
   - Run status transitions to `FAILED`.
   - Pending modules transition to `FAILED`.
   - Error message is sanitized (stripping Python internal tracebacks) and saved in `diagnostic_runs.error_message`.
2. **Individual Module Failure:**
   - Handled inside Python engine without crashing other modules.
   - Returned with `status: FAILED` and `message: "<cause>"`.
   - Spring Boot records module failure, persists any successful peer module results, and marks run as `PARTIAL`.
3. **Deferred / Unimplemented Modules:**
   - Returned with `status: NOT_IMPLEMENTED`.
   - Run marks as `PARTIAL`, clearly indicating to the UI that the module was deferred rather than crashed.
4. **Duplicate Run Triggers:**
   - Blocked at the service layer by status verification. HTTP 409 Conflict returned immediately without issuing secondary calls to Python.
5. **Duplicate Module Payloads:**
   - Blocked during creation in `DiagnosticService`. HTTP 400 Bad Request returned before database insertion.

---

## 6. Real vs. Mock Data Provenance in Frontend

- **Live Runs (`/diagnostic/[id]`):**
  - When backend results exist for a module (`status === "COMPLETED"` and `result` payload present), the workstation maps directly from live backend math.
  - If a module is `FAILED` or `NOT_IMPLEMENTED`, the workstation renders a clear failure/deferred banner. It **never** silently falls back to mock figures for an active audit.
- **Workstation Overview / Demo HUD:**
  - Overview cards and telemetry feeds include `isMockData: boolean` flags to make provenance explicit to users and automated tests.

---

## 7. Concurrency, Observability & Security

1. **Concurrency Safety:**
   - Each diagnostic run receives an isolated UUID run ID.
   - Python ML engines operate statelessly on request payloads, instantiating local estimators without global mutable state.
2. **Observability:**
   - Key structured lifecycle log lines emitted in Spring Boot:
     - Run Creation (`Created diagnostic run run_xxx`)
     - Run Execution (`Starting execution lifecycle for diagnostic run run_xxx`)
     - Engine Dispatch & Persistence (`Diagnostic run run_xxx completed successfully with all N module(s) COMPLETED`)
   - Logs omit sensitive raw tabular data and secrets.
3. **Security:**
   - Path and column parameters validated against strict string constraints.
   - Error messages sanitized before returning to API clients.

---

## 8. Phase 3B — Artifact Lifecycle, Schema Intelligence & Validation Flow

### A. Artifact Lifecycle & Soft-Delete Architecture

Model Doctor provides a centralized immutable local filesystem artifact repository for machine learning models and dataset files:
- **Storage Directories:** `backend/storage/models/{id}/` and `backend/storage/datasets/{id}/`
- **SHA-256 Deduplication:** Content hashes are computed during streaming upload. If an identical file has already been ingested, its metadata record is reused without duplicating physical disk storage.
- **Soft Deletion (`DELETE /api/artifacts/models/{id}`, `DELETE /api/artifacts/datasets/{id}`):**
  - Sets `is_deleted = true` and records `deleted_at = Instant.now()`.
  - Soft-deleted artifacts are excluded from active listing (`GET /api/artifacts/models`, `GET /api/artifacts/datasets`).
  - Soft-deleted artifacts **cannot** be selected for new diagnostic runs (rejected with `404 RESOURCE_NOT_FOUND`).
  - Physical files on disk are **never deleted**, ensuring that historical diagnostic runs (`GET /api/diagnostics/{id}`) retain 100% immutable provenance and remain fully auditable indefinitely.

### B. Rich Dataset Schema Profiling & Intelligence

During dataset ingestion (`POST /api/artifacts/datasets`), the backend performs single-pass streaming profiling without loading the entire raw file into memory repeatedly:
- **Global Metrics:** Total row count, column count, SHA-256 hash.
- **Per-Column Profiles (`ColumnProfileDto`):**
  - Classification: `numeric`, `categorical`, `boolean`, `datetime`, `text`
  - Null metrics: `nullCount`, `nullPercentage`
  - Cardinality: `uniqueCount`, `uniquePercentage`
  - Heuristic flags: `isConstant` (uniqueCount == 1), `isNearConstant` (top category > 95%), `isIdentifierLike` (uniqueCount == rowCount & high row count), `isHighCardinality`
  - Numeric distributions: `min`, `max`, `mean`, `stdDev`, `p25`, `p50` (median), `p75`
  - Sample representations: `exampleValues`

### C. Target & Prediction Column Intelligence Heuristics

1. **Target Suggestions:**
   - Transparency: Suggests candidate targets with confidence scores `[0.0, 1.0]` and explanatory reasons.
   - Signals:
     - Standard naming conventions (`target`, `label`, `y`, `is_fraud`, `fraud`, `default`, `churn`, `outcome`, `response`, `class`, `is_*`, `has_*`)
     - Binary cardinality (`uniqueCount == 2`)
     - Model feature exclusion (absent from model's input feature list)
     - Schema terminal positioning (rightmost column)
   - **Safety Rule:** Target suggestions are purely advisory. Model Doctor **never** silently auto-selects a target.
2. **Prediction Column Detection:**
   - Detects columns with names like `prediction`, `predicted`, `pred_prob`, `probability`, `score`, `y_pred`, `y_score`.
   - Checks if numerical values are bounded in `[0.0, 1.0]`.
   - **Safety Rule:** Model inference is always prioritized for REAL runs unless explicitly configured.

### D. Model ↔ Dataset Compatibility & Binary Classification Rules

Before any REAL diagnostic run can be created or executed, the preflight validation engine enforces strict compatibility checks:

| Error Code | Trigger Condition |
|---|---|
| `MISSING_MODEL_FEATURE` | Model requires feature $f$, but evaluation dataset schema does not contain $f$. |
| `TARGET_IN_MODEL_FEATURES` | Selected target column is included in the model's input feature set. |
| `TARGET_NOT_FOUND` | Configured target column is not present in the dataset schema. |
| `TARGET_HAS_INVALID_CARDINALITY` | Target column does not have exactly 2 distinct classes. |
| `MULTICLASS_NOT_SUPPORTED` | Model task type is `multiclass` (pipeline only supports binary classification). |
| `REGRESSION_NOT_SUPPORTED` | Model task type is `regression`. |
| `PROTECTED_ATTRIBUTE_IS_TARGET` | Configured protected attribute equals the target column. |
| `PROTECTED_ATTRIBUTE_NOT_FOUND` | Configured protected attribute is not in evaluation dataset. |
| `PROTECTED_ATTRIBUTE_INVALID_CARDINALITY` | Protected attribute has fewer than 2 distinct subgroups. |
| `BASELINE_PROTECTED_ATTRIBUTE_MISSING` | Protected attribute present in evaluation dataset but missing from baseline dataset. |
| `BASELINE_FEATURE_MISMATCH` | Feature present in evaluation dataset is missing from baseline dataset. |
| `MODULE_PREREQUISITE_UNMET` | Selected diagnostic module lacks required configuration. |
| `RESOURCE_NOT_FOUND` | Referenced model or dataset artifact ID does not exist or has been soft-deleted. |

### E. Diagnostic Module Prerequisites

Each diagnostic module specifies explicit prerequisites verified during preflight:
- `DATA_QUALITY`: Requires evaluation dataset artifact.
- `LEAKAGE`: Requires evaluation dataset artifact + target column.
- `DRIFT`: Requires evaluation dataset artifact + baseline dataset artifact.
- `PERFORMANCE`: Requires evaluation dataset artifact + target column (binary classification) + trained model artifact.
- `EXPLAINABILITY`: Requires evaluation dataset artifact + trained model artifact.
- `BIAS`: Requires evaluation dataset artifact + target column + trained model artifact + protected attribute (with $\ge 2$ distinct groups).
- `ROBUSTNESS`: Requires evaluation dataset artifact + trained model artifact.

---

## 9. Configuration Validation Flow

```text
       Frontend / Client
               │
               ▼
   1. Select Stored Artifacts or Upload New
   (GET /api/artifacts/models, GET /api/artifacts/datasets)
               │
               ▼
   2. Rich Schema Inspection
   (Column profiles, Cardinality, Types, Quantiles)
               │
               ▼
   3. Target & Protected Attribute Intelligence
   (Scored suggestions with transparent reasons)
               │
               ▼
   4. Preflight Validation
   POST /api/diagnostics/validate
   ┌───────────────────────────────────────────────────────────┐
   │ • Model ↔ Dataset feature schema compatibility            │
   │ • Binary classification task verification                 │
   │ • Target cardinality & model feature exclusion            │
   │ • Evaluation vs Baseline feature alignment                │
   │ • Protected attribute presence & group count              │
   │ • Module prerequisite verification for all 7 engines      │
   └───────────────────────────────────────────────────────────┘
               │
       ┌───────┴───────┐
       ▼               ▼
 [valid = false]  [valid = true]
 Actionable Error    │
 Diagnostics         ▼
              5. Create Diagnostic Run
              POST /api/diagnostics
              (Independent backend enforcement)
                     │
                     ▼
              6. Execute Diagnostics
              POST /api/diagnostics/{id}/run
                     │
                     ▼
              7. Python Runtime Execution & Validation
              (Real model inference; zero mock fallback)
```

---

## 10. Standardized API Error Contract

Validation errors return deterministic, structured payloads with machine-readable error codes:

```json
{
  "valid": false,
  "errors": [
    {
      "code": "MISSING_MODEL_FEATURE",
      "field": "evaluationDataset",
      "resourceId": null,
      "message": "Model requires feature 'income' but the evaluation dataset does not contain it."
    },
    {
      "code": "MODULE_PREREQUISITE_UNMET",
      "field": "modules",
      "resourceId": "BIAS",
      "message": "Module BIAS is missing prerequisite: Protected attribute (e.g. gender, age, is_foreign_ip)"
    }
  ],
  "warnings": [
    {
      "code": "EXTRA_DATASET_FEATURE",
      "field": "evaluationDataset",
      "resourceId": null,
      "message": "Evaluation dataset contains 1 non-model feature(s): ['notes']"
    }
  ],
  "compatibility": {
    "missingModelFeatures": ["income"],
    "extraDatasetFeatures": ["notes"],
    "isFeatureCompatible": false
  },
  "moduleValidation": {
    "DATA_QUALITY": {
      "module": "DATA_QUALITY",
      "compatible": true,
      "missingPrerequisites": [],
      "message": "Module prerequisites satisfied"
    },
    "BIAS": {
      "module": "BIAS",
      "compatible": false,
      "missingPrerequisites": ["Protected attribute (e.g. gender, age, is_foreign_ip)"],
      "message": "Missing required prerequisites: Protected attribute (e.g. gender, age, is_foreign_ip)"
    }
  }
}
```

---

## 11. Phase 3C — Diagnostic Execution Reliability, Job Control & Result Integrity

Phase 3C establishes rigorous job lifecycle controls, transactional safety boundaries, optimistic concurrency protection, selective retry capabilities, failure isolation, and auditable event tracking across the entire diagnostic execution pipeline.

### A. Execution Architecture & Flow

```text
Frontend (/diagnostic/[id])
           │
           │ POST /api/diagnostics/{id}/run (or /retry, /retry-modules)
           ▼
Spring Boot Orchestrator
   ┌───────────────────────────────────────────────────────────┐
   │ 1. Transaction 1: Atomic State Transition                 │
   │    • Optimistic locking check (@Version)                  │
   │    • Status transition: CREATED / FAILED / PARTIAL        │
   │      ──> QUEUED ──> RUNNING                               │
   │    • Reset targeted module statuses (RUNNING)             │
   │    • Persist & Commit immediately                         │
   │    • Record RUN_STARTED / RUN_RETRIED events              │
   └───────────────────────────────────────────────────────────┘
           │
           │ HTTP POST /api/v1/diagnostics/run
           │ (NO open DB transaction during network call)
           │ (Explicit Connect: 10s, Read: 120s timeouts)
           ▼
Python ML Engine (FastAPI)
   ┌───────────────────────────────────────────────────────────┐
   │ 2. Isolated Module Dispatch & Execution                   │
   │    • Sequential/Wave execution of requested modules       │
   │    • Independent try/except boundary per engine           │
   │    • Real model inference & dataset math                  │
   │    • Structured error payload on engine failure           │
   │    • Response aggregation (COMPLETED / PARTIAL / FAILED)  │
   └───────────────────────────────────────────────────────────┘
           │
           │ HTTP 200 JSON DiagnosticReport
           ▼
Spring Boot Orchestrator
   ┌───────────────────────────────────────────────────────────┐
   │ 3. Result Validation & Pre-Persistence Checks             │
   │    • Validate non-null structured output                  │
   │    • Validate module membership and runId match           │
   │    • Validate JSON serialization integrity                │
   │                                                           │
   │ 4. Transaction 2: Atomic Result Persistence               │
   │    • Upsert diagnostic_results (prevent duplicates)       │
   │    • Update diagnostic_run_modules statuses & timings     │
   │    • Record MODULE_COMPLETED / MODULE_FAILED events       │
   │    • Resolve overall Run Status (COMPLETED/PARTIAL/FAILED)│
   │    • Record RUN_COMPLETED / RUN_PARTIAL / RUN_FAILED      │
   │    • Commit result & state atomically                     │
   └───────────────────────────────────────────────────────────┘
           │
           ▼
Next.js Workstation
   ┌───────────────────────────────────────────────────────────┐
   │ 5. Real-Time Observability & Job Control                  │
   │    • Live non-overlapping status polling (GET /run)       │
   │    • Real mathematical progress: completed / total * 100% │
   │    • Live auditable execution event stream (GET /events)  │
   │    • Safe browser reload & resume polling                 │
   │    • Full run retry & selective module retry actions      │
   └───────────────────────────────────────────────────────────┘
```

---

### B. Execution State Machines

#### 1. Run Lifecycle State Machine

```text
       ┌──────────────┐
       │   CREATED    │
       └──────┬───────┘
              │
              │ (Execution Triggered)
              ▼
       ┌──────────────┐
       │    QUEUED    │
       └──────┬───────┘
              │
              │ (Worker Dispatch)
              ▼
       ┌──────────────┐
       │   RUNNING    │◄─────────────────────────────┐
       └──────┬───────┘                              │
              │                                      │
     ┌────────┼──────────────┐                       │
     │        │              │                       │
     ▼        ▼              ▼                       │ (POST /retry)
┌─────────┐ ┌─────────┐ ┌─────────┐                  │
│COMPLETED│ │ PARTIAL │ │ FAILED  │                  │
└─────────┘ └────┬────┘ └────┬────┘                  │
                 │           │                       │
                 └───────────┴───────────────────────┘
```

| State | Description | Legal Next Transitions |
|---|---|---|
| `CREATED` | Initial configuration registered; preflight validated; waiting for execution. | `QUEUED`, `RUNNING`, `FAILED` |
| `QUEUED` | Placed into worker execution queue. | `RUNNING`, `FAILED` |
| `RUNNING` | Diagnostics actively executing in Python ML engine. | `COMPLETED`, `PARTIAL`, `FAILED` |
| `COMPLETED` | All requested diagnostic modules finished successfully. Terminal state. | None (Terminal) |
| `PARTIAL` | At least one module completed and at least one module failed or skipped. | `RUNNING` (via explicit `/retry` or `/retry-modules`) |
| `FAILED` | Execution could not start, ML engine crashed, timed out, or all modules failed. | `RUNNING` (via explicit `/retry`) |

**Enforced Invariant Rules:**
- `COMPLETED` -> `RUNNING` is strictly prohibited (returns `409 RUN_ALREADY_COMPLETED`).
- `RUNNING` -> `RUNNING` is strictly prohibited (returns `409 RUN_ALREADY_RUNNING`).
- Direct re-execution of `FAILED` or `PARTIAL` runs without calling `/retry` or `/retry-modules` is prohibited (`409 CONFLICT`).

#### 2. Module Lifecycle State Machine

```text
          ┌─────────────┐
          │   PENDING   │ (or REQUESTED)
          └──────┬──────┘
                 │
         ┌───────┴───────────────┐
         │ (Selected)            │ (Not Selected / Excluded)
         ▼                       ▼
  ┌─────────────┐         ┌─────────────┐
  │   RUNNING   │         │   SKIPPED   │
  └──────┬──────┘         └─────────────┘
         │
   ┌─────┴─────────────────┐
   │ (Success + Persisted) │ (Engine Exception / Persistence Failure)
   ▼                       ▼
┌─────────────┐     ┌─────────────┐
│  COMPLETED  │     │   FAILED    │
└─────────────┘     └──────┬──────┘
                           │
                           │ (POST /retry-modules)
                           ▼
                    ┌─────────────┐
                    │   RUNNING   │
                    └─────────────┘
```

---

### C. Execution Reliability & Job Control

#### 1. Optimistic Locking & Concurrency Protection
To prevent race conditions where two simultaneous execution requests (`POST /api/diagnostics/{id}/run`) both observe `CREATED` state:
- `DiagnosticRun` entity utilizes JPA `@Version Long version`.
- The state transition from `CREATED` -> `RUNNING` executes in an isolated atomic transaction and commits before invoking the ML engine.
- A concurrent duplicate request encounters an `OptimisticLockException` or detects `status != CREATED` and is rejected with `HTTP 409 Conflict` (`RUN_ALREADY_RUNNING`).

#### 2. Transaction Boundaries & Non-Blocking Python Calls
- **Zero DB Locks During ML Inference:** A database transaction is **never** held open while waiting on Python ML execution.
- **Three-Phase Transaction Model:**
  1. *Tx1:* Transition run to `RUNNING`, mark modules `RUNNING`, commit immediately.
  2. *Network Call:* Invoke Python ML Engine over HTTP with timeout protection.
  3. *Tx2:* Validate payload, upsert results, transition module statuses, record events, resolve run status, commit.

#### 3. Execution Timeouts
Configured in `application.yml` with sensible defaults:
- Connection Timeout: `python.execution.connect-timeout-ms: 10000` (10s)
- Read Timeout: `python.execution.read-timeout-ms: 120000` (120s)
- If Python exceeds the read timeout, the connection is cleanly aborted, the run transitions to `FAILED`, and the error is structured as `PYTHON_TIMEOUT_ERROR`.

#### 4. Explicit Run & Module Retry
- **Full Run Retry (`POST /api/diagnostics/{id}/retry`):**
  - Allowed only for runs in `FAILED` or `PARTIAL` status.
  - Preserves original configuration, dataset pointers, and artifact provenance.
  - Increments `retryCount`.
  - Resets failed/skipped modules to `RUNNING` while preserving previously successful module results.
- **Selective Module Retry (`POST /api/diagnostics/{id}/retry-modules`):**
  - Accepts a list of specific module names in payload: `{"modules": ["DRIFT", "ROBUSTNESS"]}`.
  - Validates that requested modules are part of the run configuration and are currently in `FAILED` status.
  - Re-executes only the requested failed modules, keeping all other module results intact.

#### 5. Failure Isolation & Status Resolution
- Uncaught exceptions in one engine (e.g. `DRIFT` matrix singularity) are caught inside Python's module isolation boundary.
- Surviving modules (`DATA_QUALITY`, `LEAKAGE`, `PERFORMANCE`, etc.) continue execution without interruption.
- Overall Run Status Resolution:
  $$\text{Status} = \begin{cases} \text{COMPLETED} & \text{if all selected modules are COMPLETED} \\ \text{FAILED} & \text{if all selected modules are FAILED} \\ \text{PARTIAL} & \text{if } \ge 1 \text{ COMPLETED and } \ge 1 \text{ FAILED / SKIPPED / NOT\_IMPLEMENTED} \end{cases}$$

#### 6. Stale Execution Recovery
- If the application or Python process restarts while a job is in `RUNNING` status, it will not hang indefinitely.
- `DiagnosticJobService.recoverStaleRuns(staleThresholdMinutes)` identifies runs whose heartbeat/update time exceeds the threshold (default: 30 minutes).
- Marks the run as `FAILED` (`error_message: "EXECUTION_STALE: Execution timed out or crashed without reporting completion"`) and records an `EXECUTION_STALE_RECOVERED` event.
- User can inspect the failure and invoke `/retry` when ready.

---

### D. Result Integrity & Persistence Guarantees

1. **Pre-Persistence Result Validation:**
   - Spring Boot verifies that:
     - Module name is non-blank and matches requested module list.
     - Returned `run_id` matches the originating run ID.
     - Result dictionary is non-null and valid JSON.
     - If result JSON cannot be serialized or validated, the module is marked `FAILED` rather than falsely reported as `COMPLETED`.
2. **Persistence-Before-Success Ordering:**
   - Result JSON is written to `diagnostic_results` table before marking module status as `COMPLETED`.
   - If database insertion fails (e.g., disk full, DB constraint error), the transaction rolls back and the module status transitions to `FAILED`.
3. **Duplicate Result Prevention:**
   - `diagnostic_results` enforces a unique constraint `uk_result_run_module (run_id, module)`.
   - `DiagnosticJobService.saveOrUpdateDiagnosticResult` performs atomic upsert logic, ensuring that retries update the active result cleanly without duplicate rows or constraint violations.

---

### E. Execution Events & Auditing

Meaningful lifecycle milestones are recorded in `diagnostic_run_events`:

| Event Type | Associated Module | Description |
|---|---|---|
| `RUN_CREATED` | `null` | Diagnostic run entity created and preflight validated. |
| `RUN_STARTED` | `null` | Execution job initiated and dispatched to ML engine. |
| `RUN_RETRIED` | `null` | Retry attempt initiated for failed or partial run. |
| `MODULE_STARTED` | Module Enum | Individual diagnostic module began execution. |
| `MODULE_COMPLETED` | Module Enum | Module completed and structured results persisted. |
| `MODULE_FAILED` | Module Enum | Module execution failed; error recorded. |
| `RUN_COMPLETED` | `null` | All modules finished successfully. |
| `RUN_PARTIAL` | `null` | Run terminated with mixed completion status. |
| `RUN_FAILED` | `null` | Run execution failed entirely. |
| `EXECUTION_STALE_RECOVERED` | `null` | Stale run detected and safely transitioned to FAILED. |

API Endpoint: `GET /api/diagnostics/{id}/events` returns the complete chronological event stream for rendering the Workstation Execution Timeline.

---

### F. Real Progress & Workstation Observability

API Endpoint: `GET /api/diagnostics/{id}/progress` calculates real-time progress metrics derived from actual module entity states:
```json
{
  "runId": "run_a9b1c2d3",
  "runStatus": "RUNNING",
  "totalModules": 7,
  "completedModules": 4,
  "failedModules": 1,
  "runningModules": 1,
  "pendingModules": 1,
  "progressPercent": 57
}
```

$$\text{Progress \%} = \text{round}\left(\frac{\text{completedModules}}{\text{totalModules}} \times 100\right)$$

- **Browser Refresh Safety:** Refreshing `/diagnostic/[id]` at any time queries the backend source of truth, resumes status/progress polling if `RUNNING`, displays real historical events, and renders completed module diagnostic views seamlessly.

---

## 12. Phase 4 — Cross-Module Diagnostic Intelligence Layer

### A. Architectural Overview

The Cross-Module Diagnostic Intelligence Layer sits downstream of the seven primary diagnostic engines. Rather than treating diagnostic outputs as seven isolated cards, Phase 4 normalizes raw outputs, discovers multi-module statistical relationships, aggregates mathematical evidence, and derives prioritized, actionable investigation directives.

```text
7 Independent Raw Diagnostic Results (DATA_QUALITY, LEAKAGE, DRIFT, PERFORMANCE, EXPLAINABILITY, BIAS, ROBUSTNESS)
                                              ↓
                   Result Normalization (ResultNormalizer / NormalizedModuleData)
                                              ↓
               Deterministic Rule Registry (Pairwise Rules + Multi-Module Risk Patterns)
                                              ↓
                 Evidence & Provenance Aggregation (Metrics, Thresholds, Source Result IDs)
                                              ↓
               Investigation Priority & Evidence Confidence Scoring (Deterministic Formulas)
                                              ↓
                  Persistence Layer (diagnostic_correlations with Idempotent Upsert)
                                              ↓
                REST APIs: GET /correlations, GET /summary, POST /correlations/recalculate
                                              ↓
          Next.js Diagnostic Intelligence Console (HUD, Findings Feed, Feature Forensic Matrix)
```

---

### B. Core Intelligence Principles

1. **100% Deterministic Rule-Based Intelligence (Zero LLM Brain):**
   - Correlation rules, priorities, and confidence levels are evaluated purely using explicit deterministic mathematical rules and thresholds. No generative LLMs calculate correlations, invent findings, or assign severity.
2. **Authoritative Raw Results Immutability:**
   - Persisted `diagnostic_results` are never overwritten, modified, or summarized in-place. Phase 4 outputs are persisted separately in `diagnostic_correlations` and link back to source results via provenance IDs.
3. **Strictly Associative Findings (No Causal Assertions):**
   - Findings use associative and hypothesis phrasing (e.g., *"Feature X is strongly associated with observed performance degradation"* or *"Observed distribution shift coincides with robustness sensitivity"*).
   - Every finding and summary payload explicitly flags `isAssociativeOnly: true` with prominent methodology disclaimers.
4. **Resilient Failure Isolation:**
   - Malformed, missing, or failed modules do not crash the correlation engine. Unaffected rules continue to evaluate successfully against valid module data.
5. **Idempotency & Re-evaluability:**
   - Cross-module findings are keyed deterministically by `(run_id, rule_id, feature, correlation_key)`. Recalculating correlations updates existing records without creating database duplicates.

---

### C. Rule Catalogue & Mathematical Thresholds

#### 1. Pairwise Correlation Rules

| Rule ID | Source Modules | Trigger Condition / Thresholds | Derivation & Evidence | Severity / Confidence | Priority Score |
|---|---|---|---|---|---|
| `DRIFT_EXPLAINABILITY_INTERACTION` | DRIFT, EXPLAINABILITY | Feature Drift $\text{PSI} \ge 0.10$ AND SHAP Importance Rank $\le 5$ (or Top 20%) | High-impact feature experiencing material covariate shift. | $\text{HIGH}$ / $\text{HIGH}$ | $70 + \text{driftBonus} + \text{rankBonus}$ |
| `DRIFT_PERFORMANCE_INTERACTION` | DRIFT, PERFORMANCE | Feature Drift $\text{PSI} \ge 0.15$ AND ($\text{ROC-AUC} < 0.75 \lor \text{F1} < 0.65 \lor \text{LogLoss} > 0.60$) | Distribution shift coincides with degraded classification performance. | $\text{HIGH}$ / $\text{HIGH}$ | $75 + \text{metricDeviation}$ |
| `DRIFT_ROBUSTNESS_INTERACTION` | DRIFT, ROBUSTNESS | Feature Drift $\text{PSI} \ge 0.10$ AND Adversarial Flip Rate $\ge 15\%$ | Feature has shifted in production and exhibits high decision boundary instability. | $\text{HIGH}$ / $\text{HIGH}$ | $65 + \text{driftBonus} + \text{flipBonus}$ |
| `LEAKAGE_EXPLAINABILITY_INTERACTION` | LEAKAGE, EXPLAINABILITY | Leakage Score (Mutual Info / Correlation) $\ge 0.70$ AND SHAP Importance Rank $\le 5$ | Feature exhibits extreme target association and dominates model predictions (target proxy risk). | $\text{CRITICAL}$ / $\text{HIGH}$ | $85 + \text{leakageBonus}$ |
| `DATA_QUALITY_PERFORMANCE_INTERACTION` | DATA_QUALITY, PERFORMANCE | Feature Missingness $\ge 15\%$ AND ($\text{ROC-AUC} < 0.75 \lor \text{F1} < 0.65$) | Severe data nullness/corruption coincides with degraded predictive utility. | $\text{HIGH}$ / $\text{MEDIUM}$ | $60 + \text{missingBonus}$ |
| `BIAS_PERFORMANCE_INTERACTION` | BIAS, PERFORMANCE | Subgroup Disparate Impact Ratio $< 0.80$ AND ($\text{ROC-AUC} < 0.75 \lor \text{F1} < 0.65$) | Subgroup performance disparities intersect overall model performance degradation. | $\text{HIGH}$ / $\text{HIGH}$ | $75 + \text{biasBonus}$ |
| `BIAS_DRIFT_INTERACTION` | BIAS, DRIFT | Disparate Impact $< 0.80$ AND Max Feature Drift $\text{PSI} \ge 0.15$ | Subgroup fairness degradation coincides with global distribution shift. | $\text{HIGH}$ / $\text{MEDIUM}$ | $65 + \text{driftBonus}$ |
| `ROBUSTNESS_EXPLAINABILITY_INTERACTION` | ROBUSTNESS, EXPLAINABILITY | Feature Flip Rate $\ge 20\%$ AND SHAP Rank $\le 5$ | Highly influential feature exhibits extreme vulnerability under input perturbations. | $\text{HIGH}$ / $\text{HIGH}$ | $70 + \text{flipBonus}$ |

#### 2. Multi-Module Higher-Order Risk Patterns

| Rule ID | Source Modules | Multi-Module Trigger Criteria | Diagnostic Finding | Priority |
|---|---|---|---|---|
| `MULTI_DRIFT_PERFORMANCE_RISK` | DRIFT, EXPLAINABILITY, PERFORMANCE | Feature $\text{PSI} \ge 0.10$ AND SHAP Rank $\le 5$ AND Degradation in ROC-AUC / F1 | **CRITICAL:** High-impact feature has materially shifted while model performance exhibits system-wide degradation. | `CRITICAL` (Score: 90+) |
| `MULTI_LEAKAGE_PROXY_RISK` | LEAKAGE, EXPLAINABILITY, DATA_QUALITY | Leakage Score $\ge 0.70$ AND SHAP Rank $\le 3$ AND (Identifier-like flag or Near-constant) | **CRITICAL:** Feature exhibits multiple independent indicators of target leakage and proxy behavior. | `CRITICAL` (Score: 95+) |
| `MULTI_FAIRNESS_SHIFT_RISK` | BIAS, PERFORMANCE, DRIFT | Disparate Impact $< 0.80$ AND Performance Degraded AND Feature $\text{PSI} \ge 0.15$ | **HIGH:** Disproportionate subgroup disparity coincides with measurable data distribution drift. | `HIGH` (Score: 80+) |
| `MULTI_FRAGILE_FEATURE_RISK` | EXPLAINABILITY, ROBUSTNESS, DRIFT | SHAP Rank $\le 5$ AND Flip Rate $\ge 15\%$ AND Feature $\text{PSI} \ge 0.10$ | **CRITICAL:** High-importance feature is simultaneously distributionally shifted and sensitivity-prone. | `CRITICAL` (Score: 90+) |

---

### D. Priority & Confidence Definitions

#### Priority Scoring Formula
Every derived finding is assigned a deterministic priority score between 0 and 100:
$$\text{PriorityScore} = \text{BaseScore}(\text{Rule}) + \text{MetricWeight}(\text{Severity}) + \text{ModuleCountBonus}(\text{Sources}) + \text{FeatureSignificanceBonus}$$

- $\text{Score} \ge 85 \implies \text{CRITICAL}$
- $70 \le \text{Score} < 85 \implies \text{HIGH}$
- $50 \le \text{Score} < 70 \implies \text{MEDIUM}$
- $30 \le \text{Score} < 50 \implies \text{LOW}$
- $\text{Score} < 30 \implies \text{INFO}$

#### Evidence Confidence
- **HIGH:** Supported by 2+ independent modules with strong statistical significance ($p < 0.01$, $\text{PSI} \ge 0.20$, or complete metric availability).
- **MEDIUM:** Supported by 2 modules with moderate metric deviations or partial sample coverage.
- **LOW:** Rule conditions met near boundary thresholds.
*Note: Evidence confidence reflects statistical robustness of the evidence pattern, NOT a subjective belief probability.*

---

### E. Database Schema & REST APIs

#### 1. Table Schema: `diagnostic_correlations`
```sql
CREATE TABLE diagnostic_correlations (
    id BIGSERIAL PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL REFERENCES diagnostic_runs(id) ON DELETE CASCADE,
    rule_id VARCHAR(64) NOT NULL,
    correlation_key VARCHAR(128) NOT NULL,
    finding_type VARCHAR(64) NOT NULL,
    severity VARCHAR(32) NOT NULL,
    priority VARCHAR(32) NOT NULL,
    priority_score DOUBLE PRECISION NOT NULL,
    confidence VARCHAR(32) NOT NULL,
    feature VARCHAR(128),
    title VARCHAR(255),
    summary TEXT NOT NULL,
    why_it_matters TEXT,
    investigation_direction TEXT,
    evidence_json JSONB,
    source_modules_json JSONB,
    source_result_ids_json JSONB,
    is_associative_only BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_correlations_identity UNIQUE (run_id, rule_id, correlation_key)
);
CREATE INDEX idx_diag_corr_run_id ON diagnostic_correlations(run_id);
CREATE INDEX idx_diag_corr_priority ON diagnostic_correlations(run_id, priority_score DESC);
```

#### 2. REST Endpoints
- `GET /api/diagnostics/{id}/correlations`: Returns list of persisted `DiagnosticCorrelationDto` records ordered by `priorityScore DESC`.
- `GET /api/diagnostics/{id}/summary`: Returns `RunSummaryDto` containing run status, module counts, priority counts, top repeated features, top investigation directives, module contributions, and the full cross-module `featureProfiles` matrix.
- `POST /api/diagnostics/{id}/correlations/recalculate`: Triggers deterministic re-evaluation and upsert of cross-module findings.

---

## 13. Phase 5 — Performance & Error Forensics

### A. Purpose & Forensic Scope
While standard `PERFORMANCE` metrics aggregate classification scores (ROC-AUC, PR-AUC, F1), `ERROR_FORENSICS` investigates the micro-composition and segment-level localization of model failure modes:
1. **Record-Level Attribution:** On which specific records does the model fail, and what was the prediction confidence?
2. **Feature Separation:** Are False Positives and False Negatives driven by different numeric and categorical distributions?
3. **High-Confidence Mistakes:** Is the model confidently incorrect on specific feature regions?
4. **Quantile Range Concentrations:** Which feature intervals exhibit enriched error rates?
5. **Threshold Tradeoff Curves:** How does moving the decision threshold shift the error burden between False Positives and False Negatives?
6. **Subgroup Disparities:** Do protected attributes experience statistically significant error rate gaps ($95\%$ Wilson Score CIs)?

> [!IMPORTANT]
> **Strict Associative Phrasing & Non-Causal Disclaimers:**
> All forensic findings, feature separations, and subgroup comparisons are strictly associative (`isAssociativeOnly = true`). Statistical correlation or enrichment does **not** imply causality. Subgroup error rate gaps do not automatically imply intentional discrimination; users are directed to the `BIAS` console for formal fairness metrics.

---

### B. Mathematical Formulations & Statistical Tests

#### 1. Point-Biserial Correlation ($r_{pb}$)
For numeric features, error association is calculated against binary prediction correctness $E \in \{0, 1\}$:
$$r_{pb} = \frac{\bar{X}_1 - \bar{X}_0}{s_X} \sqrt{\frac{n_1 n_0}{n(n-1)}}$$
where $\bar{X}_1$ is the feature mean on incorrect records, $\bar{X}_0$ is the feature mean on correct records, and $s_X$ is sample standard deviation.

#### 2. Cramer's V ($V$)
For categorical features, association with prediction error is evaluated using Pearson's Chi-Squared statistic $\chi^2$:
$$V = \sqrt{\frac{\chi^2}{n \cdot \min(r-1, k-1)}}$$

#### 3. Standardized Mean Difference (Cohen's $d$) & Mann-Whitney U Test
Separation between False Positives ($FP$) vs True Negatives ($TN$), and False Negatives ($FN$) vs True Positives ($TP$):
$$d = \frac{\bar{X}_{\text{error}} - \bar{X}_{\text{correct}}}{s_{\text{pooled}}}$$
Accompanied by two-sided Mann-Whitney U test p-values.

#### 4. Benjamini-Hochberg False Discovery Rate (FDR)
Across all $M$ evaluated feature hypotheses, raw p-values $P_{(1)} \le P_{(2)} \le \dots \le P_{(M)}$ are adjusted to control FDR:
$$P_{\text{adj}(i)} = \min\left(1, \min_{k \ge i} \left( \frac{M}{k} P_{(k)} \right)\right)$$

#### 5. Wilson Score Confidence Intervals ($95\%$)
For subgroup error rates $\hat{p} = \frac{e}{n}$ with $z = 1.96$:
$$\text{CI} = \frac{\hat{p} + \frac{z^2}{2n} \pm z \sqrt{\frac{\hat{p}(1-\hat{p})}{n} + \frac{z^2}{4n^2}}}{1 + \frac{z^2}{n}}$$

---

### C. Phase 5 Cross-Module Correlation Rules

| Rule ID | Source Modules | Trigger Conditions | Finding Description | Priority |
|---|---|---|---|---|
| `ERROR_DRIFT_INTERACTION` | ERROR_FORENSICS, DRIFT | Feature Error Association $|r_{pb}| \ge 0.15$ AND Feature $\text{PSI} \ge 0.10$ | Feature exhibits distribution drift concurrent with elevated error rates. | `HIGH` / `CRITICAL` |
| `ERROR_EXPLAINABILITY_INTERACTION` | ERROR_FORENSICS, EXPLAINABILITY | Feature Error Association $|r_{pb}| \ge 0.15$ AND SHAP Rank $\le 5$ | Highly influential feature is simultaneously strongly error-associated. | `HIGH` / `CRITICAL` |
| `ERROR_ROBUSTNESS_INTERACTION` | ERROR_FORENSICS, ROBUSTNESS | Feature Error Association $|r_{pb}| \ge 0.15$ AND Adversarial Flip Rate $\ge 15\%$ | Feature exhibits both high empirical error correlation and high perturbation vulnerability. | `HIGH` |
| `ERROR_BIAS_INTERACTION` | ERROR_FORENSICS, BIAS | Subgroup Disparity Ratio $\ge 1.30$ AND Disparate Impact Ratio $< 0.80$ | Subgroup exhibits elevated error rate concurrent with algorithmic fairness gap. | `HIGH` / `CRITICAL` |
| `CONFIDENCE_CALIBRATION_ERROR` | ERROR_FORENSICS, PERFORMANCE | High-Confidence Error Rate $\ge 15\%$ AND Expected Calibration Error $\text{ECE} \ge 0.08$ | Elevated high-confidence errors coincide with systematic model probability miscalibration. | `HIGH` |

---

## 14. Phase 6 — Root-Cause Investigation & Evidence Graph

### A. Purpose & Core Philosophy
Phase 6 connects cross-module diagnostic evidence into an explicit, deterministic, and traceable **Root-Cause Investigation Layer**. Rather than presenting disconnected findings or generating an ungrounded LLM narrative, Phase 6 deterministically synthesizes evidence across all 8 diagnostic modules to answer:
> *"Given everything the diagnostic system discovered, what should I investigate first, what evidence supports that investigation path, what other findings are connected to it, and which original diagnostic results prove each step?"*

> [!CAUTION]
> **Strict Non-Causality Principle & Terminology:**
> - Phase 6 does **NOT** perform causal inference or claim scientific causality.
> - The graph does **NOT** contain causal edges (e.g. `CAUSES`, `PROVES`, `RESPONSIBLE_FOR`).
> - Hypotheses and paths are **strictly associative**: `"Feature X is implicated across drift, explainability, and error evidence and is therefore a high-priority investigation target."`
> - The official terminology is **Root-Cause Investigation**, not *Automated Root-Cause Proof*.

---

### B. Investigation Target Model
Every investigation target is assigned a stable, canonical identity:
- **`FEATURE::<feature_name>`**: Individual dataset features exhibiting cross-module anomalies.
- **`SUBGROUP::<attribute>=<value>`**: Demographically or functionally segmented subgroups with elevated failure rates.
- **`BEHAVIOR::<behavior_name>`**: Macro model behaviors such as `HIGH_CONFIDENCE_ERRORS`, `CALIBRATION_FAILURE`, or `GLOBAL_ROBUSTNESS_VULNERABILITY`.
- **`ERROR_TYPE::<error_type>`**: Error classes such as `FALSE_POSITIVE` or `FALSE_NEGATIVE`.

---

### C. Evidence Graph Architecture

```text
               MODULE [DRIFT]
                     │
                     │ OBSERVES (PSI=0.31, threshold >= 0.25)
                     ▼
          FEATURE [FEATURE::income] ─── ASSOCIATED_WITH ───► ERROR [ERROR::PREDICTION_MISTAKE]
                     │                                                      ▲
                     │ INFLUENCES (SHAP Rank #1)                            │
                     ▼                                                      │
         MODULE [EXPLAINABILITY] ─── PRODUCES ───► FINDING [FINDING::ERROR_DRIFT]
```

#### 1. Graph Node Types
- `MODULE`: One of the 8 diagnostic modules (`DATA_QUALITY`, `LEAKAGE`, `DRIFT`, `PERFORMANCE`, `EXPLAINABILITY`, `BIAS`, `ROBUSTNESS`, `ERROR_FORENSICS`).
- `FEATURE`: Specific feature node (`FEATURE::<name>`).
- `SUBGROUP`: Specific subgroup node (`SUBGROUP::<group>`).
- `ERROR`: Target error node (`ERROR::<type>`).
- `BEHAVIOR`: Model behavior anomaly node (`BEHAVIOR::<name>`).
- `FINDING`: Cross-module correlation finding node (`FINDING::<rule_id>`).
- `METRIC`: Specific observed measurement (`METRIC::<module>::<metric>`).

#### 2. Graph Edge Types (Strictly Associative)
- `OBSERVES`: Module observes target with specific metric and threshold.
- `PRODUCES`: Module produces a correlation finding.
- `IMPLICATES`: Finding implicates a feature or subgroup target.
- `INVOLVES`: Finding involves a subgroup or behavior.
- `SUPPORTED_BY`: Finding or target is supported by a metric observation.
- `DRIFTED_IN`: Feature exhibits distribution shift in evaluation data.
- `INFLUENCES`: Feature exhibits high model importance or explainability attribution.
- `ASSOCIATED_WITH`: Feature or subgroup correlates with prediction errors.
- `SENSITIVE_UNDER`: Feature or model exhibits vulnerability under perturbation.
- `CONCURRENT_WITH` / `CO_OCCURS_WITH`: Co-occurring anomaly signals across modules.

---

### D. Deterministic Ranking & Scoring Formula

To prevent arbitrary health metrics and avoid double-counting evidence, the **Investigation Priority Score** ($S \in [0, 100]$) is computed transparently:

$$S = S_{\text{finding}} + (N_{\text{modules}} \times 12.0) + S_{\text{error}} + S_{\text{drift}} + S_{\text{shap}} + S_{\text{robustness}} + S_{\text{subgroup}}$$

Where:
- $S_{\text{finding}}$: Maximum base priority score from Phase 4/5 correlation findings (up to $45.0$ pts).
- $N_{\text{modules}}$: Count of **distinct independent modules** contributing evidence (up to $36.0$ pts). Multiple metrics from the same module are supporting observations and do not inflate module independence.
- $S_{\text{error}}$: Error association strength ($|r_{pb}| \times 20.0$, capped at $20.0$ pts).
- $S_{\text{drift}}$: Drift severity ($\min(\text{PSI} \times 25.0, 15.0)$ pts).
- $S_{\text{shap}}$: Explainability weight (Rank 1: $10.0$ pts, Rank 2-3: $7.0$ pts, Rank 4-5: $4.0$ pts).
- $S_{\text{robustness}}$: Adversarial flip rate ($\text{flipRate} \times 20.0$, capped at $10.0$ pts).
- $S_{\text{subgroup}}$: Subgroup error disparity ($(\text{disparityRatio} - 1.0) \times 15.0$, capped at $15.0$ pts).

#### Priority Levels:
- **`CRITICAL`**: $S \ge 75.0$
- **`HIGH`**: $55.0 \le S < 75.0$
- **`MEDIUM`**: $35.0 \le S < 55.0$
- **`LOW`**: $20.0 \le S < 35.0$
- **`INFO`**: $S < 20.0$

---

### E. Evidence Independence & Anti-Inflation
Metrics are clustered by source module before scoring. Five metrics from `ERROR_FORENSICS` count as $1$ independent module supporting evidence, not $5$. Evidence confidence (`VERY_HIGH`, `HIGH`, `MEDIUM`, `LOW`) is determined by the number of independent module sources ($\ge 3 \to \text{VERY\_HIGH}$, $2 \to \text{HIGH}$, $1 \to \text{MEDIUM}$).

---

### F. Deterministic Hypotheses & Next Actions
Hypotheses and next actions are generated from controlled evidence-derived templates:
- **Hypothesis Template Example:**
  > `"Feature 'income' is a high-priority investigation target because it is simultaneously influential (SHAP rank #1), drifted (PSI = 0.31), associated with prediction errors (r = 0.27), and vulnerable under perturbation (flip rate = 18.2%)."`
- **Deterministic Next Actions Checklist:**
  1. Inspect current evaluation distribution for `income` and compare against baseline.
  2. Inspect feature contribution and partial dependence against error-associated records.
  3. Inspect sensitivity under Gaussian noise and test decision boundary proximity.

---

### G. Database Schema: `diagnostic_investigations`

```sql
CREATE TABLE diagnostic_investigations (
    id BIGSERIAL PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL REFERENCES diagnostic_runs(id) ON DELETE CASCADE,
    target_type VARCHAR(64) NOT NULL,
    target_key VARCHAR(128) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    priority VARCHAR(32) NOT NULL,
    priority_score DOUBLE PRECISION NOT NULL,
    evidence_confidence VARCHAR(32) NOT NULL,
    supporting_module_count INT NOT NULL,
    supporting_finding_count INT NOT NULL,
    supporting_evidence_count INT NOT NULL,
    hypothesis TEXT NOT NULL,
    next_actions_json JSONB,
    evidence_summary_json JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_investigation_identity UNIQUE (run_id, target_type, target_key)
);
CREATE INDEX idx_diag_inv_run_id ON diagnostic_investigations(run_id);
CREATE INDEX idx_diag_inv_priority ON diagnostic_investigations(run_id, priority_score DESC);
```

---

### H. REST Endpoints

| Endpoint | Method | Response DTO | Description |
|---|---|---|---|
| `/api/diagnostics/{id}/investigations` | `GET` | `List<InvestigationTargetDto>` | Returns ranked investigation targets ordered by `priorityScore DESC`. |
| `/api/diagnostics/{id}/investigations/{targetKey}` | `GET` | `InvestigationDossierDto` | Returns full investigation dossier with ordered path, hypotheses, actions, and provenance. |
| `/api/diagnostics/{id}/evidence-graph` | `GET` | `EvidenceGraphDto` | Returns evidence graph nodes and edges with provenance and relation labels. |
| `/api/diagnostics/{id}/correlations/recalculate` | `POST` | `List<DiagnosticCorrelationDto>` | Idempotently re-evaluates correlations, investigations, and graph state. |

---

## 15. Phase 7 — Diagnostic Decision & Remediation Planning Layer

Phase 7 evolves Model Doctor from an investigation and evidence-graph tool into a complete, deterministic **Remediation Decision Support System**. It bridges the gap between diagnostic evidence and engineer action by synthesizing ranked remediation candidates, explicit validation hypotheses, acceptance criteria, regression guards, and an automated before-and-after run comparison engine.

### A. Phase 7 Core Architecture Diagram

```text
RAW DIAGNOSTICS (8 Modules)
      ↓
CORRELATIONS (17+ Rules)
      ↓
INVESTIGATIONS (Target Synthesis)
      ↓
EVIDENCE GRAPH (Multi-relational Graph)
      ↓
REMEDIATION RULE REGISTRY (11 Deterministic Rules)
      ↓
REMEDIATION CANDIDATES (Ranked by Priority Score)
      ↓
VALIDATION PLAN (Required Modules, Acceptance Criteria, Regression Guards)
      ↓
FUTURE CANDIDATE RUN (Re-evaluated Model Artifact / Preprocessing)
      ↓
BEFORE / AFTER COMPARISON (Metric Deltas & Significance)
      ↓
VALIDATED / REJECTED / MIXED / NO_MATERIAL_CHANGE
```

---

### B. Core Principles & Safety Directives

1. **Decision Support, Not Autonomous Modification:**
   Model Doctor is an analytical workbench. It never alters user model artifacts, never executes arbitrary retraining scripts, never deploys code, and never fabricates synthetic experiment results.
2. **Strict Non-Causal Associative Language:**
   Statistical correlations and drift metrics do not establish causality. Hypotheses are framed as candidate interventions to validate empirically (e.g. *"Feature X is associated with prediction errors and exhibits high model influence; investigate distribution shift and feature consistency"* rather than *"Feature X caused the model to fail"*).
3. **Deterministic & Auditable:**
   Zero stochastic elements or black-box LLMs are used in rule evaluation or ranking. Given identical inputs, candidate sets, priority scores, and validation plans are 100% reproducible.
4. **Idempotency & Lifecycle Separation:**
   Recalculating remediations preserves deterministic integrity without duplicating database records. The remediation status (`PROPOSED`, `SELECTED`, `VALIDATING`, `VALIDATED`, `REJECTED`, `SUPERSEDED`) strictly reflects verified actions, and candidate validation requires an actual candidate run comparison.

---

### C. Remediation Types & Strategy Registry

The platform implements 14 standardized remediation strategies in `RemediationType`:

| Remediation Type | Target Category | Primary Trigger Condition | Primary Validation Modules |
|---|---|---|---|
| `DISTRIBUTION_SHIFT_INVESTIGATION` | Feature | DRIFT $\text{PSI} \ge 0.25$ + Explainability / Error association | `DRIFT`, `PERFORMANCE`, `ERROR_FORENSICS`, `ROBUSTNESS` |
| `FEATURE_ENGINEERING_REVIEW` | Feature | SHAP Rank $\le 3$ + $\|r_{\text{error}}\| \ge 0.25$ | `EXPLAINABILITY`, `ERROR_FORENSICS`, `PERFORMANCE`, `ROBUSTNESS` |
| `DATA_LEAKAGE_REVIEW` | Feature / Dataset | Leakage Score $\ge 0.70$ or ID-like feature in model | `LEAKAGE`, `PERFORMANCE`, `EXPLAINABILITY` |
| `DATA_QUALITY_REPAIR` | Feature / Dataset | Missing rate $\ge 15\%$ or severe outlier / invalid values | `DATA_QUALITY`, `PERFORMANCE`, `ERROR_FORENSICS` |
| `CALIBRATION_REVIEW` | Model | Expected Calibration Error $\text{ECE} \ge 0.10$ | `PERFORMANCE`, `ERROR_FORENSICS` |
| `THRESHOLD_REVIEW` | Model | Sub-optimal threshold in 21-point operating grid | `PERFORMANCE`, `ERROR_FORENSICS` |
| `ERROR_SEGMENT_REVIEW` | Subgroup / Segment | High-confidence error rate $\ge 10\%$ or segment concentration | `ERROR_FORENSICS`, `PERFORMANCE`, `BIAS` |
| `FAIRNESS_REVIEW` | Protected Attribute | Disparate Impact $< 0.80$ or TPR/FPR gap $\ge 0.10$ | `BIAS`, `PERFORMANCE`, `ERROR_FORENSICS` |
| `ROBUSTNESS_REVIEW` | Model / Feature | Adversarial flip rate $\ge 10\%$ or mean prob shift $\ge 0.05$ | `ROBUSTNESS`, `PERFORMANCE`, `EXPLAINABILITY` |
| `MODEL_COMPLEXITY_REVIEW` | Model | Concentrated attribution + high perturbation sensitivity | `ROBUSTNESS`, `EXPLAINABILITY`, `PERFORMANCE` |
| `FEATURE_REMOVAL_REVIEW` | Feature | Multi-module high risk (Leakage/Quality + High Error + Drift) | `PERFORMANCE`, `LEAKAGE`, `DATA_QUALITY`, `EXPLAINABILITY` |
| `CLASS_IMBALANCE_REVIEW` | Target | Extreme class disparity affecting minority recall | `PERFORMANCE`, `ERROR_FORENSICS` |
| `DATA_COLLECTION_REVIEW` | Dataset | Under-represented feature ranges or high missingness | `DATA_QUALITY`, `DRIFT` |
| `EVALUATION_DATA_REVIEW` | Dataset | Evaluation dataset distribution anomalies | `DRIFT`, `PERFORMANCE` |

---

### D. Deterministic Rule Engine

Each rule implements `DiagnosticRemediationRule` and executes in `RemediationAnalysisService` with complete exception isolation:

1. **`DistributionShiftRemediationRule`**: Triggers when a feature has severe or warning PSI ($\ge 0.25$) and is present in Explainability or Error Forensics findings. Recommends pipeline verification, schema checks, and baseline retraining window alignment.
2. **`FeatureEngineeringRemediationRule`**: Triggers when a feature has top SHAP importance (Rank $\le 3$) and point-biserial error correlation $\|r\| \ge 0.25$. Formulates hypotheses separating importance from error association.
3. **`DataLeakageRemediationRule`**: Triggers when target correlation or rule leakage $\ge 0.70$. Explicitly notes in acceptance criteria that fixing leakage may reduce apparent test metrics while establishing a trustworthy evaluation.
4. **`DataQualityRepairRemediationRule`**: Detects missingness ($\ge 15\%$) or extreme outlier counts. Recommends upstream imputation, sensor calibration, or validation schemas.
5. **`CalibrationReviewRemediationRule`**: Fires when $\text{ECE} \ge 0.10$. Recommends temperature scaling, isotonic regression, Platt scaling, and reliability curve evaluation.
6. **`ThresholdReviewRemediationRule`**: Evaluates the 21-point operating threshold curve ($0.00$ to $1.00$). Finds candidate thresholds offering favorable FNR/FPR tradeoffs (e.g. lowering FNR by $\ge 0.05$ with FPR increase $\le 0.05$).
7. **`ErrorSegmentReviewRemediationRule`**: Triggers when high-confidence errors constitute $\ge 10\%$ of errors or are concentrated in identifiable subgroups.
8. **`FairnessReviewRemediationRule`**: Triggers on 80% rule violation or TPR/FPR gap $\ge 0.10$. Recommends threshold adjustment, re-sampling, or protected subgroup auditing.
9. **`RobustnessReviewRemediationRule`**: Triggers when adversarial flip rate $\ge 10\%$ or mean probability shift $\ge 0.05$. Recommends input clipping, regularization, or noise augmentation.
10. **`ModelComplexityReviewRemediationRule`**: Evaluates attribution Gini concentration ($> 0.60$) combined with high sensitivity. Recommends regularization, pruning, or simpler architectures.
11. **`FeatureRemovalReviewRemediationRule`**: Evaluates multi-source evidence (e.g. leakage + attribution or quality + error). Explicitly frames removal as a testable ablation hypothesis.

---

### E. Priority Scoring Formula

The Remediation Priority Score ($S \in [0, 100]$) quantifies the strength of empirical justification for investigating a remediation path:

$$S = \min(100.0, S_{\text{base}} + S_{\text{modules}} + S_{\text{severity}} + S_{\text{feature}} + S_{\text{validation}} - P_{\text{uncertainty}})$$

Where:
- **$S_{\text{base}}$**: Base score according to remediation type ($30.0$ to $50.0$ pts).
- **$S_{\text{modules}}$**: Independent supporting module bonus ($N_{\text{distinct\_modules}} \times 10.0$, capped at $30.0$ pts).
- **$S_{\text{severity}}$**: Empirical metric severity bonus (e.g. $\text{PSI} \ge 0.25 \to +10.0$, $\text{ECE} \ge 0.15 \to +10.0$, $\text{flipRate} \ge 0.15 \to +10.0$).
- **$S_{\text{feature}}$**: High-influence feature bonus (SHAP Rank 1 $\to +10.0$, Rank 2-3 $\to +6.0$).
- **$S_{\text{validation}}$**: High validation leverage bonus ($+5.0$ if actionable across $\ge 3$ diagnostic modules).
- **$P_{\text{uncertainty}}$**: Missing prerequisite module penalty ($-10.0$ per missing corroborating module).

#### Priority Bands:
- **`CRITICAL`**: $S \ge 75.0$
- **`HIGH`**: $55.0 \le S < 75.0$
- **`MEDIUM`**: $35.0 \le S < 55.0$
- **`LOW`**: $20.0 \le S < 35.0$
- **`INFO`**: $S < 20.0$

Deterministic sorting order: `priorityScore DESC`, `priority DESC`, `targetKey ASC`, `remediationType ASC`.

---

### F. Structured Validation Plan & Acceptance Criteria

Each remediation entity stores structured JSON metadata defining:
1. **Validation Strategy & Objective:** High-level testing protocol.
2. **Required Modules:** The exact set of diagnostic engines that must be re-run on the candidate model/dataset.
3. **Acceptance Criteria:** Deterministic numerical thresholds defining successful empirical validation (e.g. *"PSI decreases below 0.10"*, *"F1 does not regress by > 0.01"*).
4. **Regression Guards:** Mandatory metric invariants that must not be violated during intervention.
5. **Expected Impact Hypotheses:** Structured metric direction mappings (e.g. `ECE -> DECREASE`, `FNR -> DECREASE`, `PSI -> DECREASE`) with explicit non-causal rationales.

---

### G. Before / After Run Comparison Model

`RunComparisonService` provides automated metric comparison between a baseline diagnostic run and any candidate diagnostic run:

- Computes absolute deltas ($\Delta = v_{\text{candidate}} - v_{\text{baseline}}$) and relative percentage changes.
- Directional assessment based on metric semantics:
  - Higher-is-better metrics (Accuracy, Precision, Recall, Specificity, F1, ROC-AUC, PR-AUC): $\Delta \ge 0.005 \to \text{IMPROVED}$, $\Delta \le -0.005 \to \text{REGRESSED}$.
  - Lower-is-better metrics (Log Loss, Brier Score, ECE, MCE, FPR, FNR, PSI, KS, Flip Rate, Missing Rate): $\Delta \le -0.005 \to \text{IMPROVED}$, $\Delta \ge 0.005 \to \text{REGRESSED}$.
  - Metrics within $|\Delta| < 0.005$ are classified as `NO_MATERIAL_CHANGE` / `UNCHANGED`.
- Produces an **Overall Comparison Assessment**:
  - `IMPROVED`: Improved metrics $> 0$ and Regressed metrics $== 0$.
  - `REGRESSED`: Regressed metrics $> 0$ and Improved metrics $== 0$.
  - `MIXED`: Both improved and regressed metrics exist.
  - `NO_MATERIAL_CHANGE`: Only unchanged metrics exist.
  - `INSUFFICIENT_EVIDENCE`: No common metrics could be compared.

---

### H. Database Schema: `diagnostic_remediations`

```sql
CREATE TABLE diagnostic_remediations (
    id BIGSERIAL PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL REFERENCES diagnostic_runs(id) ON DELETE CASCADE,
    target_type VARCHAR(64) NOT NULL,
    target_key VARCHAR(128) NOT NULL,
    remediation_type VARCHAR(64) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    priority VARCHAR(32) NOT NULL,
    priority_score DOUBLE PRECISION NOT NULL,
    confidence VARCHAR(32) NOT NULL,
    evidence_strength VARCHAR(32) NOT NULL,
    hypothesis TEXT NOT NULL,
    expected_effect TEXT NOT NULL,
    validation_strategy TEXT NOT NULL,
    expected_impact_json JSONB,
    acceptance_criteria_json JSONB,
    regression_guards_json JSONB,
    required_modules_json JSONB,
    source_correlation_ids_json JSONB,
    source_result_ids_json JSONB,
    source_investigation_target VARCHAR(128),
    status VARCHAR(32) NOT NULL DEFAULT 'PROPOSED',
    user_rationale TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_remediation_identity UNIQUE (run_id, remediation_type, target_key)
);
CREATE INDEX idx_diag_rem_run_id ON diagnostic_remediations(run_id);
CREATE INDEX idx_diag_rem_priority ON diagnostic_remediations(run_id, priority_score DESC);
CREATE INDEX idx_diag_rem_status ON diagnostic_remediations(run_id, status);
```

---

### I. REST Endpoints

| Endpoint | Method | Request Body / Params | Response DTO | Description |
|---|---|---|---|---|
| `/api/diagnostics/{id}/remediations` | `GET` | - | `List<DiagnosticRemediationDto>` | Returns ranked remediation candidates ordered by priority score. |
| `/api/diagnostics/{id}/remediations/{remediationId}` | `GET` | - | `DiagnosticRemediationDto` | Returns single detailed remediation candidate dossier. |
| `/api/diagnostics/{id}/remediations/recalculate` | `POST` | - | `List<DiagnosticRemediationDto>` | Idempotently re-evaluates all remediation rules against current evidence. |
| `/api/diagnostics/{id}/remediations/{remediationId}/select` | `POST` | `rationale` (optional) | `DiagnosticRemediationDto` | Marks a proposed candidate as `SELECTED` for investigation. |
| `/api/diagnostics/{id}/remediations/{remediationId}/reject` | `POST` | `rationale` (required) | `DiagnosticRemediationDto` | Marks a candidate as `REJECTED` with user explanation. |
| `/api/diagnostics/{id}/comparison/{candidateRunId}` | `GET` | - | `DiagnosticComparisonDto` | Returns delta evaluation between baseline run and candidate run. |

---

### J. Frontend Workstation: `07 REMEDIATION & VALIDATION`

Located at section `07` of the Model Doctor diagnostic workstation:
- **Decision Support HUD:** Metrics for Total Proposed, Critical, High, Selected, and Validated candidates.
- **Safety & Non-Causal Advisory:** Prominent banner emphasizing evidence-driven hypothesis testing over causal certainty.
- **Remediation Queue:** Technical table with filters for status and priority, displaying target, remediation type, evidence modules, confidence, and validation requirements.
- **Remediation Dossier:** Complete breakdown of target key, recommended action, empirical rationale, hypothesis, expected impacts with directional arrows, validation modules, acceptance criteria checklist, regression guards, and provenance trace (with direct clickable jumps to source investigation targets and correlation findings).
- **Run Comparison Console:** Interactive selector to compare the active baseline run against any historical candidate run, rendering clean metric deltas, directional indicators, and overall delta assessment badges.

---

## 16. Phase 8 — Experimental Validation & Counterfactual Evaluation Layer

Phase 8 elevates Model Doctor from remediation planning into a deterministic **Experimental Validation Layer**. It allows engineers and ML practitioners to evaluate controlled candidate interventions against baseline model and dataset behavior without automated retraining, black-box AutoML, or uncontrolled LLM hallucinations.

```text
Baseline Run (Phase 1–6 Evidence)
        ↓
Phase 7 Selected Remediation Hypothesis
        ↓
Phase 8 Controlled Candidate Intervention
        ↓
Diagnostic Experiment Strategy Dispatch
        ↓
Candidate Diagnostic Run Evaluation
        ↓
Baseline ↔ Candidate Paired Statistical Analysis
        ↓
Acceptance Criteria & Regression Guard Evaluation
        ↓
Deterministic Experiment Conclusion (VALIDATED / REJECTED / ...)
```

---

### A. Core Distinctions & Non-Causality Safety Principle

The experiment engine rigorously enforces the boundary between empirical observation and causal inference:

```text
OBSERVED               (Phase 1–5 diagnostic measurements on baseline artifact)
EXPERIMENTALLY TESTED  (Phase 8 measurable before/after evidence under controlled intervention)
HYPOTHESIZED           (Phase 7 expected metric direction prior to experimentation)
```

> [!CAUTION]
> **Non-Causal Safety Directive:**
> Experimental validation measures whether the candidate intervention improves observed evaluation metrics under the specified evaluation configuration and random seed (`42`). It does **not** establish causal validity or guarantee identical production behavior. Model Doctor strictly avoids statements like *"this feature causes errors"* or *"this remediation fixes the model"*, using language such as *"the candidate intervention produced an improvement in F1 on this evaluation dataset"*.

---

### B. Controlled Experiment Types

The system defines 6 deterministic experiment strategies in `ExperimentType`:

| Experiment Type | Category | Transformation & Execution Semantics |
|---|---|---|
| `FEATURE_ABLATION` | Feature | Drops a specified feature from the dataset. If the model interface requires the feature and cannot accept an altered schema, returns `NOT_EXECUTABLE` with explicit rationale rather than fabricating predictions. |
| `FEATURE_TRANSFORMATION` | Feature | Applies deterministic, parameter-bounded transformations: `CLIP` (quantile bounds), `WINSORIZE`, `MISSING_REPLACE` (mean/median/mode), or `STANDARDIZE`. Stores exact parameter provenance. |
| `MISSING_VALUE_STRESS` | Feature / Data | Controlled missingness injection at deterministic rates (`0.05`, `0.10`, `0.20`) with fixed seed `42` to validate model robustness against data corruption. |
| `THRESHOLD_COUNTERFACTUAL` | Model Probability | Evaluates model probability scores across a 21-point operating grid ($0.00$ to $1.00$ in steps of $0.05$) without retraining, computing FPR, FNR, Precision, Recall, F1, and Specificity. |
| `CALIBRATION_COUNTERFACTUAL` | Model Probability | Evaluates post-hoc probability calibration (Platt Scaling / Isotonic Regression) without retraining. Requires an independent calibration data partition; returns `NOT_EXECUTABLE` if independent partition is missing. |
| `SUBGROUP_COUNTERFACTUAL` | Fairness / Subgroup | Measures candidate performance, error rates, and parity metrics across demographic slices with sample size reporting and confidence intervals. |

---

### C. Experiment Lifecycle State Machine

```text
      ┌────────────┐
      │  PROPOSED  │
      └─────┬──────┘
            │
      ┌─────┴──────┐
      ▼            ▼
┌───────────┐ ┌──────────────┐
│  QUEUED   │ │NOT_EXECUTABLE│
└─────┬─────┘ └──────────────┘
      │
      ▼
┌───────────┐
│  RUNNING  │──────────┐
└─────┬─────┘          │
      ├────────────────┼──────────────┐
      ▼                ▼              ▼
┌───────────┐    ┌───────────┐  ┌───────────┐
│ COMPLETED │    │  FAILED   │  │ CANCELLED │
└───────────┘    └───────────┘  └───────────┘
```

1. `PROPOSED`: Experiment registered with intervention config and validation rules.
2. `QUEUED`: Queued for execution.
3. `RUNNING`: Intervention applied, candidate dataset constructed, diagnostic run executed.
4. `COMPLETED`: Diagnostic execution and statistical comparisons completed successfully.
5. `FAILED`: Unexpected exception occurred during evaluation (failure reason preserved).
6. `NOT_EXECUTABLE`: Prerequisites unmet (e.g. missing calibration partition or incompatible model input).
7. `CANCELLED`: User cancelled the running experiment.

---

### D. Data & Model Provenance

Every experiment persists complete, auditable provenance in `DiagnosticExperiment`:
- `datasetProvenanceJson`: Baseline dataset ID, candidate dataset ID, sample count, feature count, transformations applied, random seed (`42`).
- `modelProvenanceJson`: Model artifact ID, model framework, task type, feature schema.
- `interventionConfigJson`: Exact transformation parameters (feature name, transformation type, lower/upper quantiles, missingness rate, decision threshold).
- `deterministicSeed`: Seed integer used for reproducible sampling and perturbations (`42`).

---

### E. Acceptance Criteria & Regression Guard Evaluator

Phase 8 deterministically evaluates the validation criteria defined during remediation planning:

#### 1. Acceptance Criteria
Evaluates whether the candidate intervention achieved its primary hypothesis targets (e.g. $\text{PSI} < 0.10$, $\text{F1}$ delta $\ge -0.01$, $\text{ECE}$ decreases).

#### 2. Regression Guards
Evaluates safety invariants that must not be violated during remediation (e.g. high-confidence error rate increase $\le 0.05$, disparate impact ratio $\ge 0.80$, adversarial flip rate increase $\le 0.05$).

Each rule returns:
- `criterion` / `guard`: Rule description.
- `baselineValue`: Baseline run measurement.
- `candidateValue`: Candidate run measurement.
- `delta`: Numerical delta ($\Delta$).
- `operator`: Comparison operator (`<`, `<=`, `>`, `>=`, `DELTA >= x`).
- `threshold`: Boundary value.
- `passed`: Boolean validation result.
- `reason`: Explanation of the outcome.

---

### F. Deterministic Experiment Conclusions

The overall conclusion is synthesized deterministically without heuristic ambiguity:

| Conclusion | Trigger Condition | Interpretation |
|---|---|---|
| `VALIDATED` | All Acceptance Criteria **PASS** AND all Regression Guards **PASS**. | Primary hypothesis confirmed; no metric regressions observed. |
| `PARTIALLY_VALIDATED` | Primary objective improves, some secondary criteria fail, but **NO** critical regression guards fail. | Candidate provides partial benefit; warrants further iteration. |
| `REJECTED` | Primary objective fails OR any critical Regression Guard **FAILS**. | Intervention failed to achieve objective or caused unacceptable regressions. |
| `INCONCLUSIVE` | Sample size insufficient or contradictory metric evidence. | Evidence is ambiguous under current test conditions. |
| `NOT_EXECUTABLE` | Prerequisites unmet (e.g. missing calibration partition). | Experiment cannot be run against current artifact configuration. |
| `FAILED` | Exception or execution crash occurred. | Pipeline or ML engine failure. |

---

### G. Paired Statistical Validation Methods

Where experiments evaluate predictions on the same evaluation rows, Phase 8 calculates paired statistical evidence:

1. **Prediction Flip Rate:** Percentage of test rows where candidate prediction differs from baseline:
   $$\text{FlipRate} = \frac{1}{N} \sum_{i=1}^N \mathbb{I}(\hat{y}_{i, \text{baseline}} \neq \hat{y}_{i, \text{candidate}})$$
2. **Paired Contingency Table ($2 \times 2$):**
   - Both Correct ($a$)
   - Baseline Correct, Candidate Error ($b$)
   - Baseline Error, Candidate Correct ($c$)
   - Both Error ($d$)
3. **McNemar's Test with Continuity Correction:**
   $$\chi^2 = \frac{(|b - c| - 1)^2}{b + c}, \quad p = 1 - F_{\chi^2_1}(\chi^2)$$
4. **Bootstrap 95% Confidence Intervals (Seed = 42):**
   Computes mean probability shift and 1,000 bootstrap resamples to report empirical $95\%$ confidence bounds $[\text{CI}_{\text{lower}}, \text{CI}_{\text{upper}}]$.

---

### H. Database Schema: `diagnostic_experiments`

```sql
CREATE TABLE diagnostic_experiments (
    id VARCHAR(64) PRIMARY KEY,
    baseline_run_id VARCHAR(64) NOT NULL REFERENCES diagnostic_runs(id) ON DELETE CASCADE,
    candidate_run_id VARCHAR(64) REFERENCES diagnostic_runs(id) ON DELETE SET NULL,
    remediation_id BIGINT REFERENCES diagnostic_remediations(id) ON DELETE SET NULL,
    experiment_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PROPOSED',
    title VARCHAR(255) NOT NULL,
    description TEXT,
    target_type VARCHAR(64) NOT NULL,
    target_key VARCHAR(128) NOT NULL,
    intervention_config_json JSONB,
    dataset_provenance_json JSONB,
    model_provenance_json JSONB,
    requested_modules_json JSONB,
    executed_modules_json JSONB,
    baseline_metrics_json JSONB,
    candidate_metrics_json JSONB,
    metric_deltas_json JSONB,
    statistical_evidence_json JSONB,
    acceptance_criteria_json JSONB,
    acceptance_results_json JSONB,
    regression_guards_json JSONB,
    regression_results_json JSONB,
    conclusion VARCHAR(32),
    conclusion_reason TEXT,
    deterministic_seed INT DEFAULT 42,
    error_code VARCHAR(64),
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_experiment_identity UNIQUE (baseline_run_id, experiment_type, remediation_id, target_key)
);
CREATE INDEX idx_diag_exp_baseline_run ON diagnostic_experiments(baseline_run_id);
CREATE INDEX idx_diag_exp_candidate_run ON diagnostic_experiments(candidate_run_id);
CREATE INDEX idx_diag_exp_remediation ON diagnostic_experiments(remediation_id);
CREATE INDEX idx_diag_exp_status ON diagnostic_experiments(baseline_run_id, status);
CREATE INDEX idx_diag_exp_created_at ON diagnostic_experiments(created_at);
```

---

### I. REST API Reference

| Endpoint | Method | Payload / Params | Response DTO | Description |
|---|---|---|---|---|
| `/api/diagnostics/{id}/experiments` | `GET` | - | `List<DiagnosticExperimentDto>` | Returns all experiments associated with the baseline run. |
| `/api/diagnostics/{id}/experiments/{experimentId}` | `GET` | - | `DiagnosticExperimentDto` | Returns detailed experiment dossier including deltas, evidence, and results. |
| `/api/diagnostics/{id}/experiments` | `POST` | `CreateExperimentRequestDto` | `DiagnosticExperimentDto` | Creates a new controlled experiment record (idempotent). |
| `/api/diagnostics/{id}/experiments/{experimentId}/execute` | `POST` | - | `DiagnosticExperimentDto` | Executes the experiment strategy, evaluates candidate run, and computes conclusions. |
| `/api/diagnostics/{id}/experiments/{experimentId}/cancel` | `POST` | - | `DiagnosticExperimentDto` | Safely cancels a running or queued experiment. |
| `/api/diagnostics/{id}/experiments/{experimentId}/comparison` | `GET` | - | `DiagnosticComparisonDto` | Returns paired before/after metric comparison integrated with RunComparisonService. |

---

### J. Frontend Workstation: `08 EXPERIMENTAL VALIDATION`

Located at navigation code `08` on the Model Doctor workstation sidebar:
- **Experiment HUD:** Real-time counters for Total Experiments, Running, Completed, Validated, Rejected, and Inconclusive.
- **Safety Directive Banner:** Non-causal framing and deterministic validation principles.
- **Experiment Queue:** Detailed table displaying ID, Type, Target, Remediation Link, Status, Primary Result, Regression Guard Status, and Final Conclusion.
- **New Experiment Modal:** Configurable launcher for all 6 experiment types with parameter validation (e.g. clipping quantiles, missingness rates, threshold sliders).
- **Comprehensive Experiment Dossier:**
  - Full Execution Timeline from creation to validation.
  - Multi-Pillar Metric Comparison Table (Performance, Calibration, Drift, Error Forensics, Fairness, Robustness) with technical state badges (`IMPROVED`, `REGRESSED`, `NO_MATERIAL_CHANGE`, `INSUFFICIENT_EVIDENCE`).
  - Paired Statistical Evidence card (Flip Rate, McNemar $\chi^2$ & $p$-value, Bootstrap 95% CI probability shift).
  - Acceptance Criteria Checklist & Regression Guard Invariant Status.
  - Complete Data & Model Provenance inspector.

---

## 17. Longitudinal Model Monitoring & Temporal Intelligence Layer

### A. Architectural Overview

Phase 9 transitions Model Doctor from isolated single-run evaluations to continuous, longitudinal intelligence over a logical **model lineage**:

```text
RAW DIAGNOSTICS (8 Modules)
        ↓
CROSS-MODULE CORRELATIONS
        ↓
ROOT-CAUSE INVESTIGATION
        ↓
EVIDENCE GRAPH
        ↓
REMEDIATION HYPOTHESES
        ↓
EXPERIMENTAL VALIDATION
        ↓
BEFORE / AFTER EVIDENCE
        ↓
LONGITUDINAL HISTORY (Lineage Run Windows)
        ↓
TEMPORAL SIGNALS & THRESHOLD HISTORIES
        ↓
PERSISTENCE & RECURRENCE DETECTION
        ↓
TREND ANALYSIS (Slope, R², Mann-Kendall)
        ↓
REGIME & STEP CHANGE-POINT DETECTION
        ↓
REMEDIATION DURABILITY EVALUATION
        ↓
TEMPORAL ALERTS & MONITORING DECISION SUPPORT
```

### B. Core Principles & Governance

1. **Non-Causality Principle:** Temporal observations reflect deterministic longitudinal associations. Temporal ordering alone does not establish causal direction.
2. **Immutable Provenance:** Every temporal observation, trend, issue track, alert, and durability assessment retains explicit source IDs (`sourceResultId`, `runId`, `runIndex`, `timestamp`). No aggregation eliminates historical provenance.
3. **Operational vs. Experimental Run Segregation:** Standard production baseline observations (`runType = BASELINE`) constitute operational monitoring history. Experimental candidates (`runType = EXPERIMENT`) are isolated as comparison overlays and never pollute operational baseline history.
4. **No LLMs in Decision Path:** All statistical computations, persistence evaluations, change point tests, and alert syntheses are 100% deterministic and auditable.
5. **No Synthetic Run Interpolation:** Missing runs remain missing (`NOT_OBSERVED`); missing observations are never coerced to zero.

---

### C. Deterministic Metric Registry

A centralized registry defines all metrics eligible for longitudinal analysis across 8 modules:

| Module | Metric Name | Higher is Better | Warning Threshold | Critical Threshold | Standard Unit |
|---|---|---|---|---|---|
| `PERFORMANCE` | `f1` | `true` | `0.70` | `0.50` | `score` |
| `PERFORMANCE` | `roc_auc` | `true` | `0.75` | `0.60` | `auc` |
| `PERFORMANCE` | `pr_auc` | `true` | `0.60` | `0.40` | `auc` |
| `PERFORMANCE` | `accuracy` | `true` | `0.80` | `0.65` | `pct` |
| `PERFORMANCE` | `log_loss` | `false` | `0.50` | `1.00` | `loss` |
| `PERFORMANCE` | `brier_score` | `false` | `0.20` | `0.35` | `score` |
| `PERFORMANCE` | `expected_calibration_error` | `false` | `0.08` | `0.15` | `ece` |
| `DRIFT` | `psi` | `false` | `0.10` | `0.25` | `psi` |
| `DRIFT` | `max_psi` | `false` | `0.10` | `0.25` | `psi` |
| `DRIFT` | `wasserstein` | `false` | `0.20` | `0.50` | `dist` |
| `ERROR_FORENSICS`| `high_confidence_error_rate` | `false` | `0.05` | `0.12` | `rate` |
| `ERROR_FORENSICS`| `prediction_flip_rate` | `false` | `0.08` | `0.18` | `rate` |
| `ERROR_FORENSICS`| `error_association` | `false` | `0.20` | `0.40` | `corr` |
| `BIAS` | `disparate_impact` | `target range [0.80, 1.25]` | `0.80` | `0.65` | `ratio` |
| `BIAS` | `tpr_gap` | `false` | `0.10` | `0.20` | `gap` |
| `BIAS` | `fpr_gap` | `false` | `0.08` | `0.15` | `gap` |
| `ROBUSTNESS` | `flip_rate` | `false` | `0.10` | `0.25` | `rate` |
| `ROBUSTNESS` | `mean_probability_shift` | `false` | `0.08` | `0.18` | `shift` |
| `LEAKAGE` | `leakage_score` | `false` | `0.50` | `0.70` | `score` |
| `DATA_QUALITY` | `missing_rate` | `false` | `0.05` | `0.20` | `pct` |

---

### D. Trend Analysis & Statistical Tests

For every historical metric sequence $y = (y_1, y_2, \dots, y_n)$ across ordered baseline runs:
- **Linear Trend Slope ($\beta$) & $R^2$:**
  $$\beta = \frac{\sum (t_i - \bar{t})(y_i - \bar{y})}{\sum (t_i - \bar{t})^2}, \quad R^2 = \frac{(\sum (t_i - \bar{t})(y_i - \bar{y}))^2}{\sum (t_i - \bar{t})^2 \sum (y_i - \bar{y})^2}$$
- **Non-Parametric Mann-Kendall Trend Test:**
  $$S = \sum_{k=1}^{n-1} \sum_{j=k+1}^n \text{sgn}(y_j - y_k), \quad \tau = \frac{2S}{n(n-1)}$$
  $$\text{Var}(S) = \frac{n(n-1)(2n+5)}{18}, \quad Z = \begin{cases} \frac{S-1}{\sqrt{\text{Var}(S)}} & S > 0 \\ 0 & S = 0 \\ \frac{S+1}{\sqrt{\text{Var}(S)}} & S < 0 \end{cases}, \quad p = 2(1 - \Phi(|Z|))$$
- **Trend Classification:**
  - `IMPROVING`: Slope is statistically moving in beneficial direction ($R^2 \ge 0.25$ or Mann-Kendall $p < 0.10$).
  - `DEGRADING`: Slope is moving toward harmful threshold ($R^2 \ge 0.25$ or Mann-Kendall $p < 0.10$).
  - `STABLE`: Negligible absolute slope ($|\beta| < 0.005$ or $R^2 < 0.25$ with low variance).
  - `VOLATILE`: High standard deviation / coefficient of variation with fluctuating non-monotonic trajectory.
  - `INSUFFICIENT_DATA`: Sample size $< 3$ observations.

---

### E. Threshold State Transitions & Issue Track Persistence

1. **Threshold Reconstruction:** Reconstructs historical severity per run (`LOW` -> `MEDIUM` -> `HIGH` -> `CRITICAL`).
2. **Canonical Issue Tracks:** Aggregates multi-module evidence for specific targets (`FEATURE::income`, `SUBGROUP::gender=Female`, `GLOBAL`).
3. **Persistence Classification State Machine:**
   - `PERSISTENT`: Active elevated severity across $\ge 3$ consecutive baseline runs.
   - `EMERGING`: Issue appears in recent run after not being elevated historically.
   - `RECURRING`: Issue was elevated, cleared below warning threshold, and later returned.
   - `TRANSIENT`: Issue appeared for 1 isolated run and resolved.
   - `RECOVERED`: Previously elevated issue has remained below warning thresholds for $\ge 2$ consecutive runs.
   - `ESCALATING`: Consecutive increases in severity or degradation magnitude.
   - `DEESCALATING`: Consecutive decreases in severity toward nominal levels.

---

### F. Step Change-Point Detection

Detects structural behavioral shifts across metric series:
- Evaluates partition points $k \in [2, n-2]$ splitting history into $(y_1, \dots, y_k)$ and $(y_{k+1}, \dots, y_n)$.
- Calculates split-weighted mean difference:
  $$\Delta_k = |\bar{y}_{\text{after}} - \bar{y}_{\text{before}}| \cdot \sqrt{\frac{k(n-k)}{n}}$$
- Computes confidence levels (`HIGH`, `MEDIUM`, `LOW`) based on signal-to-noise ratio:
  $$\text{SNR} = \frac{|\bar{y}_{\text{after}} - \bar{y}_{\text{before}}|}{\sigma_{\text{pooled}}}$$

---

### G. Remediation Durability Assessment

Connects Phase 7 remediation recommendations and Phase 8 experimental validations with subsequent operational baseline monitoring:
- **States:**
  - `SUSTAINED`: Experimental improvement confirmed and maintained across all subsequent operational baseline runs.
  - `TEMPORARY`: Experimental improvement faded, metric returned to warning/critical status within follow-up window.
  - `FAILED_TO_SUSTAIN`: Follow-up baseline run immediately showed regression to pre-experiment degradation levels.
  - `INSUFFICIENT_FOLLOWUP`: Validated experiment has $< 1$ subsequent operational baseline run recorded.
  - `NOT_APPLICABLE`: Experiment was not validated or unlinked to candidate execution.

---

### H. Temporal Alert Engine

Deterministic prioritized alert generator emitting:
- `NEW_DEGRADATION`: Metric or target transitioned from nominal to warning/critical in latest run.
- `PERSISTENT_DEGRADATION`: Issue has persisted across $\ge 3$ consecutive operational runs.
- `ESCALATING_DEGRADATION`: Severity is monotonically worsening across recent runs.
- `RECOVERY`: Previously degraded issue confirmed resolved in recent runs.
- `REGRESSION_AFTER_RECOVERY`: Issue recurred after prior recovery state.
- `RECURRING_ISSUE`: Target exhibits intermittent failure cycles across the observation window.
- `CHANGE_POINT_DETECTED`: Statistically verified step-shift detected in metric trajectory.
- `REMEDIATION_NOT_SUSTAINED`: Candidate intervention failed to maintain improvements during follow-up monitoring.
- `MULTI_MODULE_ESCALATION`: Concurrently degrading signals detected across $\ge 2$ independent diagnostic modules.

---

### I. Database Entities & Persistence Schema

```sql
CREATE TABLE diagnostic_temporal_observations (
    id BIGSERIAL PRIMARY KEY,
    model_lineage_id VARCHAR(128) NOT NULL,
    run_id VARCHAR(64) NOT NULL REFERENCES diagnostic_runs(id) ON DELETE CASCADE,
    run_type VARCHAR(32) NOT NULL DEFAULT 'BASELINE',
    run_index INT NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    module VARCHAR(64) NOT NULL,
    metric_name VARCHAR(64) NOT NULL,
    target_type VARCHAR(64),
    target_key VARCHAR(128),
    metric_value DOUBLE PRECISION NOT NULL,
    unit VARCHAR(32),
    severity VARCHAR(32),
    threshold DOUBLE PRECISION,
    sample_size INT,
    source_result_id BIGINT,
    source_finding_id BIGINT,
    source_investigation_id BIGINT,
    source_remediation_id BIGINT,
    source_experiment_id VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_diag_temp_obs_lineage ON diagnostic_temporal_observations(model_lineage_id);
CREATE INDEX idx_diag_temp_obs_metric ON diagnostic_temporal_observations(model_lineage_id, metric_name, target_key);
CREATE INDEX idx_diag_temp_obs_run ON diagnostic_temporal_observations(run_id);
CREATE INDEX idx_diag_temp_obs_time ON diagnostic_temporal_observations(timestamp);

CREATE TABLE diagnostic_issue_tracks (
    id BIGSERIAL PRIMARY KEY,
    model_lineage_id VARCHAR(128) NOT NULL,
    track_fingerprint VARCHAR(128) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_key VARCHAR(128) NOT NULL,
    first_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    first_seen_run_id VARCHAR(64) NOT NULL,
    last_seen_run_id VARCHAR(64) NOT NULL,
    observation_count INT NOT NULL DEFAULT 1,
    consecutive_count INT NOT NULL DEFAULT 1,
    current_severity VARCHAR(32) NOT NULL DEFAULT 'LOW',
    peak_severity VARCHAR(32) NOT NULL DEFAULT 'LOW',
    status VARCHAR(32) NOT NULL DEFAULT 'EMERGING',
    modules_involved_json TEXT,
    metric_names_json TEXT,
    run_ids_json TEXT,
    history_json TEXT,
    remediation_history_json TEXT,
    durability_status VARCHAR(32),
    CONSTRAINT uk_issue_track_identity UNIQUE (model_lineage_id, track_fingerprint)
);
CREATE INDEX idx_diag_track_lineage ON diagnostic_issue_tracks(model_lineage_id);
CREATE INDEX idx_diag_track_status ON diagnostic_issue_tracks(model_lineage_id, status);

CREATE TABLE diagnostic_temporal_alerts (
    id BIGSERIAL PRIMARY KEY,
    model_lineage_id VARCHAR(128) NOT NULL,
    run_id VARCHAR(64) NOT NULL REFERENCES diagnostic_runs(id) ON DELETE CASCADE,
    alert_type VARCHAR(64) NOT NULL,
    priority VARCHAR(32) NOT NULL DEFAULT 'INFO',
    target_type VARCHAR(64),
    target_key VARCHAR(128),
    metric_name VARCHAR(64),
    current_value DOUBLE PRECISION,
    reference_value DOUBLE PRECISION,
    trigger_description TEXT NOT NULL,
    confidence VARCHAR(32) NOT NULL DEFAULT 'HIGH',
    run_ids_json TEXT,
    acknowledged BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_diag_temp_alert_lineage ON diagnostic_temporal_alerts(model_lineage_id);
CREATE INDEX idx_diag_temp_alert_priority ON diagnostic_temporal_alerts(priority);

CREATE TABLE diagnostic_change_points (
    id BIGSERIAL PRIMARY KEY,
    model_lineage_id VARCHAR(128) NOT NULL,
    metric_name VARCHAR(64) NOT NULL,
    target_key VARCHAR(128),
    change_run_id VARCHAR(64) NOT NULL,
    change_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    before_mean DOUBLE PRECISION NOT NULL,
    after_mean DOUBLE PRECISION NOT NULL,
    absolute_shift DOUBLE PRECISION NOT NULL,
    relative_shift DOUBLE PRECISION NOT NULL,
    confidence_level VARCHAR(32) NOT NULL DEFAULT 'MEDIUM',
    run_ids_before_json TEXT,
    run_ids_after_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_diag_cp_lineage ON diagnostic_change_points(model_lineage_id);
CREATE INDEX idx_diag_cp_metric ON diagnostic_change_points(model_lineage_id, metric_name, target_key);
```

---

### J. REST API Reference

| Endpoint | Method | Payload / Query | Response DTO | Description |
|---|---|---|---|---|
| `/api/diagnostics/{id}/temporal/history` | `GET` | `?window=ALL_AVAILABLE` | `ModelLineageHistoryDto` | Retrieves full lineage history, HUD telemetry, metric timelines, issue tracks, alerts, change points, and durability. |
| `/api/diagnostics/{id}/temporal/metrics` | `GET` | `?window=ALL_AVAILABLE` | `List<TemporalMetricHistoryDto>` | Retrieves time-series metric trajectories with slopes, $R^2$, and Mann-Kendall statistics. |
| `/api/diagnostics/{id}/temporal/issues` | `GET` | `?window=ALL_AVAILABLE` | `List<IssueTrackDto>` | Retrieves historical target issue tracks with persistence classifications. |
| `/api/diagnostics/{id}/temporal/alerts` | `GET` | `?window=ALL_AVAILABLE` | `List<TemporalAlertDto>` | Retrieves prioritized temporal alert events. |
| `/api/diagnostics/{id}/temporal/change-points`| `GET` | `?window=ALL_AVAILABLE` | `List<ChangePointDto>` | Retrieves step change-point detections. |
| `/api/diagnostics/{id}/temporal/remediations` | `GET` | `?window=ALL_AVAILABLE` | `List<RemediationDurabilityDto>`| Retrieves remediation durability assessments. |
| `/api/diagnostics/{id}/temporal/recalculate` | `POST` | - | `TemporalRecalculateResponseDto` | Idempotently clears stale derived records and recalculates temporal intelligence from immutable source evidence. |
| `/api/models/{lineageId}/history` | `GET` | `?window=ALL_AVAILABLE` | `ModelLineageHistoryDto` | Model lineage level endpoint for lineage history. |
| `/api/models/{lineageId}/temporal/recalculate`| `POST` | - | `TemporalRecalculateResponseDto` | Model lineage level idempotent recalculation trigger. |

---

### K. Frontend Workstation: `09 TEMPORAL INTELLIGENCE`

Located at navigation code `09` on the Model Doctor workstation sidebar:
- **Top HUD:** Real-time counters for Runs Observed (Baseline vs. Experiment), Active Issues, Persistent Issues, Emerging Issues, Recurring Issues, Active Alerts, and Change Points.
- **Model History Strip:** Visual chronological pipeline of all evaluated runs with health indicators, completed module tallies, and active run highlighting.
- **Experiment Overlay Toggle:** Enables overlaying Phase 8 candidate experiments over operational baseline monitoring.
- **Metric Time-Series View:** Technical time-series inspection with linear trend slope, $R^2$, Mann-Kendall $\tau$ and $p$-value, threshold bands, and change point markers.
- **Issue Tracks View:** Forensic issue tracking table with persistence badges, module involvement, and consecutive run counters.
- **Alert Console:** Dense timestamped monitoring event console displaying priority, trigger descriptions, targets, and confidence levels.
- **Remediation Durability View:** Step pipeline tracking problem $\to$ remediation $\to$ candidate run $\to$ follow-up baseline runs $\to$ durability conclusion (`SUSTAINED`, `TEMPORARY`, `FAILED_TO_SUSTAIN`, `INSUFFICIENT_FOLLOWUP`, `NOT_APPLICABLE`).
- **Temporal Investigation Dossier:** Dedicated drilldown drawer connecting temporal alerts and issue tracks back to Phase 6 Root-Cause Investigations and Phase 7 Remediations.

