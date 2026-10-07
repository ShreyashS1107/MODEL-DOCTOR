import asyncio
import pytest
import numpy as np
import pandas as pd
import xgboost as xgb
from sklearn.ensemble import RandomForestClassifier

from app.diagnostics.explainability.engine import ExplainabilityEngine
from app.models.adapter import (
    XGBoostModelAdapter,
    SklearnTreeModelAdapter,
    GenericCallableModelAdapter,
)
from app.schemas.diagnostic_models import SeverityLevel


@pytest.fixture
def synthetic_tree_data():
    """Generates a controlled dataset with known dominant and identifier features."""
    rng = np.random.RandomState(42)
    n = 600

    # Dominant feature + identifier feature + noise features
    dominant_feat = rng.normal(0, 1, size=n)
    id_hash_feat = rng.normal(0, 1, size=n)
    f3 = rng.normal(0, 1, size=n)
    f4 = rng.normal(0, 1, size=n)
    f5 = rng.normal(0, 1, size=n)

    # Target strongly determined by dominant_feat and id_hash_feat
    logits = 2.5 * dominant_feat + 1.8 * id_hash_feat + 0.2 * f3
    probs = 1.0 / (1.0 + np.exp(-logits))
    y = (probs >= 0.5).astype(int)

    df = pd.DataFrame({
        "dominant_feature": dominant_feat,
        "transaction_id_hash": id_hash_feat,
        "feature_three": f3,
        "feature_four": f4,
        "feature_five": f5,
        "is_fraud": y,
    })
    return df


def test_treeshap_xgboost_execution(synthetic_tree_data):
    """Proves TreeSHAP executes on XGBoost, returning ranked global attribution and base value."""
    df = synthetic_tree_data
    feature_cols = ["dominant_feature", "transaction_id_hash", "feature_three", "feature_four", "feature_five"]
    X = df[feature_cols]
    y = df["is_fraud"]

    clf = xgb.XGBClassifier(n_estimators=15, max_depth=3, random_state=42, eval_metric="logloss")
    clf.fit(X, y)
    adapter = XGBoostModelAdapter(name="test_xgb", raw_model=clf, feature_names=feature_cols)

    engine = ExplainabilityEngine()
    report = asyncio.run(
        engine.run_diagnostic(
            current_data=df,
            target_column="is_fraud",
            model_artifact=adapter,
            config={"max_explanation_samples": 500, "random_state": 42},
        )
    )

    assert report.category.value == "explainability"
    assert report.metadata["module"] == "EXPLAINABILITY"
    summary = report.metadata["summary"]

    assert summary["method"] == "TREE_SHAP"
    assert summary["modelFramework"] == "xgboost"
    assert summary["featureCount"] == 5
    assert len(report.metadata["globalImportance"]) == 5

    # Check top feature is ranked 1
    top_item = report.metadata["globalImportance"][0]
    assert top_item["rank"] == 1
    assert top_item["meanAbsShap"] > 0
    assert "meanSignedShap" in top_item
    assert "positiveContributionRate" in top_item
    assert "negativeContributionRate" in top_item

    # Check base value and context
    context = report.metadata["explanationContext"]
    assert context["method"] == "TREE_SHAP"
    assert "baseValue" in context
    assert context["modelOutput"] == "raw"


