import os
import json
from pathlib import Path
import numpy as np
import pandas as pd
import xgboost as xgb

def generate_diagnostic_data():
    project_root = Path(__file__).resolve().parent.parent
    base_folder = project_root / "Diagnostic Data Check"
    model_folder = base_folder / "Model Artifact"
    eval_folder = base_folder / "Evaluation Dataset"
    base_data_folder = base_folder / "Baseline Reference Dataset"

    model_folder.mkdir(parents=True, exist_ok=True)
    eval_folder.mkdir(parents=True, exist_ok=True)
    base_data_folder.mkdir(parents=True, exist_ok=True)

    print(f"Creating files in: {base_folder}")

    # Feature definitions
    feature_names = [
        "transaction_amount",
        "user_age",
        "user_income",
        "user_velocity_6h",
        "device_trust_score",
        "distance_from_home",
    ]

    # 1. Deterministic Training Data Generation (Seed 42)
    # Train set is separate from Evaluation and Baseline to prevent data leakage!
    rng_train = np.random.RandomState(42)
    n_train = 2000

    train_amount = rng_train.gamma(shape=3.0, scale=35.0, size=n_train) + 5.0
    train_age = rng_train.uniform(18, 75, size=n_train)
    train_income = rng_train.normal(65000, 22000, size=n_train).clip(18000, 250000)
    train_velocity = rng_train.poisson(lam=1.8, size=n_train).astype(float)
    train_trust = rng_train.beta(a=7, b=2, size=n_train) * 100.0
    train_distance = rng_train.exponential(scale=15.0, size=n_train) + 1.0

    # True fraud probability logit
    # Risk factors: high amount, high velocity, low trust score, high distance
    train_logits = (
        -4.2
        + 0.008 * (train_amount - 110)
        + 0.55 * (train_velocity - 1.8)
        - 0.05 * (train_trust - 78)
        + 0.03 * (train_distance - 16)
        + rng_train.normal(0, 0.4, size=n_train)
    )
    train_probs = 1.0 / (1.0 + np.exp(-train_logits))
    train_is_fraud = rng_train.binomial(1, np.clip(train_probs, 0.01, 0.95), size=n_train)

    X_train = pd.DataFrame({
        "transaction_amount": np.round(train_amount, 2),
        "user_age": np.round(train_age, 0),
        "user_income": np.round(train_income, 2),
        "user_velocity_6h": np.round(train_velocity, 0),
        "device_trust_score": np.round(train_trust, 2),
        "distance_from_home": np.round(train_distance, 2),
    })
    y_train = train_is_fraud

    # 2. Train XGBoost Native Model
    dtrain = xgb.DMatrix(X_train, label=y_train)
    params = {
        "max_depth": 4,
        "eta": 0.1,
        "objective": "binary:logistic",
        "eval_metric": "logloss",
        "seed": 42,
    }
    booster = xgb.train(params, dtrain, num_boost_round=35)

    model_path = model_folder / "fraud_model.json"
    booster.save_model(str(model_path))
    print(f"Saved genuine XGBoost native model artifact: {model_path}")

    # Verify Jackson structure in JSON
    with open(model_path, "r", encoding="utf-8") as f:
        model_json = json.load(f)
    extracted_features = model_json.get("learner", {}).get("feature_names", [])
    print(f"Model artifact feature_names in JSON learner: {extracted_features}")
    assert extracted_features == feature_names, "Feature names do not match!"

    # 3. Deterministic Baseline Reference Dataset (Seed 100)
    # Baseline represents reference / historical nominal traffic
    rng_base = np.random.RandomState(100)
    n_base = 800

    base_amount = rng_base.gamma(shape=3.0, scale=35.0, size=n_base) + 5.0
    base_age = rng_base.uniform(18, 75, size=n_base)
    base_income = rng_base.normal(65000, 22000, size=n_base).clip(18000, 250000)
    base_velocity = rng_base.poisson(lam=1.8, size=n_base).astype(float)
    base_trust = rng_base.beta(a=7, b=2, size=n_base) * 100.0
    base_distance = rng_base.exponential(scale=15.0, size=n_base) + 1.0
    base_foreign_ip = rng_base.binomial(1, 0.15, size=n_base)

    base_logits = (
        -4.2
        + 0.008 * (base_amount - 110)
        + 0.55 * (base_velocity - 1.8)
        - 0.05 * (base_trust - 78)
        + 0.03 * (base_distance - 16)
        + rng_base.normal(0, 0.4, size=n_base)
    )
    base_probs = 1.0 / (1.0 + np.exp(-base_logits))
    base_is_fraud = rng_base.binomial(1, np.clip(base_probs, 0.01, 0.95), size=n_base)

    X_base = pd.DataFrame({
        "transaction_amount": np.round(base_amount, 2),
        "user_age": np.round(base_age, 0),
        "user_income": np.round(base_income, 2),
        "user_velocity_6h": np.round(base_velocity, 0),
        "device_trust_score": np.round(base_trust, 2),
        "distance_from_home": np.round(base_distance, 2),
    })

    # Inference using the trained booster
    dbase = xgb.DMatrix(X_base)
    base_pred_prob = np.round(booster.predict(dbase), 4)

    df_baseline = X_base.copy()
    df_baseline["is_foreign_ip"] = base_foreign_ip
    df_baseline["pred_prob"] = base_pred_prob
    df_baseline["is_fraud"] = base_is_fraud

    base_csv_path = base_data_folder / "baseline_dataset.csv"
    df_baseline.to_csv(base_csv_path, index=False)
    print(f"Saved baseline reference dataset: {base_csv_path} (Shape: {df_baseline.shape})")

    # 4. Deterministic Evaluation Dataset (Seed 200)
    # Evaluation represents production traffic with controlled covariate drift
    # Drift injected on:
    # - transaction_amount: shifted higher (holiday/shopping surge, mean ~$195 vs $110)
    # - user_velocity_6h: shifted higher (increased transaction frequency, mean ~3.2 vs 1.8)
    # - distance_from_home: shifted higher (increased travel / remote activity)
    # Stable features (no drift): user_age, user_income, device_trust_score
    rng_eval = np.random.RandomState(200)
    n_eval = 800

    eval_amount = rng_eval.gamma(shape=4.5, scale=42.0, size=n_eval) + 10.0
    eval_age = rng_eval.uniform(18, 75, size=n_eval)
    eval_income = rng_eval.normal(65000, 22000, size=n_eval).clip(18000, 250000)
    eval_velocity = rng_eval.poisson(lam=3.2, size=n_eval).astype(float)
    eval_trust = rng_eval.beta(a=7, b=2, size=n_eval) * 100.0
    eval_distance = rng_eval.exponential(scale=35.0, size=n_eval) + 2.0
    eval_foreign_ip = rng_eval.binomial(1, 0.20, size=n_eval)

    eval_logits = (
        -4.2
        + 0.008 * (eval_amount - 110)
        + 0.55 * (eval_velocity - 1.8)
        - 0.05 * (eval_trust - 78)
        + 0.03 * (eval_distance - 16)
        + rng_eval.normal(0, 0.4, size=n_eval)
    )
    eval_probs = 1.0 / (1.0 + np.exp(-eval_logits))
    eval_is_fraud = rng_eval.binomial(1, np.clip(eval_probs, 0.01, 0.95), size=n_eval)

    X_eval = pd.DataFrame({
        "transaction_amount": np.round(eval_amount, 2),
        "user_age": np.round(eval_age, 0),
        "user_income": np.round(eval_income, 2),
        "user_velocity_6h": np.round(eval_velocity, 0),
        "device_trust_score": np.round(eval_trust, 2),
        "distance_from_home": np.round(eval_distance, 2),
    })

    # Inference using the trained booster
    deval = xgb.DMatrix(X_eval)
    eval_pred_prob = np.round(booster.predict(deval), 4)

    df_eval = X_eval.copy()
    df_eval["is_foreign_ip"] = eval_foreign_ip
    df_eval["pred_prob"] = eval_pred_prob
    df_eval["is_fraud"] = eval_is_fraud

    eval_csv_path = eval_folder / "evaluation_dataset.csv"
    df_eval.to_csv(eval_csv_path, index=False)
    print(f"Saved evaluation dataset: {eval_csv_path} (Shape: {df_eval.shape})")

    # Print summary statistics
    print("\n--- DATA SUMMARY ---")
    print("Baseline Fraud distribution:", df_baseline["is_fraud"].value_counts().to_dict())
    print("Baseline Foreign IP distribution:", df_baseline["is_foreign_ip"].value_counts().to_dict())
    print("Baseline Amount mean/std:", round(df_baseline["transaction_amount"].mean(), 2), round(df_baseline["transaction_amount"].std(), 2))
    print("Evaluation Fraud distribution:", df_eval["is_fraud"].value_counts().to_dict())
    print("Evaluation Foreign IP distribution:", df_eval["is_foreign_ip"].value_counts().to_dict())
    print("Evaluation Amount mean/std:", round(df_eval["transaction_amount"].mean(), 2), round(df_eval["transaction_amount"].std(), 2))
    print("Evaluation Velocity mean:", round(df_eval["user_velocity_6h"].mean(), 2))

if __name__ == "__main__":
    generate_diagnostic_data()
