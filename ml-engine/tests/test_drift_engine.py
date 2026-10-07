import asyncio
import numpy as np
import pandas as pd
import pytest
from app.diagnostics.drift.engine import DataDriftEngine
from app.schemas.diagnostic_models import DiagnosticCategory, SeverityLevel


def test_numeric_no_drift_fixture():
    """
    Test continuous feature sampled from identical deterministic Gaussian distribution.
    Expects low KS statistic, low PSI, low Wasserstein, and non-significant adjusted p-value.
    """
    rng = np.random.RandomState(42)
    n = 2000
    b_df = pd.DataFrame({
        "num_feature": rng.normal(100, 15, size=n),
        "target": rng.binomial(1, 0.1, size=n),
    })
    e_df = pd.DataFrame({
        "num_feature": rng.normal(100, 15, size=n),
        "target": rng.binomial(1, 0.1, size=n),
    })

    engine = DataDriftEngine()
    report = asyncio.run(
        engine.run_diagnostic(
            current_data=e_df,
            target_column="target",
            baseline_data=b_df,
        )
    )

    assert report.category == DiagnosticCategory.DRIFT
    feat_res = next(f for f in report.metadata["features"] if f["feature"] == "num_feature")
    assert feat_res["type"] == "NUMERIC"
    assert feat_res["ks"]["statistic"] < 0.10
    assert feat_res["psi"] < 0.10
    assert feat_res["practical_drift"] == "LOW"
    assert feat_res["severity"] == "LOW"
    assert feat_res["statistically_significant"] is False


def test_numeric_drift_fixture():
    """
    Test continuous feature with deliberate distribution shift (mean and scale change).
    Expects elevated KS statistic, elevated PSI, elevated Wasserstein, and HIGH severity.
    """
    rng = np.random.RandomState(42)
    n = 2000
    b_df = pd.DataFrame({
        "income": rng.normal(50000, 10000, size=n),
        "target": rng.binomial(1, 0.1, size=n),
    })
    e_df = pd.DataFrame({
        "income": rng.normal(85000, 20000, size=n),  # Significant shift
        "target": rng.binomial(1, 0.1, size=n),
    })

    engine = DataDriftEngine()
    report = asyncio.run(
        engine.run_diagnostic(
            current_data=e_df,
            target_column="target",
            baseline_data=b_df,
        )
    )

    feat_res = next(f for f in report.metadata["features"] if f["feature"] == "income")
    assert feat_res["type"] == "NUMERIC"
    assert feat_res["ks"]["statistic"] > 0.40
    assert feat_res["psi"] > 0.25
    assert feat_res["wasserstein"]["distance"] > 20000
    assert feat_res["wasserstein"]["normalized_distance"] is not None
    assert feat_res["wasserstein"]["normalization"] == "baseline_iqr"
    assert feat_res["statistically_significant"] is True
    assert feat_res["practical_drift"] == "HIGH"
    assert feat_res["severity"] in ["HIGH", "CRITICAL"]

    # Verify structured finding
    assert any(i.id.startswith("HIGH_NUMERIC_DRIFT_income") for i in report.issues)


def test_categorical_no_drift_fixture():
    """
    Test categorical feature with identical category proportions.
    Expects low PSI, non-significant Chi-Square, and LOW severity.
    """
    rng = np.random.RandomState(42)
    n = 2000
    cats = ["US", "EU", "APAC", "LATAM"]
    probs = [0.50, 0.30, 0.15, 0.05]

    b_df = pd.DataFrame({
        "region": rng.choice(cats, p=probs, size=n),
        "target": [0] * n,
    })
    e_df = pd.DataFrame({
        "region": rng.choice(cats, p=probs, size=n),
        "target": [0] * n,
    })

    engine = DataDriftEngine()
    report = asyncio.run(
        engine.run_diagnostic(
            current_data=e_df,
            target_column="target",
            baseline_data=b_df,
        )
    )

    feat_res = next(f for f in report.metadata["features"] if f["feature"] == "region")
    assert feat_res["type"] == "CATEGORICAL"
    assert feat_res["psi"] < 0.10
    assert feat_res["practical_drift"] == "LOW"
    assert feat_res["severity"] == "LOW"


