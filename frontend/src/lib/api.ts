/**
 * Model Doctor API Client
 * Orchestrates communication with the Spring Boot Diagnostics Orchestrator.
 */

export interface ModelInfoPayload {
  name: string;
  framework: string;
  taskType: string;
  storageUri?: string;
}

export interface ModelArtifactResponse {
  id: string;
  originalFilename: string;
  storagePath: string;
  modelFormat: string;
  framework: string;
  taskType: string;
  fileSize: number;
  sha256: string;
  featureCount?: number;
  featureNames: string[];
  status: string;
  createdAt: string;
  isDeleted?: boolean;
  deletedAt?: string;
}

export interface ColumnProfile {
  name: string;
  dtype: string;
  classification: "numeric" | "categorical" | "boolean" | "datetime" | "text" | string;
  nullCount: number;
  nullPercentage: number;
  uniqueCount: number;
  uniquePercentage: number;
  isConstant: boolean;
  isNearConstant: boolean;
  min?: number;
  max?: number;
  mean?: number;
  std?: number;
  quantiles?: Record<string, number>;
  exampleValues?: string[];
  isIdentifierLike?: boolean;
  isHighCardinality?: boolean;
}

export interface DatasetSchemaSummary {
  rowCount: number;
  columnCount: number;
  columns: ColumnProfile[];
  potentialTargetColumns: string[];
  potentialPredictionColumns: string[];
  potentialProtectedAttributes: string[];
}

export interface DatasetArtifactResponse {
  id: string;
  originalFilename: string;
  storagePath: string;
  datasetFormat: string;
  fileSize: number;
  sha256: string;
  rowCount: number;
  columnCount: number;
  columnNames: string[];
  dtypes: Record<string, string>;
  schemaSummary?: DatasetSchemaSummary;
  status: string;
  createdAt: string;
  isDeleted?: boolean;
  deletedAt?: string;
}

export interface CreateDiagnosticRunPayload {
  model?: ModelInfoPayload;
  evaluationDataset?: string;
  baselineDataset?: string;
  modelArtifactId?: string;
  evaluationDatasetArtifactId?: string;
  baselineDatasetArtifactId?: string;
  executionMode?: "REAL" | "BENCHMARK" | "TEST";
  targetColumn: string;
  predictionColumn?: string;
  protectedAttribute?: string;
  modules: string[];
}

export interface ValidationError {
  code: string;
  field?: string;
  resourceId?: string;
  message: string;
}

export interface ValidationWarning {
  code: string;
  field?: string;
  resourceId?: string;
  message: string;
}

export interface ModuleValidationStatus {
  module: string;
  compatible: boolean;
  missingPrerequisites: string[];
  message: string;
}

export interface TargetSuggestion {
  column: string;
  score: number;
  reasons: string[];
}

export interface PredictionSuggestion {
  column: string;
  score: number;
  reasons: string[];
}

export interface ValidationResult {
  valid: boolean;
  errors: ValidationError[];
  warnings: ValidationWarning[];
  compatibility: {
    missingModelFeatures?: string[];
    extraDatasetFeatures?: string[];
    isFeatureCompatible?: boolean;
    [key: string]: any;
  };
  moduleValidation: Record<string, ModuleValidationStatus>;
  targetSuggestions: TargetSuggestion[];
  predictionSuggestions: PredictionSuggestion[];
}

export interface DiagnosticRunEvent {
  id: number;
  runId: string;
  module?: string;
  eventType: string;
  message: string;
  timestamp: string;
}

export interface DiagnosticProgress {
  runId: string;
  runStatus: string;
  selectedModulesCount: number;
  completedModulesCount: number;
  failedModulesCount: number;
  runningModulesCount: number;
  pendingModulesCount: number;
  skippedModulesCount: number;
  progressPercent: number;
}

export interface ModuleStatusRecord {
  module: string;
  status: "PENDING" | "RUNNING" | "COMPLETED" | "NOT_IMPLEMENTED" | "FAILED" | "SKIPPED";
  statusMessage?: string;
  startedAt?: string;
  completedAt?: string;
  executionDurationMs?: number;
}

