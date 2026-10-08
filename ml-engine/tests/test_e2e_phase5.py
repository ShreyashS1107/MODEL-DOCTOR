import time
import json
import os
import requests
import pandas as pd
import numpy as np
import xgboost as xgb

BASE_URL = "http://localhost:8080"
PYTHON_URL = "http://localhost:8000"

def log(msg):
    print(f"[E2E Phase 5] {msg}", flush=True)

def wait_for_backend(max_retries=30):
    log("Waiting for Spring Boot backend to become ready...")
    for _ in range(max_retries):
        try:
            r = requests.get(f"{BASE_URL}/api/health", timeout=2)
            if r.status_code == 200:
                log("Backend is ready.")
                return True
        except Exception:
            pass
        time.sleep(1)
    raise RuntimeError("Backend did not become ready in time.")

def wait_for_run_completion(run_id, max_seconds=30):
    for _ in range(max_seconds):
        r = requests.get(f"{BASE_URL}/api/diagnostics/{run_id}")
        if r.status_code == 200:
            status = r.json().get("status")
            if status in ["COMPLETED", "PARTIAL", "FAILED"]:
                return status, r.json()
        time.sleep(0.5)
    return "TIMEOUT", None

def setup_phase5_test_artifacts():
    os.makedirs("scratch_e2e_5", exist_ok=True)
    np.random.seed(42)
    n = 400
    
    # Feature 1: Drifted feature strongly correlated with prediction errors
    f_drift_base = np.random.normal(0, 1, n)
    f_drift_eval = np.random.normal(2.5, 1.2, n) # severe shift -> PSI > 0.25
    
    # Feature 2: High importance / high robustness sensitivity feature
    f_important = np.random.uniform(10, 100, n)
    
    # Feature 3: Non-drifted continuous feature
    f_amount = np.random.exponential(50, n)
    
    # Target
    y = np.random.choice([0, 1], n, p=[0.7, 0.3])
    
    # Subgroup with disparity in error rates
    subgroup = np.random.choice(["domestic", "foreign"], n, p=[0.75, 0.25])
    
    # Predictions: Introduce intentional mistakes for records where f_drift_eval is high or subgroup == foreign
    pred_prob = np.zeros(n)
    for i in range(n):
        base_p = 0.85 if y[i] == 1 else 0.15
        if f_drift_eval[i] > 2.8:
            # High error region
            base_p = 1.0 - base_p
        if subgroup[i] == "foreign" and np.random.rand() < 0.3:
            base_p = 1.0 - base_p
        pred_prob[i] = np.clip(base_p + np.random.normal(0, 0.08), 0.01, 0.99)
    
    df_eval = pd.DataFrame({
        "drifted_feature": f_drift_eval,
        "important_feature": f_important,
        "amount": f_amount,
        "subgroup": subgroup,
        "target": y,
        "prediction": pred_prob
    })
    
    df_base = pd.DataFrame({
        "drifted_feature": f_drift_base,
        "important_feature": f_important,
        "amount": f_amount,
        "subgroup": subgroup,
        "target": y,
        "prediction": pred_prob
    })
    
    eval_csv = "scratch_e2e_5/eval_p5.csv"
    base_csv = "scratch_e2e_5/base_p5.csv"
    df_eval.to_csv(eval_csv, index=False)
    df_base.to_csv(base_csv, index=False)
    
    # Train XGBoost model
    X_train = pd.DataFrame({
        "drifted_feature": f_drift_eval,
        "important_feature": f_important,
        "amount": f_amount
    })
    dtrain = xgb.DMatrix(X_train, label=y)
    booster = xgb.train({"max_depth": 3, "objective": "binary:logistic"}, dtrain, num_boost_round=15)
    model_path = "scratch_e2e_5/model_p5.json"
    booster.save_model(model_path)
    
    # Ingest model
    with open(model_path, "rb") as f:
        r_mdl = requests.post(f"{BASE_URL}/api/artifacts/models", files={"file": f}, data={
            "framework": "xgboost",
            "taskType": "binary_classification",
            "description": "Phase 5 End-to-End Model"
        })
        assert r_mdl.status_code == 201, f"Model ingest failed: {r_mdl.text}"
        model_id = r_mdl.json()["id"]
        
    # Ingest datasets
    with open(eval_csv, "rb") as f:
        r_eval = requests.post(f"{BASE_URL}/api/artifacts/datasets", files={"file": f})
        assert r_eval.status_code == 201, f"Eval ingest failed: {r_eval.text}"
        eval_id = r_eval.json()["id"]
        
    with open(base_csv, "rb") as f:
        r_base = requests.post(f"{BASE_URL}/api/artifacts/datasets", files={"file": f})
        assert r_base.status_code == 201, f"Base ingest failed: {r_base.text}"
        base_id = r_base.json()["id"]
        
    return model_id, eval_id, base_id

