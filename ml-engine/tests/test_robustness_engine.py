import asyncio
import pytest
import numpy as np
import pandas as pd
import xgboost as xgb

from app.diagnostics.robustness.engine import RobustnessEngine
from app.models.adapter import XGBoostModelAdapter, SklearnTreeModelAdapter, GenericCallableModelAdapter
from app.schemas.diagnostic_models import DiagnosticCategory, SeverityLevel


@pytest.fixture
def robustness_engine():
    return RobustnessEngine()


@pytest.fixture
def synthetic_robustness_fixture():
    """
    Creates a controlled synthetic dataset and trained XGBoost model.
    Features:
      - 'signal_strong': high importance / high variance impact
      - 'signal_weak': moderate importance
      - 'noise_feat': irrelevant noise feature
      - 'subgroup': protected category ('GroupA', 'GroupB')
    """
    rng = np.random.RandomState(42)
    n = 500

    f1 = rng.normal(0, 1, size=n)
    f2 = rng.normal(0, 1, size=n)
    f3 = rng.normal(0, 1, size=n)
    subgroups = ["GroupA" if i % 2 == 0 else "GroupB" for i in range(n)]

    # Target strongly determined by f1
    logits = 3.0 * f1 + 0.8 * f2
    probs = 1.0 / (1.0 + np.exp(-logits))
    y = (probs >= 0.5).astype(int)

    df = pd.DataFrame({
        "signal_strong": f1,
        "signal_weak": f2,
        "noise_feat": f3,
        "subgroup": subgroups,
        "target": y,
    })

    X_train = df[["signal_strong", "signal_weak", "noise_feat"]]
    clf = xgb.XGBClassifier(n_estimators=25, max_depth=3, learning_rate=0.1, random_state=42)
    clf.fit(X_train, y)

    adapter = XGBoostModelAdapter(
        name="test_xgb_robustness",
        raw_model=clf,
        feature_names=["signal_strong", "signal_weak", "noise_feat"],
    )

    return df, adapter


def test_noise_sensitivity_increasing_levels(robustness_engine, synthetic_robustness_fixture):
    """Test that Gaussian jitter noise sensitivity computes multi-level degradation."""
    df, adapter = synthetic_robustness_fixture
    report = asyncio.run(
        robustness_engine.run_diagnostic(
            current_data=df,
            target_column="target",
            model_artifact=adapter,
            config={"noise_levels": [0.001, 0.05, 0.20], "random_state": 42},
        )
    )

    assert report.category == DiagnosticCategory.ROBUSTNESS
    assert report.metadata["module"] == "ROBUSTNESS"

    noise_levels = report.metadata["noiseSensitivity"]["levels"]
    assert len(noise_levels) == 3

    # Very small noise (0.001) should have very low or 0 flip rate
    assert noise_levels[0]["flipRate"] <= 0.02

    # Higher noise (0.20) should have higher probability shift than 0.001
    assert noise_levels[2]["meanProbabilityShift"] >= noise_levels[0]["meanProbabilityShift"]


def test_robustness_determinism(robustness_engine, synthetic_robustness_fixture):
    """Test that identical random state yields identical deterministic robustness outputs."""
    df, adapter = synthetic_robustness_fixture
    rep1 = asyncio.run(
        robustness_engine.run_diagnostic(
            current_data=df,
            target_column="target",
            model_artifact=adapter,
            config={"random_state": 42},
        )
    )
    rep2 = asyncio.run(
        robustness_engine.run_diagnostic(
            current_data=df,
            target_column="target",
            model_artifact=adapter,
            config={"random_state": 42},
        )
    )

    assert rep1.health_score == rep2.health_score
    assert rep1.metadata["summary"] == rep2.metadata["summary"]
    assert rep1.metadata["noiseSensitivity"] == rep2.metadata["noiseSensitivity"]
    assert rep1.metadata["featureSensitivity"] == rep2.metadata["featureSensitivity"]


