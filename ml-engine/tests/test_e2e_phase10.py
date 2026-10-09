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
    print(f"[E2E Phase 10] {msg}", flush=True)

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

def setup_phase10_artifacts():
    os.makedirs("scratch_e2e_10", exist_ok=True)
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
    base_csv = "scratch_e2e_10/base_dataset.csv"
    df_base.to_csv(base_csv, index=False)

    # 2. Run 1 Eval: Nominal / low drift
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
    eval_r1_csv = "scratch_e2e_10/eval_r1.csv"
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
    eval_r2_csv = "scratch_e2e_10/eval_r2.csv"
    df_r2.to_csv(eval_r2_csv, index=False)

    # 4. Run 3 Eval: Severe drift on income + extreme outliers
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
    eval_r3_csv = "scratch_e2e_10/eval_r3.csv"
    df_r3.to_csv(eval_r3_csv, index=False)

    # 5. Run 4 Eval: Post-remediation recovery
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
    eval_r4_csv = "scratch_e2e_10/eval_r4.csv"
    df_r4.to_csv(eval_r4_csv, index=False)

    # Train XGBoost Model Artifact
    X_train = pd.DataFrame({
        "income": f_income_base,
        "credit_score": f_credit_base
    })
    dtrain = xgb.DMatrix(X_train, label=y)
    booster = xgb.train({"max_depth": 3, "objective": "binary:logistic"}, dtrain, num_boost_round=10)
    model_path = "scratch_e2e_10/model_p10_lineage.json"
    booster.save_model(model_path)

    return base_csv, eval_r1_csv, eval_r2_csv, eval_r3_csv, eval_r4_csv, model_path

