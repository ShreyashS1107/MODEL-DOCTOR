import os
import requests
import numpy as np
import pandas as pd
import xgboost as xgb

BASE_URL = "http://localhost:8080"
PYTHON_URL = "http://localhost:8000"

def log(msg):
    print(f"[DEMO SETUP] {msg}", flush=True)

def generate_and_ingest_demo_artifacts():
    log("==================================================================")
    log("MODEL DOCTOR — SEEDING REAL ARTIFACTS FOR LIVE MENTOR DEMONSTRATION")
    log("==================================================================")
    
    # 1. Verify Services
    try:
        r_health = requests.get(f"{BASE_URL}/api/health", timeout=3)
        if r_health.status_code != 200:
            log(f"Error: Spring Boot backend returned {r_health.status_code}")
            return
        log("Spring Boot Orchestrator is UP and healthy.")
    except Exception as e:
        log(f"Error connecting to Spring Boot at {BASE_URL}: {e}")
        log("Please ensure Spring Boot is running on port 8080.")
        return

    os.makedirs("demo_artifacts", exist_ok=True)
    np.random.seed(42)
    n = 1000

    # 2. Synthesize Real-World Financial Tabular Data
    transaction_amount = np.round(np.random.exponential(scale=120.0, size=n) + 5.0, 2)
    user_age = np.random.randint(18, 75, size=n)
    num_failed_logins = np.random.poisson(lam=0.4, size=n)
    account_balance = np.round(np.random.normal(loc=4500.0, scale=2500.0, size=n).clip(0, 50000), 2)
    device_trust_score = np.round(np.random.beta(a=5, b=2, size=n) * 100, 1)
    is_foreign_ip = np.random.choice([0, 1], size=n, p=[0.82, 0.18])

    # True Fraud Risk Generation Function
    risk_logits = (
        (transaction_amount / 200.0) * 0.8 +
        (num_failed_logins * 1.2) +
        (is_foreign_ip * 0.9) -
        (device_trust_score / 50.0) * 0.7 -
        (account_balance / 10000.0) * 0.3 -
        1.5
    )
    prob_true = 1.0 / (1.0 + np.exp(-risk_logits))
    is_fraud = np.random.binomial(n=1, p=prob_true)

    # 3. Train Real XGBoost Model on Historical Sample
    X_features = pd.DataFrame({
        "transaction_amount": transaction_amount,
        "user_age": user_age,
        "num_failed_logins": num_failed_logins,
        "account_balance": account_balance,
        "device_trust_score": device_trust_score,
        "is_foreign_ip": is_foreign_ip
    })

    dtrain = xgb.DMatrix(X_features, label=is_fraud)
    params = {
        "max_depth": 4,
        "eta": 0.1,
        "objective": "binary:logistic",
        "eval_metric": "logloss"
    }
    booster = xgb.train(params, dtrain, num_boost_round=25)

    model_file = "demo_artifacts/fraud_xgboost_model_v2.json"
    booster.save_model(model_file)
    log(f"Trained & saved real XGBoost model to {model_file}")

    # Model inference prediction probabilities
    pred_prob = np.round(booster.predict(dtrain), 4)

    # 4. Evaluation Dataset (Q3 Production Batch)
    df_eval = X_features.copy()
    df_eval["is_fraud"] = is_fraud
    df_eval["pred_prob"] = pred_prob
    eval_file = "demo_artifacts/fraud_evaluation_q3.csv"
    df_eval.to_csv(eval_file, index=False)
    log(f"Generated Evaluation Dataset (1000 rows) at {eval_file}")

    # 5. Baseline Dataset (Q2 Reference Batch with Subtle Natural Drift)
    df_base = df_eval.copy()
    # Apply natural shift in transaction amount and foreign IP for drift diagnostics
    df_base["transaction_amount"] = np.round(np.random.exponential(scale=95.0, size=n) + 5.0, 2)
    df_base["is_foreign_ip"] = np.random.choice([0, 1], size=n, p=[0.90, 0.10])
    base_file = "demo_artifacts/fraud_baseline_q2.csv"
    df_base.to_csv(base_file, index=False)
    log(f"Generated Baseline Dataset (1000 rows) at {base_file}")

    # 6. Ingest Model into Model Doctor via REST API
    log("Uploading model artifact to Model Doctor...")
    with open(model_file, "rb") as f:
        r_model = requests.post(
            f"{BASE_URL}/api/artifacts/models",
            files={"file": (os.path.basename(model_file), f, "application/json")},
            data={
                "framework": "xgboost",
                "taskType": "binary_classification",
                "description": "Production Fraud Detection Classifier (XGBoost 4-Depth Trees)"
            }
        )
    if r_model.status_code == 201:
        model_meta = r_model.json()
        log(f" Model Ingested: ID={model_meta['id']} (SHA256: {model_meta.get('sha256', '')[:12]}...)")
    else:
        log(f"Model Ingestion Failed: {r_model.status_code} - {r_model.text}")
        return

    # 7. Ingest Datasets into Model Doctor via REST API
    log("Uploading evaluation dataset artifact to Model Doctor...")
    with open(eval_file, "rb") as f:
        r_eval = requests.post(
            f"{BASE_URL}/api/artifacts/datasets",
            files={"file": (os.path.basename(eval_file), f, "text/csv")}
        )
    if r_eval.status_code == 201:
        eval_meta = r_eval.json()
        log(f" Evaluation Dataset Ingested: ID={eval_meta['id']} ({eval_meta['rowCount']} rows, {eval_meta['columnCount']} cols)")
    else:
        log(f"Evaluation Dataset Ingestion Failed: {r_eval.status_code} - {r_eval.text}")
        return

    log("Uploading baseline dataset artifact to Model Doctor...")
    with open(base_file, "rb") as f:
        r_base = requests.post(
            f"{BASE_URL}/api/artifacts/datasets",
            files={"file": (os.path.basename(base_file), f, "text/csv")}
        )
    if r_base.status_code == 201:
        base_meta = r_base.json()
        log(f" Baseline Dataset Ingested: ID={base_meta['id']} ({base_meta['rowCount']} rows, {base_meta['columnCount']} cols)")
    else:
        log(f"Baseline Dataset Ingestion Failed: {r_base.status_code} - {r_base.text}")
        return

    log("==================================================================")
    log("DEMO ARTIFACTS READY IN STORED INVENTORY:")
    log(f"  • Model Artifact ID:              {model_meta['id']}")
    log(f"  • Evaluation Dataset Artifact ID:  {eval_meta['id']}")
    log(f"  • Baseline Dataset Artifact ID:    {base_meta['id']}")
    log("==================================================================")

if __name__ == "__main__":
    generate_and_ingest_demo_artifacts()
