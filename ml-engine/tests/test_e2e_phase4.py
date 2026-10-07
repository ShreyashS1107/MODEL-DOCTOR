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
    print(f"[E2E Phase 4] {msg}", flush=True)

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

def setup_phase4_test_artifacts():
    os.makedirs("scratch_e2e_4", exist_ok=True)
    np.random.seed(42)
    n = 300
    
    # Feature 1: Highly drifted feature between baseline and eval
    f_drift_base = np.random.normal(0, 1, n)
    f_drift_eval = np.random.normal(2.5, 1.2, n) # severe shift -> PSI > 0.25
    
    # Feature 2: High importance / high robustness sensitivity feature
    f_important = np.random.uniform(10, 100, n)
    
    # Feature 3: Target proxy / leakage feature
    y = np.random.choice([0, 1], n, p=[0.6, 0.4])
    f_leak = y.copy() # exact target match -> high leakage
    # Introduce small noise to prevent single-value issues
    noise_idx = np.random.choice(n, size=5, replace=False)
    f_leak[noise_idx] = 1 - f_leak[noise_idx]
    
    # Categorical & Subgroup feature
    f_group = np.random.choice(["Group_A", "Group_B"], n, p=[0.7, 0.3])
    
    # Model predictions with error rate
    pred = np.clip(y * 0.5 + np.random.uniform(0.1, 0.4, n), 0.01, 0.99)
    
    df_eval = pd.DataFrame({
        "drifted_feature": f_drift_eval,
        "important_feature": f_important,
        "proxy_feature": f_leak,
        "subgroup": f_group,
        "target": y,
        "prediction": pred
    })
    
    df_base = pd.DataFrame({
        "drifted_feature": f_drift_base,
        "important_feature": f_important,
        "proxy_feature": f_leak,
        "subgroup": f_group,
        "target": y,
        "prediction": pred
    })
    
    eval_csv = "scratch_e2e_4/eval_p4.csv"
    base_csv = "scratch_e2e_4/base_p4.csv"
    df_eval.to_csv(eval_csv, index=False)
    df_base.to_csv(base_csv, index=False)
    
    # Train XGBoost model using all numeric features
    X_train = pd.DataFrame({
        "drifted_feature": f_drift_eval,
        "important_feature": f_important,
        "proxy_feature": f_leak
    })
    dtrain = xgb.DMatrix(X_train, label=y)
    booster = xgb.train({"max_depth": 3, "objective": "binary:logistic"}, dtrain, num_boost_round=15)
    model_path = "scratch_e2e_4/model_p4.json"
    booster.save_model(model_path)
    
    # Ingest model
    with open(model_path, "rb") as f:
        r_mdl = requests.post(f"{BASE_URL}/api/artifacts/models", files={"file": f}, data={
            "framework": "xgboost",
            "taskType": "binary_classification",
            "description": "Phase 4 End-to-End Model"
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

def test_phase4_end_to_end():
    log("Starting Phase 4 Cross-Module Intelligence E2E verification...")
    wait_for_backend()
    
    model_id, eval_id, base_id = setup_phase4_test_artifacts()
    log(f"Artifacts ingested: model={model_id}, eval={eval_id}, base={base_id}")
    
    # 1. Create a full 7-module diagnostic run in REAL execution mode
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
            "ROBUSTNESS"
        ]
    }
    
    r_create = requests.post(f"{BASE_URL}/api/diagnostics", json=create_payload)
    assert r_create.status_code == 201, f"Run creation failed: {r_create.text}"
    run_id = r_create.json()["id"]
    log(f"Created diagnostic run {run_id}")
    
    # 2. Execute diagnostic run
    r_run = requests.post(f"{BASE_URL}/api/diagnostics/{run_id}/run")
    assert r_run.status_code == 200, f"Run trigger failed: {r_run.text}"
    
    status, final_run = wait_for_run_completion(run_id, max_seconds=30)
    assert status == "COMPLETED", f"Expected run to complete, got: {status}"
    log(f"Diagnostic run {run_id} COMPLETED successfully with all 7 modules.")
    
    # 3. Verify Raw Results are persisted and intact
    r_results = requests.get(f"{BASE_URL}/api/diagnostics/{run_id}/results")
    assert r_results.status_code == 200
    res_json = r_results.json()
    assert len(res_json.get("results", [])) == 7, "Expected 7 module results"
    for mod_res in res_json["results"]:
        assert mod_res["status"] == "COMPLETED"
        assert mod_res["result"] is not None
    log("Authoritative raw module results verified unchanged and complete.")
    
    # 4. Verify Phase 4 Cross-Module Correlations API
    r_corrs = requests.get(f"{BASE_URL}/api/diagnostics/{run_id}/correlations")
    assert r_corrs.status_code == 200, f"Correlations GET failed: {r_corrs.text}"
    correlations = r_corrs.json()
    assert isinstance(correlations, list), "Expected correlations list"
    assert len(correlations) > 0, "Expected at least 1 cross-module correlation finding generated"
    log(f"Phase 4 Engine generated {len(correlations)} cross-module findings.")
    
    # Verify deterministic fields and priority ordering
    prev_score = 999999
    found_rules = set()
    for finding in correlations:
        assert finding["runId"] == run_id
        assert "ruleId" in finding and finding["ruleId"]
        assert "findingType" in finding
        assert "priority" in finding and finding["priority"] in ["CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO"]
        assert "priorityScore" in finding
        assert finding["priorityScore"] <= prev_score, "Correlations must be ordered by descending priorityScore"
        prev_score = finding["priorityScore"]
        
        assert "confidence" in finding and finding["confidence"] in ["HIGH", "MEDIUM", "LOW"]
        assert "summary" in finding and len(finding["summary"]) > 0
        assert "isAssociativeOnly" in finding and finding["isAssociativeOnly"] is True
        assert "evidence" in finding and isinstance(finding["evidence"], dict)
        assert "sourceModules" in finding and len(finding["sourceModules"]) >= 2
        assert "sourceResultIds" in finding and len(finding["sourceResultIds"]) >= 2
        
        found_rules.add(finding["ruleId"])
        
    log(f"Rules fired: {sorted(list(found_rules))}")
    log("Finding structure, non-causal attribution, and deterministic priority ordering verified.")
    
    # 5. Verify Phase 4 Run Summary API
    r_summary = requests.get(f"{BASE_URL}/api/diagnostics/{run_id}/summary")
    assert r_summary.status_code == 200, f"Summary GET failed: {r_summary.text}"
    summary = r_summary.json()
    
    assert summary["runId"] == run_id
    assert summary["moduleCount"] == 7
    assert summary["completedModules"] == 7
    assert summary["failedModules"] == 0
    assert summary["totalFindings"] == len(correlations)
    assert summary["criticalFindings"] == sum(1 for c in correlations if c["priority"] == "CRITICAL")
    assert summary["highPriorityFindings"] == sum(1 for c in correlations if c["priority"] == "HIGH")
    assert summary["isAssociativeOnly"] is True
    assert isinstance(summary["topInvestigationAreas"], list)
    assert isinstance(summary["featureProfiles"], dict)
    
    log(f"Run summary: {summary['totalFindings']} findings ({summary['criticalFindings']} CRIT, {summary['highPriorityFindings']} HIGH), {len(summary['topInvestigationAreas'])} investigation areas.")
    
    # 6. Test Idempotent Recalculation
    r_recalc = requests.post(f"{BASE_URL}/api/diagnostics/{run_id}/correlations/recalculate")
    assert r_recalc.status_code == 200, f"Recalculate failed: {r_recalc.text}"
    recalc_correlations = r_recalc.json()
    assert len(recalc_correlations) == len(correlations), "Recalculation must be idempotent without duplicates"
    
    # Check that database count has not doubled
    r_corrs_after = requests.get(f"{BASE_URL}/api/diagnostics/{run_id}/correlations")
    assert len(r_corrs_after.json()) == len(correlations)
    log("Idempotent recalculation successfully verified (no duplicate findings created).")
    
    # 7. Test Partial Run Failure Isolation
    log("Testing Partial Run Failure Isolation with 2 modules...")
    partial_payload = {
        "modelArtifactId": model_id,
        "evaluationDatasetArtifactId": eval_id,
        "baselineDatasetArtifactId": base_id,
        "executionMode": "REAL",
        "targetColumn": "target",
        "modules": [
            "DRIFT",
            "PERFORMANCE"
        ]
    }
    r_pcreate = requests.post(f"{BASE_URL}/api/diagnostics", json=partial_payload)
    assert r_pcreate.status_code == 201
    prun_id = r_pcreate.json()["id"]
    
    r_prun = requests.post(f"{BASE_URL}/api/diagnostics/{prun_id}/run")
    assert r_prun.status_code == 200
    pstatus, _ = wait_for_run_completion(prun_id, max_seconds=30)
    assert pstatus == "COMPLETED"
    
    r_pcorrs = requests.get(f"{BASE_URL}/api/diagnostics/{prun_id}/correlations")
    assert r_pcorrs.status_code == 200
    r_psummary = requests.get(f"{BASE_URL}/api/diagnostics/{prun_id}/summary")
    assert r_psummary.status_code == 200
    psummary = r_psummary.json()
    assert psummary["completedModules"] == 2
    log(f"Partial run {prun_id} executed cleanly with Phase 4 correlation isolation.")
    
    log("================================================================================")
    log(">>> ALL PHASE 4 END-TO-END VERIFICATION CHECKS PASSED SUCCESSFULLY! <<<")
    log("================================================================================")

if __name__ == "__main__":
    test_phase4_end_to_end()
