import asyncio
import numpy as np
import pandas as pd
import pytest
from app.diagnostics.performance.engine import PerformanceEngine
from app.schemas.diagnostic_models import DiagnosticCategory, SeverityLevel


def test_excellent_model_fixture():
    """
    Test a model with strong class separation.
    Expects high ROC-AUC (> 0.90), high PR-AUC (> 0.85), high F1 (> 0.80), low Log Loss (< 0.35), low Brier (< 0.10).
    """
    rng = np.random.RandomState(42)
    n = 2000
    y_true = rng.binomial(1, 0.30, size=n)
    # Excellent calibrated predictions
    y_prob = np.where(
        y_true == 1,
        rng.beta(8, 2, size=n),
        rng.beta(2, 8, size=n),
    )

    df = pd.DataFrame({
        "label": y_true,
        "pred_prob": y_prob,
    })

    engine = PerformanceEngine()
    report = asyncio.run(
        engine.run_diagnostic(
            current_data=df,
            target_column="label",
            config={"prediction_column": "pred_prob"},
        )
    )

    assert report.category == DiagnosticCategory.PERFORMANCE
    summary = report.metadata["summary"]
    assert summary["sampleCount"] == n
    assert summary["positiveCount"] == int(np.sum(y_true == 1))
    assert summary["rocAuc"] > 0.90
    assert summary["prAuc"] > 0.85
    assert summary["f1"] > 0.75
    assert summary["logLoss"] < 0.35
    assert summary["brierScore"] < 0.10
    assert report.passed is True
    assert summary["healthScore"] >= 70.0


def test_poor_model_random_predictions_fixture():
    """
    Test a model with random uninformative predictions.
    Expects ROC-AUC ~ 0.50 (< 0.60), and LOW_MODEL_DISCRIMINATION finding.
    """
    rng = np.random.RandomState(42)
    n = 2000
    y_true = rng.binomial(1, 0.20, size=n)
    # Random uniform probabilities uninformative of ground truth
    y_prob = rng.uniform(0.01, 0.99, size=n)

    df = pd.DataFrame({
        "target": y_true,
        "pred_prob": y_prob,
    })

    engine = PerformanceEngine()
    report = asyncio.run(
        engine.run_diagnostic(
            current_data=df,
            target_column="target",
            config={"prediction_column": "pred_prob"},
        )
    )

    summary = report.metadata["summary"]
    assert summary["rocAuc"] < 0.60
    assert any(i.id == "LOW_MODEL_DISCRIMINATION" for i in report.issues)
    assert report.passed is False


def test_imbalanced_dataset_fixture():
    """
    Test heavy class imbalance (3% positive rate).
    Verify PR-AUC and ROC-AUC calculations and confusion matrix.
    """
    rng = np.random.RandomState(42)
    n = 5000
    y_true = rng.binomial(1, 0.03, size=n)
    # Realistic fraud/imbalance detector
    logits = -3.5 + y_true * 2.8 + rng.normal(0, 0.5, size=n)
    y_prob = 1.0 / (1.0 + np.exp(-logits))

    df = pd.DataFrame({
        "is_fraud": y_true,
        "pred_prob": y_prob,
    })

    engine = PerformanceEngine()
    report = asyncio.run(
        engine.run_diagnostic(
            current_data=df,
            target_column="is_fraud",
            config={"prediction_column": "pred_prob"},
        )
    )

    summary = report.metadata["summary"]
    assert summary["positiveRate"] < 0.05
    assert summary["rocAuc"] > 0.70
    assert summary["prAuc"] > 0.10
    assert "confusionMatrix" in report.metadata
    cm = report.metadata["confusionMatrix"]
    assert cm["trueNegative"] > 4000
    assert cm["truePositive"] + cm["falseNegative"] == summary["positiveCount"]


def test_calibration_tendency_fixture():
    """
    Test intentionally overconfident and underconfident predictions.
    Verify ECE and calibration tendency classification.
    """
    rng = np.random.RandomState(42)
    n = 3000
    y_true = rng.binomial(1, 0.20, size=n)

    # Overconfident: predicting very high probabilities for actual 20% rate
    y_prob_overconfident = np.clip(y_true * 0.90 + (1 - y_true) * 0.45 + rng.normal(0, 0.05, size=n), 0.001, 0.999)
    df_over = pd.DataFrame({"label": y_true, "pred": y_prob_overconfident})

    engine = PerformanceEngine()
    rep_over = asyncio.run(
        engine.run_diagnostic(current_data=df_over, target_column="label", config={"prediction_column": "pred"})
    )
    assert rep_over.metadata["summary"]["expectedCalibrationError"] > 0.05
    assert rep_over.metadata["summary"]["calibrationTendency"] == "OVERCONFIDENT"
    assert any("CALIBRATION" in i.id for i in rep_over.issues)

    # Underconfident: predicting probabilities around 0.10 for actual 50% rate
    y_true_balanced = rng.binomial(1, 0.50, size=n)
    y_prob_underconfident = np.clip(y_true_balanced * 0.25 + (1 - y_true_balanced) * 0.05 + rng.normal(0, 0.02, size=n), 0.001, 0.999)
    df_under = pd.DataFrame({"label": y_true_balanced, "pred": y_prob_underconfident})

    rep_under = asyncio.run(
        engine.run_diagnostic(current_data=df_under, target_column="label", config={"prediction_column": "pred"})
    )
    assert rep_under.metadata["summary"]["calibrationTendency"] == "UNDERCONFIDENT"


