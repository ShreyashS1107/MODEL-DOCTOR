import requests
import json
import time
import os
import sys
import pandas as pd
import numpy as np
import xgboost as xgb
from sklearn.datasets import make_classification

BASE_URL = "http://localhost:8080"
ML_URL = "http://localhost:8000"

def log(msg):
    print(f"[E2E Phase 3B] {msg}", flush=True)

def wait_for_backend():
    log("Waiting for Spring Boot backend to become ready...")
    for _ in range(30):
        try:
            r = requests.get(f"{BASE_URL}/actuator/health", timeout=2)
            if r.status_code == 200:
                log("Backend is ready.")
                return True
        except Exception:
            pass
        time.sleep(1)
    raise RuntimeError("Backend failed to start in time.")

def scenario_a_reuse():
    log("\n========================================================")
    log("SCENARIO A — Artifact Reuse Across Multiple Runs")
    log("========================================================")
    
    # 1. Create a dummy model and datasets
    X, y = make_classification(n_samples=200, n_features=4, n_informative=3, n_redundant=0, random_state=42)
    feature_names = ["feat_a", "feat_b", "feat_c", "feat_d"]
    df = pd.DataFrame(X, columns=feature_names)
    df["target"] = y
    df["protected_group"] = np.random.choice(["group_0", "group_1"], size=len(df))
    
    # Train XGBoost
    dtrain = xgb.DMatrix(df[feature_names], label=df["target"])
    bst = xgb.train({"max_depth": 3, "objective": "binary:logistic", "eval_metric": "logloss"}, dtrain, num_boost_round=10)
    
    os.makedirs("scratch_e2e", exist_ok=True)
    model_path = "scratch_e2e/scenario_a_model.json"
    bst.save_model(model_path)
    
    eval_csv = "scratch_e2e/scenario_a_eval.csv"
    baseline_csv = "scratch_e2e/scenario_a_baseline.csv"
    df.to_csv(eval_csv, index=False)
    df.to_csv(baseline_csv, index=False)
    
    # Upload artifacts
    with open(model_path, "rb") as mf:
        r = requests.post(f"{BASE_URL}/api/artifacts/models", files={"file": mf}, data={"framework": "xgboost", "taskType": "binary_classification"})
        assert r.status_code == 201, f"Model upload failed: {r.text}"
        model_id = r.json()["id"]
        log(f"Uploaded Model: {model_id}")
        
    with open(eval_csv, "rb") as ef:
        r = requests.post(f"{BASE_URL}/api/artifacts/datasets", files={"file": ef})
        assert r.status_code == 201, f"Eval upload failed: {r.text}"
        eval_id = r.json()["id"]
        log(f"Uploaded Eval Dataset: {eval_id}")
        
    with open(baseline_csv, "rb") as bf:
        r = requests.post(f"{BASE_URL}/api/artifacts/datasets", files={"file": bf})
        assert r.status_code == 201, f"Baseline upload failed: {bf.text}"
        baseline_id = r.json()["id"]
        log(f"Uploaded Baseline Dataset: {baseline_id}")

    # Validate listing contains them
    models_list = requests.get(f"{BASE_URL}/api/artifacts/models").json()
    assert any(m["id"] == model_id for m in models_list), "Model not in active listing"
    datasets_list = requests.get(f"{BASE_URL}/api/artifacts/datasets").json()
    assert any(d["id"] == eval_id for d in datasets_list), "Eval dataset not in active listing"
    
    # Preflight validate
    val_req = {
        "executionMode": "REAL",
        "modelArtifactId": model_id,
        "evaluationDatasetArtifactId": eval_id,
        "baselineDatasetArtifactId": baseline_id,
        "targetColumn": "target",
        "protectedAttribute": "protected_group",
        "selectedModules": ["DATA_QUALITY", "LEAKAGE", "DRIFT", "PERFORMANCE", "EXPLAINABILITY", "BIAS", "ROBUSTNESS"]
    }
    vr = requests.post(f"{BASE_URL}/api/diagnostics/validate", json=val_req).json()
    assert vr["valid"] is True, f"Preflight validation failed: {vr}"
    log("Preflight validation passed for Run 1.")
    
    # Create Run 1
    create_req = {
        "model": {
            "name": "Scenario A Run 1",
            "framework": "xgboost",
            "taskType": "binary_classification"
        },
        "executionMode": "REAL",
        "modelArtifactId": model_id,
        "evaluationDatasetArtifactId": eval_id,
        "baselineDatasetArtifactId": baseline_id,
        "targetColumn": "target",
        "protectedAttribute": "protected_group",
        "modules": ["DATA_QUALITY", "LEAKAGE", "DRIFT", "PERFORMANCE", "EXPLAINABILITY", "BIAS", "ROBUSTNESS"]
    }
    r1 = requests.post(f"{BASE_URL}/api/diagnostics", json=create_req)
    assert r1.status_code == 201, f"Run 1 creation failed: {r1.text}"
    run1_id = r1.json()["id"]
    log(f"Created Run 1: {run1_id}. Triggering execution...")
    
    start1 = requests.post(f"{BASE_URL}/api/diagnostics/{run1_id}/run")
    assert start1.status_code == 200, f"Run 1 start failed: {start1.text}"
    
    # Wait for Run 1 completion
    status1 = wait_for_run_completion(run1_id)
    assert status1 == "COMPLETED", f"Run 1 status is {status1}"
    log("Run 1 completed successfully.")
    
    # Reuse exact same artifacts for Run 2 without uploading
    create_req2 = {
        "model": {
            "name": "Scenario A Run 2 (Reused Artifacts)",
            "framework": "xgboost",
            "taskType": "binary_classification"
        },
        "executionMode": "REAL",
        "modelArtifactId": model_id,
        "evaluationDatasetArtifactId": eval_id,
        "baselineDatasetArtifactId": baseline_id,
        "targetColumn": "target",
        "protectedAttribute": "protected_group",
        "modules": ["DATA_QUALITY", "PERFORMANCE"]
    }
    r2 = requests.post(f"{BASE_URL}/api/diagnostics", json=create_req2)
    assert r2.status_code == 201, f"Run 2 creation failed: {r2.text}"
    run2_id = r2.json()["id"]
    log(f"Created Run 2 with Reused Artifacts: {run2_id}. Triggering execution...")
    
    start2 = requests.post(f"{BASE_URL}/api/diagnostics/{run2_id}/run")
    assert start2.status_code == 200, f"Run 2 start failed: {start2.text}"
    
    status2 = wait_for_run_completion(run2_id)
    assert status2 == "COMPLETED", f"Run 2 status is {status2}"
    log("Run 2 completed successfully using reused artifacts!")
    return model_id, eval_id, baseline_id, run1_id