def test_phase5_end_to_end():
    log("Starting Phase 5 Performance & Error Forensics E2E verification...")
    wait_for_backend()
    
    model_id, eval_id, base_id = setup_phase5_test_artifacts()
    log(f"Artifacts ingested: model={model_id}, eval={eval_id}, base={base_id}")
    
    # 1. Create a full 8-module diagnostic run in REAL execution mode
    create_payload = {
        "modelArtifactId": model_id,
        "evaluationDatasetArtifactId": eval_id,
        "baselineDatasetArtifactId": base_id,
        "executionMode": "REAL",
        "targetColumn": "target",
        "predictionColumn": "prediction",
        "protectedAttribute": "subgroup",
        "modules": [
            "DATA_QUALITY",
            "LEAKAGE",
            "DRIFT",
            "PERFORMANCE",
            "EXPLAINABILITY",
            "BIAS",
            "ROBUSTNESS",
            "ERROR_FORENSICS"
        ]
    }
    
    r_create = requests.post(f"{BASE_URL}/api/diagnostics", json=create_payload)
    assert r_create.status_code == 201, f"Run creation failed: {r_create.text}"
    run_id = r_create.json()["id"]
    log(f"Created diagnostic run {run_id}")
    
    # 2. Trigger execution
    r_exec = requests.post(f"{BASE_URL}/api/diagnostics/{run_id}/run")
    assert r_exec.status_code == 200, f"Execution trigger failed: {r_exec.text}"
    
    # 3. Await completion
    status, run_data = wait_for_run_completion(run_id, max_seconds=45)
    log(f"Run {run_id} completed with status: {status}")
    assert status == "COMPLETED", f"Expected COMPLETED status but got {status}"
    
    # 4. Retrieve structured results
    r_results = requests.get(f"{BASE_URL}/api/diagnostics/{run_id}/results")
    assert r_results.status_code == 200, f"Results fetch failed: {r_results.text}"
    results_map = {m["module"]: m for m in r_results.json()["results"]}
    
    assert "PERFORMANCE" in results_map, "PERFORMANCE module missing"
    assert "ERROR_FORENSICS" in results_map, "ERROR_FORENSICS module missing"
    
    perf_res = results_map["PERFORMANCE"]["result"]
    err_res = results_map["ERROR_FORENSICS"]["result"]
    
    # 5. Check confusion matrix consistency between PERFORMANCE and ERROR_FORENSICS
    perf_cm = perf_res["confusionMatrix"]
    err_summary = err_res["errorSummary"]
    
    assert (perf_cm.get("truePositive") or perf_cm.get("truePositives")) == err_summary["truePositive"]["count"], "TP count mismatch"
    assert (perf_cm.get("trueNegative") or perf_cm.get("trueNegatives")) == err_summary["trueNegative"]["count"], "TN count mismatch"
    assert (perf_cm.get("falsePositive") or perf_cm.get("falsePositives")) == err_summary["falsePositive"]["count"], "FP count mismatch"
    assert (perf_cm.get("falseNegative") or perf_cm.get("falseNegatives")) == err_summary["falseNegative"]["count"], "FN count mismatch"
    log("Verified confusion matrix counts match exactly between PERFORMANCE and ERROR_FORENSICS.")
    
    # 6. Verify Error Forensics payload components
    assert "confidenceAnalysis" in err_res, "confidenceAnalysis missing"
    assert "featureAssociations" in err_res, "featureAssociations missing"
    assert len(err_res["featureAssociations"]) > 0, "featureAssociations is empty"
    assert "featureRanges" in err_res, "featureRanges missing"
    assert "highConfidenceErrors" in err_res, "highConfidenceErrors missing"
    assert "thresholdAnalysis" in err_res, "thresholdAnalysis missing"
    assert len(err_res["thresholdAnalysis"]) == 21, f"Expected 21 threshold points, got {len(err_res['thresholdAnalysis'])}"
    assert "calibrationForensics" in err_res, "calibrationForensics missing"
    assert "subgroupAnalysis" in err_res, "subgroupAnalysis missing"
    log("Verified all 8 sub-analyses in ERROR_FORENSICS payload.")
    
    # 7. Retrieve Phase 4/5 Cross-Module Correlations
    r_corrs = requests.get(f"{BASE_URL}/api/diagnostics/{run_id}/correlations")
    assert r_corrs.status_code == 200, f"Correlations fetch failed: {r_corrs.text}"
    correlations = r_corrs.json()
    log(f"Retrieved {len(correlations)} cross-module correlation findings.")
    assert len(correlations) > 0, "Expected at least 1 cross-module correlation finding"
    
    rule_ids = {c["ruleId"] for c in correlations}
    for c in correlations:
        assert c.get("associativeOnly") is True, f"Finding {c['id']} must be marked associativeOnly"
        assert len(c.get("sourceResultIds", [])) > 0, f"Finding {c['id']} missing sourceResultIds"
        
    log(f"Triggered Rule IDs: {rule_ids}")
    
    # 8. Retrieve Run Summary & verify Forensic Matrix
    r_summary = requests.get(f"{BASE_URL}/api/diagnostics/{run_id}/summary")
    assert r_summary.status_code == 200, f"Summary fetch failed: {r_summary.text}"
    summary = r_summary.json()
    
    assert "featureProfiles" in summary, "featureProfiles missing in RunSummary"
    feature_profiles = summary["featureProfiles"]
    assert "drifted_feature" in feature_profiles, "drifted_feature missing from feature profiles"
    drift_prof = feature_profiles["drifted_feature"]
    assert "driftPsi" in drift_prof, "driftPsi missing from feature profile"
    assert "errorCorrelation" in drift_prof, "errorCorrelation missing from feature profile"
    log("Verified feature profile contains error correlation metrics in Forensic Matrix.")
    
    # 9. Verify Idempotent recalculation
    r_recalc = requests.post(f"{BASE_URL}/api/diagnostics/{run_id}/correlations/recalculate")
    assert r_recalc.status_code == 200, f"Recalculation failed: {r_recalc.text}"
    recalc_corrs = r_recalc.json()
    assert len(recalc_corrs) == len(correlations), "Recalculation produced duplicate or different findings count"
    log("Verified idempotent recalculation preserves exact finding count.")
    
    log("SUCCESS: Phase 5 Performance & Error Forensics E2E verification passed!")

if __name__ == "__main__":
    test_phase5_end_to_end()