export interface DiagnosticRunRecordResponse {
  id: string;
  status: "CREATED" | "QUEUED" | "RUNNING" | "COMPLETED" | "PARTIAL" | "FAILED";
  retryCount?: number;
  model: ModelInfoPayload;
  modelArtifactId?: string;
  baselineDatasetArtifactId?: string;
  evaluationDatasetArtifactId?: string;
  modelArtifact?: ModelArtifactResponse;
  baselineDatasetArtifact?: DatasetArtifactResponse;
  evaluationDatasetArtifact?: DatasetArtifactResponse;
  executionMode?: string;
  evaluationDataset: string;
  baselineDataset?: string;
  targetColumn: string;
  predictionColumn?: string;
  protectedAttribute?: string;
  selectedModules: ModuleStatusRecord[];
  progress?: DiagnosticProgress;
  createdAt: string;
  queuedAt?: string;
  startedAt?: string;
  completedAt?: string;
  executionDurationMs?: number;
  errorMessage?: string;
}

export interface ModuleResultOutput {
  module: string;
  status: "COMPLETED" | "NOT_IMPLEMENTED" | "FAILED";
  statusMessage?: string;
  result?: Record<string, any>;
}

export interface DiagnosticResultsResponse {
  runId: string;
  status: string;
  completedAt?: string;
  results: ModuleResultOutput[];
  errorMessage?: string;
}

export interface ModelHealthScoreResponse {
  modelId: string;
  modelName: string;
  overallScore: number;
  statusCategory: string;
  categoryBreakdown: Record<string, number>;
  isMockData: boolean;
}

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const url = `${API_BASE_URL}${path}`;
  const response = await fetch(url, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
      ...(options.headers || {}),
    },
  });

  if (!response.ok) {
    let errorDetail = `Request failed with status ${response.status}`;
    try {
      const errorJson = await response.json();
      errorDetail = errorJson.message || errorJson.error || errorDetail;
      if (errorJson.validationDetails && Array.isArray(errorJson.validationDetails)) {
        errorDetail += ` - ${errorJson.validationDetails.join(", ")}`;
      }
    } catch {
      // Non-JSON response
    }
    throw new Error(errorDetail);
  }

  return response.json() as Promise<T>;
}

/**
 * Validates a diagnostic run configuration without creating a run.
 */