def test_local_explanations_deterministic_selection(synthetic_tree_data):
    """Proves local explanations generate 5 deterministic representative cases with top contributors."""
    df = synthetic_tree_data
    feature_cols = ["dominant_feature", "transaction_id_hash", "feature_three", "feature_four", "feature_five"]
    X = df[feature_cols]
    y = df["is_fraud"]

    clf = xgb.XGBClassifier(n_estimators=15, max_depth=3, random_state=42, eval_metric="logloss")
    clf.fit(X, y)
    adapter = XGBoostModelAdapter(name="test_xgb", raw_model=clf, feature_names=feature_cols)

    engine = ExplainabilityEngine()
    report = asyncio.run(
        engine.run_diagnostic(
            current_data=df,
            target_column="is_fraud",
            model_artifact=adapter,
        )
    )

    locals_list = report.metadata["localExplanations"]
    assert len(locals_list) >= 3  # At least Max, Min, Borderline
    types_found = {item["observationType"] for item in locals_list}
    assert "MAX_PROBABILITY" in types_found
    assert "MIN_PROBABILITY" in types_found

    for item in locals_list:
        assert 0.0 <= item["prediction"] <= 1.0
        assert item["actual"] in [0, 1]
        assert "baseValue" in item
        assert len(item["topContributors"]) > 0
        contrib = item["topContributors"][0]
        assert "feature" in contrib
        assert "value" in contrib
        assert "shapValue" in contrib
        assert contrib["direction"] in ["POSITIVE", "NEGATIVE"]


def test_permutation_importance_and_agreement(synthetic_tree_data):
    """Proves Permutation Importance executes and Spearman rank correlation is computed."""
    df = synthetic_tree_data
    feature_cols = ["dominant_feature", "transaction_id_hash", "feature_three", "feature_four", "feature_five"]
    X = df[feature_cols]
    y = df["is_fraud"]

    clf = xgb.XGBClassifier(n_estimators=15, max_depth=3, random_state=42, eval_metric="logloss")
    clf.fit(X, y)
    adapter = XGBoostModelAdapter(name="test_xgb", raw_model=clf, feature_names=feature_cols)

    engine = ExplainabilityEngine()
    report = asyncio.run(
        engine.run_diagnostic(
            current_data=df,
            target_column="is_fraud",
            model_artifact=adapter,
            config={"n_permutation_repeats": 3},
        )
    )

    perm_list = report.metadata["permutationImportance"]
    assert len(perm_list) == 5
    for item in perm_list:
        assert "feature" in item
        assert "importanceMean" in item
        assert "importanceStd" in item
        assert "rank" in item

    agreement = report.metadata["importanceAgreement"]
    assert agreement["methodA"] == "TREE_SHAP"
    assert agreement["methodB"] == "PERMUTATION_IMPORTANCE"
    assert agreement["spearmanCorrelation"] is not None
    assert -1.0 <= agreement["spearmanCorrelation"] <= 1.0


def test_attribution_concentration_and_findings(synthetic_tree_data):
    """Proves concentration shares and identifier dominance findings are correctly flagged."""
    df = synthetic_tree_data
    feature_cols = ["dominant_feature", "transaction_id_hash", "feature_three", "feature_four", "feature_five"]
    X = df[feature_cols]
    y = df["is_fraud"]

    clf = xgb.XGBClassifier(n_estimators=20, max_depth=3, random_state=42, eval_metric="logloss")
    clf.fit(X, y)
    adapter = XGBoostModelAdapter(name="test_xgb", raw_model=clf, feature_names=feature_cols)

    engine = ExplainabilityEngine()
    report = asyncio.run(
        engine.run_diagnostic(
            current_data=df,
            target_column="is_fraud",
            model_artifact=adapter,
        )
    )

    conc = report.metadata["concentration"]
    assert 0.0 <= conc["top1Share"] <= 1.0
    assert 0.0 <= conc["top3Share"] <= 1.0
    assert conc["top1Share"] <= conc["top3Share"] <= conc["top5Share"]
    assert 0.0 <= conc["normalizedEntropy"] <= 1.0

    # Check identifier dominance finding for transaction_id_hash
    findings = report.metadata["findings"]
    id_findings = [f for f in findings if "IDENTIFIER_DOMINANCE" in f["id"]]
    assert len(id_findings) >= 1
    assert "transaction_id_hash" in id_findings[0]["affected_features"]


