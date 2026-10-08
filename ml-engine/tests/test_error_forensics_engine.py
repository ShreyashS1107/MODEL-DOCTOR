import pytest
import numpy as np
import pandas as pd
from app.diagnostics.error_forensics.engine import ErrorForensicsEngine


@pytest.fixture
def synthetic_error_data():
    np.random.seed(42)
    n = 200
    y_true = np.random.choice([0, 1], size=n, p=[0.6, 0.4])
    # Feature 1: Highly correlated with errors
    feat_err = np.random.normal(0, 1, n)
    # Predictions: generally good, but with intentional high-confidence mistakes
    y_prob = np.clip(y_true * 0.7 + np.random.uniform(0.05, 0.25, n), 0.01, 0.99)
    # Inject 15 false positives with high confidence
    fp_idx = np.where(y_true == 0)[0][:15]
    y_prob[fp_idx] = 0.85
    feat_err[fp_idx] = 3.5  # Distinct feature separation in FPs

    # Inject 10 false negatives with high confidence
    fn_idx = np.where(y_true == 1)[0][:10]
    y_prob[fn_idx] = 0.15
    feat_err[fn_idx] = -3.5  # Distinct feature separation in FNs

    subgroups = np.random.choice(["Region_A", "Region_B"], size=n, p=[0.7, 0.3])
    # Give Region_B a higher error rate
    reg_b_idx = np.where(subgroups == "Region_B")[0]
    y_prob[reg_b_idx[:8]] = 1.0 - y_prob[reg_b_idx[:8]]

    df = pd.DataFrame({
        "feature_err": feat_err,
        "feature_norm": np.random.normal(10, 2, n),
        "category_col": np.random.choice(["cat1", "cat2", "cat3"], size=n),
        "region": subgroups,
        "label": y_true,
        "pred_prob": y_prob,
    })
    return df


@pytest.mark.anyio
async def test_error_forensics_basic_execution(synthetic_error_data):
    engine = ErrorForensicsEngine()
    report = await engine.run_diagnostic(
        current_data=synthetic_error_data,
        target_column="label",
        config={"prediction_column": "pred_prob", "protected_attribute": "region"},
    )

    assert report is not None
    assert report.metadata["module"] == "ERROR_FORENSICS"
    assert report.metadata["sampleCount"] == len(synthetic_error_data)

    summary = report.metadata["summary"]
    assert "totalErrors" in summary
    assert "overallErrorRate" in summary
    assert "highConfidenceErrorCount" in summary
    assert summary["totalErrors"] > 0


@pytest.mark.anyio
async def test_error_distribution_metrics(synthetic_error_data):
    engine = ErrorForensicsEngine()
    report = await engine.run_diagnostic(
        current_data=synthetic_error_data,
        target_column="label",
        config={"prediction_column": "pred_prob"},
    )

    err_sum = report.metadata["errorSummary"]
    tp = err_sum["truePositive"]["count"]
    tn = err_sum["trueNegative"]["count"]
    fp = err_sum["falsePositive"]["count"]
    fn = err_sum["falseNegative"]["count"]
    total = tp + tn + fp + fn

    assert total == len(synthetic_error_data)
    assert fp > 0
    assert fn > 0
    assert err_sum["totalErrors"] == fp + fn
    assert round(err_sum["accuracy"], 4) == round((tp + tn) / total, 4)


@pytest.mark.anyio
async def test_confidence_error_analysis(synthetic_error_data):
    engine = ErrorForensicsEngine()
    report = await engine.run_diagnostic(
        current_data=synthetic_error_data,
        target_column="label",
        config={"prediction_column": "pred_prob"},
    )

    conf_anal = report.metadata["confidenceAnalysis"]
    assert "meanErrorConfidence" in conf_anal
    assert "medianErrorConfidence" in conf_anal
    assert "p90ErrorConfidence" in conf_anal
    assert "highConfidenceErrorCount" in conf_anal
    assert conf_anal["highConfidenceErrorCount"] > 0
    assert len(conf_anal["confidenceBands"]) == 4


