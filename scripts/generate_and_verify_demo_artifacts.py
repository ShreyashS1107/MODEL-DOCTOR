import os
import sys
import json
import asyncio
from pathlib import Path
import numpy as np
import pandas as pd
import xgboost as xgb

# Project path configuration
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

def generate_demo_artifacts():
    base_folder = project_root / "Diagnostic Data Check"
    model_folder = base_folder / "Model Artifact"
    eval_folder = base_folder / "Evaluation Dataset"
    base_data_folder = base_folder / "Baseline Reference Dataset"

    model_folder.mkdir(parents=True, exist_ok=True)
    eval_folder.mkdir(parents=True, exist_ok=True)
    base_data_folder.mkdir(parents=True, exist_ok=True)

    # 1. Feature Specifications
    feature_names = [
        "transaction_amount",
        "user_age",
        "user_income",
        "user_velocity_6h",
        "device_trust_score",
        "distance_from_home",
        "is_foreign_ip",
    ]

    # 2. Independent Training Partition (Seed 42, 3000 observations)
    # Zero leakage: Training partition is strictly separate from evaluation and baseline partitions
    rng_train = np.random.RandomState(42)
    n_train = 3000

    train_amount = np.round(rng_train.gamma(shape=3.2, scale=32.0, size=n_train) + 8.0, 2)
    train_age = np.round(rng_train.uniform(18, 72, size=n_train), 0)
    train_income = np.round(rng_train.normal(65000, 22000, size=n_train).clip(18000, 250000), 2)
    train_velocity = np.round(rng_train.poisson(lam=2.2, size=n_train).astype(float), 0)
    train_trust = np.round((rng_train.beta(a=7, b=2, size=n_train) * 100.0).clip(10, 100), 2)
    train_distance = np.round(rng_train.exponential(scale=18.0, size=n_train) + 1.5, 2)
    train_foreign_ip = rng_train.binomial(1, 0.18, size=n_train)

    train_z = (
        -0.85
        + 0.013 * (train_amount - 105.0)
        + 0.48 * (train_velocity - 2.2)
        - 0.055 * (train_trust - 78.0)
        + 0.028 * (train_distance - 18.0)
        + 0.55 * train_foreign_ip
        - 0.000012 * (train_income - 65000.0)
    )
    train_probs = 1.0 / (1.0 + np.exp(-train_z))
    train_is_fraud = rng_train.binomial(1, np.clip(train_probs, 0.02, 0.98), size=n_train)

    X_train = pd.DataFrame({
        "transaction_amount": train_amount,
        "user_age": train_age,
        "user_income": train_income,
        "user_velocity_6h": train_velocity,
        "device_trust_score": train_trust,
        "distance_from_home": train_distance,
        "is_foreign_ip": train_foreign_ip,
    })

    # Train genuine XGBoost Booster
    dtrain = xgb.DMatrix(X_train, label=train_is_fraud)
    xgb_params = {
        "max_depth": 4,
        "eta": 0.12,
        "objective": "binary:logistic",
        "eval_metric": "logloss",
        "subsample": 0.85,
        "colsample_bytree": 0.85,
        "seed": 42,
    }
    booster = xgb.train(xgb_params, dtrain, num_boost_round=45)

    # Save Native XGBoost JSON Model Artifact
    model_path = model_folder / "fraud_model.json"
    booster.save_model(str(model_path))

    # 3. Deterministic Baseline Reference Dataset (Seed 100, exactly 800 observations)
    # Nominal reference distribution representing historical nominal traffic
    rng_base = np.random.RandomState(100)
    n_base = 800

    base_amount = np.round(rng_base.gamma(shape=3.2, scale=32.0, size=n_base) + 8.0, 2)
    base_age = np.round(rng_base.uniform(18, 72, size=n_base), 0)
    base_income = np.round(rng_base.normal(65000, 22000, size=n_base).clip(18000, 250000), 2)
    base_velocity = np.round(rng_base.poisson(lam=2.2, size=n_base).astype(float), 0)
    base_trust = np.round((rng_base.beta(a=7, b=2, size=n_base) * 100.0).clip(10, 100), 2)
    base_distance = np.round(rng_base.exponential(scale=18.0, size=n_base) + 1.5, 2)
    base_foreign_ip = rng_base.binomial(1, 0.18, size=n_base)

    base_z = (
        -0.85
        + 0.013 * (base_amount - 105.0)
        + 0.48 * (base_velocity - 2.2)
        - 0.055 * (base_trust - 78.0)
        + 0.028 * (base_distance - 18.0)
        + 0.55 * base_foreign_ip
        - 0.000012 * (base_income - 65000.0)
    )
    base_probs = 1.0 / (1.0 + np.exp(-base_z))
    base_is_fraud = rng_base.binomial(1, np.clip(base_probs, 0.02, 0.98), size=n_base)

    X_base = pd.DataFrame({
        "transaction_amount": base_amount,
        "user_age": base_age,
        "user_income": base_income,
        "user_velocity_6h": base_velocity,
        "device_trust_score": base_trust,
        "distance_from_home": base_distance,
        "is_foreign_ip": base_foreign_ip,
    })

    # Compute genuine predictions using the trained booster
    dbase = xgb.DMatrix(X_base)
    base_pred_prob = np.round(booster.predict(dbase), 4)

    df_base = X_base.copy()
    df_base["pred_prob"] = base_pred_prob
    df_base["is_fraud"] = base_is_fraud

    base_csv_path = base_data_folder / "baseline_dataset.csv"
    df_base.to_csv(base_csv_path, index=False)

    # 4. Deterministic Evaluation Dataset (Seed 200, exactly 800 observations)
    # Production traffic partition with controlled covariate shift:
    # - transaction_amount: shifted higher (holiday surge / inflation, mean ~$180 vs $110)
    # - user_velocity_6h: shifted higher (increased shopping frequency, mean ~3.5 vs 2.2)
    # - distance_from_home: shifted higher (increased remote transactions, mean ~38 km vs 19 km)
    # Stable features (no drift): user_age, user_income, device_trust_score, is_foreign_ip
    rng_eval = np.random.RandomState(200)
    n_eval = 800

    eval_amount = np.round(rng_eval.gamma(shape=4.2, scale=40.0, size=n_eval) + 12.0, 2)
    eval_age = np.round(rng_eval.uniform(18, 72, size=n_eval), 0)
    eval_income = np.round(rng_eval.normal(65000, 22000, size=n_eval).clip(18000, 250000), 2)
    eval_velocity = np.round(rng_eval.poisson(lam=3.5, size=n_eval).astype(float), 0)
    eval_trust = np.round((rng_eval.beta(a=7, b=2, size=n_eval) * 100.0).clip(10, 100), 2)
    eval_distance = np.round(rng_eval.exponential(scale=38.0, size=n_eval) + 2.0, 2)
    eval_foreign_ip = rng_eval.binomial(1, 0.20, size=n_eval)

    eval_z = (
        -0.85
        + 0.013 * (eval_amount - 105.0)
        + 0.48 * (eval_velocity - 2.2)
        - 0.055 * (eval_trust - 78.0)
        + 0.028 * (eval_distance - 18.0)
        + 0.55 * eval_foreign_ip
        - 0.000012 * (eval_income - 65000.0)
    )
    eval_probs = 1.0 / (1.0 + np.exp(-eval_z))
    eval_is_fraud = rng_eval.binomial(1, np.clip(eval_probs, 0.02, 0.98), size=n_eval)

    X_eval = pd.DataFrame({
        "transaction_amount": eval_amount,
        "user_age": eval_age,
        "user_income": eval_income,
        "user_velocity_6h": eval_velocity,
        "device_trust_score": eval_trust,
        "distance_from_home": eval_distance,
        "is_foreign_ip": eval_foreign_ip,
    })

    # Compute genuine predictions using the trained booster
    deval = xgb.DMatrix(X_eval)
    eval_pred_prob = np.round(booster.predict(deval), 4)

    df_eval = X_eval.copy()
    df_eval["pred_prob"] = eval_pred_prob
    df_eval["is_fraud"] = eval_is_fraud

    eval_csv_path = eval_folder / "evaluation_dataset.csv"
    df_eval.to_csv(eval_csv_path, index=False)

    return model_path, eval_csv_path, base_csv_path

