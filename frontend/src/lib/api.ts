/**
 * Model Doctor API Client
 * Orchestrates communication with the Spring Boot Diagnostics Orchestrator.
 */

import {
  InvestigationTarget,
  InvestigationDossier,
  EvidenceGraph,
  DiagnosticRemediation,
  DiagnosticComparison,
  MetricComparisonItem,
  DiagnosticExperiment,
  CreateExperimentRequest,
  ModelLineageHistory,
  TemporalMetricHistory,
  IssueTrack,
  TemporalAlert,
  ChangePoint,
  RemediationDurability,
  TemporalRecalculateResponse,
} from "@/types/diagnostics";


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
  investigationTargetCount?: number;
  criticalInvestigationCount?: number;
  highInvestigationCount?: number;
  topInvestigationTarget?: string;
  topInvestigationScore?: number;
  evidenceGraphNodeCount?: number;
  evidenceGraphEdgeCount?: number;
  remediationCount?: number;
  criticalRemediationCount?: number;
  highRemediationCount?: number;
  selectedRemediationCount?: number;
  validatedRemediationCount?: number;
  topRemediationType?: string;
  topRemediationTarget?: string;
  remediationAvailable?: boolean;
}

export interface ErrorSummaryMetric {
  count: number;
  rate: number;
  percentageOfAll?: number;
  percentageOfPositives?: number;
  percentageOfNegatives?: number;
}

export interface ErrorForensicsSummaryData {
  totalRecords: number;
  totalErrors: number;
  errorRate: number;
  truePositive: ErrorSummaryMetric;
  trueNegative: ErrorSummaryMetric;
  falsePositive: ErrorSummaryMetric;
  falseNegative: ErrorSummaryMetric;
}

export interface ConfidenceBand {
  band: string;
  lowerBound: number;
  upperBound: number;
  totalCount: number;
  errorCount: number;
  errorRate: number;
}

export interface ConfidenceAnalysisData {
  meanIncorrectConfidence: number;
  medianIncorrectConfidence: number;
  p90IncorrectConfidence: number;
  p95IncorrectConfidence: number;
  maxIncorrectConfidence: number;
  meanCorrectConfidence?: number;
  medianCorrectConfidence?: number;
  highConfidenceErrorCount: number;
  highConfidenceErrorRate: number;
  highConfidenceErrorShare: number;
  confidenceBands: ConfidenceBand[];
}

export interface FeatureSeparationStat {
  feature: string;
  errorMean?: number;
  nonErrorMean?: number;
  standardizedMeanDifference: number;
  mannWhitneyPValue?: number;
  adjustedPValue?: number;
  categoryDifferences?: Record<string, any>;
}

export interface FalsePositiveAnalysisData {
  fpCount: number;
  fpRate: number;
  meanPredictedProbability: number;
  medianPredictedProbability: number;
  topSeparations: FeatureSeparationStat[];
}

export interface FalseNegativeAnalysisData {
  fnCount: number;
  fnRate: number;
  meanPredictedProbability: number;
  medianPredictedProbability: number;
  topSeparations: FeatureSeparationStat[];
}

export interface FeatureAssociationData {
  feature: string;
  featureType: "numeric" | "categorical" | string;
  statisticName: string;
  statistic: number;
  effectSize: number;
  direction?: string;
  pValue: number;
  adjustedPValue: number;
  rank: number;
}

export interface FeatureRangeData {
  feature: string;
  binIndex: number;
  binLower: number;
  binUpper: number;
  recordCount: number;
  errorCount: number;
  errorRate: number;
  falsePositiveRate?: number;
  falseNegativeRate?: number;
  meanProbability?: number;
  isErrorEnriched: boolean;
  isInsufficientSample: boolean;
}

export interface HighConfidenceErrorRecordData {
  stableRowIndex: number;
  actualClass: number;
  predictedClass: number;
  predictedProbability: number;
  confidence: number;
  errorType: "FALSE_POSITIVE" | "FALSE_NEGATIVE" | string;
  forensicPriority: number;
  keyAssociatedFeatures?: Record<string, any>;
  segmentMembership?: string;
}

