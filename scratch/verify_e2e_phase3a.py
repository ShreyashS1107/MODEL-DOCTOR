#!/usr/bin/env python3
"""
Phase 3A End-to-End Verification Suite
Validates real model & dataset artifact ingestion, SHA-256 checksumming, secure local storage,
path traversal defense, diagnostic execution across all 7 engines in REAL mode, and artifact provenance persistence.
"""

import os
import sys
import time
import json
import tempfile
import requests
import pandas as pd
import numpy as np
import xgboost as xgb

BACKEND_URL = os.environ.get("BACKEND_URL", "http://localhost:8080")
ML_ENGINE_URL = os.environ.get("ML_ENGINE_URL", "http://localhost:8000")


def print_banner(text: str):
    print("\n" + "=" * 80)
    print(f"  {text}")
    print("=" * 80)


def test_positive_flow_all_seven_engines(temp_dir: str):
    print_banner("TEST 1: Positive End-to-End Flow with Real Artifacts & 7 Diagnostic Engines")

    # 1. Create a real deterministic tabular dataset
    n_samples = 400
    rng = np.random.RandomState(42)
    
    # Features: age, income, transaction_amount, device_trust, is_foreign_ip, is_fraud, pred_prob
    is_foreign_ip = rng.binomial(1, 0.3, size=n_samples)
    user_age = rng.randint(20, 70, size=n_samples).astype(float)
    user_income = rng.normal(50000, 15000, size=n_samples).clip(15000, 150000)
    transaction_amount = rng.exponential(scale=100, size=n_samples) + 10.0
    device_trust = rng.uniform(10, 100, size=n_samples)

    # Ground truth target
    logits = -1.5 + (user_income < 30000) * 1.2 + (transaction_amount > 200) * 1.0 + is_foreign_ip * 0.5
    is_fraud = (1.0 / (1.0 + np.exp(-logits)) > 0.5).astype(int)

    # Train a real XGBoost Booster
    X = pd.DataFrame({
        "user_age": user_age,
        "user_income": user_income,
        "transaction_amount": transaction_amount,
        "device_trust": device_trust,
        "is_foreign_ip": is_foreign_ip,
    })
    y = is_fraud
    dtrain = xgb.DMatrix(X, label=y)
    params = {"max_depth": 3, "eta": 0.1, "objective": "binary:logistic", "eval_metric": "logloss"}
    booster = xgb.train(params, dtrain, num_boost_round=10)

    # Evaluate predictions
    pred_prob = np.round(booster.predict(dtrain), 4)

    # Save real model artifact (.json)
    model_file_path = os.path.join(temp_dir, "fraud_real_model.json")
    booster.save_model(model_file_path)
    print(f"[OK] Trained & saved real XGBoost model artifact: {model_file_path}")

    # Create Evaluation Dataset CSV
    eval_df = X.copy()
    eval_df["is_fraud"] = is_fraud
    eval_df["pred_prob"] = pred_prob
    eval_file_path = os.path.join(temp_dir, "eval_dataset_real.csv")
    eval_df.to_csv(eval_file_path, index=False)
    print(f"[OK] Created real evaluation dataset CSV: {eval_file_path} ({len(eval_df)} rows, {len(eval_df.columns)} cols)")

    # Create Baseline Dataset CSV (for drift & leakage baseline comparisons)
    baseline_df = eval_df.copy()
    baseline_df["transaction_amount"] = baseline_df["transaction_amount"] * 0.95
    baseline_file_path = os.path.join(temp_dir, "baseline_dataset_real.csv")
    baseline_df.to_csv(baseline_file_path, index=False)
    print(f"[OK] Created real baseline dataset CSV: {baseline_file_path}")

    # Step 2: Upload Model Artifact
    with open(model_file_path, "rb") as f:
        resp = requests.post(
            f"{BACKEND_URL}/api/artifacts/models",
            files={"file": ("fraud_real_model.json", f, "application/json")},
            data={"framework": "xgboost", "taskType": "binary_classification"},
            timeout=10,
        )
    assert resp.status_code == 201, f"Model upload failed: {resp.status_code} {resp.text}"
    model_artifact = resp.json()
    model_id = model_artifact["id"]
    model_sha256 = model_artifact["sha256"]
    print(f"[OK] Uploaded model artifact. ID: {model_id}, SHA-256: {model_sha256}, Status: {model_artifact['status']}")

    # Step 3: Upload Evaluation Dataset
    with open(eval_file_path, "rb") as f:
        resp = requests.post(
            f"{BACKEND_URL}/api/artifacts/datasets",
            files={"file": ("eval_dataset_real.csv", f, "text/csv")},
            timeout=10,
        )
    assert resp.status_code == 201, f"Evaluation dataset upload failed: {resp.status_code} {resp.text}"
    eval_artifact = resp.json()
    eval_id = eval_artifact["id"]
    eval_sha256 = eval_artifact["sha256"]
    print(f"[OK] Uploaded evaluation dataset. ID: {eval_id}, SHA-256: {eval_sha256}, Rows: {eval_artifact['rowCount']}, Cols: {eval_artifact['columnCount']}")

    # Step 4: Upload Baseline Dataset
    with open(baseline_file_path, "rb") as f:
        resp = requests.post(
            f"{BACKEND_URL}/api/artifacts/datasets",
            files={"file": ("baseline_dataset_real.csv", f, "text/csv")},
            timeout=10,
        )
    assert resp.status_code == 201, f"Baseline dataset upload failed: {resp.status_code} {resp.text}"
    baseline_artifact = resp.json()
    baseline_id = baseline_artifact["id"]
    baseline_sha256 = baseline_artifact["sha256"]
    print(f"[OK] Uploaded baseline dataset. ID: {baseline_id}, SHA-256: {baseline_sha256}")

    # Step 5: Register Diagnostic Run using Artifact IDs
    create_payload = {
        "modelArtifactId": model_id,
        "evaluationDatasetArtifactId": eval_id,
        "baselineDatasetArtifactId": baseline_id,
        "targetColumn": "is_fraud",
        "predictionColumn": "pred_prob",
        "protectedAttribute": "is_foreign_ip",
        "modules": [
            "DATA_QUALITY",
            "LEAKAGE",
            "DRIFT",
            "PERFORMANCE",
            "EXPLAINABILITY",
            "BIAS",
            "ROBUSTNESS",
        ],
    }
    resp = requests.post(f"{BACKEND_URL}/api/diagnostics", json=create_payload, timeout=10)
    assert resp.status_code == 201, f"Run registration failed: {resp.status_code} {resp.text}"
    run_dto = resp.json()
    run_id = run_dto["id"]
    assert run_dto["status"] == "CREATED"
    assert run_dto["executionMode"] == "REAL"
    assert run_dto["modelArtifactId"] == model_id
    assert run_dto["evaluationDatasetArtifactId"] == eval_id
    assert run_dto["baselineDatasetArtifactId"] == baseline_id
    print(f"[OK] Diagnostic run registered with Artifact IDs. Run ID: {run_id}, Execution Mode: REAL")

    # Step 6: Trigger Diagnostic Execution
    resp = requests.post(f"{BACKEND_URL}/api/diagnostics/{run_id}/run", timeout=60)
    assert resp.status_code == 200, f"Run execution failed: {resp.status_code} {resp.text}"
    executed_run = resp.json()
    print(f"[OK] ML engine execution finished with status: {executed_run['status']}")
    assert executed_run["status"] == "COMPLETED", f"Expected COMPLETED but got {executed_run['status']}"

    # Step 7: Retrieve Structured Diagnostic Results & Verify Provenance
    resp = requests.get(f"{BACKEND_URL}/api/diagnostics/{run_id}/results", timeout=10)
    assert resp.status_code == 200, f"Results query failed: {resp.status_code} {resp.text}"
    results_dto = resp.json()
    modules_results = results_dto["results"]
    assert len(modules_results) == 7, f"Expected 7 modules, got {len(modules_results)}"

    print("\n--- Module Results Breakdown ---")
    for mod in modules_results:
        print(f"  [{mod['status']}] {mod['module']}: {mod.get('statusMessage', '')}")
        assert mod["status"] == "COMPLETED", f"Module {mod['module']} was not COMPLETED: {mod.get('statusMessage')}"
        assert mod["result"] is not None and len(mod["result"]) > 0

    # Step 8: Verify Provenance Metadata in Run Record
    resp = requests.get(f"{BACKEND_URL}/api/diagnostics/{run_id}", timeout=10)
    assert resp.status_code == 200
    run_provenance = resp.json()
    assert run_provenance["modelArtifact"]["sha256"] == model_sha256
    assert run_provenance["evaluationDatasetArtifact"]["sha256"] == eval_sha256
    assert run_provenance["baselineDatasetArtifact"]["sha256"] == baseline_sha256
    assert run_provenance["executionMode"] == "REAL"
    print("\n[SUCCESS] Provenance Verified:")
    print(f"  Model Artifact:      {run_provenance['modelArtifact']['originalFilename']} ({run_provenance['modelArtifact']['sha256'][:12]}...)")
    print(f"  Evaluation Dataset:  {run_provenance['evaluationDatasetArtifact']['originalFilename']} ({run_provenance['evaluationDatasetArtifact']['sha256'][:12]}...)")
    print(f"  Baseline Dataset:    {run_provenance['baselineDatasetArtifact']['originalFilename']} ({run_provenance['baselineDatasetArtifact']['sha256'][:12]}...)")


