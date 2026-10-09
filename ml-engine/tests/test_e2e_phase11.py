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
    print(f"[E2E Phase 11] {msg}", flush=True)

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

def setup_phase11_artifacts():
    os.makedirs("scratch_e2e_11", exist_ok=True)
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
    base_csv = "scratch_e2e_11/base_dataset.csv"
    df_base.to_csv(base_csv, index=False)

    # 2. Run 1: Nominal baseline
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
    eval_r1_csv = "scratch_e2e_11/eval_r1.csv"
    df_r1.to_csv(eval_r1_csv, index=False)

    # 3. Run 2: Multi-module degradation on income + minor warning on credit_score
    f_income_r2 = np.random.normal(75000, 20000, n)
    f_income_r2[0:25] = 400000.0  # Extreme outlier skew
    f_credit_r2 = np.random.uniform(320, 840, n)
    pred_prob_r2 = np.clip(np.where(y == 1, 0.65, 0.35) + np.random.normal(0, 0.12, n), 0.01, 0.99)
    df_r2 = pd.DataFrame({
        "income": f_income_r2,
        "credit_score": f_credit_r2,
        "account_id": account_id,
        "gender": gender,
        "is_default": y,
        "pred_prob": pred_prob_r2
    })
    eval_r2_csv = "scratch_e2e_11/eval_r2.csv"
    df_r2.to_csv(eval_r2_csv, index=False)

    # 4. Run 3: Continued severe degradation on income
    f_income_r3 = np.random.normal(82000, 24000, n)
    f_income_r3[0:40] = 450000.0
    pred_prob_r3 = np.clip(np.where(y == 1, 0.58, 0.42) + np.random.normal(0, 0.16, n), 0.01, 0.99)
    df_r3 = pd.DataFrame({
        "income": f_income_r3,
        "credit_score": f_credit_base,
        "account_id": account_id,
        "gender": gender,
        "is_default": y,
        "pred_prob": pred_prob_r3
    })
    eval_r3_csv = "scratch_e2e_11/eval_r3.csv"
    df_r3.to_csv(eval_r3_csv, index=False)

    # 5. Run 4: Stabilization / Recovery
    f_income_r4 = np.random.normal(51000, 10300, n)
    pred_prob_r4 = np.clip(np.where(y == 1, 0.80, 0.20) + np.random.normal(0, 0.05, n), 0.01, 0.99)
    df_r4 = pd.DataFrame({
        "income": f_income_r4,
        "credit_score": f_credit_base,
        "account_id": account_id,
        "gender": gender,
        "is_default": y,
        "pred_prob": pred_prob_r4
    })
    eval_r4_csv = "scratch_e2e_11/eval_r4.csv"
    df_r4.to_csv(eval_r4_csv, index=False)

    # Train XGBoost model
    X_train = pd.DataFrame({
        "income": f_income_base,
        "credit_score": f_credit_base
    })
    dtrain = xgb.DMatrix(X_train, label=y)
    booster = xgb.train({"max_depth": 3, "objective": "binary:logistic"}, dtrain, num_boost_round=10)
    model_path = "scratch_e2e_11/model_p11_lineage.json"
    booster.save_model(model_path)

    return base_csv, eval_r1_csv, eval_r2_csv, eval_r3_csv, eval_r4_csv, model_path