@pytest.mark.anyio
async def test_false_positive_and_negative_forensics(synthetic_error_data):
    engine = ErrorForensicsEngine()
    report = await engine.run_diagnostic(
        current_data=synthetic_error_data,
        target_column="label",
        config={"prediction_column": "pred_prob"},
    )

    fp_anal = report.metadata["falsePositiveAnalysis"]
    fn_anal = report.metadata["falseNegativeAnalysis"]

    assert fp_anal["count"] > 0
    assert fn_anal["count"] > 0
    assert len(fp_anal["topFeatureDifferences"]) > 0
    assert len(fn_anal["topFeatureDifferences"]) > 0

    # feature_err should be the top differentiating feature for both FP and FN
    top_fp_feat = fp_anal["topFeatureDifferences"][0]
    assert top_fp_feat["feature"] == "feature_err"
    assert "standardizedDifference" in top_fp_feat
    assert "adjustedPValue" in top_fp_feat


@pytest.mark.anyio
async def test_feature_error_associations(synthetic_error_data):
    engine = ErrorForensicsEngine()
    report = await engine.run_diagnostic(
        current_data=synthetic_error_data,
        target_column="label",
        config={"prediction_column": "pred_prob"},
    )

    assocs = report.metadata["featureAssociations"]
    assert len(assocs) > 0

    top_assoc = assocs[0]
    assert top_assoc["feature"] == "feature_err"
    assert "statistic" in top_assoc
    assert "adjustedPValue" in top_assoc
    assert top_assoc["absoluteAssociation"] > 0.15


@pytest.mark.anyio
async def test_quantile_feature_ranges(synthetic_error_data):
    engine = ErrorForensicsEngine()
    report = await engine.run_diagnostic(
        current_data=synthetic_error_data,
        target_column="label",
        config={"prediction_column": "pred_prob"},
    )

    ranges = report.metadata["featureRanges"]
    assert len(ranges) > 0

    for r in ranges:
        assert "rangeDisplay" in r
        assert "sampleCount" in r
        assert "errorRate" in r
        assert "isErrorEnriched" in r


@pytest.mark.anyio
async def test_bounded_high_priority_error_records(synthetic_error_data):
    engine = ErrorForensicsEngine()
    report = await engine.run_diagnostic(
        current_data=synthetic_error_data,
        target_column="label",
        config={"prediction_column": "pred_prob", "max_error_records": 10},
    )

    records = report.metadata["highConfidenceErrors"]
    assert len(records) <= 10
    assert len(records) > 0

    # Ensure deterministic priority ordering descending
    prev_priority = 999999
    for rec in records:
        assert "rowIndex" in rec
        assert "actual" in rec
        assert "predicted" in rec
        assert "probability" in rec
        assert "confidence" in rec
        assert "priorityScore" in rec
        assert "keyFeatures" in rec
        assert rec["priorityScore"] <= prev_priority
        prev_priority = rec["priorityScore"]


@pytest.mark.anyio
async def test_threshold_forensics(synthetic_error_data):
    engine = ErrorForensicsEngine()
    report = await engine.run_diagnostic(
        current_data=synthetic_error_data,
        target_column="label",
        config={"prediction_column": "pred_prob"},
    )

    grid = report.metadata["thresholdAnalysis"]
    assert len(grid) == 21
    assert grid[0]["threshold"] == 0.0
    assert grid[-1]["threshold"] == 1.0


@pytest.mark.anyio
async def test_calibration_error_forensics(synthetic_error_data):
    engine = ErrorForensicsEngine()
    report = await engine.run_diagnostic(
        current_data=synthetic_error_data,
        target_column="label",
        config={"prediction_column": "pred_prob"},
    )

    cal = report.metadata["calibrationForensics"]
    assert "expectedCalibrationError" in cal
    assert len(cal["bins"]) > 0


@pytest.mark.anyio
async def test_subgroup_error_forensics(synthetic_error_data):
    engine = ErrorForensicsEngine()
    report = await engine.run_diagnostic(
        current_data=synthetic_error_data,
        target_column="label",
        config={"prediction_column": "pred_prob", "protected_attribute": "region"},
    )

    subgroups = report.metadata["subgroupAnalysis"]
    assert len(subgroups) == 2
    for sg in subgroups:
        assert "subgroup" in sg
        assert "errorRate" in sg
        assert "errorRateConfidenceInterval" in sg
        assert "lower" in sg["errorRateConfidenceInterval"]
        assert "upper" in sg["errorRateConfidenceInterval"]


