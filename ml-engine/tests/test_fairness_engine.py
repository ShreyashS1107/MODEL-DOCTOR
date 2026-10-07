import asyncio
import pytest
import numpy as np
import pandas as pd

from app.diagnostics.fairness.engine import FairnessEngine
from app.schemas.diagnostic_models import DiagnosticCategory, SeverityLevel


@pytest.fixture
def fairness_engine():
    return FairnessEngine()


@pytest.fixture
def synthetic_binary_fairness_data():
    """
    Constructs a deterministic synthetic evaluation dataset with two protected groups (Group A and Group B).
    Group A (Reference): N=100
      - 50 actual positives (Y=1), 50 actual negatives (Y=0)
      - Predictions: 40 TP, 10 FN, 10 FP, 40 TN
      - Selection rate: (40+10)/100 = 0.50
      - TPR: 40/50 = 0.80, FPR: 10/50 = 0.20
    Group B (Comparison): N=100
      - 50 actual positives (Y=1), 50 actual negatives (Y=0)
      - Predictions: 20 TP, 30 FN, 5 FP, 45 TN
      - Selection rate: (20+5)/100 = 0.25
      - TPR: 20/50 = 0.40, FPR: 5/50 = 0.10
    DIR(B / A) = 0.25 / 0.50 = 0.50 (< 0.70 Critical Disparity)
    DP Gap = 0.50 - 0.25 = 0.25
    Equal Opportunity Gap = 0.80 - 0.40 = 0.40
    Equalized Odds: max(0.40, 0.10) = 0.40
    """
    rows = []
    # Group A
    for _ in range(40):
        rows.append({"group": "A", "target": 1, "pred_prob": 0.85, "prediction": 1})  # TP
    for _ in range(10):
        rows.append({"group": "A", "target": 1, "pred_prob": 0.30, "prediction": 0})  # FN
    for _ in range(10):
        rows.append({"group": "A", "target": 0, "pred_prob": 0.75, "prediction": 1})  # FP
    for _ in range(40):
        rows.append({"group": "A", "target": 0, "pred_prob": 0.15, "prediction": 0})  # TN

    # Group B
    for _ in range(20):
        rows.append({"group": "B", "target": 1, "pred_prob": 0.80, "prediction": 1})  # TP
    for _ in range(30):
        rows.append({"group": "B", "target": 1, "pred_prob": 0.25, "prediction": 0})  # FN
    for _ in range(5):
        rows.append({"group": "B", "target": 0, "pred_prob": 0.70, "prediction": 1})  # FP
    for _ in range(45):
        rows.append({"group": "B", "target": 0, "pred_prob": 0.10, "prediction": 0})  # TN

    return pd.DataFrame(rows)


def test_basic_group_metrics_and_confusion_matrix(fairness_engine, synthetic_binary_fairness_data):
    """Test group-level confusion matrices and derived rates (TPR, FPR, TNR, FNR, PPV, NPV, Accuracy)."""
    report = asyncio.run(
        fairness_engine.run_diagnostic(
            current_data=synthetic_binary_fairness_data,
            target_column="target",
            config={"protected_attribute": "group", "prediction_column": "pred_prob", "reference_group": "A"},
        )
    )

    assert report.category == DiagnosticCategory.FAIRNESS
    groups = report.metadata["groups"]
    assert len(groups) == 2

    # Group A verification
    grp_a = next(g for g in groups if g["group"] == "A")
    assert grp_a["sampleCount"] == 100
    assert grp_a["isReference"] is True
    assert grp_a["confusionMatrix"] == {"tp": 40, "tn": 40, "fp": 10, "fn": 10}
    assert grp_a["positivePredictionRate"] == 0.50
    assert grp_a["actualPositiveRate"] == 0.50
    assert grp_a["tpr"] == 0.80
    assert grp_a["tnr"] == 0.80
    assert grp_a["fpr"] == 0.20
    assert grp_a["fnr"] == 0.20
    assert grp_a["ppv"] == 0.80
    assert grp_a["npv"] == 0.80
    assert grp_a["accuracy"] == 0.80

    # Group B verification
    grp_b = next(g for g in groups if g["group"] == "B")
    assert grp_b["sampleCount"] == 100
    assert grp_b["isReference"] is False
    assert grp_b["confusionMatrix"] == {"tp": 20, "tn": 45, "fp": 5, "fn": 30}
    assert grp_b["positivePredictionRate"] == 0.25
    assert grp_b["actualPositiveRate"] == 0.50
    assert grp_b["tpr"] == 0.40
    assert grp_b["fpr"] == 0.10
    assert grp_b["ppv"] == pytest.approx(20 / 25, 0.001)
    assert grp_b["accuracy"] == 0.65


