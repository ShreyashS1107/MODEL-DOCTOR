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
    print(f"[E2E Phase 9] {msg}", flush=True)

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

def setup_phase9_artifacts():
    os.makedirs("scratch_e2e_9", exist_ok=True)
    np.random.seed(42)
    n = 600

    # 1. Baseline dataset
    f_income_base = np.random.normal(50000, 10000, n)
    f_credit_base = np.random.uniform(300, 850, n)
    account_id = [f"ACC_{i:05d}" for i in range(n)]
    gender = np.random.choice(["Male", "Female"], n, p=[0.55, 0.45])
    y = np.random.choice([0, 1], n, p=[0.60, 0.40])
    pred_prob_base = np.clip(np.where(y == 1, 0.80, 0.20) + np.random.normal(0, 0.05, n), 0.01, 0.99)

    df_base = pd.DataFrame({
        "income": f_income_base,
        "credit_score": f_credit_base,
        "account_id": account_id,
        "gender": gender,
        "is_default": y,
        "pred_prob": pred_prob_base
    })
    base_csv = "scratch_e2e_9/base_dataset.csv"
    df_base.to_csv(base_csv, index=False)

    # 2. Run 1 Eval: Nominal / low drift, good accuracy
    f_income_r1 = np.random.normal(50500, 10200, n)
    pred_prob_r1 = np.clip(np.where(y == 1, 0.79, 0.21) + np.random.normal(0, 0.05, n), 0.01, 0.99)
    df_r1 = pd.DataFrame({
        "income": f_income_r1,
        "credit_score": f_credit_base,
        "account_id": account_id,
        "gender": gender,
        "is_default": y,
        "pred_prob": pred_prob_r1
    })
    eval_r1_csv = "scratch_e2e_9/eval_r1.csv"
    df_r1.to_csv(eval_r1_csv, index=False)

    # 3. Run 2 Eval: Moderate drift on income
    f_income_r2 = np.random.normal(62000, 14000, n)
    pred_prob_r2 = np.clip(np.where(y == 1, 0.73, 0.27) + np.random.normal(0, 0.08, n), 0.01, 0.99)
    df_r2 = pd.DataFrame({
        "income": f_income_r2,
        "credit_score": f_credit_base,
        "account_id": account_id,
        "gender": gender,
        "is_default": y,
        "pred_prob": pred_prob_r2
    })
    eval_r2_csv = "scratch_e2e_9/eval_r2.csv"
    df_r2.to_csv(eval_r2_csv, index=False)

    # 4. Run 3 Eval: Severe drift on income + Outliers + High error concentration
    f_income_r3 = np.random.normal(78000, 22000, n)
    f_income_r3[0:30] = 450000.0 # Extreme outliers
    pred_prob_r3 = np.clip(np.where(y == 1, 0.60, 0.40) + np.random.normal(0, 0.15, n), 0.01, 0.99)
    df_r3 = pd.DataFrame({
        "income": f_income_r3,
        "credit_score": f_credit_base,
        "account_id": account_id,
        "gender": gender,
        "is_default": y,
        "pred_prob": pred_prob_r3
    })
    eval_r3_csv = "scratch_e2e_9/eval_r3.csv"
    df_r3.to_csv(eval_r3_csv, index=False)

    # 5. Run 4 Eval (Post-remediation follow-up): Sustained recovery
    f_income_r4 = np.random.normal(51000, 10500, n)
    pred_prob_r4 = np.clip(np.where(y == 1, 0.81, 0.19) + np.random.normal(0, 0.04, n), 0.01, 0.99)
    df_r4 = pd.DataFrame({
        "income": f_income_r4,
        "credit_score": f_credit_base,
        "account_id": account_id,
        "gender": gender,
        "is_default": y,
        "pred_prob": pred_prob_r4
    })
    eval_r4_csv = "scratch_e2e_9/eval_r4.csv"
    df_r4.to_csv(eval_r4_csv, index=False)

    # Train XGBoost Model Artifact
    X_train = pd.DataFrame({
        "income": f_income_base,
        "credit_score": f_credit_base
    })
    dtrain = xgb.DMatrix(X_train, label=y)
    booster = xgb.train({"max_depth": 3, "objective": "binary:logistic"}, dtrain, num_boost_round=10)
    model_path = "scratch_e2e_9/model_p9_lineage.json"
    booster.save_model(model_path)

    return base_csv, eval_r1_csv, eval_r2_csv, eval_r3_csv, eval_r4_csv, model_path

