# Model Doctor — Artifact Ingestion Architecture & Security Guide (Phase 3A)

## 1. Overview

Model Doctor Phase 3A introduces local artifact ingestion and provenance tracking. It enables users to upload trained Machine Learning models and evaluation/baseline datasets directly into secure local storage, validate formats and schemas, compute cryptographic checksums, and reference immutable artifacts across the seven diagnostic engines.

```text
REAL MODEL ARTIFACT (.json, .xgb, .joblib, .pkl, .bin)
+
REAL DATASET ARTIFACTS (.csv, .parquet, .json)
        ↓ Multipart Upload (POST /api/artifacts/*)
VALIDATION (Size limits, format whitelist, path traversal defense)
        ↓ SHA-256 Checksum Calculation & Dedup Check
SECURE LOCAL STORAGE (storage/models/<id>/, storage/datasets/<id>/)
        ↓ Metadata Persistence
DATABASE (model_artifacts, dataset_artifacts)
        ↓ Reference by Artifact ID
DIAGNOSTIC RUN (diagnostic_runs.model_artifact_id, ...)
        ↓ Real Path Resolution
PYTHON ML ENGINE (DatasetLoader, ModelLoader, ModelAdapters)
        ↓ No Silent Synthetic Fallback in REAL Mode
7 REAL DIAGNOSTIC ENGINES (Quality, Leakage, Drift, Perf, Explain, Bias, Robustness)
        ↓
WORKSTATION PROVENANCE DISPLAY
```

---

## 2. Supported Artifact Formats

### Model Artifacts
| Format / Extension | Target Framework | Engine Capabilities | Notes / Deserialization Security |
|---|---|---|---|
| `.json` | XGBoost 1.6+ / 2.0+ | All 7 engines (TreeSHAP, Probes) | Safe native format without arbitrary code execution. |
| `.xgb`, `.bin` | XGBoost binary | All 7 engines (TreeSHAP, Probes) | Native XGBoost model serialization. |
| `.joblib`, `.pkl` | Scikit-Learn (Trees/Ensembles) | All 7 engines (TreeSHAP / Permutation) | **Security Note:** Python pickle formats execute bytecode during deserialization. Uploads restricted to trusted local instances. |

### Dataset Artifacts
| Format / Extension | Supported Dialect | Schema Extraction |
|---|---|---|
| `.csv`, `.txt` | Standard Comma-Separated Values | Row count, Column count, Column names, Inferred Dtypes |
| `.parquet`, `.pq` | Apache Parquet columnar binary | High-performance tabular loading |
| `.json` | JSON records / array format | Standard tabular dictionary records |

---

## 3. Storage Model & Directory Structure

Artifacts are physically stored on the filesystem, while relational database tables store identity, metadata, and cryptographic checksums.

```text
storage/
    models/
        mdl_01JA482C0E.../
            fraud_model.json
    datasets/
        ds_01JA482F8A.../
            production_2026_10.csv
        ds_01JA482H9B.../
            training_golden_baseline.csv
```

### Path Immutability & Deduplication
- **Immutability:** Once written to disk, stored files are immutable. Diagnostic reproducibility is guaranteed by referencing exact file checksums.
- **Deduplication:** When an identical file (same SHA-256) is uploaded, the backend detects the duplicate and reuses the existing canonical artifact entity and path, avoiding redundant disk usage.

---

## 4. Database Entities & Schemas

### `model_artifacts`
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | VARCHAR(64) | PRIMARY KEY | Stable identity (e.g. `mdl_01JA4...`) |
| `original_filename` | VARCHAR(255) | NOT NULL | Sanitized client filename |
| `storage_path` | VARCHAR(512) | NOT NULL | Resolved storage filesystem path |
| `model_format` | VARCHAR(32) | NOT NULL | Extension/format (e.g., `json`, `joblib`) |
| `framework` | VARCHAR(64) | NOT NULL | Framework (`xgboost`, `sklearn`) |
| `task_type` | VARCHAR(64) | NOT NULL | `binary_classification`, `regression`, etc. |
| `file_size` | BIGINT | NOT NULL | File size in bytes |
| `sha256` | VARCHAR(64) | NOT NULL, INDEXED | SHA-256 hash computed during ingestion |
| `feature_names_json` | TEXT | NULLABLE | JSON array of feature names extracted from model |
| `status` | VARCHAR(32) | NOT NULL | `READY` or `FAILED` |
| `created_at` | TIMESTAMP | NOT NULL | Ingestion timestamp |

