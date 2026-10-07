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
 │ 7 Core Diagnostic Engines:                                  │
 │ 1. DATA_QUALITY      (Nulls, Duplicates, Types, Schema)     │
 │ 2. LEAKAGE           (Target/Feature Correlation Heuristics)│
 │ 3. DRIFT             (PSI, KS-Test, Jensen-Shannon)         │
 │ 4. PERFORMANCE       (ROC-AUC, PR-AUC, F1, LogLoss, Brier)  │
 │ 5. EXPLAINABILITY    (TreeSHAP, LinearSHAP, KernelSHAP)     │
 │ 6. BIAS              (Disparate Impact, Demographic Parity) │
 │ 7. ROBUSTNESS        (Gaussian Noise, Boundary Flip, Rank)  │
 └─────────────────────────────────────────────────────────────┘
                      ↓ JSON DiagnosticReport
              Spring Boot Persistence & Status Resolution
                      ↓ Transactional Save
           Database (diagnostic_runs, diagnostic_results)
                      ↓ HTTP GET /api/diagnostics/{id}/results
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


