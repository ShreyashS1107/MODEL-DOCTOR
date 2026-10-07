export type NavSection =
  | "00_INTELLIGENCE"
  | "01_OVERVIEW"
  | "02_DATA"
  | "03_FORENSICS"
  | "04_DRIFT"
  | "05_PERFORMANCE"
  | "06_EXPLAIN"
  | "07_BIAS"
  | "08_ROBUSTNESS"
  | "09_EXPERIMENTS"
  | "10_REPORTS";

export type ViewportTab =
  | "TOPOLOGY"
  | "DISTRIBUTION"
  | "CALIBRATION"
  | "LEAKAGE_SHAP"
  | "RUN_HISTORY";

export type DiagnosticSeverity = "CRITICAL" | "WARNING" | "OBSERVATION" | "NOMINAL";

export interface FeatureNode {
  id: string;
  name: string;
  dataType: "float64" | "int64" | "categorical" | "timestamp" | "identifier";
  missingRate: number; // 0.0 - 1.0
  mutualInfoTarget: number; // 0.0 - 1.0
  correlationTarget: number;
  ksPValue: number;
  psiValue: number;
  shapImportance: number;
  outlierFraction: number;
  severity: DiagnosticSeverity;
  anomalyType?: string;
  rootCause?: string;
  recommendation?: string;
  x: number;
  y: number;
}

export interface FeatureEdge {
  source: string;
  target: string;
  weight: number; // correlation or mutual information strength (0.0 - 1.0)
  isAnomaly: boolean;
}

export interface ModelDiagnosticSummary {
  modelId: string;
  modelVersion: string;
  framework: string;
  taskType: string;
  datasetName: string;
  sampleCount: number;
  featureCount: number;
  runId: string;
  healthScore: number; // 0 - 100
  criticalFindingsCount: number;
  warningsCount: number;
  observationsCount: number;
  maxPsiDrift: number;
  maxMutualInfoLeakage: number;
  rocAuc?: number;
  f1Score?: number;
  expectedCalibrationError: number;
  brierScore: number;
  disparateImpactRatio: number;
  gaussianJitterFlipRate: number;
  isMockData: boolean;
  executionMode?: string;
  modelArtifact?: {
    id: string;
    originalFilename: string;
    framework: string;
    modelFormat: string;
    fileSize: number;
    sha256: string;
  };
  evaluationDatasetArtifact?: {
    id: string;
    originalFilename: string;
    datasetFormat: string;
    fileSize: number;
    rowCount: number;
    columnCount: number;
    sha256: string;
  };
  baselineDatasetArtifact?: {
    id: string;
    originalFilename: string;
    datasetFormat: string;
    fileSize: number;
    rowCount: number;
    columnCount: number;
    sha256: string;
  };
}

export interface DistributionBin {
  binIndex: number;
  binRange: string;
  baselineDensity: number;
  currentDensity: number;
  psiDelta: number;
  psiContribution?: number;
}

export interface CalibrationBin {
  binIndex: number;
  predictedProbability: number;
  observedFrequency: number;
  sampleCount: number;
}

export interface LogEntry {
  id: string;
  timestamp: string;
  level: "CRIT" | "WARN" | "INFO" | "EVAL";
  module: string;
  message: string;
}

export interface DiagnosticRunRecord {
  id: string;
  runNumber: string;
  modelName: string;
  modelVersion: string;
  datasetName: string;
  healthScore: number;
  criticalCount: number;
  warningCount: number;
  sampleCount: number;
  durationMs: number;
  timestamp: string;
  status: "FAIL" | "WARN" | "PASS";
}