async def run_validation_suite(model_path, eval_csv_path, base_csv_path):
    print("=== EXECUTING MODEL DOCTOR VALIDATION SUITE ===")

    # 1. Load Model with ModelLoader
    adapter = ModelLoader.load_model_adapter(
        model_name="fraud_model",
        framework="xgboost",
        task_type="binary_classification",
        storage_uri=str(model_path),
        execution_mode="REAL",
    )
    print(f"[PASS] ModelLoader: Loaded '{adapter.name}' (Framework: {adapter.framework})")
    print(f"       Model Features ({len(adapter.feature_names)}): {adapter.feature_names}")
    print(f"       TreeSHAP Supported: {adapter.supports_tree_shap()}")

    # 2. Ingest Datasets with DatasetLoader
    eval_df = DatasetLoader.load(str(eval_csv_path), execution_mode="REAL")
    base_df = DatasetLoader.load(str(base_csv_path), execution_mode="REAL")
    print(f"[PASS] DatasetLoader: Evaluation shape {eval_df.shape}, Baseline shape {base_df.shape}")
    print(f"       Evaluation Fraud class balance: {eval_df['is_fraud'].value_counts().to_dict()}")
    print(f"       Evaluation Foreign IP balance: {eval_df['is_foreign_ip'].value_counts().to_dict()}")
    print(f"       Baseline Fraud class balance: {base_df['is_fraud'].value_counts().to_dict()}")

    # Verify predictions fidelity
    raw_probs = adapter.predict_proba(eval_df)
    p_pos = raw_probs[:, 1] if raw_probs.ndim == 2 else raw_probs
    max_diff = np.max(np.abs(p_pos - eval_df["pred_prob"].values))
    print(f"[PASS] Prediction Fidelity: Max difference between model output and CSV pred_prob = {max_diff:.6f}")
    assert max_diff < 0.001, "pred_prob does not match model inference output!"

    # 3. Data Quality Engine
    dq = DataQualityEngine()
    dq_res = await dq.run_diagnostic(eval_df, target_column="is_fraud", baseline_data=base_df)
    print(f"\n1. Data Quality Engine: Health Score={dq_res.health_score}, Passed={dq_res.passed}, Issues={len(dq_res.issues)}")

    # 4. Data Leakage Engine
    leak = DataLeakageEngine()
    leak_res = await leak.run_diagnostic(eval_df, target_column="is_fraud", baseline_data=base_df)
    print(f"2. Data Leakage Engine: Health Score={leak_res.health_score}, Passed={leak_res.passed}, Issues={len(leak_res.issues)}")

    # 5. Distribution Drift Engine
    drift = DataDriftEngine()
    drift_res = await drift.run_diagnostic(eval_df, target_column="is_fraud", baseline_data=base_df)
    drifted_cols = [f["feature"] for f in drift_res.metadata.get("features", []) if f.get("statistically_significant") or f.get("practical_drift") in ["MODERATE", "HIGH"]]
    stable_cols = [f["feature"] for f in drift_res.metadata.get("features", []) if f["feature"] not in drifted_cols]
    print(f"3. Distribution Drift Engine: Health Score={drift_res.health_score}, Passed={drift_res.passed}")
    print(f"   Drifted Features Detected: {drifted_cols}")
    print(f"   Stable Features: {stable_cols}")
    print(f"   Max PSI Feature: {drift_res.metadata.get('summary', {}).get('maxPsiFeature')} (PSI: {drift_res.metadata.get('summary', {}).get('maxPsi'):.4f})")

    # 6. Performance Engine
    perf = PerformanceEngine()
    perf_res = await perf.run_diagnostic(eval_df, target_column="is_fraud", baseline_data=base_df, model_artifact=adapter, config={"prediction_column": "pred_prob"})
    p_summary = perf_res.metadata.get("summary", {})
    print(f"4. Performance Engine: Health Score={perf_res.health_score}, Passed={perf_res.passed}")
    print(f"   Accuracy: {p_summary.get('accuracy'):.4f}, ROC-AUC: {p_summary.get('rocAuc'):.4f}, F1: {p_summary.get('f1'):.4f}")
    print(f"   Precision: {p_summary.get('precision'):.4f}, Recall: {p_summary.get('recall'):.4f}, ECE: {p_summary.get('expectedCalibrationError'):.4f}")

    # 7. Explainability Engine (TreeSHAP)
    exp = ExplainabilityEngine()
    exp_res = await exp.run_diagnostic(eval_df, target_column="is_fraud", baseline_data=base_df, model_artifact=adapter, config={"storage_uri": str(model_path), "max_explanation_samples": 100})
    top_attribs = [(f["feature"], f.get("meanAbsShap", f.get("importanceMean", 0.0))) for f in exp_res.metadata.get("globalImportance", [])[:4]]
    print(f"5. Explainability Engine: Health Score={exp_res.health_score}, Passed={exp_res.passed}")
    print(f"   Top TreeSHAP Attributions: {top_attribs}")

    # 8. Fairness & Bias Engine
    fair = FairnessEngine()
    fair_res = await fair.run_diagnostic(eval_df, target_column="is_fraud", baseline_data=base_df, model_artifact=adapter, config={"protected_attribute": "is_foreign_ip", "prediction_column": "pred_prob"})
    print(f"6. Fairness & Bias Engine: Health Score={fair_res.health_score}, Passed={fair_res.passed}, Issues={len(fair_res.issues)}")
    print(f"   Evaluated Sensitive Slices: {[g.get('group') for g in fair_res.metadata.get('groups', [])]}")
    disparity = fair_res.metadata.get("disparity", {})
    dp = disparity.get("demographicParity", [])
    di = disparity.get("disparateImpact", [])
    if di:
        print(f"   Disparate Impact ratio: {di[0].get('disparateImpact')}")
    if dp:
        print(f"   Demographic Parity gap: {dp[0].get('difference')}")

    # 9. Robustness Engine
    rob = RobustnessEngine()
    rob_res = await rob.run_diagnostic(eval_df, target_column="is_fraud", baseline_data=base_df, model_artifact=adapter, config={"storage_uri": str(model_path), "max_sample_size": 100})
    print(f"7. Robustness Engine: Health Score={rob_res.health_score}, Passed={rob_res.passed}, Issues={len(rob_res.issues)}")

    # 10. Error Forensics Engine
    ef = ErrorForensicsEngine()
    ef_res = await ef.run_diagnostic(eval_df, target_column="is_fraud", baseline_data=base_df, model_artifact=adapter, config={"prediction_column": "pred_prob"})
    ef_summary = ef_res.metadata.get("summary", {})
    print(f"8. Error Forensics Engine: Health Score={ef_res.health_score}, Passed={ef_res.passed}")
    print(f"   Total Errors: {ef_summary.get('totalErrors')}, Error Rate: {ef_summary.get('overallErrorRate')}, High Conf Errors: {ef_summary.get('highConfidenceErrorCount')}")
    print(f"   FP: {ef_summary.get('fpCount')}, FN: {ef_summary.get('fnCount')}")

if __name__ == "__main__":
    m_path, e_path, b_path = generate_demo_artifacts()
    asyncio.run(run_validation_suite(m_path, e_path, b_path))