def test_categorical_drift_fixture():
    """
    Test categorical feature with deliberately shifted category proportions and new category.
    Expects elevated PSI, significant Chi-Square, and HIGH severity.
    """
    rng = np.random.RandomState(42)
    n = 2000
    b_df = pd.DataFrame({
        "channel": rng.choice(["WEB", "MOBILE", "PARTNER"], p=[0.70, 0.25, 0.05], size=n),
        "target": [0] * n,
    })
    e_df = pd.DataFrame({
        "channel": rng.choice(["WEB", "MOBILE", "PARTNER", "API_NEW"], p=[0.20, 0.40, 0.10, 0.30], size=n),
        "target": [0] * n,
    })

    engine = DataDriftEngine()
    report = asyncio.run(
        engine.run_diagnostic(
            current_data=e_df,
            target_column="target",
            baseline_data=b_df,
        )
    )

    feat_res = next(f for f in report.metadata["features"] if f["feature"] == "channel")
    assert feat_res["type"] == "CATEGORICAL"
    assert feat_res["psi"] > 0.25
    assert feat_res["chi_square"]["statistic"] > 50.0
    assert feat_res["chi_square"]["adjusted_p_value"] < 0.001
    assert feat_res["statistically_significant"] is True
    assert feat_res["practical_drift"] == "HIGH"
    assert feat_res["severity"] in ["HIGH", "CRITICAL"]

    # Verify category-level details contain all categories including newly introduced API_NEW
    cat_dist = feat_res["categoryDistribution"]
    assert len(cat_dist) == 4
    api_new_entry = next(c for c in cat_dist if c["category"] == "API_NEW")
    assert api_new_entry["baselineCount"] == 0
    assert api_new_entry["evaluationCount"] > 0


def test_schema_drift_detection():
    """
    Test detection of:
    - Missing column in evaluation
    - New column in evaluation
    - Data type mismatch between baseline and evaluation
    """
    b_df = pd.DataFrame({
        "col_common": [1.0, 2.0, 3.0, 4.0] * 100,
        "col_only_baseline": [10, 20, 30, 40] * 100,
        "col_type_mismatch": [100.5, 200.5, 300.5, 400.5] * 100,  # Float Numeric
        "target": [0, 1, 0, 1] * 100,
    })
    e_df = pd.DataFrame({
        "col_common": [1.0, 2.0, 3.0, 4.0] * 100,
        "col_only_eval": ["A", "B", "C", "D"] * 100,
        "col_type_mismatch": ["HIGH", "LOW", "MEDIUM", "HIGH"] * 100,  # String Categorical
        "target": [0, 1, 0, 1] * 100,
    })

    engine = DataDriftEngine()
    report = asyncio.run(
        engine.run_diagnostic(
            current_data=e_df,
            target_column="target",
            baseline_data=b_df,
        )
    )

    schema = report.metadata["schema"]
    assert "col_only_baseline" in schema["missingInEvaluation"]
    assert "col_only_eval" in schema["newInEvaluation"]
    assert len(schema["dtypeDifferences"]) == 1
    assert schema["dtypeDifferences"][0]["column"] == "col_type_mismatch"

    # Verify schema findings
    issue_ids = [i.id for i in report.issues]
    assert "SCHEMA_MISSING_IN_EVALUATION" in issue_ids
    assert "SCHEMA_NEW_IN_EVALUATION" in issue_ids
    assert "SCHEMA_TYPE_MISMATCH" in issue_ids


