import os
import uuid
import numpy as np
import pandas as pd
from fastapi import APIRouter, HTTPException, status
from pydantic import BaseModel, Field
from typing import Dict, Any, List, Optional

from app.experiments.dataset_transforms import (
    apply_feature_ablation,
    apply_feature_transformation,
    inject_missingness_stress
)
from app.experiments.counterfactuals import (
    evaluate_threshold_grid,
    evaluate_calibration_counterfactual,
    evaluate_subgroup_counterfactual
)
from app.experiments.statistical import compute_paired_comparison_statistics

router = APIRouter(prefix="/experiments", tags=["experiments"])

class ApplyInterventionRequest(BaseModel):
    datasetPath: str
    interventionType: str # "FEATURE_ABLATION", "FEATURE_TRANSFORMATION", "MISSING_VALUE_STRESS"
    feature: str
    strategy: Optional[str] = "zero"
    transformationType: Optional[str] = "CLIP"
    lowerQuantile: Optional[float] = 0.01
    upperQuantile: Optional[float] = 0.99
    missingnessRate: Optional[float] = 0.10
    deterministicSeed: Optional[int] = 42

class StatisticalComparisonRequest(BaseModel):
    yTrue: List[int]
    baselinePreds: List[int]
    candidatePreds: List[int]
    baselineProbs: Optional[List[float]] = None
    candidateProbs: Optional[List[float]] = None
    deterministicSeed: Optional[int] = 42

class ThresholdCounterfactualRequest(BaseModel):
    yTrue: List[int]
    yProb: List[float]
    baselineThreshold: float = 0.50
    candidateThreshold: float = 0.50

class CalibrationCounterfactualRequest(BaseModel):
    yEvalTrue: List[int]
    yEvalProb: List[float]
    yCalibTrue: Optional[List[int]] = None
    yCalibProb: Optional[List[float]] = None
    method: str = "PLATT"

class SubgroupCounterfactualRequest(BaseModel):
    subgroups: List[str]
    yTrue: List[int]
    baselinePreds: List[int]
    candidatePreds: List[int]

@router.post("/apply-intervention")
def apply_intervention(req: ApplyInterventionRequest) -> Dict[str, Any]:
    if not os.path.exists(req.datasetPath):
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Dataset file not found at: {req.datasetPath}"
        )

    try:
        # Load dataset
        if req.datasetPath.endswith(".parquet") or req.datasetPath.endswith(".pq"):
            df = pd.read_parquet(req.datasetPath)
        else:
            df = pd.read_csv(req.datasetPath)
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Failed to read dataset: {str(e)}"
        )

    try:
        if req.interventionType == "FEATURE_ABLATION":
            df_trans, prov = apply_feature_ablation(
                df, req.feature, strategy=req.strategy or "zero"
            )
        elif req.interventionType == "FEATURE_TRANSFORMATION":
            df_trans, prov = apply_feature_transformation(
                df, req.feature,
                transformation_type=req.transformationType or "CLIP",
                lower_quantile=req.lowerQuantile or 0.01,
                upper_quantile=req.upperQuantile or 0.99
            )
        elif req.interventionType == "MISSING_VALUE_STRESS":
            df_trans, prov = inject_missingness_stress(
                df, req.feature,
                rate=req.missingnessRate or 0.10,
                seed=req.deterministicSeed or 42
            )
        else:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Unsupported intervention type: {req.interventionType}"
            )

        # Write candidate dataset file next to original with unique ID
        dir_name = os.path.dirname(req.datasetPath)
        base_name = os.path.basename(req.datasetPath)
        cand_id = f"cand_exp_{uuid.uuid4().hex[:8]}"
        out_filename = f"{cand_id}_{base_name}"
        out_path = os.path.join(dir_name, out_filename)

        if req.datasetPath.endswith(".parquet") or req.datasetPath.endswith(".pq"):
            df_trans.to_parquet(out_path, index=False)
        else:
            df_trans.to_csv(out_path, index=False)

        return {
            "success": True,
            "candidateDatasetPath": out_path,
            "originalDatasetPath": req.datasetPath,
            "provenance": prov,
            "rowCount": int(len(df_trans)),
            "columnCount": int(df_trans.shape[1])
        }

    except ValueError as ve:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=str(ve)
        )
    except Exception as ex:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Intervention application failed: {str(ex)}"
        )

@router.post("/statistical-comparison")
def statistical_comparison(req: StatisticalComparisonRequest) -> Dict[str, Any]:
    try:
        results = compute_paired_comparison_statistics(
            y_true=np.array(req.yTrue),
            baseline_preds=np.array(req.baselinePreds),
            candidate_preds=np.array(req.candidatePreds),
            baseline_probs=np.array(req.baselineProbs) if req.baselineProbs else None,
            candidate_probs=np.array(req.candidateProbs) if req.candidateProbs else None,
            seed=req.deterministicSeed or 42
        )
        return results
    except Exception as ex:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Statistical calculation failed: {str(ex)}"
        )

@router.post("/threshold-counterfactual")
def threshold_counterfactual(req: ThresholdCounterfactualRequest) -> Dict[str, Any]:
    try:
        results = evaluate_threshold_grid(
            y_true=np.array(req.yTrue),
            y_prob=np.array(req.yProb),
            baseline_threshold=req.baselineThreshold,
            candidate_threshold=req.candidateThreshold
        )
        return results
    except Exception as ex:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Threshold evaluation failed: {str(ex)}"
        )

@router.post("/calibration-counterfactual")
def calibration_counterfactual(req: CalibrationCounterfactualRequest) -> Dict[str, Any]:
    try:
        results = evaluate_calibration_counterfactual(
            y_eval_true=np.array(req.yEvalTrue),
            y_eval_prob=np.array(req.yEvalProb),
            y_calib_true=np.array(req.yCalibTrue) if req.yCalibTrue else None,
            y_calib_prob=np.array(req.yCalibProb) if req.yCalibProb else None,
            method=req.method
        )
        return results
    except Exception as ex:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Calibration evaluation failed: {str(ex)}"
        )

@router.post("/subgroup-counterfactual")
def subgroup_counterfactual(req: SubgroupCounterfactualRequest) -> Dict[str, Any]:
    try:
        results = evaluate_subgroup_counterfactual(
            subgroups=np.array(req.subgroups),
            y_true=np.array(req.yTrue),
            baseline_preds=np.array(req.baselinePreds),
            candidate_preds=np.array(req.candidatePreds)
        )
        return results
    except Exception as ex:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Subgroup evaluation failed: {str(ex)}"
        )
