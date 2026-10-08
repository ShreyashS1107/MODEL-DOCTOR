import pytest
import numpy as np
import pandas as pd
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

def test_feature_ablation():
    df = pd.DataFrame({
        "feature_a": [1.0, 2.0, 3.0, 4.0],
        "feature_b": [10.0, 20.0, 30.0, 40.0]
    })
    
    # 1. Zero strategy
    df_zero, prov_zero = apply_feature_ablation(df, "feature_a", strategy="zero")
    assert (df_zero["feature_a"] == 0.0).all()
    assert prov_zero["ablation_strategy"] == "zero"
    assert prov_zero["candidate_feature_count"] == 2

    # 2. Drop strategy
    df_drop, prov_drop = apply_feature_ablation(df, "feature_a", strategy="drop")
    assert "feature_a" not in df_drop.columns
    assert prov_drop["candidate_feature_count"] == 1

def test_feature_transformation_clip():
    df = pd.DataFrame({
        "val": [1.0, 2.0, 3.0, 4.0, 100.0]
    })
    df_trans, prov = apply_feature_transformation(
        df, "val", transformation_type="CLIP", lower_quantile=0.10, upper_quantile=0.80
    )
    assert df_trans["val"].max() < 100.0
    assert prov["parameters"]["clippedCount"] > 0

def test_missingness_stress_determinism():
    df = pd.DataFrame({
        "num": list(range(100))
    })
    df_1, prov_1 = inject_missingness_stress(df, "num", rate=0.20, seed=42)
    df_2, prov_2 = inject_missingness_stress(df, "num", rate=0.20, seed=42)
    
    assert prov_1["injectedMissingCount"] == prov_2["injectedMissingCount"]
    assert df_1["num"].isna().equals(df_2["num"].isna())
    assert prov_1["injectedMissingCount"] > 0

def test_threshold_counterfactual():
    y_true = np.array([0, 0, 1, 1, 1, 0, 1, 0])
    y_prob = np.array([0.1, 0.2, 0.45, 0.8, 0.9, 0.4, 0.6, 0.15])
    
    res = evaluate_threshold_grid(y_true, y_prob, baseline_threshold=0.50, candidate_threshold=0.40)
    assert len(res["gridPoints"]) == 21
    assert "baselineMetrics" in res
    assert "candidateMetrics" in res
    assert "deltas" in res
    assert res["candidateMetrics"]["threshold"] == 0.40

def test_calibration_counterfactual_insufficient():
    y_true = np.array([0, 1, 0, 1])
    y_prob = np.array([0.2, 0.8, 0.3, 0.7])
    # Missing independent calibration split
    res = evaluate_calibration_counterfactual(y_true, y_prob, None, None)
    assert not res["executable"]
    assert "reason" in res

def test_calibration_counterfactual_with_split():
    rng = np.random.default_rng(42)
    y_calib_true = rng.integers(0, 2, 50)
    y_calib_prob = rng.uniform(0.1, 0.9, 50)
    y_eval_true = rng.integers(0, 2, 50)
    y_eval_prob = rng.uniform(0.1, 0.9, 50)
    
    res = evaluate_calibration_counterfactual(y_eval_true, y_eval_prob, y_calib_true, y_calib_prob, method="PLATT")
    assert res["executable"]
    assert "baselineEce" in res
    assert "candidateEce" in res

def test_subgroup_counterfactual():
    subgroups = np.array(["A", "A", "B", "B", "B", "A"])
    y_true = np.array([0, 1, 0, 1, 1, 0])
    base_preds = np.array([0, 1, 1, 1, 0, 0])
    cand_preds = np.array([0, 1, 0, 1, 1, 0])
    
    res = evaluate_subgroup_counterfactual(subgroups, y_true, base_preds, cand_preds)
    assert res["totalGroups"] == 2
    assert len(res["subgroups"]) == 2

def test_paired_comparison_and_mcnemar():
    y_true = np.array([0, 1, 0, 1, 0, 1, 0, 1, 0, 1])
    base_preds = np.array([0, 0, 0, 1, 0, 1, 0, 0, 0, 1])
    cand_preds = np.array([0, 1, 0, 1, 0, 1, 0, 1, 0, 1])
    base_probs = np.array([0.1, 0.4, 0.2, 0.8, 0.1, 0.7, 0.2, 0.3, 0.1, 0.9])
    cand_probs = np.array([0.1, 0.6, 0.2, 0.8, 0.1, 0.7, 0.2, 0.6, 0.1, 0.9])
    
    stats = compute_paired_comparison_statistics(
        y_true, base_preds, cand_preds, base_probs, cand_probs, seed=42
    )
    assert stats["sampleSize"] == 10
    assert stats["changedPredictionsCount"] == 2
    assert stats["predictionFlipRate"] == 0.2
    assert "contingencyTable" in stats
    assert "mcNemarStatistic" in stats
    assert "probShiftCiLower" in stats
    assert "probShiftCiUpper" in stats