def test_missing_and_non_finite_values_robustness():
    """
    Test that NaN, +Infinity, -Infinity, and small subsets do not crash the engine.
    """
    b_df = pd.DataFrame({
        "col_inf": [1.0, np.inf, 2.5, -np.inf, np.nan, 3.0, 4.5, 5.0, 6.0, 7.0] * 50,
        "col_all_nan": [np.nan] * 500,
        "target": [0, 1] * 250,
    })
    e_df = pd.DataFrame({
        "col_inf": [1.2, 2.3, np.nan, 3.1, 4.0, 5.5, np.inf, 6.2, 7.1, 8.0] * 50,
        "col_all_nan": [np.nan] * 500,
        "target": [0, 1] * 250,
    })

    engine = DataDriftEngine()
    report = asyncio.run(
        engine.run_diagnostic(
            current_data=e_df,
            target_column="target",
            baseline_data=b_df,
        )
    )

    assert report.metadata["module"] == "DRIFT"
    inf_feat = next(f for f in report.metadata["features"] if f["feature"] == "col_inf")
    assert inf_feat["status"] == "EVALUATED"
    assert inf_feat["ks"]["statistic"] >= 0.0

    nan_feat = next(f for f in report.metadata["features"] if f["feature"] == "col_all_nan")
    assert nan_feat["status"] == "UNTESTABLE"
    assert any(i.id == "FEATURE_UNTESTABLE_col_all_nan" for i in report.issues)


def test_constant_feature_handling():
    """
    Test zero-variance continuous feature in baseline and evaluation.
    Should handle zero IQR, zero std gracefully without dividing by zero.
    """
    n = 1000
    b_df = pd.DataFrame({
        "constant_col": [42.0] * n,
        "target": [0] * n,
    })
    e_df = pd.DataFrame({
        "constant_col": [42.0] * n,
        "target": [0] * n,
    })

    engine = DataDriftEngine()
    report = asyncio.run(
        engine.run_diagnostic(
            current_data=e_df,
            target_column="target",
            baseline_data=b_df,
        )
    )

    feat_res = next(f for f in report.metadata["features"] if f["feature"] == "constant_col")
    assert feat_res["ks"]["statistic"] == 0.0
    assert feat_res["psi"] == 0.0
    assert feat_res["wasserstein"]["distance"] == 0.0
    assert feat_res["wasserstein"]["normalized_distance"] is None
    assert feat_res["wasserstein"]["normalization"] == "unavailable_zero_variance"


def test_multiple_testing_fdr_correction():
    """
    Test that Benjamini-Hochberg False Discovery Rate correction is deterministically applied.
    """
    rng = np.random.RandomState(42)
    n = 1000
    # Create 5 numeric features: 3 with no drift, 2 with massive drift
    b_df = pd.DataFrame({
        "feat_no_1": rng.normal(0, 1, size=n),
        "feat_no_2": rng.normal(10, 2, size=n),
        "feat_no_3": rng.normal(50, 5, size=n),
        "feat_drift_1": rng.normal(0, 1, size=n),
        "feat_drift_2": rng.normal(10, 2, size=n),
        "target": [0] * n,
    })
    e_df = pd.DataFrame({
        "feat_no_1": rng.normal(0, 1, size=n),
        "feat_no_2": rng.normal(10, 2, size=n),
        "feat_no_3": rng.normal(50, 5, size=n),
        "feat_drift_1": rng.normal(5, 1, size=n),     # Shifted mean
        "feat_drift_2": rng.normal(50, 10, size=n),   # Shifted mean and variance
        "target": [0] * n,
    })

    engine = DataDriftEngine()
    report = asyncio.run(
        engine.run_diagnostic(
            current_data=e_df,
            target_column="target",
            baseline_data=b_df,
        )
    )

    for f in report.metadata["features"]:
        if f["type"] == "NUMERIC":
            ks_info = f["ks"]
            assert "raw_p_value" in ks_info or "p_value" in ks_info
            assert "adjusted_p_value" in ks_info
            assert ks_info["correction_method"] == "benjamini_hochberg"
            # Adjusted p-value should be >= raw p-value under Benjamini-Hochberg
            assert ks_info["adjusted_p_value"] >= ks_info["p_value"] - 1e-9


def test_baseline_dataset_missing_error():
    """
    Test that running drift without a baseline dataset produces a clear CRITICAL report.
    """
    e_df = pd.DataFrame({"a": [1, 2, 3], "target": [0, 1, 0]})
    engine = DataDriftEngine()
    report = asyncio.run(
        engine.run_diagnostic(
            current_data=e_df,
            target_column="target",
            baseline_data=None,
        )
    )

    assert report.passed is False
    assert report.health_score == 0.0
    assert any(i.id == "DRIFT-NO-BASELINE" for i in report.issues)
