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
    print(f"[E2E Phase 7] {msg}", flush=True)

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

def wait_for_run_completion(run_id, max_seconds=45):
    for _ in range(max_seconds):
        r = requests.get(f"{BASE_URL}/api/diagnostics/{run_id}")
        if r.status_code == 200:
            status = r.json().get("status")
            if status in ["COMPLETED", "PARTIAL", "FAILED"]:
                return status, r.json()
        time.sleep(0.5)
    return "TIMEOUT", None

def setup_phase7_test_artifacts():
    os.makedirs("scratch_e2e_7", exist_ok=True)
    np.random.seed(42)
    n = 600
    
    # 1. Drifted influential feature
    f_drift_base = np.random.normal(0, 1, n)
    f_drift_eval = np.random.normal(2.5, 1.2, n) # severe shift -> PSI >= 0.25
    
    # 2. High importance feature
    f_important = np.random.uniform(10, 100, n)
    
    # 3. Fragile feature with 18% missingness in eval
    f_fragile_base = np.random.normal(50, 10, n)
    f_fragile_eval = np.random.normal(50, 10, n)
    null_idx = np.random.choice(n, int(n * 0.18), replace=False)
    f_fragile_eval[null_idx] = np.nan
    
    # 4. Target variable
    y = np.random.choice([0, 1], n, p=[0.65, 0.35])
    
    # 5. Identifier leakage feature
    account_id = [f"ACC_{i:05d}" for i in range(n)]
    
    # 6. Protected attribute for fairness review
    subgroup = np.random.choice(["north", "south"], n, p=[0.7, 0.3])
    
    # 7. Prediction probabilities with miscalibration & error concentration
    pred_prob = np.zeros(n)
    for i in range(n):
        base_p = 0.86 if y[i] == 1 else 0.14
        if f_drift_eval[i] > 2.5:
            # Concentrated error region
            base_p = 1.0 - base_p
        if subgroup[i] == "south" and np.random.rand() < 0.30:
            base_p = 1.0 - base_p
        pred_prob[i] = np.clip(base_p + np.random.normal(0, 0.08), 0.01, 0.99)
    
    df_eval = pd.DataFrame({
        "drifted_feature": f_drift_eval,
        "important_feature": f_important,
        "fragile_feature": f_fragile_eval,
        "account_id": account_id,
        "protected_region": subgroup,
        "is_fraud": y,
        "pred_prob": pred_prob
    })
    
    df_base = pd.DataFrame({
        "drifted_feature": f_drift_base,
        "important_feature": f_important,
        "fragile_feature": f_fragile_base,
        "account_id": account_id,
        "protected_region": subgroup,
        "is_fraud": y,
        "pred_prob": pred_prob
    })
    
    eval_csv = "scratch_e2e_7/eval_p7_baseline.csv"
    base_csv = "scratch_e2e_7/base_p7_baseline.csv"
    df_eval.to_csv(eval_csv, index=False)
    df_base.to_csv(base_csv, index=False)
    
    # Train XGBoost model
    X_train = pd.DataFrame({
        "drifted_feature": np.nan_to_num(f_drift_eval, nan=0.0),
        "important_feature": f_important,
        "fragile_feature": np.nan_to_num(f_fragile_eval, nan=0.0)
    })
    dtrain = xgb.DMatrix(X_train, label=y)
    booster = xgb.train({"max_depth": 3, "objective": "binary:logistic"}, dtrain, num_boost_round=15)
    model_path = "scratch_e2e_7/model_p7_baseline.json"
    booster.save_model(model_path)
    
    # Also create candidate dataset with resolved drift and cleaned data
    f_drift_cand = np.random.normal(0.1, 1.0, n) # minimal shift
    f_fragile_cand = np.random.normal(50, 10, n) # zero missing
    
    # Calibrated predictions for candidate
    cand_pred_prob = np.zeros(n)
    for i in range(n):
        base_p = 0.90 if y[i] == 1 else 0.10
        cand_pred_prob[i] = np.clip(base_p + np.random.normal(0, 0.03), 0.01, 0.99)
        
    df_cand = pd.DataFrame({
        "drifted_feature": f_drift_cand,
        "important_feature": f_important,
        "fragile_feature": f_fragile_cand,
        "account_id": account_id,
        "protected_region": subgroup,
        "is_fraud": y,
        "pred_prob": cand_pred_prob
    })
    cand_eval_csv = "scratch_e2e_7/eval_p7_candidate.csv"
    df_cand.to_csv(cand_eval_csv, index=False)
    
    cand_model_path = "scratch_e2e_7/model_p7_candidate.json"
    booster.save_model(cand_model_path)

    return model_path, eval_csv, base_csv, cand_model_path, cand_eval_csv