def test_generic_model_permutation_fallback(synthetic_tree_data):
    """Proves non-tree generic model falls back to Permutation Importance without crashing."""
    df = synthetic_tree_data
    feature_cols = ["dominant_feature", "transaction_id_hash", "feature_three", "feature_four", "feature_five"]
    X = df[feature_cols]
    y = df["is_fraud"]

    class MockLogisticRegression:
        def predict_proba(self, X_df):
            vals = X_df["dominant_feature"].values
            probs = 1.0 / (1.0 + np.exp(-vals))
            return np.column_stack([1.0 - probs, probs])

        def predict(self, X_df):
            return (self.predict_proba(X_df)[:, 1] >= 0.5).astype(int)

    adapter = GenericCallableModelAdapter(
        name="custom_logistic",
        raw_model=MockLogisticRegression(),
        feature_names=feature_cols,
    )

    engine = ExplainabilityEngine()
    report = asyncio.run(
        engine.run_diagnostic(
            current_data=df,
            target_column="is_fraud",
            model_artifact=adapter,
        )
    )

    summary = report.metadata["summary"]
    assert summary["method"] == "PERMUTATION_IMPORTANCE"
    assert len(report.metadata["globalImportance"]) == 5
    assert len(report.metadata["permutationImportance"]) == 5


def test_missing_model_artifact_failure():
    """Proves missing model artifact returns structured failure with MODEL_ARTIFACT_UNAVAILABLE."""
    df = pd.DataFrame({
        "feature_1": [1.0, 2.0, 3.0, 4.0],
        "target": [0, 1, 0, 1],
    })

    engine = ExplainabilityEngine()
    report = asyncio.run(
        engine.run_diagnostic(
            current_data=df,
            target_column="target",
            config={"model_name": "non_existent_unregistered_model_xyz", "storage_uri": "/invalid/path.json"},
        )
    )

    assert not report.passed
    assert report.health_score == 0.0
    assert any(i.id == "MODEL_ARTIFACT_UNAVAILABLE" for i in report.issues)


def test_empty_dataset_and_missing_target_failures():
    """Proves empty dataset and missing target columns fail safely without crashing."""
    engine = ExplainabilityEngine()

    # Empty df
    rep_empty = asyncio.run(engine.run_diagnostic(current_data=pd.DataFrame(), target_column="y"))
    assert not rep_empty.passed
    assert rep_empty.issues[0].id == "EXPLAIN-EMPTY-DATASET"

    # Missing target
    df = pd.DataFrame({"x1": [1, 2], "x2": [3, 4]})
    rep_missing_target = asyncio.run(engine.run_diagnostic(current_data=df, target_column="missing_target"))
    assert not rep_missing_target.passed
    assert rep_missing_target.issues[0].id == "EXPLAIN-MISSING-TARGET"


def test_sampling_determinism(synthetic_tree_data):
    """Proves sampling is strictly deterministic when dataset exceeds max_explanation_samples."""
    df = pd.concat([synthetic_tree_data] * 4, ignore_index=True)  # 2400 rows
    feature_cols = ["dominant_feature", "transaction_id_hash", "feature_three", "feature_four", "feature_five"]

    clf = xgb.XGBClassifier(n_estimators=10, max_depth=2, random_state=42, eval_metric="logloss")
    clf.fit(df[feature_cols], df["is_fraud"])
    adapter = XGBoostModelAdapter(name="test_xgb", raw_model=clf, feature_names=feature_cols)

    engine = ExplainabilityEngine()
    r1 = asyncio.run(
        engine.run_diagnostic(
            current_data=df,
            target_column="is_fraud",
            model_artifact=adapter,
            config={"max_explanation_samples": 400, "random_state": 42},
        )
    )
    r2 = asyncio.run(
        engine.run_diagnostic(
            current_data=df,
            target_column="is_fraud",
            model_artifact=adapter,
            config={"max_explanation_samples": 400, "random_state": 42},
        )
    )

    assert r1.metadata["summary"]["rawSampleCount"] == 2400
    assert r1.metadata["summary"]["sampleCount"] == 400
    assert r1.metadata["summary"]["topFeature"] == r2.metadata["summary"]["topFeature"]
    assert r1.metadata["summary"]["topFeatureMeanAbsShap"] == r2.metadata["summary"]["topFeatureMeanAbsShap"]