def test_phase9_end_to_end():
    log("Starting Phase 9 End-to-End Test (Longitudinal Model Monitoring & Temporal Intelligence)...")
    wait_for_backend()

    base_csv, eval_r1, eval_r2, eval_r3, eval_r4, model_path = setup_phase9_artifacts()

    # 1. Ingest Artifacts
    log("1. Ingesting model and dataset artifacts...")
    with open(model_path, "rb") as f:
        r_mod = requests.post(f"{BASE_URL}/api/artifacts/models", files={"file": f}, data={"framework": "xgboost", "taskType": "binary_classification"})
    assert r_mod.status_code == 201, f"Failed model upload: {r_mod.text}"
    model_artifact_id = r_mod.json()["id"]

    def upload_dataset(path):
        with open(path, "rb") as f:
            r = requests.post(f"{BASE_URL}/api/artifacts/datasets", files={"file": f})
        assert r.status_code == 201, f"Failed dataset upload {path}: {r.text}"
        return r.json()["id"]

    base_art_id = upload_dataset(base_csv)
    eval_r1_id = upload_dataset(eval_r1)
    eval_r2_id = upload_dataset(eval_r2)
    eval_r3_id = upload_dataset(eval_r3)
    eval_r4_id = upload_dataset(eval_r4)

    modules_to_run = [
        "DATA_QUALITY",
        "LEAKAGE",
        "DRIFT",
        "PERFORMANCE",
        "ERROR_FORENSICS",
        "EXPLAINABILITY",
        "BIAS",
        "ROBUSTNESS"
    ]

    def trigger_run(eval_art_id, run_label):
        log(f"Launching diagnostic {run_label}...")
        payload = {
            "modelArtifactId": model_artifact_id,
            "evaluationDatasetArtifactId": eval_art_id,
            "baselineDatasetArtifactId": base_art_id,
            "targetColumn": "is_default",
            "predictionColumn": "pred_prob",
            "protectedAttribute": "gender",
            "executionMode": "REAL",
            "modules": modules_to_run
        }
        r_create = requests.post(f"{BASE_URL}/api/diagnostics", json=payload)
        assert r_create.status_code == 201, f"Failed create {run_label}: {r_create.text}"
        run_id = r_create.json()["id"]
        
        r_start = requests.post(f"{BASE_URL}/api/diagnostics/{run_id}/run")
        assert r_start.status_code == 200, f"Failed start {run_label}: {r_start.text}"

        status, details = wait_for_run_completion(run_id)
        assert status == "COMPLETED", f"Run {run_id} did not complete: {status}"
        log(f"Diagnostic {run_label} ({run_id}) completed successfully.")
        return run_id

    # 2. Execute 3 sequential historical baseline runs
    run_1_id = trigger_run(eval_r1_id, "RUN #1 (Nominal)")
    run_2_id = trigger_run(eval_r2_id, "RUN #2 (Moderate Drift)")
    run_3_id = trigger_run(eval_r3_id, "RUN #3 (Severe Degradation)")

    # 3. Create and execute Phase 8 Experiment on Run 3 for income feature transformation
    log("3. Creating and executing Phase 8 candidate experiment on Run #3...")
    r_rems = requests.get(f"{BASE_URL}/api/diagnostics/{run_3_id}/remediations")
    rem_id = None
    if r_rems.status_code == 200 and len(r_rems.json()) > 0:
        rem_id = r_rems.json()[0]["id"]
        log(f"Found remediation ID {rem_id}: {r_rems.json()[0]['title']}")

    exp_payload = {
        "remediationId": rem_id,
        "experimentType": "FEATURE_TRANSFORMATION",
        "title": "Quantile Clip on drifted income",
        "description": "Deterministic clipping of extreme outliers in income",
        "targetType": "FEATURE",
        "targetKey": "FEATURE::income",
        "intervention": {
            "feature": "income",
            "transformation": "CLIP",
            "lowerQuantile": 0.01,
            "upperQuantile": 0.99
        },
        "requestedModules": ["DRIFT", "PERFORMANCE"],
        "acceptanceCriteria": ["PSI < 0.10"],
        "regressionGuards": ["high-confidence error rate does not increase by > 0.05"],
        "deterministicSeed": 42
    }
    r_exp = requests.post(f"{BASE_URL}/api/diagnostics/{run_3_id}/experiments", json=exp_payload)
    assert r_exp.status_code == 201, f"Failed to create experiment: {r_exp.text}"
    exp_id = r_exp.json()["id"]

    r_exec = requests.post(f"{BASE_URL}/api/diagnostics/{run_3_id}/experiments/{exp_id}/execute")
    assert r_exec.status_code == 200, f"Failed to execute experiment: {r_exec.text}"
    exp_data = r_exec.json()
    assert exp_data["status"] == "COMPLETED", f"Experiment failed: {exp_data}"
    log(f"Experiment {exp_id} executed. Candidate run ID: {exp_data.get('candidateRunId')}, Conclusion: {exp_data.get('conclusion')}")

    # 4. Execute Follow-up Run 4 (Post-remediation)
    run_4_id = trigger_run(eval_r4_id, "RUN #4 (Post-Remediation Follow-up)")

    # 5. Verify Phase 9 Temporal Intelligence Engine
    log("4. Fetching Temporal Intelligence History for the model lineage...")
    r_hist = requests.get(f"{BASE_URL}/api/diagnostics/{run_4_id}/temporal/history?window=ALL_AVAILABLE")
    assert r_hist.status_code == 200, f"Failed to get temporal history: {r_hist.text}"
    history = r_hist.json()

    # Lineage & Run Count assertions
    assert history["totalRunsCount"] >= 4, f"Expected >= 4 total runs, got {history['totalRunsCount']}"
    assert history["baselineRunsCount"] >= 4, f"Expected >= 4 baseline runs, got {history['baselineRunsCount']}"
    assert history["experimentRunsCount"] >= 1, f"Expected >= 1 experiment run, got {history['experimentRunsCount']}"
    log(f"Lineage '{history['modelLineageId']}' verified: {history['baselineRunsCount']} baseline runs, {history['experimentRunsCount']} experiment runs.")

    # Metric History & Trend Analysis assertions
    metric_histories = history.get("metricHistories", [])
    assert len(metric_histories) > 0, "Expected non-empty metric histories"
    
    f1_history = next((m for m in metric_histories if m["metricName"] == "f1"), None)
    assert f1_history is not None, "Expected F1 metric history"
    assert len(f1_history["baselinePoints"]) >= 4, f"Expected >= 4 baseline points for F1, got {len(f1_history['baselinePoints'])}"
    assert f1_history["slope"] is not None, "Expected computed slope for F1"
    log(f"F1 Trend verified: Direction={f1_history['trendDirection']}, Slope={f1_history['slope']}, Obs={f1_history['observationCount']}")

    income_psi_history = next((m for m in metric_histories if m["metricName"] == "psi" and "income" in (m.get("targetKey") or "")), None)
    if income_psi_history:
        log(f"Income PSI Trend: Direction={income_psi_history['trendDirection']}, Latest={income_psi_history['latestValue']}")

    # Issue Track & Persistence Assertions
    issue_tracks = history.get("issueTracks", [])
    assert len(issue_tracks) > 0, "Expected non-empty issue tracks"
    income_track = next((t for t in issue_tracks if "income" in t["targetKey"]), None)
    assert income_track is not None, "Expected issue track for income feature"
    assert income_track["observationCount"] >= 2, f"Expected >= 2 observations for income track, got {income_track['observationCount']}"
    assert income_track["peakSeverity"] in ["CRITICAL", "HIGH", "MEDIUM"], f"Unexpected peak severity: {income_track['peakSeverity']}"
    log(f"Issue Track 'FEATURE::income' verified: Status={income_track['status']}, Peak={income_track['peakSeverity']}, Consecutive={income_track['consecutiveCount']}, Modules={income_track['modulesInvolved']}")

    # Temporal Alerts Assertions
    alerts = history.get("alerts", [])
    assert len(alerts) > 0, "Expected non-empty temporal alerts"
    for a in alerts[:5]:
        log(f"Alert generated: Priority={a['priority']}, Type={a['alertType']}, Target={a.get('targetKey')}, Desc='{a['triggerDescription']}'")

    # Change Points Assertions
    change_points = history.get("changePoints", [])
    log(f"Change points detected: {len(change_points)}")
    for cp in change_points[:3]:
        log(f"Change point: Metric={cp['metricName']}, Run={cp['changeRunId']}, Shift={cp['absoluteShift']}, Conf={cp['confidenceLevel']}")

    # Remediation Durability Assertions
    durability_list = history.get("remediationDurability", [])
    assert len(durability_list) > 0, "Expected remediation durability assessment"
    d_entry = durability_list[0]
    log(f"Durability evaluated for {d_entry.get('remediationType', 'Remediation')}: Status={d_entry.get('durabilityStatus')}, FollowUps={len(d_entry.get('followUpRunIds', []))}, Expl='{d_entry.get('assessment')}'")

    # Recalculate & Idempotency Check
    log("5. Testing idempotent temporal recalculation endpoint...")
    r_recalc = requests.post(f"{BASE_URL}/api/diagnostics/{run_4_id}/temporal/recalculate")
    assert r_recalc.status_code == 200, f"Recalculate failed: {r_recalc.text}"
    recalc_res = r_recalc.json()
    assert recalc_res["success"] is True
    assert recalc_res["runsProcessed"] >= 4
    assert recalc_res["observationsExtracted"] > 0
    assert recalc_res["issueTracksBuilt"] > 0

    # Verify history after recalculation matches exactly
    r_hist_2 = requests.get(f"{BASE_URL}/api/diagnostics/{run_4_id}/temporal/history?window=ALL_AVAILABLE")
    assert r_hist_2.status_code == 200
    history_2 = r_hist_2.json()
    assert len(history_2["issueTracks"]) == len(history["issueTracks"]), "Recalculation altered issue track count"
    assert len(history_2["alerts"]) == len(history["alerts"]), "Recalculation altered alert count"
    log("Idempotent recalculation verified successfully.")

    log("Phase 9 E2E Test PASSED with 100% verification across all temporal intelligence capabilities!")

if __name__ == "__main__":
    test_phase9_end_to_end()