export async function validateDiagnosticRun(
  payload: CreateDiagnosticRunPayload
): Promise<ValidationResult> {
  return request<ValidationResult>("/api/diagnostics/validate", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

/**
 * Registers a new diagnostic run in CREATED state.
 */
export async function createDiagnosticRun(
  payload: CreateDiagnosticRunPayload
): Promise<DiagnosticRunRecordResponse> {
  return request<DiagnosticRunRecordResponse>("/api/diagnostics", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

/**
 * Retrieves the run record and lifecycle metadata for a given ID.
 */
export async function getDiagnosticRun(
  runId: string
): Promise<DiagnosticRunRecordResponse> {
  return request<DiagnosticRunRecordResponse>(`/api/diagnostics/${encodeURIComponent(runId)}`);
}

/**
 * Triggers diagnostic execution on the backend orchestrator and Python ML engine.
 */
export async function startDiagnosticRun(
  runId: string
): Promise<DiagnosticRunRecordResponse> {
  return request<DiagnosticRunRecordResponse>(`/api/diagnostics/${encodeURIComponent(runId)}/run`, {
    method: "POST",
  });
}

/**
 * Retries a FAILED or PARTIAL diagnostic run, re-executing failed modules.
 */
export async function retryDiagnosticRun(
  runId: string
): Promise<DiagnosticRunRecordResponse> {
  return request<DiagnosticRunRecordResponse>(`/api/diagnostics/${encodeURIComponent(runId)}/retry`, {
    method: "POST",
  });
}

/**
 * Retries specific failed modules for a FAILED or PARTIAL run.
 */
export async function retryDiagnosticModules(
  runId: string,
  modules: string[]
): Promise<DiagnosticRunRecordResponse> {
  return request<DiagnosticRunRecordResponse>(`/api/diagnostics/${encodeURIComponent(runId)}/retry-modules`, {
    method: "POST",
    body: JSON.stringify({ modules }),
  });
}

/**
 * Retrieves chronological execution events audit log for a run.
 */
export async function getDiagnosticRunEvents(
  runId: string
): Promise<DiagnosticRunEvent[]> {
  return request<DiagnosticRunEvent[]>(`/api/diagnostics/${encodeURIComponent(runId)}/events`);
}

/**
 * Retrieves calculated execution progress metrics.
 */
export async function getDiagnosticRunProgress(
  runId: string
): Promise<DiagnosticProgress> {
  return request<DiagnosticProgress>(`/api/diagnostics/${encodeURIComponent(runId)}/progress`);
}

/**
 * Retrieves structured per-module diagnostic results.
 */
export async function getDiagnosticResults(
  runId: string
): Promise<DiagnosticResultsResponse> {
  return request<DiagnosticResultsResponse>(`/api/diagnostics/${encodeURIComponent(runId)}/results`);
}

/**
 * Fetches high-level health HUD score summary.
 */
export async function getModelHealthScore(
  modelId?: string
): Promise<ModelHealthScoreResponse> {
  const query = modelId ? `?modelId=${encodeURIComponent(modelId)}` : "";
  return request<ModelHealthScoreResponse>(`/api/diagnostics/health-score${query}`);
}

/**
 * Lists all active (non-deleted) model artifacts stored in the system.
 */
export async function listModelArtifacts(): Promise<ModelArtifactResponse[]> {
  return request<ModelArtifactResponse[]>("/api/artifacts/models");
}

/**
 * Lists all active (non-deleted) dataset artifacts stored in the system.
 */
export async function listDatasetArtifacts(): Promise<DatasetArtifactResponse[]> {
  return request<DatasetArtifactResponse[]>("/api/artifacts/datasets");
}

/**
 * Uploads a real ML model artifact (.json, .xgb, .joblib, .pkl, .bin) to local storage.
 */
export async function uploadModelArtifact(
  file: File,
  framework = "xgboost",
  taskType = "binary_classification"
): Promise<ModelArtifactResponse> {
  const formData = new FormData();
  formData.append("file", file);
  formData.append("framework", framework);
  formData.append("taskType", taskType);

  const url = `${API_BASE_URL}/api/artifacts/models`;
  const response = await fetch(url, {
    method: "POST",
    body: formData,
  });

  if (!response.ok) {
    let errorDetail = `Model upload failed with status ${response.status}`;
    try {
      const errorJson = await response.json();
      errorDetail = errorJson.message || errorJson.error || errorDetail;
    } catch {
      // Non-JSON response
    }
    throw new Error(errorDetail);
  }

  return response.json() as Promise<ModelArtifactResponse>;
}

/**
 * Uploads a real dataset artifact (.csv, .parquet, .json) to local storage.
 */
export async function uploadDatasetArtifact(
  file: File,
  format = "csv"
): Promise<DatasetArtifactResponse> {
  const formData = new FormData();
  formData.append("file", file);
  formData.append("format", format);

  const url = `${API_BASE_URL}/api/artifacts/datasets`;
  const response = await fetch(url, {
    method: "POST",
    body: formData,
  });

  if (!response.ok) {
    let errorDetail = `Dataset upload failed with status ${response.status}`;
    try {
      const errorJson = await response.json();
      errorDetail = errorJson.message || errorJson.error || errorDetail;
    } catch {
      // Non-JSON response
    }
    throw new Error(errorDetail);
  }

  return response.json() as Promise<DatasetArtifactResponse>;
}

/**
 * Retrieves model artifact metadata by ID.
 */
export async function getModelArtifact(
  id: string
): Promise<ModelArtifactResponse> {
  return request<ModelArtifactResponse>(`/api/artifacts/models/${encodeURIComponent(id)}`);
}

/**
 * Retrieves dataset artifact metadata by ID.
 */
export async function getDatasetArtifact(
  id: string
): Promise<DatasetArtifactResponse> {
  return request<DatasetArtifactResponse>(`/api/artifacts/datasets/${encodeURIComponent(id)}`);
}

/**
 * Soft-deletes a model artifact.
 */
export async function deleteModelArtifact(id: string): Promise<void> {
  const url = `${API_BASE_URL}/api/artifacts/models/${encodeURIComponent(id)}`;
  const response = await fetch(url, { method: "DELETE" });
  if (!response.ok && response.status !== 204) {
    throw new Error(`Failed to delete model artifact: ${response.status}`);
  }
}

/**
 * Soft-deletes a dataset artifact.
 */
export async function deleteDatasetArtifact(id: string): Promise<void> {
  const url = `${API_BASE_URL}/api/artifacts/datasets/${encodeURIComponent(id)}`;
  const response = await fetch(url, { method: "DELETE" });
  if (!response.ok && response.status !== 204) {
    throw new Error(`Failed to delete dataset artifact: ${response.status}`);
  }
}

export type InvestigationPriority = "CRITICAL" | "HIGH" | "MEDIUM" | "LOW" | "INFO";
export type EvidenceConfidence = "HIGH" | "MEDIUM" | "LOW";

export interface DiagnosticCorrelation {
  id: string;
  runId: string;
  ruleId: string;
  findingType: string;
  severity: string;
  priority: InvestigationPriority;
  priorityScore: number;
  confidence: EvidenceConfidence;
  feature?: string;
  summary: string;
  investigationDirection?: string;
  whyItMatters?: string;
  isAssociativeOnly: boolean;
  evidence: Record<string, any>;
  sourceModules: string[];
  sourceResultIds: string[];
  createdAt: string;
}

export interface FeatureProfile {
  featureName: string;
  driftPsi?: number;
  driftSeverity?: string;
  importanceRank?: number;
  importanceScore?: number;
  robustnessFlipRate?: number;
  robustnessSeverity?: string;
  leakageCorrelation?: number;
  leakageSeverity?: string;
  missingPercentage?: number;
  isIdentifierLike?: boolean;
  findingsCount: number;
}

export interface RunSummary {
  runId: string;
  status: string;
  moduleCount: number;
  completedModules: number;
  failedModules: number;
  criticalFindings: number;
  highPriorityFindings: number;
  mediumPriorityFindings: number;
  lowPriorityFindings: number;
  infoFindings: number;
  totalFindings: number;
  topFeatures: string[];
  topInvestigationAreas: string[];
  contributingModules: Record<string, number>;
  featureProfiles: Record<string, FeatureProfile>;
  isAssociativeOnly: boolean;
  notes: string;
}

/**
 * Retrieves cross-module correlation findings for a diagnostic run.
 */
export async function getDiagnosticCorrelations(
  runId: string
): Promise<DiagnosticCorrelation[]> {
  return request<DiagnosticCorrelation[]>(`/api/diagnostics/${encodeURIComponent(runId)}/correlations`);
}

/**
 * Retrieves a diagnostic run's summary & cross-module investigation metrics.
 */
export async function getDiagnosticRunSummary(
  runId: string
): Promise<RunSummary> {
  return request<RunSummary>(`/api/diagnostics/${encodeURIComponent(runId)}/summary`);
}

/**
 * Triggers deterministic recalculation of cross-module correlations for a run.
 */
export async function recalculateDiagnosticCorrelations(
  runId: string
): Promise<DiagnosticCorrelation[]> {
  const url = `${API_BASE_URL}/api/diagnostics/${encodeURIComponent(runId)}/correlations/recalculate`;
  const response = await fetch(url, { method: "POST" });
  if (!response.ok) {
    throw new Error(`Failed to recalculate correlations: ${response.status}`);
  }
  return response.json() as Promise<DiagnosticCorrelation[]>;
}