def scenario_b_incompatible_schema(model_id, baseline_id):
    log("\n========================================================")
    log("SCENARIO B — Incompatible Feature Schema Detection")
    log("========================================================")
    # Create dataset missing required features
    df_bad = pd.DataFrame({"feat_a": [1.0, 2.0], "wrong_feat": [3.0, 4.0], "target": [0, 1]})
    bad_csv = "scratch_e2e/bad_schema.csv"
    df_bad.to_csv(bad_csv, index=False)
    
    with open(bad_csv, "rb") as f:
        r = requests.post(f"{BASE_URL}/api/artifacts/datasets", files={"file": f})
        assert r.status_code == 201
        bad_eval_id = r.json()["id"]
        
    val_req = {
        "executionMode": "REAL",
        "modelArtifactId": model_id,
        "evaluationDatasetArtifactId": bad_eval_id,
        "baselineDatasetArtifactId": baseline_id,
        "targetColumn": "target",
        "selectedModules": ["DATA_QUALITY", "PERFORMANCE"]
    }
    vr = requests.post(f"{BASE_URL}/api/diagnostics/validate", json=val_req).json()
    assert vr["valid"] is False, "Preflight should fail for incompatible schema"
    error_codes = [e["code"] for e in vr["errors"]]
    assert "MISSING_MODEL_FEATURE" in error_codes, f"Expected MISSING_MODEL_FEATURE, got: {error_codes}"
    log(f"Preflight correctly rejected incompatible schema: {vr['errors']}")
    
    # Test direct creation rejection (bypassing preflight)
    create_bad = {
        "model": {
            "name": "Scenario B Invalid",
            "framework": "xgboost",
            "taskType": "binary_classification"
        },
        "executionMode": "REAL",
        "modelArtifactId": model_id,
        "evaluationDatasetArtifactId": bad_eval_id,
        "baselineDatasetArtifactId": baseline_id,
        "targetColumn": "target",
        "modules": ["PERFORMANCE"]
    }
    res = requests.post(f"{BASE_URL}/api/diagnostics", json=create_bad)
    assert res.status_code == 400, f"Expected 400 Bad Request, got {res.status_code}"
    assert "MISSING_MODEL_FEATURE" in res.text, f"Expected error code in response: {res.text}"
    log("Backend directly rejected invalid run creation attempt with 400 Bad Request.")