def test_negative_cases():
    print_banner("TEST 2: Negative & Security Validation Tests")

    # 1. Path Traversal in Filename
    print("Test 2.1: Path Traversal filename upload...")
    resp = requests.post(
        f"{BACKEND_URL}/api/artifacts/models",
        files={"file": ("../../../../etc/shadow.json", b'{"dummy": 1}', "application/json")},
        timeout=10,
    )
    assert resp.status_code == 201
    sanitized_name = resp.json()["originalFilename"]
    assert ".." not in sanitized_name and "/" not in sanitized_name and "\\" not in sanitized_name
    print(f"  [PASS] Path traversal sanitized to: {sanitized_name}")

    # 2. Unsupported Model Extension
    print("Test 2.2: Unsupported model format...")
    resp = requests.post(
        f"{BACKEND_URL}/api/artifacts/models",
        files={"file": ("malicious.exe", b"executable bytes", "application/octet-stream")},
        timeout=10,
    )
    assert resp.status_code == 400
    print(f"  [PASS] Executable extension rejected (HTTP 400): {resp.json().get('message')}")

    # 3. Empty File Upload
    print("Test 2.3: Empty file upload...")
    resp = requests.post(
        f"{BACKEND_URL}/api/artifacts/datasets",
        files={"file": ("empty.csv", b"", "text/csv")},
        timeout=10,
    )
    assert resp.status_code == 400
    print(f"  [PASS] Empty file rejected (HTTP 400): {resp.json().get('message')}")

    # 4. Non-existent Artifact ID in Diagnostic Run
    print("Test 2.4: Non-existent Artifact ID in Diagnostic Run...")
    resp = requests.post(
        f"{BACKEND_URL}/api/diagnostics",
        json={
            "modelArtifactId": "mdl_non_existent_99999",
            "evaluationDataset": "some_dataset.csv",
            "targetColumn": "is_fraud",
            "modules": ["DATA_QUALITY"],
        },
        timeout=10,
    )
    assert resp.status_code == 404
    print(f"  [PASS] Non-existent artifact ID rejected (HTTP 404): {resp.json().get('message')}")

    # 5. Missing Target Column in Evaluation Dataset
    print("Test 2.5: Missing Target Column compatibility validation...")
    resp = requests.post(
        f"{BACKEND_URL}/api/artifacts/datasets",
        files={"file": ("no_target.csv", b"col_a,col_b\n1,2\n3,4\n", "text/csv")},
        timeout=10,
    )
    assert resp.status_code == 201
    no_target_id = resp.json()["id"]

    resp = requests.post(
        f"{BACKEND_URL}/api/artifacts/models",
        files={"file": ("valid.json", b'{"features":["col_a"]}', "application/json")},
        timeout=10,
    )
    assert resp.status_code == 201
    valid_model_id = resp.json()["id"]

    resp = requests.post(
        f"{BACKEND_URL}/api/diagnostics",
        json={
            "modelArtifactId": valid_model_id,
            "evaluationDatasetArtifactId": no_target_id,
            "targetColumn": "is_fraud",
            "modules": ["LEAKAGE"],
        },
        timeout=10,
    )
    assert resp.status_code == 400
    print(f"  [PASS] Missing target column in dataset rejected (HTTP 400): {resp.json().get('message')}")

    # 6. Missing Protected Attribute in Dataset when Bias module requested
    print("Test 2.6: Missing Protected Attribute compatibility validation...")
    resp = requests.post(
        f"{BACKEND_URL}/api/artifacts/datasets",
        files={"file": ("no_prot.csv", b"col_a,is_fraud\n1,0\n3,1\n", "text/csv")},
        timeout=10,
    )
    assert resp.status_code == 201
    no_prot_id = resp.json()["id"]

    resp = requests.post(
        f"{BACKEND_URL}/api/diagnostics",
        json={
            "modelArtifactId": valid_model_id,
            "evaluationDatasetArtifactId": no_prot_id,
            "targetColumn": "is_fraud",
            "protectedAttribute": "gender",
            "modules": ["BIAS"],
        },
        timeout=10,
    )
    assert resp.status_code == 400
    print(f"  [PASS] Missing protected attribute in dataset rejected (HTTP 400): {resp.json().get('message')}")


def main():
    print_banner("MODEL DOCTOR — PHASE 3A E2E VERIFICATION SUITE")
    print(f"Backend Target:   {BACKEND_URL}")
    print(f"ML Engine Target: {ML_ENGINE_URL}")

    # Check services health
    try:
        r_back = requests.get(f"{BACKEND_URL}/actuator/health", timeout=5)
        print(f"[OK] Backend health: {r_back.status_code} {r_back.json()}")
    except Exception as e:
        print(f"[ERROR] Cannot connect to Spring Boot backend at {BACKEND_URL}: {e}")
        sys.exit(1)

    try:
        r_ml = requests.get(f"{ML_ENGINE_URL}/health", timeout=5)
        print(f"[OK] ML Engine health: {r_ml.status_code} {r_ml.json()}")
    except Exception as e:
        print(f"[ERROR] Cannot connect to Python ML Engine at {ML_ENGINE_URL}: {e}")
        sys.exit(1)

    with tempfile.TemporaryDirectory() as temp_dir:
        test_positive_flow_all_seven_engines(temp_dir)
        test_negative_cases()

    print_banner("ALL PHASE 3A END-TO-END VERIFICATION CHECKS PASSED (100%)")


if __name__ == "__main__":
    main()