def test_demographic_parity_and_disparate_impact(fairness_engine, synthetic_binary_fairness_data):
    """Test demographic parity difference and disparate impact ratio (four-fifths rule)."""
    report = asyncio.run(
        fairness_engine.run_diagnostic(
            current_data=synthetic_binary_fairness_data,
            target_column="target",
            config={"protected_attribute": "group", "prediction_column": "pred_prob", "reference_group": "A"},
        )
    )

    summary = report.metadata["summary"]
    assert summary["demographicParityGap"] == 0.25
    assert summary["worstDisparateImpactRatio"] == 0.50

    disparity = report.metadata["disparity"]
    di_b = next(d for d in disparity["disparateImpact"] if d["group"] == "B")
    assert di_b["disparateImpactRatio"] == 0.50
    assert di_b["violates80Rule"] is True

    dp_b = next(d for d in disparity["demographicParity"] if d["group"] == "B")
    assert dp_b["difference"] == -0.25
    assert dp_b["absoluteDifference"] == 0.25

    # Check issues generated for disparate impact violation
    crit_di_issues = [i for i in report.issues if "DISPARATE_IMPACT" in i.id]
    assert len(crit_di_issues) > 0
    assert any(i.severity == SeverityLevel.HIGH for i in crit_di_issues)


def test_equal_opportunity_and_equalized_odds(fairness_engine, synthetic_binary_fairness_data):
    """Test equal opportunity (TPR gap) and equalized odds (both TPR and FPR gaps)."""
    report = asyncio.run(
        fairness_engine.run_diagnostic(
            current_data=synthetic_binary_fairness_data,
            target_column="target",
            config={"protected_attribute": "group", "prediction_column": "pred_prob", "reference_group": "A"},
        )
    )

    summary = report.metadata["summary"]
    assert summary["equalOpportunityGap"] == 0.40
    assert summary["equalizedOdds"]["tprGap"] == 0.40
    assert summary["equalizedOdds"]["fprGap"] == 0.10
    assert summary["equalizedOdds"]["maxGap"] == 0.40

    # Verify equal opportunity issue generated
    eo_issues = [i for i in report.issues if "EQUAL_OPPORTUNITY" in i.id]
    assert len(eo_issues) > 0


def test_group_calibration(fairness_engine, synthetic_binary_fairness_data):
    """Test group calibration calculation (mean predicted prob vs empirical positive rate)."""
    report = asyncio.run(
        fairness_engine.run_diagnostic(
            current_data=synthetic_binary_fairness_data,
            target_column="target",
            config={"protected_attribute": "group", "prediction_column": "pred_prob"},
        )
    )

    summary = report.metadata["summary"]
    assert summary["hasProbabilities"] is True
    assert summary["worstCalibrationGap"] is not None

    groups = report.metadata["groups"]
    for grp in groups:
        assert grp["meanPredictedProbability"] is not None
        assert grp["actualPositiveRate"] is not None
        assert grp["calibrationGap"] == pytest.approx(grp["meanPredictedProbability"] - grp["actualPositiveRate"], 0.001)