def scenario_c_multiclass_rejection(eval_id, baseline_id):
    log("\n========================================================")
    log("SCENARIO C — Multiclass Task Rejection")
    log("========================================================")
    # Create distinct multiclass model dummy
    model_content = json.dumps({
        "learner": {
            "attributes": {"scikit_learn": "{\"classes_\": [0, 1, 2], \"n_classes_\": 3, \"unique_tag\": \"scenario_c_unique\"}"},
            "learner_model_param": {"num_feature": "4", "num_class": "3"}
        }
    })
    multi_path = "scratch_e2e/scenario_c_multi.json"
    with open(multi_path, "w") as f:
        f.write(model_content)
        
    with open(multi_path, "rb") as mf:
        r = requests.post(f"{BASE_URL}/api/artifacts/models", files={"file": mf}, data={"framework": "xgboost", "taskType": "multiclass"})
        assert r.status_code == 201
        multi_model_id = r.json()["id"]
        
    val_req = {
        "executionMode": "REAL",
        "modelArtifactId": multi_model_id,
        "evaluationDatasetArtifactId": eval_id,
        "baselineDatasetArtifactId": baseline_id,
        "targetColumn": "target",
        "selectedModules": ["PERFORMANCE"]
    }
    vr = requests.post(f"{BASE_URL}/api/diagnostics/validate", json=val_req).json()
    assert vr["valid"] is False, "Preflight should reject multiclass"
    error_codes = [e["code"] for e in vr["errors"]]
    assert "MULTICLASS_NOT_SUPPORTED" in error_codes, f"Expected MULTICLASS_NOT_SUPPORTED, got: {error_codes}"
    log(f"Preflight correctly rejected multiclass model: {vr['errors']}")
    
    # Direct creation test
    create_multi = {
        "model": {
            "name": "Scenario C Multiclass",
            "framework": "xgboost",
            "taskType": "multiclass"
        },
        "executionMode": "REAL",
        "modelArtifactId": multi_model_id,
        "evaluationDatasetArtifactId": eval_id,
        "targetColumn": "target",
        "modules": ["PERFORMANCE"]
    }
    res = requests.post(f"{BASE_URL}/api/diagnostics", json=create_multi)
    assert res.status_code == 400
    assert "MULTICLASS_NOT_SUPPORTED" in res.text
    log("Backend directly rejected multiclass run creation with 400 Bad Request.")

def scenario_d_soft_deletion(model_id, run1_id, eval_id):
    log("\n========================================================")
    log("SCENARIO D — Soft Deletion & Provenance Preservation")
    log("========================================================")
    # Check historical run before deletion
    run_before = requests.get(f"{BASE_URL}/api/diagnostics/{run1_id}").json()
    assert run_before["status"] == "COMPLETED"
    assert run_before["modelArtifactId"] == model_id
    
    # Soft delete the model artifact
    del_res = requests.delete(f"{BASE_URL}/api/artifacts/models/{model_id}")
    assert del_res.status_code == 204, f"Delete failed: {del_res.status_code}"
    log(f"Successfully soft-deleted model artifact: {model_id}")
    
    # Verify it is excluded from active listing
    active_models = requests.get(f"{BASE_URL}/api/artifacts/models").json()
    assert not any(m["id"] == model_id for m in active_models), "Soft-deleted model still in active listing!"
    log("Soft-deleted model successfully excluded from active artifact selection list.")
    
    # Verify historical run is still intact and readable
    run_after = requests.get(f"{BASE_URL}/api/diagnostics/{run1_id}").json()
    assert run_after["status"] == "COMPLETED", "Historical run corrupted after artifact deletion"
    assert run_after["modelArtifactId"] == model_id, "Provenance artifact ID lost"
    log("Historical diagnostic run remains 100% accessible with full provenance intact.")
    
    # Verify new run cannot use the soft-deleted artifact
    new_run_req = {
        "model": {
            "name": "Scenario D Invalid Reuse",
            "framework": "xgboost",
            "taskType": "binary_classification"
        },
        "executionMode": "REAL",
        "modelArtifactId": model_id,
        "evaluationDatasetArtifactId": eval_id,
        "targetColumn": "target",
        "modules": ["DATA_QUALITY"]
    }
    r = requests.post(f"{BASE_URL}/api/diagnostics", json=new_run_req)
    assert r.status_code in [400, 404], f"Expected rejection of deleted artifact, got {r.status_code}"
    log(f"Attempt to create new run with soft-deleted artifact correctly rejected: {r.status_code}")