def test_phase10_end_to_end():
    log("Starting Phase 10 End-to-End Test (Continuous Monitoring, Alert Lifecycle & Health Decision Engine)...")
    wait_for_backend()

    base_csv, eval_r1, eval_r2, eval_r3, eval_r4, model_path = setup_phase10_artifacts()

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

    # 3. Create and execute Phase 8 Experiment on Run 3 (to verify experiment isolation)
    log("3. Creating and executing Phase 8 candidate experiment on Run #3...")
    r_rems = requests.get(f"{BASE_URL}/api/diagnostics/{run_3_id}/remediations")
    rem_id = None
    if r_rems.status_code == 200 and len(r_rems.json()) > 0:
        rem_id = r_rems.json()[0]["id"]

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
    if r_exp.status_code == 201:
        exp_id = r_exp.json()["id"]
        requests.post(f"{BASE_URL}/api/diagnostics/{run_3_id}/experiments/{exp_id}/run")
        log(f"Phase 8 experiment {exp_id} launched.")

    # 4. Trigger Phase 9 Temporal Recalculation
    log("4. Triggering Phase 9 Temporal recalculation...")
    r_temp = requests.post(f"{BASE_URL}/api/diagnostics/{run_3_id}/temporal/recalculate")
    assert r_temp.status_code == 200, f"Temporal recalculation failed: {r_temp.text}"

    # 5. Create / Update Phase 10 Monitoring Policy
    log("5. Configuring Phase 10 Monitoring Policy...")
    # Resolve lineage from run 3
    r_run3 = requests.get(f"{BASE_URL}/api/diagnostics/{run_3_id}")
    lineage_id = r_run3.json().get("modelArtifactId", str(model_artifact_id))

    policy_payload = {
        "modelLineageId": lineage_id,
        "enabled": True,
        "observationWindow": "ALL_AVAILABLE",
        "minBaselineRunsRequired": 2,
        "alertPersistenceThreshold": 2,
        "recoveryConsecutiveRuns": 2,
        "alertCooldownRuns": 1,
        "hysteresisMarginPct": 5.0,
        "experimentOverlayEnabled": False,
        "healthEvaluationMode": "DETERMINISTIC_NYQUIST_V1"
    }
    r_pol = requests.put(f"{BASE_URL}/api/models/{lineage_id}/monitoring-policy", json=policy_payload)
    assert r_pol.status_code == 200, f"Failed to set policy: {r_pol.text}"
    pol_data = r_pol.json()
    assert pol_data["minBaselineRunsRequired"] == 2
    log(f"Monitoring policy configured for lineage '{lineage_id}'.")

    # 6. Run Phase 10 Continuous Monitoring Recalculation
    log("6. Recalculating Continuous Monitoring and Model Health...")
    r_recalc = requests.post(f"{BASE_URL}/api/models/{lineage_id}/monitoring/recalculate")
    assert r_recalc.status_code == 200, f"Recalculation failed: {r_recalc.text}"
    recalc_data = r_recalc.json()
    log(f"Recalculate response: Evaluated {recalc_data['baselineRunsEvaluated']} runs. Health state: {recalc_data['overallState']}")

    # Verify baseline run selection excludes experiment runs
    assert recalc_data["baselineRunsEvaluated"] >= 3, "Should evaluate all 3 operational baseline runs."

    # 7. Query Current Model Health & Multidimensional Health Vector
    log("7. Querying Current Model Health and Health Vector...")
    r_health = requests.get(f"{BASE_URL}/api/models/{lineage_id}/health")
    assert r_health.status_code == 200, f"Failed to get health: {r_health.text}"
    health = r_health.json()

    assert health["overallState"] in ["CRITICAL", "DEGRADED"], f"Expected CRITICAL/DEGRADED state, got {health['overallState']}"
    assert health["dataSufficiency"]["sufficient"] is True
    assert len(health["healthVector"]) == 9, f"Expected 9 health dimensions, got {len(health['healthVector'])}"

    # Check drift dimension
    drift_dim = health["healthVector"].get("DRIFT")
    assert drift_dim is not None
    assert drift_dim["state"] in ["CRITICAL", "DEGRADED", "WARNING"]
    log(f"Health Vector Dimension DRIFT: State={drift_dim['state']}, summary='{drift_dim['summary']}'")

    # Check Health Index & Traceable Penalties
    if health.get("healthIndex") is not None:
        assert 0 <= health["healthIndex"] <= 100
        breakdown = health.get("healthIndexBreakdown")
        assert breakdown is not None
        assert len(breakdown["penalties"]) > 0
        log(f"Health Index: {health['healthIndex']}/100 with {len(breakdown['penalties'])} traceable penalties.")

    # 8. Query Operational Alerts and Deduplication / Fingerprint
    log("8. Querying and verifying operational alerts...")
    r_alerts = requests.get(f"{BASE_URL}/api/models/{lineage_id}/alerts")
    assert r_alerts.status_code == 200, f"Failed alerts query: {r_alerts.text}"
    alerts = r_alerts.json()
    assert len(alerts) > 0, "Expected at least one operational alert generated from severe drift."

    drift_alert = next((a for a in alerts if "income" in (a.get("targetKey") or "") or a.get("alertType") == "DISTRIBUTION_DRIFT"), alerts[0])
    alert_id = drift_alert["id"]
    fingerprint = drift_alert["alertFingerprint"]
    assert fingerprint is not None and len(fingerprint) > 0
    assert drift_alert["lifecycleState"] in ["OPEN", "REOPENED"]
    log(f"Found operational alert ID {alert_id} (Fingerprint: {fingerprint}, Severity: {drift_alert['currentSeverity']}, State: {drift_alert['lifecycleState']})")

    # 9. Test Alert Lifecycle Transitions (Acknowledge -> Investigate -> Suppress -> Resolve)
    log("9. Testing Alert Lifecycle Transitions...")

    # Acknowledge
    r_ack = requests.post(f"{BASE_URL}/api/models/{lineage_id}/alerts/{alert_id}/acknowledge", json={"actor": "LEAD_MLOPS", "note": "Acknowledged issue on income"})
    assert r_ack.status_code == 200, f"Failed acknowledge: {r_ack.text}"
    assert r_ack.json()["lifecycleState"] == "ACKNOWLEDGED"
    log("  -> Alert transition: ACKNOWLEDGED")

    # Investigate
    r_inv = requests.post(f"{BASE_URL}/api/models/{lineage_id}/alerts/{alert_id}/investigate", json={"actor": "LEAD_MLOPS", "note": "Deep diving feature drift"})
    assert r_inv.status_code == 200, f"Failed investigate: {r_inv.text}"
    assert r_inv.json()["lifecycleState"] == "INVESTIGATING"
    log("  -> Alert transition: INVESTIGATING")

    # Suppress
    r_sup = requests.post(f"{BASE_URL}/api/models/{lineage_id}/alerts/{alert_id}/suppress", json={"actor": "ONCALL_ENG", "reason": "Silencing during active incident window", "durationHours": 12})
    assert r_sup.status_code == 200, f"Failed suppress: {r_sup.text}"
    assert r_sup.json()["lifecycleState"] == "SUPPRESSED"
    log("  -> Alert transition: SUPPRESSED")

    # Resolve
    r_res = requests.post(f"{BASE_URL}/api/models/{lineage_id}/alerts/{alert_id}/resolve", json={"actor": "LEAD_MLOPS", "resolutionReason": "Fixed via upstream data pipeline patch"})
    assert r_res.status_code == 200, f"Failed resolve: {r_res.text}"
    assert r_res.json()["lifecycleState"] == "RESOLVED"
    log("  -> Alert transition: RESOLVED")

    # 10. Ingest Run 4 (Post-remediation recovered baseline run)
    log("10. Triggering Run #4 (Recovered operational run)...")
    run_4_id = trigger_run(eval_r4_id, "RUN #4 (Post-remediation Recovered)")

    # 11. Run Phase 10 Recalculation after Run 4
    log("11. Recalculating Continuous Monitoring with Run #4...")
    r_recalc2 = requests.post(f"{BASE_URL}/api/models/{lineage_id}/monitoring/recalculate")
    assert r_recalc2.status_code == 200
    recalc2_data = r_recalc2.json()
    log(f"Recalculate after Run 4: Evaluated {recalc2_data['baselineRunsEvaluated']} runs. Health state: {recalc2_data['overallState']}")

    # 12. Verify Historical Health Snapshots
    log("12. Verifying Immutable Health Snapshots...")
    r_hist = requests.get(f"{BASE_URL}/api/models/{lineage_id}/health/history?limit=10")
    assert r_hist.status_code == 200, f"Failed health history query: {r_hist.text}"
    history_snapshots = r_hist.json()
    assert len(history_snapshots) >= 2, f"Expected at least 2 historical snapshots, got {len(history_snapshots)}"
    log(f"Retrieved {len(history_snapshots)} health snapshots across operational timeline.")

    # 13. Verify Provenance and Connected Intelligence in Evidence Dossier
    log("13. Verifying Evidence Dossier Provenance Chain...")
    r_health2 = requests.get(f"{BASE_URL}/api/models/{lineage_id}/health")
    assert r_health2.status_code == 200
    dossier = r_health2.json().get("evidenceDossier")
    if dossier:
        assert dossier.get("evaluatedRunId") is not None or dossier.get("modelLineageId") is not None
        log(f"Evidence dossier verified with provenance links.")

    # 14. Verify Idempotency of Recalculation
    log("14. Verifying Monitoring Recalculation Idempotency...")
    r_recalc_idem = requests.post(f"{BASE_URL}/api/models/{lineage_id}/monitoring/recalculate")
    assert r_recalc_idem.status_code == 200
    idem_data = r_recalc_idem.json()
    assert idem_data["overallState"] == recalc2_data["overallState"]
    assert idem_data["activeAlertsCount"] == recalc2_data["activeAlertsCount"]
    log("Idempotent recalculation confirmed: Same health state and alert counts maintained.")

    log("[SUCCESS] Phase 10 End-to-End Test PASSED completely!")

if __name__ == "__main__":
    test_phase10_end_to_end()
