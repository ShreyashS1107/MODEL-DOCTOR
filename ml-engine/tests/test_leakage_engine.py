import asyncio
import pytest
import numpy as np
import pandas as pd

from app.diagnostics.leakage.engine import DataLeakageEngine
from app.schemas.diagnostic_models import DiagnosticCategory, SeverityLevel


@pytest.fixture
def deterministic_leakage_dataset() -> pd.DataFrame:
    """
    Creates a deterministic dataset with clear, verifiable leakage characteristics:
    - 500 samples, binary target 'is_fraud' (50% positive rate)
    - 'leaked_direct': direct proxy with 95% correlation with target
    - 'moderate_feature': moderate association with target (~0.40)
    - 'random_noise': independent Gaussian noise (near 0 MI and association)
    - 'constant_col': zero variance
    - 'cat_leaked': categorical column perfectly matching target class
    """
    rng = np.random.RandomState(42)
    n = 500

    is_fraud = np.array([1] * 250 + [0] * 250)

    # 1. Leaked direct feature (strong signal)
    leaked_direct = is_fraud * 0.95 + rng.normal(0, 0.05, size=n)

    # 2. Moderate feature
    moderate_feature = is_fraud * 0.45 + rng.normal(0, 0.5, size=n)

    # 3. Random noise feature
    random_noise = rng.normal(100, 15, size=n)

    # 4. Constant column
    constant_col = ["FIXED_SYS_ID"] * n

    # 5. Categorical leaked feature
    cat_leaked = ["FRAUD_FLAG_HIGH" if f == 1 else "FRAUD_FLAG_NONE" for f in is_fraud]

    return pd.DataFrame({
        "leaked_direct": leaked_direct,
        "moderate_feature": moderate_feature,
        "random_noise": random_noise,
        "constant_col": constant_col,
        "cat_leaked": cat_leaked,
        "is_fraud": is_fraud,
    })


def test_leakage_target_validation():
    engine = DataLeakageEngine()

    # 1. Missing target column
    df = pd.DataFrame({"feat_a": [1, 2, 3], "feat_b": [4, 5, 6]})
    report_missing = asyncio.run(engine.run_diagnostic(df, target_column="non_existent_target"))
    assert report_missing.passed is False
    assert report_missing.health_score == 0.0
    assert any("LEAK-TARGET-MISSING" in issue.id for issue in report_missing.issues)

    # 2. Constant zero-variance target
    df_const_target = pd.DataFrame({"feat_a": [1, 2, 3, 4, 5], "target": [1, 1, 1, 1, 1]})
    report_const = asyncio.run(engine.run_diagnostic(df_const_target, target_column="target"))
    assert report_const.passed is False
    assert report_const.health_score == 0.0
    assert any("LEAK-TARGET-VARIANCE" in issue.id for issue in report_const.issues)


def test_leakage_detection_and_ranking(deterministic_leakage_dataset):
    engine = DataLeakageEngine()
    report = asyncio.run(
        engine.run_diagnostic(
            current_data=deterministic_leakage_dataset,
            target_column="is_fraud",
        )
    )

    assert report.category == DiagnosticCategory.LEAKAGE
    metadata = report.metadata

    # Summary
    summary = metadata["summary"]
    assert summary["criticalLeakageCount"] >= 1
    assert summary["maxMutualInformation"] > 0.60
    assert summary["maxTargetAssociation"] > 0.85

    # Features ranking
    features = metadata["features"]
    assert len(features) == 5  # 5 candidate features

    # Check top feature is one of the leaked features
    top_feature = features[0]
    assert top_feature["name"] in ["leaked_direct", "cat_leaked"]
    assert top_feature["risk"] == "CRITICAL"
    assert top_feature["isPotentialProxy"] is True

    # Check random noise is LOW risk
    noise_feat = next(f for f in features if f["name"] == "random_noise")
    assert noise_feat["risk"] == "LOW"
    assert noise_feat["mutualInformation"] < 0.20

    # Findings
    assert any("LEAK-CRIT-leaked_direct" in issue.id for issue in report.issues)


def test_leakage_sample_contamination():
    engine = DataLeakageEngine()

    eval_df = pd.DataFrame({
        "feat_x": [10, 20, 30, 40, 50],
        "target": [0, 1, 0, 1, 0],
    })

    # Baseline with 3 identical rows (60% contamination)
    baseline_df = pd.DataFrame({
        "feat_x": [10, 20, 30, 99, 100],
        "target": [0, 1, 0, 1, 1],
    })

    report = asyncio.run(
        engine.run_diagnostic(
            current_data=eval_df,
            target_column="target",
            baseline_data=baseline_df,
        )
    )

    metadata = report.metadata
    assert metadata["summary"]["overlapRowCount"] == 3
    assert metadata["summary"]["overlapRowRate"] == 0.6
    assert any("LEAK-CONTAM-CRIT" in issue.id for issue in report.issues)


def test_leakage_regression_task():
    engine = DataLeakageEngine()

    n = 200
    rng = np.random.RandomState(42)
    y_continuous = rng.normal(50, 10, size=n)
    feat_leaked = y_continuous * 1.05 + rng.normal(0, 0.5, size=n)
    feat_unrelated = rng.uniform(0, 100, size=n)

    df = pd.DataFrame({
        "feat_leaked": feat_leaked,
        "feat_unrelated": feat_unrelated,
        "target_price": y_continuous,
    })

    report = asyncio.run(
        engine.run_diagnostic(
            current_data=df,
            target_column="target_price",
            config={"task_type": "regression"},
        )
    )

    assert report.metadata["target"]["taskType"] == "REGRESSION"
    features = {f["name"]: f for f in report.metadata["features"]}
    assert features["feat_leaked"]["risk"] == "CRITICAL"
    assert features["feat_leaked"]["targetAssociation"] > 0.90
