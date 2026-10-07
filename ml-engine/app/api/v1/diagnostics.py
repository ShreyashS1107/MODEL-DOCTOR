import time
import logging
from typing import Any, Dict, List, Optional
import pandas as pd
from fastapi import APIRouter, HTTPException

from app.data.loader import DatasetLoader
from app.diagnostics import AVAILABLE_ENGINES
from app.diagnostics.data_quality.engine import DataQualityEngine
from app.diagnostics.leakage.engine import DataLeakageEngine
from app.diagnostics.drift.engine import DataDriftEngine
from app.diagnostics.performance.engine import PerformanceEngine
from app.diagnostics.explainability.engine import ExplainabilityEngine
from app.diagnostics.fairness.engine import FairnessEngine
from app.diagnostics.robustness.engine import RobustnessEngine
from app.schemas.diagnostic_models import (
    DiagnosticJobRequest,
    DiagnosticJobResponse,
    ModuleExecutionResult,
    ModuleExecutionStatus,
    SeverityLevel,
)

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/diagnostics", tags=["Diagnostics Engine"])

MODULE_CATEGORY_MAP = {
    "DATA_QUALITY": "data_quality",
    "LEAKAGE": "leakage",
    "DRIFT": "drift",
    "PERFORMANCE": "performance",
    "FAIRNESS": "fairness",
    "BIAS": "fairness",
    "ROBUSTNESS": "robustness",
    "EXPLAINABILITY": "explainability",
}

DEFERRED_MODULES = {"EXPERIMENTS", "LLM_EXPLANATIONS", "REPORTS"}

# Engines that are fully implemented with real statistical calculations in Phase 2B.1 - 2B.6
IMPLEMENTED_MODULES = {"DATA_QUALITY", "LEAKAGE", "DRIFT", "PERFORMANCE", "EXPLAINABILITY", "FAIRNESS", "BIAS", "ROBUSTNESS"}


@router.get("/engines", summary="List Registered Diagnostic Engines")
async def list_engines() -> List[Dict[str, str]]:
    """Returns metadata for all available diagnostic modules."""
    engine_list = []
    for key, engine_cls in AVAILABLE_ENGINES.items():
        engine_inst = engine_cls()
        is_implemented = key in ["data_quality", "leakage", "drift", "performance", "explainability", "fairness", "robustness"]
        phase = "2B.1" if key in ["data_quality", "leakage"] else "2B.2" if key == "drift" else "2B.3" if key == "performance" else "2B.4" if key == "explainability" else "2B.5" if key == "fairness" else "2B.6" if key == "robustness" else "SUBSEQUENT"
        engine_list.append({
            "id": key,
            "name": engine_inst.name,
            "category": engine_inst.category.value,
            "description": engine_inst.description,
            "status": "READY" if is_implemented else "DEFERRED_PHASE",
            "phase": phase,
        })
    return engine_list


