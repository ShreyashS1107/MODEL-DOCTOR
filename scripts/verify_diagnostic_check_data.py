import asyncio
import json
from pathlib import Path
import pandas as pd
import numpy as np

# Adjust python path to include ml-engine
import sys
project_root = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(project_root / "ml-engine"))

from app.models.loader import ModelLoader
from app.data.loader import DatasetLoader
from app.diagnostics.data_quality.engine import DataQualityEngine
from app.diagnostics.leakage.engine import DataLeakageEngine
from app.diagnostics.drift.engine import DataDriftEngine
from app.diagnostics.performance.engine import PerformanceEngine
from app.diagnostics.explainability.engine import ExplainabilityEngine
from app.diagnostics.fairness.engine import FairnessEngine
from app.diagnostics.robustness.engine import RobustnessEngine
from app.diagnostics.error_forensics.engine import ErrorForensicsEngine

async def run_verification():
    print("=== MODEL DOCTOR DIAGNOSTIC DATA CHECK VERIFICATION ===")
    
    base_folder = project_root / "Diagnostic Data Check"
    model_path = base_folder / "Model Artifact" / "fraud_model.json"
    eval_path = base_folder / "Evaluation Dataset" / "evaluation_dataset.csv"
    baseline_path = base_folder / "Baseline Reference Dataset" / "baseline_dataset.csv"

    # 1. Verify file existence
    assert model_path.exists(), f"Model artifact missing: {model_path}"
    assert eval_path.exists(), f"Evaluation dataset missing: {eval_path}"
    assert baseline_path.exists(), f"Baseline dataset missing: {baseline_path}"
    print("[PASS] All 3 files exist in their target directories.")

    # 2. Verify Model Loader
    print("\n1. Testing ModelLoader...")
    adapter = ModelLoader.load_model_adapter(
        model_name="fraud_model",
        framework="xgboost",
        task_type="binary_classification",
        storage_uri=str(model_path),
        execution_mode="REAL",
    )
    print(f"   [PASS] Model loaded successfully: {adapter.name}")
    print(f"   Framework: {adapter.framework}, Task Type: {adapter.task_type}")
    print(f"   Feature Names: {adapter.feature_names}")
    assert adapter.supports_tree_shap(), "Model should support TreeSHAP"

    # 3. Verify Dataset Loader
    print("\n2. Testing DatasetLoader...")
    eval_df = DatasetLoader.load(str(eval_path), execution_mode="REAL")
    base_df = DatasetLoader.load(str(baseline_path), execution_mode="REAL")
    print(f"   [PASS] Evaluation dataset loaded: shape {eval_df.shape}")
    print(f"   [PASS] Baseline dataset loaded: shape {base_df.shape}")
    print(f"   Columns: {list(eval_df.columns)}")
    assert list(eval_df.columns) == list(base_df.columns), "Column schema mismatch!"

    # 4. Verify Model Adapter Inference on Datasets
    print("\n3. Testing Model Adapter Inference...")
    probs = adapter.predict_proba(eval_df)
    preds = adapter.predict(eval_df)
    print(f"   [PASS] Generated predictions for {len(preds)} rows. Sample probs (first 3): {probs[:3]}")

    # 5. Execute All Diagnostic Engines
    print("\n4. Executing Diagnostic Engines...")

    # Data Quality
    dq = DataQualityEngine()
    dq_res = await dq.run_diagnostic(
        current_data=eval_df,
        target_column="is_fraud",
        baseline_data=base_df,
    )
    print(f"   [PASS] Data Quality Engine: Health Score={dq_res.health_score}, Issues={len(dq_res.issues)}")

    # Leakage
    leak = DataLeakageEngine()
    leak_res = await leak.run_diagnostic(
        current_data=eval_df,
        target_column="is_fraud",
        baseline_data=base_df,
    )
    print(f"   [PASS] Leakage Engine: Health Score={leak_res.health_score}, Issues={len(leak_res.issues)}")

    # Drift
    drift = DataDriftEngine()
    drift_res = await drift.run_diagnostic(
        current_data=eval_df,
        target_column="is_fraud",
        baseline_data=base_df,
    )
    drifted_features = [f["feature"] for f in drift_res.metadata.get("features", []) if f.get("statistically_significant") or f.get("practical_drift") in ["MODERATE", "HIGH"]]
    print(f"   [PASS] Drift Engine: Health Score={drift_res.health_score}")
    print(f"          Detected Drifted Features: {drifted_features}")
    print(f"          Max PSI Feature: {drift_res.metadata.get('summary', {}).get('maxPsiFeature')} (PSI: {drift_res.metadata.get('summary', {}).get('maxPsi')})")
    assert "transaction_amount" in drifted_features, "Expected transaction_amount to drift!"

    # Performance
    perf = PerformanceEngine()
    perf_res = await perf.run_diagnostic(
        current_data=eval_df,
        target_column="is_fraud",
        baseline_data=base_df,
        model_artifact=adapter,
        config={"prediction_column": "pred_prob"},
    )
    roc_auc = perf_res.metadata.get("summary", {}).get("rocAuc")
    f1 = perf_res.metadata.get("summary", {}).get("f1")
    accuracy = perf_res.metadata.get("summary", {}).get("accuracy")
    print(f"   [PASS] Performance Engine: Health Score={perf_res.health_score}, Accuracy={accuracy}, ROC-AUC={roc_auc}, F1={f1}")
    assert roc_auc is not None and roc_auc > 0.65, f"ROC-AUC too low: {roc_auc}"

    # Explainability (TreeSHAP)
    exp = ExplainabilityEngine()
    exp_res = await exp.run_diagnostic(
        current_data=eval_df,
        target_column="is_fraud",
        baseline_data=base_df,
        model_artifact=adapter,
        config={"storage_uri": str(model_path), "max_explanation_samples": 100},
    )
    top_features = [f["feature"] for f in exp_res.metadata.get("globalImportance", [])[:3]]
    print(f"   [PASS] Explainability Engine: Health Score={exp_res.health_score}")
    print(f"          Top TreeSHAP Attribution Features: {top_features}")
    assert len(top_features) > 0, "Expected non-empty SHAP feature attributions!"

    # Fairness / Bias
    fairness = FairnessEngine()
    fairness_res = await fairness.run_diagnostic(
        current_data=eval_df,
        target_column="is_fraud",
        baseline_data=base_df,
        model_artifact=adapter,
        config={"protected_attribute": "is_foreign_ip", "prediction_column": "pred_prob"},
    )
    groups = [g.get("group") for g in fairness_res.metadata.get("groups", [])]
    print(f"   [PASS] Fairness Engine: Health Score={fairness_res.health_score}, Issues={len(fairness_res.issues)}")
    print(f"          Evaluated Groups: {groups}")

    # Robustness
    robustness = RobustnessEngine()
    robust_res = await robustness.run_diagnostic(
        current_data=eval_df,
        target_column="is_fraud",
        baseline_data=base_df,
        model_artifact=adapter,
        config={"storage_uri": str(model_path), "max_sample_size": 100},
    )
    print(f"   [PASS] Robustness Engine: Health Score={robust_res.health_score}, Issues={len(robust_res.issues)}")

    # Error Forensics
    error_eng = ErrorForensicsEngine()
    err_res = await error_eng.run_diagnostic(
        current_data=eval_df,
        target_column="is_fraud",
        baseline_data=base_df,
        model_artifact=adapter,
        config={"prediction_column": "pred_prob"},
    )
    print(f"   [PASS] Error Forensics Engine: Health Score={err_res.health_score}")

    print("\n[ALL 8 DIAGNOSTIC ENGINES COMPLETED AND PASSED SUCCESSFULLY ON GENERATED DATA]")

if __name__ == "__main__":
    asyncio.run(run_verification())