def scenario_e_full_real_run():
    log("\n========================================================")
    log("SCENARIO E — Full Valid REAL Run Across All 7 Modules")
    log("========================================================")
    np.random.seed(99)
    n = 300
    age = np.random.randint(18, 70, size=n)
    income = np.random.exponential(scale=50000, size=n) + 20000
    credit_score = np.random.normal(loc=650, scale=80, size=n)
    loan_amount = np.random.uniform(5000, 50000, size=n)
    gender = np.random.choice(["Male", "Female"], size=n)
    
    # Target logic: prob depends on features
    logits = (age * 0.02) - (income * 0.00002) - (credit_score * 0.005) + (loan_amount * 0.00005)
    probs = 1 / (1 + np.exp(-logits))
    fraud = (np.random.rand(n) < probs).astype(int)
    
    features = ["age", "income", "credit_score", "loan_amount"]
    eval_df = pd.DataFrame({
        "age": age,
        "income": income,
        "credit_score": credit_score,
        "loan_amount": loan_amount,
        "gender": gender,
        "fraud": fraud
    })
    
    # Baseline with slightly shifted distributions
    base_n = 250
    base_df = pd.DataFrame({
        "age": np.random.randint(20, 65, size=base_n),
        "income": np.random.exponential(scale=48000, size=base_n) + 18000,
        "credit_score": np.random.normal(loc=640, scale=75, size=base_n),
        "loan_amount": np.random.uniform(4000, 45000, size=base_n),
        "gender": np.random.choice(["Male", "Female"], size=base_n),
        "fraud": np.random.choice([0, 1], size=base_n, p=[0.8, 0.2])
    })
    
    # Train model on features
    dtrain = xgb.DMatrix(eval_df[features], label=eval_df["fraud"])
    model = xgb.train({"max_depth": 4, "objective": "binary:logistic", "eval_metric": "logloss"}, dtrain, num_boost_round=15)
    
    m_path = "scratch_e2e/scenario_e_model.json"
    e_path = "scratch_e2e/scenario_e_eval.csv"
    b_path = "scratch_e2e/scenario_e_baseline.csv"
    model.save_model(m_path)
    eval_df.to_csv(e_path, index=False)
    base_df.to_csv(b_path, index=False)
    
    # Upload model
    with open(m_path, "rb") as f:
        r = requests.post(f"{BASE_URL}/api/artifacts/models", files={"file": f}, data={"framework": "xgboost", "taskType": "binary_classification"})
        assert r.status_code == 201
        m_id = r.json()["id"]
        assert r.json()["featureCount"] == 4
        assert set(r.json()["featureNames"]) == set(features)
        
    # Upload eval dataset
    with open(e_path, "rb") as f:
        r = requests.post(f"{BASE_URL}/api/artifacts/datasets", files={"file": f})
        assert r.status_code == 201
        e_id = r.json()["id"]
        schema = r.json()["schemaSummary"]
        assert schema is not None
        assert schema["rowCount"] == 300
        assert schema["columnCount"] == 6
        
        # Verify rich column profiling
        columns = schema["columns"]
        assert len(columns) == 6
        fraud_col = next((c for c in columns if c["name"] == "fraud"), None)
        assert fraud_col is not None
        assert fraud_col["uniqueCount"] == 2
        assert fraud_col["nullCount"] == 0
        log(f"Dataset profiling verified for column 'fraud': uniqueCount={fraud_col['uniqueCount']}, classification={fraud_col['classification']}")
        
    # Upload baseline dataset
    with open(b_path, "rb") as f:
        r = requests.post(f"{BASE_URL}/api/artifacts/datasets", files={"file": f})
        assert r.status_code == 201
        b_id = r.json()["id"]
        
    # Preflight Validation
    val_req = {
        "executionMode": "REAL",
        "modelArtifactId": m_id,
        "evaluationDatasetArtifactId": e_id,
        "baselineDatasetArtifactId": b_id,
        "targetColumn": "fraud",
        "protectedAttribute": "gender",
        "selectedModules": ["DATA_QUALITY", "LEAKAGE", "DRIFT", "PERFORMANCE", "EXPLAINABILITY", "BIAS", "ROBUSTNESS"]
    }
    vr = requests.post(f"{BASE_URL}/api/diagnostics/validate", json=val_req).json()
    assert vr["valid"] is True, f"Preflight failed: {vr}"
    assert vr["compatibility"]["isFeatureCompatible"] is True
    for mod, mod_res in vr["moduleValidation"].items():
        assert mod_res["compatible"] is True, f"Module prerequisite not satisfied for {mod}: {mod_res}"
        
    # Verify target suggestion intelligence from preflight
    target_suggs = vr.get("targetSuggestions", [])
    assert len(target_suggs) > 0
    top_sugg = target_suggs[0]
    assert top_sugg["column"] == "fraud", f"Top target suggestion should be 'fraud', got: {top_sugg}"
    log(f"Target intelligence successfully suggested '{top_sugg['column']}' (score: {top_sugg['score']}) with reasons: {top_sugg['reasons']}")
    log("Preflight validation passed with 100% prerequisite satisfaction for all 7 modules.")
    
    # Create Diagnostic Run
    create_req = {
        "model": {
            "name": "Scenario E Real XGBoost Credit Diagnostic",
            "framework": "xgboost",
            "taskType": "binary_classification"
        },
        "executionMode": "REAL",
        "modelArtifactId": m_id,
        "evaluationDatasetArtifactId": e_id,
        "baselineDatasetArtifactId": b_id,
        "targetColumn": "fraud",
        "protectedAttribute": "gender",
        "modules": ["DATA_QUALITY", "LEAKAGE", "DRIFT", "PERFORMANCE", "EXPLAINABILITY", "BIAS", "ROBUSTNESS"]
    }
    cr = requests.post(f"{BASE_URL}/api/diagnostics", json=create_req)
    assert cr.status_code == 201, f"Creation failed: {cr.text}"
    run_id = cr.json()["id"]
    log(f"Diagnostic run created: {run_id}. Triggering execution across all 7 modules...")
    
    start_run = requests.post(f"{BASE_URL}/api/diagnostics/{run_id}/run")
    assert start_run.status_code == 200, f"Start run failed: {start_run.text}"
    
    status = wait_for_run_completion(run_id)
    assert status == "COMPLETED", f"Expected COMPLETED, got {status}"
    
    # Inspect final diagnostic run metadata & provenance
    run_meta = requests.get(f"{BASE_URL}/api/diagnostics/{run_id}").json()
    assert run_meta["status"] == "COMPLETED"
    assert run_meta["modelArtifactId"] == m_id
    assert run_meta["evaluationDatasetArtifactId"] == e_id
    assert run_meta["baselineDatasetArtifactId"] == b_id
    assert len(run_meta["selectedModules"]) == 7
    log(f"Diagnostic run metadata verified. Provenance: model={m_id}, eval={e_id}, baseline={b_id}")
    
    # Inspect detailed module results
    results_dto = requests.get(f"{BASE_URL}/api/diagnostics/{run_id}/results").json()
    assert results_dto["status"] == "COMPLETED"
    assert len(results_dto["results"]) == 7
    for mod in results_dto["results"]:
        assert mod["status"] == "COMPLETED", f"Module {mod['module']} status is {mod['status']}"
        health_score = mod.get("result", {}).get("health_score")
        log(f" - Module {mod['module']}: status={mod['status']}, health_score={health_score}")
        
    log("SCENARIO E PASSED PERFECTLY!")

def wait_for_run_completion(run_id, max_seconds=30):
    for _ in range(max_seconds):
        r = requests.get(f"{BASE_URL}/api/diagnostics/{run_id}")
        if r.status_code == 200:
            status = r.json().get("status")
            if status in ["COMPLETED", "FAILED"]:
                return status
        time.sleep(1)
    return "TIMEOUT"

def test_e2e_phase3b_suite():
    wait_for_backend()
    model_id, eval_id, baseline_id, run1_id = scenario_a_reuse()
    scenario_b_incompatible_schema(model_id, baseline_id)
    scenario_c_multiclass_rejection(eval_id, baseline_id)
    scenario_d_soft_deletion(model_id, run1_id, eval_id)
    scenario_e_full_real_run()
    log("\n========================================================")
    log("ALL 5 PHASE 3B VERIFICATION SCENARIOS PASSED WITH ZERO ERRORS!")
    log("========================================================")

if __name__ == "__main__":
    test_e2e_phase3b_suite()