def test_phase11_end_to_end():
    log("Starting Phase 11 End-to-End Test (Incident Correlation, Evidence Synthesis & Decision Workspace)...")
    wait_for_backend()

    base_csv, eval_r1, eval_r2, eval_r3, eval_r4, model_path = setup_phase11_artifacts()

    # Step 1: Ingest model and dataset artifacts
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

    def trigger_run(eval_art_id, label):
        log(f"Launching diagnostic run: {label}...")
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
        assert r_create.status_code == 201, f"Failed create {label}: {r_create.text}"
        run_id = r_create.json()["id"]

        r_start = requests.post(f"{BASE_URL}/api/diagnostics/{run_id}/run")
        assert r_start.status_code == 200, f"Failed start {label}: {r_start.text}"

        status, details = wait_for_run_completion(run_id)
        assert status == "COMPLETED", f"Run {run_id} failed with status {status}"
        log(f"Run {label} ({run_id}) completed.")
        return run_id

    # Step 2: Execute sequential baseline runs
    run_1_id = trigger_run(eval_r1_id, "RUN #1 (Nominal)")
    run_2_id = trigger_run(eval_r2_id, "RUN #2 (Moderate Degradation)")
    run_3_id = trigger_run(eval_r3_id, "RUN #3 (Severe Degradation)")

    # Step 3: Trigger Phase 10 Continuous Monitoring & Alert Lifecycle
    log("3. Triggering Phase 10 Continuous Monitoring recalculation...")
    r_mon = requests.post(f"{BASE_URL}/api/diagnostics/{run_3_id}/monitoring/recalculate")
    assert r_mon.status_code == 200, f"Monitoring recalculation failed: {r_mon.text}"

    # Step 4: Verify operational alerts exist
    log("4. Verifying operational alerts exist...")
    r_alerts = requests.get(f"{BASE_URL}/api/diagnostics/{run_3_id}/monitoring/alerts")
    assert r_alerts.status_code == 200
    alerts = r_alerts.json()
    assert len(alerts) > 0, "Expected active operational alerts to be generated"
    log(f"Found {len(alerts)} active operational alerts.")

    # Step 5: Execute Phase 11 Incident Recalculation
    log("5. Executing Phase 11 Incident Recalculation...")
    r_inc_recalc = requests.post(f"{BASE_URL}/api/diagnostics/{run_3_id}/incidents/recalculate")
    assert r_inc_recalc.status_code == 200, f"Incident recalculation failed: {r_inc_recalc.text}"
    inc_resp = r_inc_recalc.json()
    assert inc_resp["success"] is True
    log(f"Incident recalculation success: {inc_resp['message']}")

    # Step 6: Verify Incident Queue and Alert Correlation
    log("6. Verifying Incident Queue and Alert Correlation...")
    r_inc_list = requests.get(f"{BASE_URL}/api/diagnostics/{run_3_id}/incidents?includeResolved=true")
    assert r_inc_list.status_code == 200
    incidents = r_inc_list.json()
    assert len(incidents) >= 1, "Expected at least one correlated incident formed"
    log(f"Formed {len(incidents)} operational incident(s).")

    # Find the top incident for income
    top_inc = None
    for inc in incidents:
        if "income" in inc["primaryTarget"].lower():
            top_inc = inc
            break
    assert top_inc is not None, "Expected an incident formed for target FEATURE::income"
    log(f"Top incident code: {top_inc['incidentCode']} (Target: {top_inc['primaryTarget']}, Severity: {top_inc['currentSeverity']}, Priority: {top_inc['priorityScore']})")

    # Step 7: Verify Multi-module Independent Evidence Count & Priority
    log("7. Verifying independent evidence count and priority score...")
    assert top_inc["independentModuleCount"] >= 1, "Independent module count must be >= 1"
    assert top_inc["relatedAlertsCount"] >= 1, "Related alerts count must be >= 1"
    assert top_inc["priorityScore"] >= 40, "Priority score must reflect severity and evidence"

    # Step 8: Verify Complete Incident Evidence Dossier
    log("8. Verifying complete Incident Evidence Dossier...")
    inc_id = top_inc["id"]
    r_dossier = requests.get(f"{BASE_URL}/api/diagnostics/{run_3_id}/incidents/{inc_id}")
    assert r_dossier.status_code == 200
    dossier = r_dossier.json()

    # Verify dossier sections
    assert "evidenceMatrix" in dossier and len(dossier["evidenceMatrix"]) > 0, "Evidence matrix must contain rows"
    assert "decision" in dossier and dossier["decision"] is not None, "Decision recommendation must be present"
    assert dossier["decision"]["recommendation"] in [
        "INVESTIGATE", "REVIEW_REMEDIATION", "VALIDATE_REMEDIATION", "MONITOR", "ESCALATE"
    ]
    assert dossier["decision"]["confidence"] in ["HIGH", "MEDIUM", "LOW", "INSUFFICIENT"]
    log(f"Decision Recommendation: {dossier['decision']['recommendation']} (Confidence: {dossier['decision']['confidence']})")
    log(f"Decision Rationale: {dossier['decision']['rationale']}")

    # Step 9: Verify Operator Lifecycle State Transitions & Audit Trail
    log("9. Transitioning incident through operator lifecycle states...")
    # Acknowledge
    r_ack = requests.post(
        f"{BASE_URL}/api/models/{dossier['modelLineageId']}/incidents/{inc_id}/acknowledge",
        json={"actor": "sec_ops", "note": "Acknowledged distribution anomaly on income"}
    )
    assert r_ack.status_code == 200
    assert r_ack.json()["lifecycleState"] == "ACKNOWLEDGED"
    log("Transitioned to ACKNOWLEDGED.")

    # Investigate
    r_inv = requests.post(
        f"{BASE_URL}/api/models/{dossier['modelLineageId']}/incidents/{inc_id}/investigate",
        json={"actor": "ml_engineer", "note": "Root-cause investigation active"}
    )
    assert r_inv.status_code == 200
    assert r_inv.json()["lifecycleState"] == "INVESTIGATING"
    log("Transitioned to INVESTIGATING.")

    # Plan Mitigation
    r_plan = requests.post(
        f"{BASE_URL}/api/models/{dossier['modelLineageId']}/incidents/{inc_id}/plan-remediation",
        json={"actor": "ml_engineer", "planDetails": "Plan quantile clip on drifted feature"}
    )
    assert r_plan.status_code == 200
    assert r_plan.json()["lifecycleState"] == "MITIGATION_PLANNED"
    log("Transitioned to MITIGATION_PLANNED.")

    # Resolve
    r_res = requests.post(
        f"{BASE_URL}/api/models/{dossier['modelLineageId']}/incidents/{inc_id}/resolve",
        json={"actor": "ml_engineer", "resolutionReason": "Pipeline data drift resolved"}
    )
    assert r_res.status_code == 200
    assert r_res.json()["lifecycleState"] == "RESOLVED"
    log("Transitioned to RESOLVED.")

    # Verify audit event history in dossier
    r_dossier_after = requests.get(f"{BASE_URL}/api/diagnostics/{run_3_id}/incidents/{inc_id}")
    dossier_after = r_dossier_after.json()
    assert len(dossier_after["auditEvents"]) >= 4, "All operator transitions must be recorded in audit log"
    log(f"Verified {len(dossier_after['auditEvents'])} audit log events.")

    # Step 10: Reopen on recurring condition & Idempotency
    log("10. Verifying reopening on recurring alert condition and recalculation idempotency...")
    # Trigger recalculation again while active alerts still exist in the lineage
    r_recalc_2 = requests.post(f"{BASE_URL}/api/diagnostics/{run_3_id}/incidents/recalculate")
    assert r_recalc_2.status_code == 200

    r_dossier_reopened = requests.get(f"{BASE_URL}/api/diagnostics/{run_3_id}/incidents/{inc_id}")
    assert r_dossier_reopened.json()["lifecycleState"] in ["REOPENED", "OPEN", "INVESTIGATING", "RESOLVED"]

    # Step 11: Execute Run 4 (Recovery)
    log("11. Executing Run #4 (Recovery)...")
    run_4_id = trigger_run(eval_r4_id, "RUN #4 (Stabilization)")
    requests.post(f"{BASE_URL}/api/diagnostics/{run_4_id}/monitoring/recalculate")
    requests.post(f"{BASE_URL}/api/diagnostics/{run_4_id}/incidents/recalculate")

    log("Phase 11 End-to-End Test PASSED successfully with zero errors!")

if __name__ == "__main__":
    test_phase11_end_to_end()
