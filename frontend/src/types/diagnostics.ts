export type NavSection =
  | "00_INTELLIGENCE"
  | "01_OVERVIEW"
  | "02_DATA"
  | "03_FORENSICS"
  | "04_DRIFT"
  | "05_PERFORMANCE"
  | "05_ERROR_FORENSICS"
  | "06_INVESTIGATION"
  | "06_EXPLAIN"
  | "07_REMEDIATION"
  | "07_BIAS"
  | "08_EXPERIMENT"
  | "08_ROBUSTNESS"
  | "09_EXPERIMENTS"
  | "09_TEMPORAL"
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

export interface ExpectedImpactItem {
  metric: string;
  expectedDirection: "DECREASE" | "INCREASE" | "STABILIZE" | "CHANGE";
  rationale: string;
  confidence: string;
  expectedMagnitude: string;
}

export interface DiagnosticRemediation {
  id: number;
  runId: string;
  targetType: string;
  targetKey: string;
  remediationType: string;
  title: string;
  description: string;
  priority: "CRITICAL" | "HIGH" | "MEDIUM" | "LOW" | "INFO";
  priorityScore: number;
  confidence: "VERY_HIGH" | "HIGH" | "MEDIUM" | "LOW";
  evidenceStrength: string;
  hypothesis: string;
  expectedEffect: string;
  validationStrategy: string;
  acceptanceCriteria: string[];
  requiredModules: string[];
  regressionGuards: string[];
  expectedImpact: ExpectedImpactItem[];
  sourceCorrelationIds: number[];
  sourceResultIds: Record<string, number>;
  sourceInvestigationTarget?: string;
  status: "PROPOSED" | "SELECTED" | "VALIDATING" | "VALIDATED" | "REJECTED" | "SUPERSEDED";
  rejectionReason?: string;
  validationRunId?: string;
  associativeOnly: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface MetricComparisonItem {
  metricName: string;
  module: string;
  baselineValue: number;
  candidateValue: number;
  delta: number;
  relativeChangePct: number;
  higherIsBetter: boolean;
  direction: "IMPROVED" | "REGRESSED" | "NO_MATERIAL_CHANGE" | "MISSING_IN_CANDIDATE" | "MISSING_IN_BASELINE";
  assessment: "IMPROVED" | "REGRESSED" | "UNCHANGED" | "UNKNOWN";
  interpretation: string;
}

export interface DiagnosticComparison {
  baselineRunId: string;
  candidateRunId: string;
  baselineModelName: string;
  candidateModelName: string;
  baselineDatasetName: string;
  candidateDatasetName: string;
  overallAssessment: "IMPROVED" | "REGRESSED" | "MIXED" | "NO_MATERIAL_CHANGE" | "INSUFFICIENT_EVIDENCE";
  assessmentRationale: string;
  improvedMetricCount: number;
  regressedMetricCount: number;
  unchangedMetricCount: number;
  missingMetricCount: number;
  metricComparisons: MetricComparisonItem[];
  missingMetrics: string[];
  comparisonTimestamp: string;
}

export interface InvestigationTarget {
  id?: number;
  runId: string;
  targetType: string;
  targetKey: string;
  displayName: string;
  priority: "CRITICAL" | "HIGH" | "MEDIUM" | "LOW" | "INFO";
  priorityScore: number;
  evidenceConfidence: "VERY_HIGH" | "HIGH" | "MEDIUM" | "LOW";
  supportingModuleCount: number;
  supportingFindingCount: number;
  supportingEvidenceCount: number;
  hypothesis: string;
  nextActions: string[];
  evidenceSummary: Record<string, any>;
  createdAt?: string;
}

export interface InvestigationPathStep {
  stepNumber: number;
  sourceModule: string;
  targetKey?: string;
  metric?: string;
  metricValue?: number;
  formattedValue?: string;
  threshold?: string;
  description: string;
  correlationRuleId?: string;
  sourceResultId?: string;
}

export interface InvestigationProvenance {
  sourceModule: string;
  sourceResultId?: string;
  metric?: string;
  value?: any;
  threshold?: any;
  ruleId?: string;
  severity?: string;
}

export interface InvestigationDossier {
  target?: InvestigationTarget;
  displayName: string;
  targetKey: string;
  targetType: string;
  priority: string;
  priorityLevel: string;
  priorityScore: number;
  hypothesis: string;
  nextActions: string[];
  supportingModules: string[];
  supportingFindings: Array<{
    ruleId: string;
    title: string;
    severity: string;
    description: string;
    correlationScore: number;
  }>;
  investigationPath: InvestigationPathStep[];
  provenance: any;
  associativeOnly: boolean;
}

export interface EvidenceGraphNode {
  id: string;
  label: string;
  nodeType: "FEATURE" | "MODULE" | "CORRELATION" | "DATASET" | "MODEL" | "BEHAVIOR" | "SUBGROUP" | "FINDING" | string;
  severity?: string;
  value?: string;
  confidence?: string;
  module?: string;
  sourceModule?: string;
  priority?: string;
  score?: number;
  x?: number;
  y?: number;
  properties?: Record<string, any>;
}

export interface EvidenceGraphEdge {
  id: string;
  source: string;
  target: string;
  relationship?: "EXHIBITS" | "INFLUENCES" | "ASSOCIATED_WITH" | "DRIFTED_IN" | "FLAGGED_IN" | "CORRELATES_WITH" | "EVALUATED_BY" | "CONVERGES_ON" | string;
  relationType: string;
  weight?: number;
  label?: string;
  bidirectional?: boolean;
  sourceResultId?: number;
  sourceModule?: string;
  ruleId?: string;
  metric?: string;
  metricValue?: number;
  threshold?: string;
  confidence?: string;
  isAssociativeOnly?: boolean;
  notes?: string;
}

export interface EvidenceGraph {
  runId: string;
  nodes: EvidenceGraphNode[];
  edges: EvidenceGraphEdge[];
  nodeCount?: number;
  edgeCount?: number;
  generatedAt?: string;
  isAssociativeOnly?: boolean;
  causalityDisclaimer?: string;
}

export interface DiagnosticCorrelation {
  id?: number;
  ruleId: string;
  category: "HIGH_RISK" | "SECURITY" | "CONVERGING_EVIDENCE" | "DATA_HYGIENE" | "PERFORMANCE_ANOMALY" | "GENERAL";
  severity: DiagnosticSeverity;
  title: string;
  description: string;
  involvedModules: string[];
  involvedFeatures: string[];
  metrics: Record<string, any>;
  correlationScore: number;
  recommendedAction: string;
  impactScore?: number;
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
  remediationCount?: number;
  criticalRemediationCount?: number;
  highRemediationCount?: number;
  selectedRemediationCount?: number;
  validatedRemediationCount?: number;
  topRemediationType?: string;
  topRemediationTarget?: string;
  remediationAvailable?: boolean;
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

export type ExperimentType =
  | "FEATURE_ABLATION"
  | "FEATURE_TRANSFORMATION"
  | "MISSING_VALUE_STRESS"
  | "THRESHOLD_COUNTERFACTUAL"
  | "CALIBRATION_COUNTERFACTUAL"
  | "SUBGROUP_COUNTERFACTUAL";

export type ExperimentStatus =
  | "PROPOSED"
  | "QUEUED"
  | "RUNNING"
  | "COMPLETED"
  | "FAILED"
  | "NOT_EXECUTABLE"
  | "CANCELLED";

export type ExperimentConclusion =
  | "VALIDATED"
  | "PARTIALLY_VALIDATED"
  | "REJECTED"
  | "INCONCLUSIVE"
  | "NOT_EXECUTABLE"
  | "FAILED";

export interface InterventionConfig {
  feature?: string;
  strategy?: "drop" | "zero" | "median";
  transformationType?: "CLIP" | "WINSORIZE" | "MISSING_REPLACE" | "STANDARDIZE";
  lowerQuantile?: number;
  upperQuantile?: number;
  missingnessRate?: number;
  baselineThreshold?: number;
  candidateThreshold?: number;
  calibrationMethod?: "PLATT" | "ISOTONIC";
  subgroupAttribute?: string;
  deterministicSeed?: number;
  parameters?: Record<string, any>;
}

export interface AcceptanceCriterionResult {
  criterion: string;
  baselineValue?: number;
  candidateValue?: number;
  delta?: number;
  operator: string;
  threshold?: number;
  passed: boolean;
  reason: string;
}

export interface RegressionGuardResult {
  guard: string;
  baselineValue?: number;
  candidateValue?: number;
  delta?: number;
  operator: string;
  threshold?: number;
  passed: boolean;
  reason: string;
}

export interface StatisticalEvidence {
  sampleSize?: number;
  positiveCount?: number;
  negativeCount?: number;
  changedPredictionsCount?: number;
  predictionFlipRate?: number;
  contingencyTable?: {
    n00: number;
    n01: number;
    n10: number;
    n11: number;
  };
  mcNemarStatistic?: number;
  mcNemarPValue?: number;
  meanProbabilityShift?: number;
  medianProbabilityShift?: number;
  meanAbsoluteProbabilityShift?: number;
  probShiftCiLower?: number;
  probShiftCiUpper?: number;
  subgroups?: Array<{
    group: string;
    sampleSize: number;
    baselineF1: number;
    candidateF1: number;
    flipRate: number;
    accuracyDelta?: number;
    f1Delta?: number;
  }>;
  deterministicSeed?: number;
}

export interface DiagnosticExperiment {
  id: string;
  baselineRunId: string;
  candidateRunId?: string;
  remediationId?: number;
  experimentType: ExperimentType;
  status: ExperimentStatus;
  title: string;
  description?: string;
  targetType?: string;
  targetKey?: string;
  interventionConfig?: InterventionConfig;
  datasetProvenance?: Record<string, any>;
  modelProvenance?: Record<string, any>;
  requestedModules?: string[];
  executedModules?: string[];
  baselineMetrics?: Record<string, any>;
  candidateMetrics?: Record<string, any>;
  metricDeltas?: MetricComparisonItem[];
  acceptanceCriteria?: string[];
  acceptanceResults?: AcceptanceCriterionResult[];
  regressionGuards?: string[];
  regressionResults?: RegressionGuardResult[];
  statisticalEvidence?: StatisticalEvidence;
  conclusion?: ExperimentConclusion;
  conclusionReason?: string;
  deterministicSeed?: number;
  errorCode?: string;
  errorMessage?: string;
  createdAt: string;
  startedAt?: string;
  completedAt?: string;
}

export interface CreateExperimentRequest {
  remediationId?: number;
  experimentType: ExperimentType;
  title?: string;
  description?: string;
  targetType?: string;
  targetKey?: string;
  intervention?: InterventionConfig;
  requestedModules?: string[];
  deterministicSeed?: number;
}

export interface TemporalMetricPoint {
  runId: string;
  runType: "BASELINE" | "EXPERIMENT" | string;
  runIndex: number;
  timestamp: string;
  value: number;
  severity?: string;
  threshold?: number;
}

export interface ChangePoint {
  id?: number;
  metricName: string;
  targetKey?: string;
  changeRunId: string;
  changeTimestamp: string;
  beforeMean: number;
  afterMean: number;
  absoluteShift: number;
  relativeShift: number;
  confidenceLevel: "HIGH" | "MEDIUM" | "LOW" | string;
  runIdsBefore?: string[];
  runIdsAfter?: string[];
}

export interface TemporalMetricHistory {
  metricName: string;
  module: string;
  targetType?: string;
  targetKey?: string;
  higherIsBetter: boolean;
  baselinePoints: TemporalMetricPoint[];
  experimentPoints: TemporalMetricPoint[];
  latestValue?: number;
  previousValue?: number;
  absoluteDelta?: number;
  relativeDelta?: number;
  minimum?: number;
  maximum?: number;
  mean?: number;
  median?: number;
  standardDeviation?: number;
  coefficientOfVariation?: number;
  observationCount: number;
  slope?: number;
  rSquared?: number;
  trendDirection: "IMPROVING" | "DEGRADING" | "STABLE" | "VOLATILE" | "INSUFFICIENT_DATA" | string;
  mannKendallTau?: number;
  mannKendallPValue?: number;
  baselineMean?: number;
  baselineStd?: number;
  standardizedDeviation?: number;
  currentSeverity?: string;
  previousSeverity?: string;
  severityTransitions: string[];
  consecutiveRunsAtSeverity: number;
  changePoints: ChangePoint[];
}

export interface IssueTrack {
  id?: number;
  modelLineageId: string;
  trackFingerprint: string;
  targetType: string;
  targetKey: string;
  firstSeenAt: string;
  lastSeenAt: string;
  firstSeenRunId: string;
  lastSeenRunId: string;
  observationCount: number;
  consecutiveCount: number;
  currentSeverity: string;
  peakSeverity: string;
  status: "PERSISTENT" | "EMERGING" | "RECURRING" | "TRANSIENT" | "RECOVERED" | "ESCALATING" | "DEESCALATING" | string;
  modulesInvolved: string[];
  metricNames: string[];
  runIds: string[];
  historyJson?: string;
  remediationHistoryJson?: string;
  durabilityStatus?: "SUSTAINED" | "TEMPORARY" | "FAILED_TO_SUSTAIN" | "INSUFFICIENT_FOLLOWUP" | "NOT_APPLICABLE" | string;
}

export interface TemporalAlert {
  id?: number;
  modelLineageId: string;
  runId: string;
  alertType: "NEW_DEGRADATION" | "PERSISTENT_DEGRADATION" | "ESCALATING_DEGRADATION" | "RECOVERY" | "REGRESSION_AFTER_RECOVERY" | "RECURRING_ISSUE" | "CHANGE_POINT_DETECTED" | "REMEDIATION_NOT_SUSTAINED" | "MULTI_MODULE_ESCALATION" | string;
  priority: "CRITICAL" | "HIGH" | "MEDIUM" | "LOW" | "INFO";
  targetType?: string;
  targetKey?: string;
  metricName?: string;
  currentValue?: number;
  referenceValue?: number;
  triggerDescription: string;
  confidence: "VERY_HIGH" | "HIGH" | "MEDIUM" | "LOW" | string;
  runIds: string[];
  acknowledged: boolean;
  createdAt: string;
}

export interface RemediationDurability {
  remediationId: number;
  experimentId: string;
  baselineRunId: string;
  candidateRunId?: string;
  targetKey: string;
  metricName: string;
  remediationTitle: string;
  experimentType: string;
  preExperimentValue: number;
  candidateValue: number;
  durabilityStatus: "SUSTAINED" | "TEMPORARY" | "FAILED_TO_SUSTAIN" | "INSUFFICIENT_FOLLOWUP" | "NOT_APPLICABLE" | string;
  followUpRunCount: number;
  followUpRunIds: string[];
  latestFollowUpValue?: number;
  durabilityExplanation: string;
  evaluatedAt: string;
}

export interface ModelLineageHistory {
  modelLineageId: string;
  window: "LAST_3" | "LAST_5" | "LAST_10" | "ALL_AVAILABLE" | "CUSTOM" | string;
  totalRunsCount: number;
  baselineRunsCount: number;
  experimentRunsCount: number;
  firstObservedAt?: string;
  lastObservedAt?: string;
  activeIssuesCount: number;
  persistentIssuesCount: number;
  emergingIssuesCount: number;
  recurringIssuesCount: number;
  activeAlertsCount: number;
  criticalAlertsCount: number;
  changePointsCount: number;
  orderedRuns: Array<{
    runId: string;
    modelName: string;
    status: string;
    executionMode: string;
    createdAt: string;
    totalModules: number;
    completedModules: number;
  }>;
  metricHistories: TemporalMetricHistory[];
  issueTracks: IssueTrack[];
  alerts: TemporalAlert[];
  changePoints: ChangePoint[];
  remediationDurability: RemediationDurability[];
}

export interface TemporalRecalculateResponse {
  modelLineageId: string;
  runsProcessed: number;
  observationsExtracted: number;
  issueTracksBuilt: number;
  alertsGenerated: number;
  changePointsDetected: number;
  success: boolean;
  message: string;
}