def test_statistical_uncertainty_wilson_ci(fairness_engine, synthetic_binary_fairness_data):
    """Test that 95% Wilson binomial confidence intervals are properly calculated."""
    report = asyncio.run(
        fairness_engine.run_diagnostic(
            current_data=synthetic_binary_fairness_data,
            target_column="target",
            config={"protected_attribute": "group", "prediction_column": "pred_prob"},
        )
    )

    for grp in report.metadata["groups"]:
        ci_sel = grp["confidenceIntervals"]["selectionRate"]
        assert 0.0 <= ci_sel["lower"] <= grp["positivePredictionRate"] <= ci_sel["upper"] <= 1.0


def test_multi_group_support(fairness_engine):
    """Test multiple (4+) protected groups."""
    rows = []
    groups = ["North", "South", "East", "West"]
    for g_idx, g in enumerate(groups):
        n = 50 + g_idx * 20
        pos_rate = 0.2 + g_idx * 0.15
        for i in range(n):
            y = 1 if i < int(n * pos_rate) else 0
            pred = 1 if i < int(n * (pos_rate + 0.05)) else 0
            prob = 0.8 if pred == 1 else 0.2
            rows.append({"region": g, "target": y, "pred_prob": prob, "pred": pred})

    df = pd.DataFrame(rows)
    report = asyncio.run(
        fairness_engine.run_diagnostic(
            current_data=df,
            target_column="target",
            config={"protected_attribute": "region", "prediction_column": "pred_prob"},
        )
    )

    assert report.metadata["summary"]["groupCount"] == 4
    assert len(report.metadata["groups"]) == 4
    assert len(report.metadata["disparity"]["disparateImpact"]) == 4
    # Reference group should default to largest group ('West' with N=110)
    assert report.metadata["summary"]["referenceGroup"] == "West"


def test_reference_group_selection_explicit_vs_fallback(fairness_engine):
    """Test explicit reference group config vs largest group fallback."""
    df = pd.DataFrame([
        {"subgroup": "SmallA", "target": 1, "pred": 1} for _ in range(40)
    ] + [
        {"subgroup": "LargeB", "target": 0, "pred": 0} for _ in range(100)
    ])

    # 1. Explicit reference group SmallA
    rep_explicit = asyncio.run(
        fairness_engine.run_diagnostic(
            current_data=df,
            target_column="target",
            config={"protected_attribute": "subgroup", "prediction_column": "pred", "reference_group": "SmallA"},
        )
    )
    assert rep_explicit.metadata["summary"]["referenceGroup"] == "SmallA"
    assert rep_explicit.metadata["summary"]["referenceSelection"] == "configured"

    # 2. Largest group fallback LargeB
    rep_fallback = asyncio.run(
        fairness_engine.run_diagnostic(
            current_data=df,
            target_column="target",
            config={"protected_attribute": "subgroup", "prediction_column": "pred"},
        )
    )
    assert rep_fallback.metadata["summary"]["referenceGroup"] == "LargeB"
    assert rep_fallback.metadata["summary"]["referenceSelection"] == "largest_group"


def test_missing_protected_attribute(fairness_engine, synthetic_binary_fairness_data):
    """Test clean critical failure when protected attribute is missing."""
    df = synthetic_binary_fairness_data.drop(columns=["group"])
    report = asyncio.run(
        fairness_engine.run_diagnostic(
            current_data=df,
            target_column="target",
            config={"protected_attribute": "non_existent_column"},
        )
    )

    assert report.passed is False
    assert any(i.id == "BIAS_PROTECTED_ATTRIBUTE_MISSING" for i in report.issues)


def test_missing_values_excluded_reporting(fairness_engine):
    """Test proper exclusion and telemetry of null/missing protected attribute values."""
    df = pd.DataFrame([
        {"cohort": "A", "target": 1, "pred": 1},
        {"cohort": "A", "target": 0, "pred": 0},
        {"cohort": "B", "target": 1, "pred": 1},
        {"cohort": None, "target": 1, "pred": 1},   # null cohort
        {"cohort": np.nan, "target": 0, "pred": 0}, # nan cohort
    ] * 20)

    report = asyncio.run(
        fairness_engine.run_diagnostic(
            current_data=df,
            target_column="target",
            config={"protected_attribute": "cohort", "prediction_column": "pred"},
        )
    )

    summary = report.metadata["summary"]
    assert summary["rawTotalRows"] == 100
    assert summary["totalEvaluatedRows"] == 60
    assert summary["excludedMissingProtectedAttribute"] == 40