def test_phase7_e2e():
    log("Starting Phase 7 E2E Verification...")
    wait_for_backend()

    # 1. Setup real model and dataset artifacts
    model_path, eval_csv, base_csv, cand_model_path, cand_eval_csv = setup_phase7_test_artifacts()

    # 2. Upload baseline artifacts
    log("Uploading baseline model & dataset artifacts...")
    with open(model_path, "rb") as f:
        r = requests.post(f"{BASE_URL}/api/artifacts/models", files={"file": f}, data={"framework": "xgboost", "taskType": "binary_classification"})
        assert r.status_code == 201, f"Model upload failed: {r.text}"
        model_artifact_id = r.json()["id"]

    with open(eval_csv, "rb") as f:
        r = requests.post(f"{BASE_URL}/api/artifacts/datasets", files={"file": f})
        assert r.status_code == 201, f"Eval dataset upload failed: {r.text}"
        eval_artifact_id = r.json()["id"]

    with open(base_csv, "rb") as f:
        r = requests.post(f"{BASE_URL}/api/artifacts/datasets", files={"file": f})
        assert r.status_code == 201, f"Base dataset upload failed: {r.text}"
        base_artifact_id = r.json()["id"]

    # 3. Create real diagnostic run
    log("Creating baseline diagnostic run across all 8 modules...")
    run_payload = {
        "modelArtifactId": model_artifact_id,
        "evaluationDatasetArtifactId": eval_artifact_id,
        "baselineDatasetArtifactId": base_artifact_id,
        "targetColumn": "is_fraud",
        "predictionColumn": "pred_prob",
        "protectedAttribute": "protected_region",
        "executionMode": "REAL",
        "modules": [
            "DATA_QUALITY", "LEAKAGE", "DRIFT", "PERFORMANCE",
            "EXPLAINABILITY", "BIAS", "ROBUSTNESS", "ERROR_FORENSICS"
        ]
    }
    r = requests.post(f"{BASE_URL}/api/diagnostics", json=run_payload)
    assert r.status_code == 201, f"Run creation failed: {r.text}"
    baseline_run_id = r.json()["id"]
    log(f"Baseline Run registered: {baseline_run_id}")

    # 4. Start execution and wait for completion
    log("Starting baseline diagnostic execution...")
    r = requests.post(f"{BASE_URL}/api/diagnostics/{baseline_run_id}/run")
    assert r.status_code == 200, f"Run start failed: {r.text}"

    status, run_data = wait_for_run_completion(baseline_run_id)
    assert status in ["COMPLETED", "PARTIAL"], f"Run failed to complete: {status}"
    log(f"Baseline run completed with status: {status}")

    # 5. Retrieve Remediations
    log("Retrieving ranked remediation candidates...")
    r = requests.get(f"{BASE_URL}/api/diagnostics/{baseline_run_id}/remediations")
    assert r.status_code == 200, f"Remediations retrieval failed: {r.text}"
    remediations = r.json()
    assert len(remediations) > 0, "Must generate at least one remediation recommendation"
    log(f"Successfully retrieved {len(remediations)} remediation candidate(s)")

    # 6. Verify deterministic descending priority score sort
    for i in range(len(remediations) - 1):
        assert remediations[i]["priorityScore"] >= remediations[i+1]["priorityScore"], \
            "Remediations must be ordered deterministically by priorityScore DESC"

    # 7. Inspect top remediation candidate dossier
    top_rem = remediations[0]
    log(f"Inspecting Top Remediation: [{top_rem['priority']}] {top_rem['remediationType']} on {top_rem['targetKey']}")
    rem_id = top_rem["id"]
    
    r = requests.get(f"{BASE_URL}/api/diagnostics/{baseline_run_id}/remediations/{rem_id}")
    assert r.status_code == 200, f"Remediation dossier retrieval failed: {r.text}"
    dossier = r.json()
    
    assert dossier["id"] == rem_id
    assert dossier["status"] == "PROPOSED"
    assert dossier["associativeOnly"] is True, "Must be explicitly marked as associative only"
    assert "causes" not in dossier["hypothesis"].lower(), "Hypothesis must not claim causality"
    assert len(dossier["requiredModules"]) > 0, "Must specify required validation modules"
    assert len(dossier["acceptanceCriteria"]) > 0, "Must specify measurable acceptance criteria"
    assert len(dossier["sourceResultIds"]) > 0, "Must contain provenance source result IDs"

    # 8. Test Lifecycle: Select Remediation
    log(f"Selecting remediation #{rem_id} for investigation...")
    r = requests.post(f"{BASE_URL}/api/diagnostics/{baseline_run_id}/remediations/{rem_id}/select")
    assert r.status_code == 200, f"Select remediation failed: {r.text}"
    selected_rem = r.json()
    assert selected_rem["status"] == "SELECTED", f"Expected status SELECTED, got {selected_rem['status']}"

    # 9. Test Lifecycle: Reject Remediation on second candidate
    if len(remediations) > 1:
        rej_id = remediations[1]["id"]
        log(f"Rejecting remediation #{rej_id} with rationale...")
        r = requests.post(f"{BASE_URL}/api/diagnostics/{baseline_run_id}/remediations/{rej_id}/reject?reason=Operational+risk+acceptable")
        assert r.status_code == 200, f"Reject remediation failed: {r.text}"
        rej_rem = r.json()
        assert rej_rem["status"] == "REJECTED"
        assert rej_rem["rejectionReason"] == "Operational risk acceptable"

    # 10. Test Idempotency: Recalculate Remediations
    log("Testing idempotent recalculation of remediations...")
    r = requests.post(f"{BASE_URL}/api/diagnostics/{baseline_run_id}/remediations/recalculate")
    assert r.status_code == 200, f"Recalculate failed: {r.text}"
    recalc_rems = r.json()
    assert len(recalc_rems) == len(remediations), "Recalculation must preserve deterministic candidate count"

    # 11. Create Candidate Run for Before / After Comparison
    log("Creating Candidate Run for before/after validation comparison...")
    with open(cand_eval_csv, "rb") as f:
        r = requests.post(f"{BASE_URL}/api/artifacts/datasets", files={"file": f})
        assert r.status_code == 201
        cand_eval_artifact_id = r.json()["id"]

    cand_payload = {
        "modelArtifactId": model_artifact_id,
        "evaluationDatasetArtifactId": cand_eval_artifact_id,
        "baselineDatasetArtifactId": base_artifact_id,
        "targetColumn": "is_fraud",
        "predictionColumn": "pred_prob",
        "protectedAttribute": "protected_region",
        "executionMode": "REAL",
        "modules": [
            "DATA_QUALITY", "LEAKAGE", "DRIFT", "PERFORMANCE",
            "EXPLAINABILITY", "BIAS", "ROBUSTNESS", "ERROR_FORENSICS"
        ]
    }
    r = requests.post(f"{BASE_URL}/api/diagnostics", json=cand_payload)
    assert r.status_code == 201
    candidate_run_id = r.json()["id"]
    
    r = requests.post(f"{BASE_URL}/api/diagnostics/{candidate_run_id}/run")
    assert r.status_code == 200
    cand_status, _ = wait_for_run_completion(candidate_run_id)
    assert cand_status in ["COMPLETED", "PARTIAL"]
    log(f"Candidate Run {candidate_run_id} completed with status {cand_status}")

    # 12. Test Run Comparison Endpoint
    log(f"Comparing Baseline Run {baseline_run_id} vs Candidate Run {candidate_run_id}...")
    r = requests.get(f"{BASE_URL}/api/diagnostics/{baseline_run_id}/comparison/{candidate_run_id}")
    assert r.status_code == 200, f"Run comparison failed: {r.text}"
    comparison = r.json()

    assert comparison["baselineRunId"] == baseline_run_id
    assert comparison["candidateRunId"] == candidate_run_id
    assert len(comparison["metricComparisons"]) > 0, "Must produce metric comparisons"
    assert comparison["overallAssessment"] in ["IMPROVED", "REGRESSED", "MIXED", "NO_MATERIAL_CHANGE"], \
        f"Invalid assessment: {comparison['overallAssessment']}"
    log(f"Comparison Assessment: {comparison['overallAssessment']} ({comparison['improvedMetricCount']} improved, {comparison['regressedMetricCount']} regressed)")

    # 13. Verify Phase 6 Investigations & Evidence Graph Remain Intact
    log("Verifying Phase 6 Root-Cause Investigations remain intact...")
    r = requests.get(f"{BASE_URL}/api/diagnostics/{baseline_run_id}/investigations")
    assert r.status_code == 200
    investigations = r.json()
    assert len(investigations) > 0, "Phase 6 investigations must be present"

    r = requests.get(f"{BASE_URL}/api/diagnostics/{baseline_run_id}/evidence-graph")
    assert r.status_code == 200
    graph = r.json()
    assert len(graph["nodes"]) > 0 and len(graph["edges"]) > 0, "Phase 6 evidence graph must be populated"

    # 14. Verify Phase 5 Error Forensics Results Remain Intact
    log("Verifying Phase 5 Error Forensics results remain intact...")
    r = requests.get(f"{BASE_URL}/api/diagnostics/{baseline_run_id}/results")
    assert r.status_code == 200
    results = r.json()
    assert any(res["module"] == "ERROR_FORENSICS" for res in results.get("results", [])), "Phase 5 error forensics result must be present"

    # Print real remediation demonstration
    log("=" * 70)
    log("PHASE 7 REAL REMEDIATION DEMONSTRATION:")
    log(f"Run ID:              {top_rem['runId']}")
    log(f"Target:              {top_rem['targetKey']}")
    log(f"Remediation Type:    {top_rem['remediationType']}")
    log(f"Priority:            {top_rem['priority']} (Score: {top_rem['priorityScore']:.1f})")
    log(f"Confidence:          {top_rem['confidence']}")
    log(f"Required Modules:    {top_rem['requiredModules']}")
    log(f"Hypothesis:          {top_rem['hypothesis']}")
    log(f"Acceptance Criteria: {top_rem['acceptanceCriteria']}")
    log("=" * 70)
    log("Phase 7 E2E Verification PASSED successfully.")

if __name__ == "__main__":
    test_phase7_e2e()