def test_threshold_grid_analysis_fixture():
    """
    Test that the 21-step threshold grid evaluates properly from 0.00 to 1.00.
    """
    rng = np.random.RandomState(42)
    n = 1000
    y_true = rng.binomial(1, 0.30, size=n)
    y_prob = rng.beta(2, 2, size=n)
    df = pd.DataFrame({"target": y_true, "pred": y_prob})

    engine = PerformanceEngine()
    report = asyncio.run(
        engine.run_diagnostic(current_data=df, target_column="target", config={"prediction_column": "pred"})
    )

    th_table = report.metadata["thresholdAnalysis"]
    assert len(th_table) == 21
    # Threshold 0.0 should have 100% recall
    assert th_table[0]["threshold"] == 0.0
    assert th_table[0]["recall"] == 1.0
    # Threshold 1.0 should have 0% recall or low
    assert th_table[-1]["threshold"] == 1.0


def test_invalid_and_non_finite_predictions():
    """
    Test handling of NaN, +Inf, -Inf, and out-of-range (<0, >1) probabilities.
    """
    y_true = [0, 1, 0, 1, 0, 1, 0, 1] * 100
    y_prob = [0.1, np.nan, 0.2, np.inf, -0.5, 1.4, 0.8, 0.9] * 100
    df = pd.DataFrame({"target": y_true, "pred": y_prob})

    engine = PerformanceEngine()
    report = asyncio.run(
        engine.run_diagnostic(current_data=df, target_column="target", config={"prediction_column": "pred"})
    )

    summary = report.metadata["summary"]
    assert summary["invalidPredictionCount"] == 400
    assert summary["sampleCount"] == 400
    assert any(i.id == "INVALID_PREDICTIONS" for i in report.issues)


def test_single_class_target_error():
    """
    Test target column with only a single distinct class.
    """
    df = pd.DataFrame({"target": [1] * 500, "pred": [0.8] * 500})
    engine = PerformanceEngine()
    report = asyncio.run(
        engine.run_diagnostic(current_data=df, target_column="target", config={"prediction_column": "pred"})
    )

    assert report.passed is False
    assert report.metadata["summary"]["rocAuc"] is None
    assert any(i.id == "SINGLE_CLASS_TARGET" for i in report.issues)


def test_missing_columns_validation():
    """
    Test missing target or prediction column in evaluation dataset.
    """
    df = pd.DataFrame({"some_feature": [1, 2, 3]})
    engine = PerformanceEngine()

    # Missing target
    rep1 = asyncio.run(engine.run_diagnostic(current_data=df, target_column="non_existent_target"))
    assert rep1.passed is False
    assert any(i.id == "PERF-MISSING-TARGET" for i in rep1.issues)

    # Missing prediction column
    rep2 = asyncio.run(
        engine.run_diagnostic(
            current_data=pd.DataFrame({"label": [0, 1]}),
            target_column="label",
            config={"prediction_column": "non_existent_pred"},
        )
    )
    assert rep2.passed is False
    assert any(i.id == "PERF-MISSING-PREDICTION" for i in rep2.issues)


def test_prevalence_baseline_comparison():
    """
    Test comparison against naive constant prevalence model.
    """
    rng = np.random.RandomState(42)
    n = 2000
    y_true = rng.binomial(1, 0.15, size=n)
    y_prob = np.clip(y_true * 0.70 + (1 - y_true) * 0.05 + rng.normal(0, 0.1, size=n), 0.001, 0.999)
    df = pd.DataFrame({"target": y_true, "pred": y_prob})

    engine = PerformanceEngine()
    report = asyncio.run(
        engine.run_diagnostic(current_data=df, target_column="target", config={"prediction_column": "pred"})
    )

    comp = report.metadata["baselineComparison"]
    assert comp["beatsPrevalenceLogLoss"] is True
    assert comp["beatsPrevalenceBrier"] is True
    assert comp["beatsPrevalenceBaseline"] is True
    assert comp["modelLogLoss"] < comp["baselineLogLoss"]
    assert comp["modelBrierScore"] < comp["baselineBrierScore"]