export interface ThresholdForensicPoint {
  threshold: number;
  truePositive: number;
  trueNegative: number;
  falsePositive: number;
  falseNegative: number;
  precision: number;
  recall: number;
  specificity: number;
  f1: number;
  falsePositiveRate: number;
  falseNegativeRate: number;
}

export interface CalibrationForensicBinData {
  binIndex: number;
  lowerBound: number;
  upperBound: number;
  sampleCount: number;
  positiveCount: number;
  errorCount: number;
  meanPredictedProbability: number;
  observedPositiveRate: number;
  calibrationError: number;
  isHighError: boolean;
}

export interface SubgroupErrorData {
  group: string;
  sampleCount: number;
  errorCount: number;
  errorRate: number;
  falsePositiveRate: number;
  falseNegativeRate: number;
  highConfidenceErrorRate: number;
  wilsonCiLower: number;
  wilsonCiUpper: number;
  disparityRatio: number;
}

export interface ErrorForensicsResult {
  schemaVersion: number;
  module: "ERROR_FORENSICS";
  sampleCount: number;
  errorSummary: ErrorForensicsSummaryData;
  confidenceAnalysis: ConfidenceAnalysisData;
  falsePositiveAnalysis: FalsePositiveAnalysisData;
  falseNegativeAnalysis: FalseNegativeAnalysisData;
  featureAssociations: FeatureAssociationData[];
  featureRanges: FeatureRangeData[];
  highConfidenceErrors: HighConfidenceErrorRecordData[];
  thresholdAnalysis: ThresholdForensicPoint[];
  calibrationForensics: {
    expectedCalibrationError: number;
    bins: CalibrationForensicBinData[];
  };
  subgroupAnalysis: SubgroupErrorData[];
  findings: Array<{
    findingId: string;
    title: string;
    severity: string;
    summary: string;
    whyItMatters: string;
    recommendation: string;
    isAssociativeOnly: boolean;
    evidence: Record<string, any>;
  }>;
  validationWarnings?: string[];
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

/**
 * Retrieves ranked investigation targets for a diagnostic run.
 */
export async function getDiagnosticInvestigations(
  runId: string
): Promise<InvestigationTarget[]> {
  return request<InvestigationTarget[]>(`/api/diagnostics/${encodeURIComponent(runId)}/investigations`);
}

/**
 * Retrieves the full investigation dossier for a specific investigation target.
 */
export async function getDiagnosticInvestigationDossier(
  runId: string,
  targetKey: string
): Promise<InvestigationDossier> {
  return request<InvestigationDossier>(
    `/api/diagnostics/${encodeURIComponent(runId)}/investigations/${encodeURIComponent(targetKey)}`
  );
}

/**
 * Retrieves the evidence graph nodes and edges for a diagnostic run.
 */
export async function getDiagnosticEvidenceGraph(
  runId: string
): Promise<EvidenceGraph> {
  return request<EvidenceGraph>(`/api/diagnostics/${encodeURIComponent(runId)}/evidence-graph`);
}

/**
 * Phase 7: Retrieves ranked remediation candidates for a diagnostic run.
 */
export async function getDiagnosticRemediations(
  runId: string
): Promise<DiagnosticRemediation[]> {
  return request<DiagnosticRemediation[]>(`/api/diagnostics/${encodeURIComponent(runId)}/remediations`);
}

/**
 * Phase 7: Retrieves a specific remediation by ID.
 */
export async function getDiagnosticRemediationById(
  runId: string,
  remediationId: number | string
): Promise<DiagnosticRemediation> {
  return request<DiagnosticRemediation>(
    `/api/diagnostics/${encodeURIComponent(runId)}/remediations/${encodeURIComponent(String(remediationId))}`
  );
}

/**
 * Phase 7: Explicitly recalculates remediation candidates for a run.
 */
export async function recalculateDiagnosticRemediations(
  runId: string
): Promise<DiagnosticRemediation[]> {
  const url = `${API_BASE_URL}/api/diagnostics/${encodeURIComponent(runId)}/remediations/recalculate`;
  const response = await fetch(url, { method: "POST" });
  if (!response.ok) {
    throw new Error(`Failed to recalculate remediations: ${response.status}`);
  }
  return response.json() as Promise<DiagnosticRemediation[]>;
}

/**
 * Phase 7: Selects a remediation candidate.
 */
export async function selectDiagnosticRemediation(
  runId: string,
  remediationId: number | string
): Promise<DiagnosticRemediation> {
  const url = `${API_BASE_URL}/api/diagnostics/${encodeURIComponent(runId)}/remediations/${encodeURIComponent(String(remediationId))}/select`;
  const response = await fetch(url, { method: "POST" });
  if (!response.ok) {
    throw new Error(`Failed to select remediation: ${response.status}`);
  }
  return response.json() as Promise<DiagnosticRemediation>;
}

/**
 * Phase 7: Rejects a remediation candidate with an optional reason.
 */
export async function rejectDiagnosticRemediation(
  runId: string,
  remediationId: number | string,
  reason?: string
): Promise<DiagnosticRemediation> {
  const queryParam = reason ? `?reason=${encodeURIComponent(reason)}` : "";
  const url = `${API_BASE_URL}/api/diagnostics/${encodeURIComponent(runId)}/remediations/${encodeURIComponent(String(remediationId))}/reject${queryParam}`;
  const response = await fetch(url, { method: "POST" });
  if (!response.ok) {
    throw new Error(`Failed to reject remediation: ${response.status}`);
  }
  return response.json() as Promise<DiagnosticRemediation>;
}

/**
 * Phase 7: Compares a baseline run with a candidate run.
 */
export async function compareDiagnosticRuns(
  baselineRunId: string,
  candidateRunId: string
): Promise<DiagnosticComparison> {
  return request<DiagnosticComparison>(
    `/api/diagnostics/${encodeURIComponent(baselineRunId)}/comparison/${encodeURIComponent(candidateRunId)}`
  );
}

/**
 * Retrieves recent diagnostic runs.
 */
export async function getDiagnosticRuns(): Promise<DiagnosticRunRecordResponse[]> {
  return request<DiagnosticRunRecordResponse[]>("/api/diagnostics/runs");
}

/**
 * Phase 8: Retrieves all experiments for a baseline diagnostic run.
 */
export async function getDiagnosticExperiments(
  runId: string
): Promise<DiagnosticExperiment[]> {
  return request<DiagnosticExperiment[]>(
    `/api/diagnostics/${encodeURIComponent(runId)}/experiments`
  );
}

/**
 * Phase 8: Retrieves a specific experiment dossier by ID.
 */
export async function getDiagnosticExperimentById(
  runId: string,
  experimentId: string
): Promise<DiagnosticExperiment> {
  return request<DiagnosticExperiment>(
    `/api/diagnostics/${encodeURIComponent(runId)}/experiments/${encodeURIComponent(experimentId)}`
  );
}

/**
 * Phase 8: Creates a new experimental validation candidate.
 */
export async function createDiagnosticExperiment(
  runId: string,
  req: CreateExperimentRequest
): Promise<DiagnosticExperiment> {
  return request<DiagnosticExperiment>(
    `/api/diagnostics/${encodeURIComponent(runId)}/experiments`,
    {
      method: "POST",
      body: JSON.stringify(req),
    }
  );
}

/**
 * Phase 8: Executes an experiment and evaluates candidate evidence.
 */
export async function executeDiagnosticExperiment(
  runId: string,
  experimentId: string
): Promise<DiagnosticExperiment> {
  return request<DiagnosticExperiment>(
    `/api/diagnostics/${encodeURIComponent(runId)}/experiments/${encodeURIComponent(experimentId)}/execute`,
    {
      method: "POST",
    }
  );
}

/**
 * Phase 8: Cancels an experiment.
 */
export async function cancelDiagnosticExperiment(
  runId: string,
  experimentId: string
): Promise<DiagnosticExperiment> {
  return request<DiagnosticExperiment>(
    `/api/diagnostics/${encodeURIComponent(runId)}/experiments/${encodeURIComponent(experimentId)}/cancel`,
    {
      method: "POST",
    }
  );
}

// ==========================================
// Phase 9: Longitudinal Model Monitoring & Temporal Intelligence
// ==========================================

/**
 * Phase 9: Retrieves the complete temporal intelligence and lineage history for a diagnostic run.
 */
export async function getDiagnosticTemporalHistory(
  runId: string,
  window: string = "ALL_AVAILABLE"
): Promise<ModelLineageHistory> {
  return request<ModelLineageHistory>(
    `/api/diagnostics/${encodeURIComponent(runId)}/temporal/history?window=${encodeURIComponent(window)}`
  );
}

/**
 * Phase 9: Retrieves temporal metric histories for a run's model lineage.
 */
export async function getDiagnosticTemporalMetrics(
  runId: string,
  window: string = "ALL_AVAILABLE"
): Promise<TemporalMetricHistory[]> {
  return request<TemporalMetricHistory[]>(
    `/api/diagnostics/${encodeURIComponent(runId)}/temporal/metrics?window=${encodeURIComponent(window)}`
  );
}

/**
 * Phase 9: Retrieves historical issue tracks for a run's model lineage.
 */
export async function getDiagnosticTemporalIssues(
  runId: string,
  window: string = "ALL_AVAILABLE"
): Promise<IssueTrack[]> {
  return request<IssueTrack[]>(
    `/api/diagnostics/${encodeURIComponent(runId)}/temporal/issues?window=${encodeURIComponent(window)}`
  );
}

/**
 * Phase 9: Retrieves temporal alerts for a run's model lineage.
 */
export async function getDiagnosticTemporalAlerts(
  runId: string,
  window: string = "ALL_AVAILABLE"
): Promise<TemporalAlert[]> {
  return request<TemporalAlert[]>(
    `/api/diagnostics/${encodeURIComponent(runId)}/temporal/alerts?window=${encodeURIComponent(window)}`
  );
}

/**
 * Phase 9: Retrieves change point detections for a run's model lineage.
 */
export async function getDiagnosticTemporalChangePoints(
  runId: string,
  window: string = "ALL_AVAILABLE"
): Promise<ChangePoint[]> {
  return request<ChangePoint[]>(
    `/api/diagnostics/${encodeURIComponent(runId)}/temporal/change-points?window=${encodeURIComponent(window)}`
  );
}

/**
 * Phase 9: Retrieves remediation durability assessments for a run's model lineage.
 */
export async function getDiagnosticTemporalRemediations(
  runId: string,
  window: string = "ALL_AVAILABLE"
): Promise<RemediationDurability[]> {
  return request<RemediationDurability[]>(
    `/api/diagnostics/${encodeURIComponent(runId)}/temporal/remediations?window=${encodeURIComponent(window)}`
  );
}

/**
 * Phase 9: Idempotently recalculates and persists temporal intelligence for a run's model lineage.
 */
export async function recalculateDiagnosticTemporal(
  runId: string
): Promise<TemporalRecalculateResponse> {
  return request<TemporalRecalculateResponse>(
    `/api/diagnostics/${encodeURIComponent(runId)}/temporal/recalculate`,
    {
      method: "POST",
    }
  );
}

/**
 * Phase 9: Retrieves model lineage history by lineage name.
 */
export async function getModelLineageHistory(
  lineageId: string,
  window: string = "ALL_AVAILABLE"
): Promise<ModelLineageHistory> {
  return request<ModelLineageHistory>(
    `/api/models/${encodeURIComponent(lineageId)}/history?window=${encodeURIComponent(window)}`
  );
}

/**
 * Phase 9: Idempotently recalculates model lineage temporal intelligence by lineage name.
 */
export async function recalculateModelTemporal(
  lineageId: string
): Promise<TemporalRecalculateResponse> {
  return request<TemporalRecalculateResponse>(
    `/api/models/${encodeURIComponent(lineageId)}/temporal/recalculate`,
    {
      method: "POST",
    }
  );
}