def test_feature_sensitivity_ranking(robustness_engine, synthetic_robustness_fixture):
    """Test that strong signal feature ranks above noise feature in perturbation sensitivity."""
    df, adapter = synthetic_robustness_fixture
    report = asyncio.run(
        robustness_engine.run_diagnostic(
            current_data=df,
            target_column="target",
            model_artifact=adapter,
            config={"random_state": 42},
        )
    )

    features = report.metadata["featureSensitivity"]["features"]
    assert len(features) == 3

    feat_names = [f["feature"] for f in features]
    assert "signal_strong" in feat_names
    assert "noise_feat" in feat_names

    strong_feat = next(f for f in features if f["feature"] == "signal_strong")
    noise_feat = next(f for f in features if f["feature"] == "noise_feat")

    # signal_strong should have higher or equal mean probability shift than pure noise
    assert strong_feat["meanProbabilityShift"] >= noise_feat["meanProbabilityShift"]


def test_tree_boundary_search(robustness_engine, synthetic_robustness_fixture):
    """Test bounded decision boundary search for tree models."""
    df, adapter = synthetic_robustness_fixture
    report = asyncio.run(
        robustness_engine.run_diagnostic(
            current_data=df,
            target_column="target",
            model_artifact=adapter,
            config={"max_boundary_samples": 100, "random_state": 42},
        )
    )

    bnd = report.metadata["boundarySearch"]
    assert bnd["method"] == "TREE_BOUNDARY_SEARCH"
    assert bnd["samplesTested"] == 100
    assert bnd["successfulFlips"] > 0
    assert 0.0 <= bnd["flipRate"] <= 1.0
    assert bnd["medianNormalizedDistance"] is not None
    assert len(bnd["features"]) > 0


def test_fgsm_applicability_for_tree_model(robustness_engine, synthetic_robustness_fixture):
    """Test that tree model adapter reports FGSM as NOT_APPLICABLE without fake gradients."""
    df, adapter = synthetic_robustness_fixture
    report = asyncio.run(
        robustness_engine.run_diagnostic(
            current_data=df,
            target_column="target",
            model_artifact=adapter,
        )
    )

    fgsm = report.metadata["fgsm"]
    assert fgsm["method"] == "FGSM"
    assert fgsm["status"] == "NOT_APPLICABLE"
    assert "gradients" in fgsm["reason"].lower()


def test_missingness_stress_xgboost(robustness_engine, synthetic_robustness_fixture):
    """Test that XGBoost model natively evaluates feature missingness stress."""
    df, adapter = synthetic_robustness_fixture
    report = asyncio.run(
        robustness_engine.run_diagnostic(
            current_data=df,
            target_column="target",
            model_artifact=adapter,
        )
    )

    miss = report.metadata["missingnessStress"]
    assert miss["status"] == "COMPLETED"
    assert len(miss["levels"]) == 3
    for lvl in miss["levels"]:
        assert 0.0 <= lvl["flipRate"] <= 1.0
        assert lvl["meanProbabilityShift"] >= 0.0


def test_subgroup_robustness_analysis(robustness_engine, synthetic_robustness_fixture):
    """Test subgroup robustness computation across demographic categories."""
    df, adapter = synthetic_robustness_fixture
    report = asyncio.run(
        robustness_engine.run_diagnostic(
            current_data=df,
            target_column="target",
            model_artifact=adapter,
            config={"protected_attribute": "subgroup", "random_state": 42},
        )
    )

    sub = report.metadata["subgroupRobustness"]
    assert sub["protectedAttribute"] == "subgroup"
    assert len(sub["groups"]) == 2
    assert sub["robustnessGap"] is not None
    assert sub["robustnessGap"] >= 0.0


def test_missing_protected_attribute_graceful(robustness_engine, synthetic_robustness_fixture):
    """Test that missing protected attribute does not crash robustness engine."""
    df, adapter = synthetic_robustness_fixture
    df_no_sub = df.drop(columns=["subgroup"])
    report = asyncio.run(
        robustness_engine.run_diagnostic(
            current_data=df_no_sub,
            target_column="target",
            model_artifact=adapter,
            config={"protected_attribute": "non_existent_group"},
        )
    )

    assert report.passed is True
    sub = report.metadata["subgroupRobustness"]
    assert sub["groups"] == []
    assert sub["robustnessGap"] is None


def test_empty_dataset_failure(robustness_engine):
    """Test clean failure on empty dataset."""
    empty_df = pd.DataFrame()
    report = asyncio.run(
        robustness_engine.run_diagnostic(
            current_data=empty_df,
            target_column="target",
        )
    )

    assert report.passed is False
    assert any(i.id == "ROBUSTNESS_EMPTY_DATASET" for i in report.issues)