def test_small_group_warning(fairness_engine):
    """Test small group warning when group sample size < minimum_group_size (30)."""
    df = pd.DataFrame([
        {"slice": "Regular", "target": 1, "pred": 1} for _ in range(80)
    ] + [
        {"slice": "Tiny", "target": 1, "pred": 1} for _ in range(15)  # N=15 < 30
    ])

    report = asyncio.run(
        fairness_engine.run_diagnostic(
            current_data=df,
            target_column="target",
            config={"protected_attribute": "slice", "prediction_column": "pred", "minimum_group_size": 30},
        )
    )

    assert report.metadata["summary"]["smallGroupsCount"] == 1
    tiny_grp = next(g for g in report.metadata["groups"] if g["group"] == "Tiny")
    assert tiny_grp["isSmallSample"] is True
    assert any(i.id == "BIAS_SMALL_GROUP_SAMPLE" for i in report.issues)


def test_high_cardinality_rejection_and_warning(fairness_engine):
    """Test high cardinality protection (warning > 20, critical > 50)."""
    # 60 distinct groups -> Critical failure
    df_60 = pd.DataFrame([
        {"id_cat": f"cat_{i}", "target": i % 2, "pred": i % 2} for i in range(60)
    ])
    rep_60 = asyncio.run(
        fairness_engine.run_diagnostic(
            current_data=df_60,
            target_column="target",
            config={"protected_attribute": "id_cat", "prediction_column": "pred"},
        )
    )
    assert rep_60.passed is False
    assert any(i.id == "BIAS_HIGH_CARDINALITY" for i in rep_60.issues)


def test_single_class_group_zero_denominator_safe_handling(fairness_engine):
    """Test that zero denominator rates return None (null) rather than 0.0 or raising division error."""
    # Group A has NO positive ground truth cases (tp + fn = 0)
    df = pd.DataFrame([
        {"group": "NoPositives", "target": 0, "pred": 0} for _ in range(40)
    ] + [
        {"group": "Standard", "target": 1, "pred": 1} for _ in range(30)
    ] + [
        {"group": "Standard", "target": 0, "pred": 0} for _ in range(30)
    ])

    report = asyncio.run(
        fairness_engine.run_diagnostic(
            current_data=df,
            target_column="target",
            config={"protected_attribute": "group", "prediction_column": "pred"},
        )
    )

    no_pos = next(g for g in report.metadata["groups"] if g["group"] == "NoPositives")
    assert no_pos["confusionMatrix"] == {"tp": 0, "tn": 40, "fp": 0, "fn": 0}
    # TPR should be None (undefined), not 0.0
    assert no_pos["tpr"] is None
    assert no_pos["fnr"] is None
    assert no_pos["tnr"] == 1.0


def test_fairness_determinism(fairness_engine, synthetic_binary_fairness_data):
    """Test that repeated runs produce identical deterministic results."""
    rep1 = asyncio.run(
        fairness_engine.run_diagnostic(
            current_data=synthetic_binary_fairness_data,
            target_column="target",
            config={"protected_attribute": "group", "prediction_column": "pred_prob"},
        )
    )
    rep2 = asyncio.run(
        fairness_engine.run_diagnostic(
            current_data=synthetic_binary_fairness_data,
            target_column="target",
            config={"protected_attribute": "group", "prediction_column": "pred_prob"},
        )
    )

    assert rep1.health_score == rep2.health_score
    assert rep1.metadata["summary"] == rep2.metadata["summary"]
    assert rep1.metadata["groups"] == rep2.metadata["groups"]