### `dataset_artifacts`
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | VARCHAR(64) | PRIMARY KEY | Stable identity (e.g. `ds_01JA4...`) |
| `original_filename` | VARCHAR(255) | NOT NULL | Sanitized client filename |
| `storage_path` | VARCHAR(512) | NOT NULL | Resolved storage filesystem path |
| `dataset_format` | VARCHAR(32) | NOT NULL | Format (`csv`, `parquet`, `json`) |
| `file_size` | BIGINT | NOT NULL | File size in bytes |
| `sha256` | VARCHAR(64) | NOT NULL, INDEXED | SHA-256 hash computed during ingestion |
| `row_count` | BIGINT | NOT NULL | Number of parsed data rows |
| `column_count` | INTEGER | NOT NULL | Number of parsed data columns |
| `column_names_json` | TEXT | NOT NULL | JSON array of column headers |
| `dtypes_json` | TEXT | NULLABLE | JSON map of inferred column data types |
| `status` | VARCHAR(32) | NOT NULL | `READY` or `FAILED` |
| `created_at` | TIMESTAMP | NOT NULL | Ingestion timestamp |

---

## 5. REST API Endpoints

### 1. Upload Model Artifact
- **Endpoint:** `POST /api/artifacts/models`
- **Content-Type:** `multipart/form-data`
- **Parameters:**
  - `file`: Multipart file
  - `framework` (optional): `xgboost`, `sklearn`
  - `taskType` (optional): `binary_classification`, `regression`
- **Response:** `201 Created` with `ModelArtifactResponseDto`

### 2. Get Model Artifact
- **Endpoint:** `GET /api/artifacts/models/{id}`
- **Response:** `200 OK` with `ModelArtifactResponseDto`

### 3. Upload Dataset Artifact
- **Endpoint:** `POST /api/artifacts/datasets`
- **Content-Type:** `multipart/form-data`
- **Parameters:**
  - `file`: Multipart file
- **Response:** `201 Created` with `DatasetArtifactResponseDto`

### 4. Get Dataset Artifact
- **Endpoint:** `GET /api/artifacts/datasets/{id}`
- **Response:** `200 OK` with `DatasetArtifactResponseDto`

---

## 6. Execution Modes: REAL vs. BENCHMARK

Model Doctor strictly isolates execution modes to prevent silent synthetic fallbacks:

### `REAL` Execution Mode
- Requires valid uploaded artifact IDs (`modelArtifactId`, `evaluationDatasetArtifactId`).
- Spring Boot resolves the physical filesystem paths and verifies schema compatibility:
  - Target column existence in dataset
  - Protected attribute existence in dataset (when Bias module is selected)
- Python `DatasetLoader` and `ModelLoader` strictly load the designated paths from disk.
- If a file is missing or corrupted, execution **fails explicitly** (HTTP 400 / 404 / 500) and returns a descriptive error. **No synthetic fallback data or benchmark models are ever generated.**

### `BENCHMARK` Execution Mode
- Used for pre-built benchmark suites, automated verification tests, and interactive demos.
- Uses deterministic synthetic data generators and trained XGBoost benchmark models.

---

## 7. Security Hardening & Defenses

1. **Path Traversal Protection:**
   - Client filenames are stripped of directories and special traversal tokens (`..`, `/`, `\`).
   - Final storage paths are generated internally via generated ULID-style identifiers (`mdl_...`, `ds_...`).
   - Every resolved path is checked with `child.normalize().startsWith(parent.normalize())`.
2. **Configurable Size Limits:**
   - Models: default max 50 MB (`modeldoctor.storage.max-model-size-mb`)
   - Datasets: default max 100 MB (`modeldoctor.storage.max-dataset-size-mb`)
   - Spring Boot and Tomcat multipart limits configured to reject oversized payloads before buffering.
3. **Format Whitelisting:**
   - Strict extension validation before disk writing. Unrecognized or executable formats (`.exe`, `.sh`, `.bat`, etc.) are rejected with HTTP 400.
4. **Checksum Verification:**
   - Checksum is calculated on the actual byte stream during storage.
5. **Deserialization Boundary:**
   - XGBoost native JSON format is prioritized. Arbitrary pickle/joblib deserialization is documented as requiring trusted local execution.