@pytest.mark.anyio
async def test_zero_errors_dataset():
    # 100% accurate predictions
    n = 50
    df = pd.DataFrame({
        "feature_1": np.random.normal(0, 1, n),
        "target": np.random.choice([0, 1], n),
    })
    df["pred_prob"] = np.where(df["target"] == 1, 0.95, 0.05)

    engine = ErrorForensicsEngine()
    report = await engine.run_diagnostic(
        current_data=df,
        target_column="target",
        config={"prediction_column": "pred_prob"},
    )

    assert report.metadata["summary"]["totalErrors"] == 0
    assert report.metadata["summary"]["overallErrorRate"] == 0.0
    assert report.metadata["confidenceAnalysis"]["highConfidenceErrorCount"] == 0
    assert report.passed is True


@pytest.mark.anyio
async def test_zero_fp_dataset():
    # Model only predicts 0, so FP=0, FN > 0
    n = 50
    df = pd.DataFrame({
        "feature_1": np.random.normal(0, 1, n),
        "target": [0]*30 + [1]*20,
        "pred_prob": [0.10] * 50,
    })

    engine = ErrorForensicsEngine()
    report = await engine.run_diagnostic(
        current_data=df,
        target_column="target",
        config={"prediction_column": "pred_prob"},
    )

    assert report.metadata["errorSummary"]["falsePositive"]["count"] == 0
    assert report.metadata["errorSummary"]["falseNegative"]["count"] == 20


@pytest.mark.anyio
async def test_zero_fn_dataset():
    # Model only predicts 1, so FN=0, FP > 0
    n = 50
    df = pd.DataFrame({
        "feature_1": np.random.normal(0, 1, n),
        "target": [0]*30 + [1]*20,
        "pred_prob": [0.90] * 50,
    })

    engine = ErrorForensicsEngine()
    report = await engine.run_diagnostic(
        current_data=df,
        target_column="target",
        config={"prediction_column": "pred_prob"},
    )

    assert report.metadata["errorSummary"]["falseNegative"]["count"] == 0
    assert report.metadata["errorSummary"]["falsePositive"]["count"] == 30


@pytest.mark.anyio
async def test_edge_cases_empty_missing_and_invalid():
    engine = ErrorForensicsEngine()

    # Empty df
    rep_empty = await engine.run_diagnostic(pd.DataFrame(), "target")
    assert rep_empty.passed is False
    assert rep_empty.issues[0].id == "ERR-EMPTY-DATASET"

    # Missing target
    df_no_target = pd.DataFrame({"col1": [1, 2, 3]})
    rep_no_target = await engine.run_diagnostic(df_no_target, "target")
    assert rep_no_target.passed is False
    assert rep_no_target.issues[0].id == "ERR-TARGET-MISSING"

    # Missing prediction column
    df_no_pred = pd.DataFrame({"target": [0, 1, 0, 1]})
    rep_no_pred = await engine.run_diagnostic(df_no_pred, "target")
    assert rep_no_pred.passed is False
    assert rep_no_pred.issues[0].id == "ERR-MISSING-PREDICTION"


@pytest.mark.anyio
async def test_determinism_and_reproducibility(synthetic_error_data):
    engine = ErrorForensicsEngine()

    report1 = await engine.run_diagnostic(
        current_data=synthetic_error_data,
        target_column="label",
        config={"prediction_column": "pred_prob", "protected_attribute": "region"},
    )

    report2 = await engine.run_diagnostic(
        current_data=synthetic_error_data,
        target_column="label",
        config={"prediction_column": "pred_prob", "protected_attribute": "region"},
    )

    assert report1.metadata["summary"] == report2.metadata["summary"]
    assert len(report1.metadata["highConfidenceErrors"]) == len(report2.metadata["highConfidenceErrors"])
    assert report1.metadata["highConfidenceErrors"][0]["rowIndex"] == report2.metadata["highConfidenceErrors"][0]["rowIndex"]
    assert report1.metadata["featureAssociations"] == report2.metadata["featureAssociations"]
