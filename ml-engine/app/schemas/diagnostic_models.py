from datetime import datetime, timezone
from enum import Enum
from typing import Any, Dict, List, Optional
from pydantic import BaseModel, Field


class DiagnosticCategory(str, Enum):
    DATA_QUALITY = "data_quality"
    LEAKAGE = "leakage"
    DRIFT = "drift"
    PERFORMANCE = "performance"
    FAIRNESS = "fairness"
    ROBUSTNESS = "robustness"
    EXPLAINABILITY = "explainability"


class SeverityLevel(str, Enum):
    INFO = "INFO"
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    WARNING = "WARNING"
    HIGH = "HIGH"
    CRITICAL = "CRITICAL"


class DiagnosticIssue(BaseModel):
    id: str = Field(..., description="Unique issue identifier")
    category: DiagnosticCategory = Field(..., description="Diagnostic domain")
    severity: SeverityLevel = Field(..., description="Calculated severity")
    title: str = Field(..., description="Concise issue title")
    description: str = Field(..., description="Detailed technical explanation of the failure mode")
    affected_features: List[str] = Field(default_factory=list, description="Columns or features impacted")
    evidence: Dict[str, Any] = Field(default_factory=dict, description="Numerical/statistical evidence")
    recommendation: str = Field(..., description="Actionable remediation steps")


class DiagnosticMetric(BaseModel):
    name: str = Field(..., description="Metric name")
    value: float = Field(..., description="Numerical score or statistic")
    unit: Optional[str] = Field(default=None, description="Measurement unit")
    threshold_min: Optional[float] = Field(default=None, description="Lower acceptable threshold")
    threshold_max: Optional[float] = Field(default=None, description="Upper acceptable threshold")
    passed: bool = Field(default=True, description="Whether metric is within acceptable bounds")


class DiagnosticReport(BaseModel):
    engine_name: str = Field(..., description="Engine name")
    category: DiagnosticCategory = Field(..., description="Diagnostic category")
    health_score: float = Field(..., ge=0.0, le=100.0, description="Category health index (0-100)")
    passed: bool = Field(..., description="Overall pass status for this engine")
    execution_time_ms: float = Field(..., description="Execution time in milliseconds")
    metrics: List[DiagnosticMetric] = Field(default_factory=list, description="Evaluated diagnostic metrics")
    issues: List[DiagnosticIssue] = Field(default_factory=list, description="Discovered issues and anomalies")
    metadata: Dict[str, Any] = Field(default_factory=dict, description="Telemetry and execution context")


class DiagnosticRunRequest(BaseModel):
    run_id: str = Field(..., description="Unique run identifier")
    model_name: str = Field(..., description="Model name or registry ID")
    model_type: str = Field(..., description="Model architecture type (e.g., xgboost, lightgbm, pytorch, sklearn)")
    task_type: str = Field(default="binary_classification", description="ML task type")
    dataset_reference: str = Field(..., description="Path or S3 key to evaluation dataset")
    baseline_dataset_reference: Optional[str] = Field(default=None, description="Path to reference baseline dataset")
    target_column: str = Field(..., description="Name of the ground truth label column")
    prediction_column: Optional[str] = Field(default=None, description="Prediction column name if pre-computed")
    protected_attributes: Optional[List[str]] = Field(default=None, description="Demographic / fairness features")
    engines_to_run: Optional[List[DiagnosticCategory]] = Field(
        default=None,
        description="Subset of engines to execute. If omitted, all engines run."
    )


class CompositeDiagnosticResult(BaseModel):
    run_id: str = Field(..., description="Unique run ID")
    model_name: str = Field(..., description="Target model name")
    overall_health_score: float = Field(..., ge=0.0, le=100.0, description="Aggregate health score")
    status: str = Field(..., description="Status string: PASS, WARNING, CRITICAL_ISSUES, FAILED")
    created_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))
    reports: Dict[str, DiagnosticReport] = Field(default_factory=dict, description="Engine specific reports")
    summary_issues: List[DiagnosticIssue] = Field(default_factory=list, description="Consolidated critical issues")


class ModuleExecutionStatus(str, Enum):
    COMPLETED = "COMPLETED"
    NOT_IMPLEMENTED = "NOT_IMPLEMENTED"
    FAILED = "FAILED"


class ModuleExecutionResult(BaseModel):
    module: str = Field(..., description="Diagnostic module name (e.g. DATA_QUALITY, LEAKAGE)")
    status: ModuleExecutionStatus = Field(..., description="Execution status for this module")
    message: Optional[str] = Field(default=None, description="Detailed status message")
    result: Optional[Dict[str, Any]] = Field(default_factory=dict, description="Structured result data")
    error: Optional[str] = Field(default=None, description="Error details if execution failed")


class DiagnosticJobRequest(BaseModel):
    runId: str = Field(..., description="Canonical Run ID generated by backend")
    modelName: str = Field(..., description="Target model identifier")
    modelFramework: Optional[str] = Field(default="xgboost", description="Model framework")
    taskType: Optional[str] = Field(default="binary_classification", description="ML task type")
    modelStorageUri: Optional[str] = Field(default=None, description="Local or remote path to stored model artifact")
    modelArtifactId: Optional[str] = Field(default=None, description="Model artifact identifier")
    executionMode: Optional[str] = Field(default="REAL", description="Execution mode: REAL, BENCHMARK, TEST")
    evaluationDataset: str = Field(..., description="Evaluation dataset reference or file path")
    baselineDataset: Optional[str] = Field(default=None, description="Baseline reference dataset or file path")
    targetColumn: str = Field(..., description="Ground truth label column name")
    predictionColumn: Optional[str] = Field(default=None, description="Prediction column name")
    protectedAttribute: Optional[str] = Field(default=None, description="Protected fairness attribute")
    modules: List[str] = Field(default_factory=list, description="Requested diagnostic module names")


class DiagnosticJobResponse(BaseModel):
    runId: str = Field(..., description="Canonical Run ID")
    status: str = Field(..., description="Run outcome status (COMPLETED, FAILED)")
    executionTimeMs: float = Field(..., description="Execution duration in milliseconds")
    modules: List[ModuleExecutionResult] = Field(default_factory=list, description="Individual module execution outputs")
    error: Optional[str] = Field(default=None, description="Job level error message if failed")

