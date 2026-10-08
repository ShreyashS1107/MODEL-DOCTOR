from typing import List, Optional, Dict, Any
from fastapi import APIRouter
from pydantic import BaseModel, Field

from app.temporal.trends import analyze_metric_trend, mann_kendall_test
from app.temporal.change_points import detect_change_points
from app.temporal.associations import compute_temporal_lag_correlation

router = APIRouter(prefix="/temporal", tags=["temporal"])


class TrendAnalysisRequest(BaseModel):
    values: List[float] = Field(..., description="Chronological metric observations")
    timestamps: Optional[List[str]] = Field(None, description="Observation timestamps")
    higher_is_better: bool = Field(True, description="True if higher metric values represent improvement")
    min_samples: int = Field(3, description="Minimum samples required for trend slope")


class ChangePointRequest(BaseModel):
    values: List[float] = Field(..., description="Chronological metric observations")
    timestamps: Optional[List[str]] = None
    run_ids: Optional[List[str]] = None
    threshold_shift: float = 0.05


class TemporalAssociationRequest(BaseModel):
    series_a: List[float]
    series_b: List[float]
    signal_a_name: str = "signal_a"
    signal_b_name: str = "signal_b"
    max_lag: int = 2


@router.post("/trends")
def calculate_trends(request: TrendAnalysisRequest) -> Dict[str, Any]:
    """Calculate trend slope, direction, R², summary stats, and Mann-Kendall significance."""
    return analyze_metric_trend(
        values=request.values,
        timestamps=request.timestamps,
        higher_is_better=request.higher_is_better,
        min_samples=request.min_samples
    )


@router.post("/change-points")
def find_change_points(request: ChangePointRequest) -> List[Dict[str, Any]]:
    """Detect deterministic change points in time-series."""
    return detect_change_points(
        values=request.values,
        timestamps=request.timestamps,
        run_ids=request.run_ids,
        threshold_shift=request.threshold_shift
    )


@router.post("/associations")
def evaluate_temporal_associations(request: TemporalAssociationRequest) -> List[Dict[str, Any]]:
    """Evaluate temporal lag correlations between two aligned signals."""
    return compute_temporal_lag_correlation(
        series_a=request.series_a,
        series_b=request.series_b,
        signal_a_name=request.signal_a_name,
        signal_b_name=request.signal_b_name,
        max_lag=request.max_lag
    )