@router.post("/run", response_model=DiagnosticJobResponse, summary="Execute Diagnostic Job")
async def run_diagnostics(request: DiagnosticJobRequest) -> DiagnosticJobResponse:
    """
    Executes selected diagnostic modules for the given dataset and model specifications.
    Phase 2B executes real statistical engines for:
    - DATA_QUALITY: Real dataset statistics, missingness, constant features, IQR outliers, duplicates, non-finite values.
    - LEAKAGE: Real target mutual information, Pearson / Cramer's V association, proxy detection, train/eval contamination.
    - DRIFT: Real KS-test, Wasserstein-1 distance with IQR normalization, quantile-binned PSI, Chi-Square contingency, categorical PSI, Benjamini-Hochberg FDR correction.
    - PERFORMANCE: Real Confusion Matrix, ROC-AUC, PR-AUC, Brier score, Log Loss, 10-bin calibration curves, ECE, multi-threshold grid, baseline prevalence comparison.
    - EXPLAINABILITY: Real TreeSHAP, permutation importance fallback, Spearman agreement, concentration, local waterfall explanations.
    Other modules remain explicitly NOT_IMPLEMENTED until subsequent phases.
    """
    start_time = time.perf_counter()
    logger.info("Received diagnostic job %s for model %s (modules: %s)", request.runId, request.modelName, request.modules)

    module_results: List[ModuleExecutionResult] = []

    # 1. Ingest evaluation and optional baseline datasets
    eval_df: Optional[pd.DataFrame] = None
    baseline_df: Optional[pd.DataFrame] = None
    dataset_load_error: Optional[str] = None

    try:
        eval_df = DatasetLoader.load(request.evaluationDataset, execution_mode=request.executionMode)
        logger.info("Loaded evaluation dataset with %d rows and %d columns", len(eval_df), len(eval_df.columns))
    except Exception as e:
        logger.error("Failed to load evaluation dataset '%s': %s", request.evaluationDataset, str(e), exc_info=True)
        dataset_load_error = f"Failed to load evaluation dataset: {str(e)}"

    if not dataset_load_error and request.baselineDataset and request.baselineDataset.strip():
        try:
            baseline_df = DatasetLoader.load(request.baselineDataset, execution_mode=request.executionMode)
            logger.info("Loaded baseline dataset with %d rows and %d columns", len(baseline_df), len(baseline_df.columns))
        except Exception as e:
            logger.warning("Failed to load baseline dataset '%s': %s", request.baselineDataset, str(e))
            # Non-fatal for engines that don't strictly require baseline, but keep baseline_df as None

    # If evaluation dataset failed to load, fail all requested modules
    if dataset_load_error:
        exec_time_ms = round((time.perf_counter() - start_time) * 1000, 2)
        for mod_name in request.modules:
            module_results.append(
                ModuleExecutionResult(
                    module=mod_name.upper(),
                    status=ModuleExecutionStatus.FAILED,
                    message=dataset_load_error,
                    error=dataset_load_error,
                    result={},
                )
            )
        return DiagnosticJobResponse(
            runId=request.runId,
            status="FAILED",
            executionTimeMs=exec_time_ms,
            modules=module_results,
            error=dataset_load_error,
        )

    # 2. Execute each requested module
    for mod_name in request.modules:
        mod_upper = mod_name.upper()
        engine_key = MODULE_CATEGORY_MAP.get(mod_upper, mod_name.lower())

        if mod_upper in DEFERRED_MODULES:
            module_results.append(
                ModuleExecutionResult(
                    module=mod_upper,
                    status=ModuleExecutionStatus.NOT_IMPLEMENTED,
                    message=f"Module '{mod_name}' is registered for subsequent diagnostic phases.",
                    result={},
                )
            )
            continue

        if engine_key not in AVAILABLE_ENGINES:
            logger.warning("Unrecognized diagnostic module requested: %s", mod_name)
            module_results.append(
                ModuleExecutionResult(
                    module=mod_upper,
                    status=ModuleExecutionStatus.FAILED,
                    message=f"Module '{mod_name}' is not supported by the ML engine registry.",
                    error=f"Unrecognized module identifier '{mod_name}'",
                    result={},
                )
            )
            continue

        engine_cls = AVAILABLE_ENGINES[engine_key]
        engine_inst = engine_cls()

        # Check if module is implemented in this phase
        if mod_upper == "DATA_QUALITY":
            try:
                dq_engine = DataQualityEngine()
                report = await dq_engine.run_diagnostic(
                    current_data=eval_df,
                    target_column=request.targetColumn,
                    baseline_data=baseline_df,
                    config={},
                )
                module_results.append(
                    ModuleExecutionResult(
                        module="DATA_QUALITY",
                        status=ModuleExecutionStatus.COMPLETED,
                        message=f"Data Quality audit completed: {len(report.issues)} findings discovered. Health score: {report.health_score:.1f}.",
                        result=report.metadata,
                    )
                )
            except Exception as e:
                logger.error("Error executing DataQualityEngine: %s", str(e), exc_info=True)
                module_results.append(
                    ModuleExecutionResult(
                        module="DATA_QUALITY",
                        status=ModuleExecutionStatus.FAILED,
                        message=f"Execution error in DATA_QUALITY: {str(e)}",
                        error=str(e),
                        result={},
                    )
                )

        elif mod_upper == "LEAKAGE":
            try:
                leak_engine = DataLeakageEngine()
                report = await leak_engine.run_diagnostic(
                    current_data=eval_df,
                    target_column=request.targetColumn,
                    baseline_data=baseline_df,
                    config={"task_type": request.taskType},
                )
                if not report.passed and any(issue.id in ["LEAK-TARGET-MISSING-001", "LEAK-EMPTY-001", "LEAK-TARGET-VARIANCE-001"] for issue in report.issues):
                    crit_issue = next(issue for issue in report.issues if issue.severity == SeverityLevel.CRITICAL)
                    module_results.append(
                        ModuleExecutionResult(
                            module="LEAKAGE",
                            status=ModuleExecutionStatus.FAILED,
                            message=crit_issue.description,
                            error=crit_issue.id,
                            result=report.metadata,
                        )
                    )
                else:
                    module_results.append(
                        ModuleExecutionResult(
                            module="LEAKAGE",
                            status=ModuleExecutionStatus.COMPLETED,
                            message=f"Data Leakage forensic audit completed: {len(report.issues)} potential leakage findings discovered. Health score: {report.health_score:.1f}.",
                            result=report.metadata,
                        )
                    )
            except Exception as e:
                logger.error("Error executing DataLeakageEngine: %s", str(e), exc_info=True)
                module_results.append(
                    ModuleExecutionResult(
                        module="LEAKAGE",
                        status=ModuleExecutionStatus.FAILED,
                        message=f"Execution error in LEAKAGE: {str(e)}",
                        error=str(e),
                        result={},
                    )
                )

        elif mod_upper == "DRIFT":
            try:
                drift_engine = DataDriftEngine()
                report = await drift_engine.run_diagnostic(
                    current_data=eval_df,
                    target_column=request.targetColumn,
                    baseline_data=baseline_df,
                    config={},
                )
                module_results.append(
                    ModuleExecutionResult(
                        module="DRIFT",
                        status=ModuleExecutionStatus.COMPLETED,
                        message=f"Distribution Drift audit completed: {len(report.issues)} findings discovered. Max PSI: {report.metadata.get('summary', {}).get('maxPsi', 0.0):.4f}. Health score: {report.health_score:.1f}.",
                        result=report.metadata,
                    )
                )
            except Exception as e:
                logger.error("Error executing DataDriftEngine: %s", str(e), exc_info=True)
                module_results.append(
                    ModuleExecutionResult(
                        module="DRIFT",
                        status=ModuleExecutionStatus.FAILED,
                        message=f"Execution error in DRIFT: {str(e)}",
                        error=str(e),
                        result={},
                    )
                )

        elif mod_upper == "PERFORMANCE":
            try:
                perf_engine = PerformanceEngine()
                report = await perf_engine.run_diagnostic(
                    current_data=eval_df,
                    target_column=request.targetColumn,
                    baseline_data=baseline_df,
                    config={
                        "prediction_column": request.predictionColumn,
                        "task_type": request.taskType,
                    },
                )
                roc_str = f"{report.metadata.get('summary', {}).get('rocAuc'):.4f}" if report.metadata.get('summary', {}).get('rocAuc') is not None else "N/A"
                f1_str = f"{report.metadata.get('summary', {}).get('f1', 0.0):.4f}"
                ece_str = f"{report.metadata.get('summary', {}).get('expectedCalibrationError', 0.0):.4f}"
                module_results.append(
                    ModuleExecutionResult(
                        module="PERFORMANCE",
                        status=ModuleExecutionStatus.COMPLETED,
                        message=f"Model Performance audit completed: {len(report.issues)} findings discovered. ROC-AUC: {roc_str}, F1: {f1_str}, ECE: {ece_str}. Health score: {report.health_score:.1f}.",
                        result=report.metadata,
                    )
                )
            except Exception as e:
                logger.error("Error executing PerformanceEngine: %s", str(e), exc_info=True)
                module_results.append(
                    ModuleExecutionResult(
                        module="PERFORMANCE",
                        status=ModuleExecutionStatus.FAILED,
                        message=f"Execution error in PERFORMANCE: {str(e)}",
                        error=str(e),
                        result={},
                    )
                )

        elif mod_upper == "EXPLAINABILITY":
            try:
                explain_engine = ExplainabilityEngine()
                report = await explain_engine.run_diagnostic(
                    current_data=eval_df,
                    target_column=request.targetColumn,
                    baseline_data=baseline_df,
                    config={
                        "model_name": request.modelName,
                        "model_framework": request.modelFramework,
                        "task_type": request.taskType,
                        "prediction_column": request.predictionColumn,
                        "storage_uri": request.modelStorageUri,
                        "execution_mode": request.executionMode,
                    },
                )
                if not report.passed and any(issue.id == "MODEL_ARTIFACT_UNAVAILABLE" for issue in report.issues):
                    module_results.append(
                        ModuleExecutionResult(
                            module="EXPLAINABILITY",
                            status=ModuleExecutionStatus.FAILED,
                            message=f"Model artifact unavailable for '{request.modelName}'. Explainability requires a valid trained model artifact.",
                            error="MODEL_ARTIFACT_UNAVAILABLE",
                            result=report.metadata,
                        )
                    )
                else:
                    top_f = report.metadata.get("summary", {}).get("topFeature", "none")
                    spearman = report.metadata.get("summary", {}).get("importanceAgreementSpearman")
                    spearman_str = f"{spearman:.2f}" if spearman is not None else "N/A"
                    module_results.append(
                        ModuleExecutionResult(
                            module="EXPLAINABILITY",
                            status=ModuleExecutionStatus.COMPLETED,
                            message=(
                                f"Model Explainability & Feature Attribution audit completed: {len(report.issues)} findings discovered. "
                                f"Top feature: '{top_f}', Rank agreement Spearman ρ: {spearman_str}. Health score: {report.health_score:.1f}."
                            ),
                            result=report.metadata,
                        )
                    )
            except Exception as e:
                logger.error("Error executing ExplainabilityEngine: %s", str(e), exc_info=True)
                module_results.append(
                    ModuleExecutionResult(
                        module="EXPLAINABILITY",
                        status=ModuleExecutionStatus.FAILED,
                        message=f"Execution error in EXPLAINABILITY: {str(e)}",
                        error=str(e),
                        result={},
                    )
                )

        elif mod_upper in ["FAIRNESS", "BIAS"]:
            try:
                fair_engine = FairnessEngine()
                report = await fair_engine.run_diagnostic(
                    current_data=eval_df,
                    target_column=request.targetColumn,
                    baseline_data=baseline_df,
                    config={
                        "protected_attribute": request.protectedAttribute,
                        "prediction_column": request.predictionColumn,
                        "task_type": request.taskType,
                    },
                )
                if not report.passed and any(issue.id in ["BIAS_PROTECTED_ATTRIBUTE_MISSING", "BIAS_UNSUPPORTED_TASK", "BIAS_EMPTY_DATASET", "BIAS_MISSING_TARGET", "BIAS_HIGH_CARDINALITY"] for issue in report.issues):
                    crit_issue = next(issue for issue in report.issues if issue.severity == SeverityLevel.CRITICAL)
                    module_results.append(
                        ModuleExecutionResult(
                            module="BIAS",
                            status=ModuleExecutionStatus.FAILED,
                            message=crit_issue.description,
                            error=crit_issue.id,
                            result=report.metadata,
                        )
                    )
                else:
                    prot_attr_used = report.metadata.get("summary", {}).get("protectedAttribute", "N/A")
                    dp_gap = report.metadata.get("summary", {}).get("demographicParityGap", 0.0)
                    dir_val = report.metadata.get("summary", {}).get("worstDisparateImpactRatio", 1.0)
                    module_results.append(
                        ModuleExecutionResult(
                            module="BIAS",
                            status=ModuleExecutionStatus.COMPLETED,
                            message=(
                                f"Bias, Fairness & Disparate Impact audit completed for protected attribute '{prot_attr_used}': "
                                f"{len(report.issues)} findings discovered. Min DIR: {dir_val:.4f}, DP Gap: {dp_gap:.4f}. Health score: {report.health_score:.1f}."
                            ),
                            result=report.metadata,
                        )
                    )
            except Exception as e:
                logger.error("Error executing FairnessEngine: %s", str(e), exc_info=True)
                module_results.append(
                    ModuleExecutionResult(
                        module="BIAS",
                        status=ModuleExecutionStatus.FAILED,
                        message=f"Execution error in BIAS: {str(e)}",
                        error=str(e),
                        result={},
                    )
                )

        elif mod_upper == "ROBUSTNESS":
            try:
                robust_engine = RobustnessEngine()
                report = await robust_engine.run_diagnostic(
                    current_data=eval_df,
                    target_column=request.targetColumn,
                    baseline_data=baseline_df,
                    config={
                        "model_name": request.modelName,
                        "model_framework": request.modelFramework,
                        "task_type": request.taskType,
                        "protected_attribute": request.protectedAttribute,
                        "storage_uri": request.modelStorageUri,
                        "execution_mode": request.executionMode,
                    },
                )
                if not report.passed and any(issue.id in ["ROBUSTNESS_EMPTY_DATASET", "ROBUSTNESS_UNSUPPORTED_TASK", "MODEL_ARTIFACT_UNAVAILABLE", "ROBUSTNESS_NO_NUMERIC_FEATURES"] for issue in report.issues):
                    crit_issue = next(issue for issue in report.issues if issue.severity == SeverityLevel.CRITICAL)
                    module_results.append(
                        ModuleExecutionResult(
                            module="ROBUSTNESS",
                            status=ModuleExecutionStatus.FAILED,
                            message=crit_issue.description,
                            error=crit_issue.id,
                            result=report.metadata,
                        )
                    )
                else:
                    flip_rate = report.metadata.get("summary", {}).get("gaussianJitter5PctFlipRate", 0.0)
                    bnd_flip = report.metadata.get("summary", {}).get("boundaryFlipRate", 0.0)
                    top_f = report.metadata.get("summary", {}).get("topSensitiveFeature", "none")
                    module_results.append(
                        ModuleExecutionResult(
                            module="ROBUSTNESS",
                            status=ModuleExecutionStatus.COMPLETED,
                            message=(
                                f"Adversarial & Perturbation Robustness audit completed: {len(report.issues)} findings discovered. "
                                f"5% Noise Flip Rate: {flip_rate * 100:.1f}%, Boundary Flip Rate: {bnd_flip * 100:.1f}%, Top Sensitive Feature: '{top_f}'. Health score: {report.health_score:.1f}."
                            ),
                            result=report.metadata,
                        )
                    )
            except Exception as e:
                logger.error("Error executing RobustnessEngine: %s", str(e), exc_info=True)
                module_results.append(
                    ModuleExecutionResult(
                        module="ROBUSTNESS",
                        status=ModuleExecutionStatus.FAILED,
                        message=f"Execution error in ROBUSTNESS: {str(e)}",
                        error=str(e),
                        result={},
                    )
                )

        else:
            # Future deferred modules (e.g. EXPERIMENTS, LLM_EXPLANATIONS)
            module_results.append(
                ModuleExecutionResult(
                    module=mod_upper,
                    status=ModuleExecutionStatus.NOT_IMPLEMENTED,
                    message=f"{engine_inst.name} is verified and registered. Detailed statistical math will be executed in subsequent phases.",
                    result={},
                )
            )

    # 3. Determine canonical overall run status
    has_failed = any(m.status == ModuleExecutionStatus.FAILED for m in module_results)
    has_not_implemented = any(m.status == ModuleExecutionStatus.NOT_IMPLEMENTED for m in module_results)
    has_completed = any(m.status == ModuleExecutionStatus.COMPLETED for m in module_results)
    all_failed = all(m.status == ModuleExecutionStatus.FAILED for m in module_results) if module_results else False

    if all_failed:
        overall_status = "FAILED"
    elif has_completed and not has_failed and not has_not_implemented:
        overall_status = "COMPLETED"
    elif has_completed and (has_failed or has_not_implemented):
        overall_status = "PARTIAL"
    elif has_not_implemented and not has_failed:
        overall_status = "PARTIAL"
    elif has_failed:
        overall_status = "FAILED"
    else:
        overall_status = "COMPLETED"

    execution_time_ms = round((time.perf_counter() - start_time) * 1000, 2)

    return DiagnosticJobResponse(
        runId=request.runId,
        status=overall_status,
        executionTimeMs=execution_time_ms,
        modules=module_results,
        error=None,
    )
