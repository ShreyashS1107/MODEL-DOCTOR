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
    print(f"[E2E Phase 12] {msg}", flush=True)

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

def setup_phase12_artifacts():
    os.makedirs("scratch_e2e_12", exist_ok=True)
    np.random.seed(42)
    n = 600

    # -------------------------------------------------------------
    # LINEAGE A: Credit Risk Model (Recovers after drift)
    # -------------------------------------------------------------
    f_income_base = np.random.normal(50000, 10000, n)
    f_credit_base = np.random.uniform(300, 850, n)
    account_id = [f"ACC_{i:05d}" for i in range(n)]
    gender = np.random.choice(["Male", "Female"], n, p=[0.55, 0.45])
    y_a = np.random.choice([0, 1], n, p=[0.60, 0.40])
    pred_prob_base = np.clip(np.where(y_a == 1, 0.80, 0.20) + np.random.normal(0, 0.05, n), 0.01, 0.99)

    df_base_a = pd.DataFrame({
        "income": f_income_base,
        "credit_score": f_credit_base,
        "account_id": account_id,
        "gender": gender,
        "is_default": y_a,
        "pred_prob": pred_prob_base
    })
    base_a_csv = "scratch_e2e_12/base_credit_risk.csv"
    df_base_a.to_csv(base_a_csv, index=False)

    # Lineage A Run 1: Nominal baseline (Healthy)
    f_income_a1 = np.random.normal(50500, 10200, n)
    pred_prob_a1 = np.clip(np.where(y_a == 1, 0.79, 0.21) + np.random.normal(0, 0.05, n), 0.01, 0.99)
    df_a1 = pd.DataFrame({
        "income": f_income_a1,
        "credit_score": f_credit_base,
        "account_id": account_id,
        "gender": gender,
        "is_default": y_a,
        "pred_prob": pred_prob_a1
    })
    eval_a1_csv = "scratch_e2e_12/eval_a1.csv"
    df_a1.to_csv(eval_a1_csv, index=False)

    # Lineage A Run 2: Drift & Degraded on income
    f_income_a2 = np.random.normal(75000, 20000, n)
    f_income_a2[0:30] = 400000.0  # outlier skew
    pred_prob_a2 = np.clip(np.where(y_a == 1, 0.65, 0.35) + np.random.normal(0, 0.12, n), 0.01, 0.99)
    df_a2 = pd.DataFrame({
        "income": f_income_a2,
        "credit_score": f_credit_base,
        "account_id": account_id,
        "gender": gender,
        "is_default": y_a,
        "pred_prob": pred_prob_a2
    })
    eval_a2_csv = "scratch_e2e_12/eval_a2.csv"
    df_a2.to_csv(eval_a2_csv, index=False)

    # Lineage A Run 3: Recovery / Stabilization
    f_income_a3 = np.random.normal(50800, 10100, n)
    pred_prob_a3 = np.clip(np.where(y_a == 1, 0.81, 0.19) + np.random.normal(0, 0.05, n), 0.01, 0.99)
    df_a3 = pd.DataFrame({
        "income": f_income_a3,
        "credit_score": f_credit_base,
        "account_id": account_id,
        "gender": gender,
        "is_default": y_a,
        "pred_prob": pred_prob_a3
    })
    eval_a3_csv = "scratch_e2e_12/eval_a3.csv"
    df_a3.to_csv(eval_a3_csv, index=False)

    # Lineage A Run 4 [EXPERIMENT]: Candidate experiment run with quantile clipping
    f_income_a4 = np.random.normal(50200, 10000, n)
    pred_prob_a4 = np.clip(np.where(y_a == 1, 0.83, 0.17) + np.random.normal(0, 0.04, n), 0.01, 0.99)
    df_a4 = pd.DataFrame({
        "income": f_income_a4,
        "credit_score": f_credit_base,
        "account_id": account_id,
        "gender": gender,
        "is_default": y_a,
        "pred_prob": pred_prob_a4
    })
    eval_a4_exp_csv = "scratch_e2e_12/eval_a4_exp.csv"
    df_a4.to_csv(eval_a4_exp_csv, index=False)

    # Train Model A
    ts = int(time.time())
    X_train_a = pd.DataFrame({"income": f_income_base, "credit_score": f_credit_base})
    dtrain_a = xgb.DMatrix(X_train_a, label=y_a)
    booster_a = xgb.train({"max_depth": 3, "objective": "binary:logistic"}, dtrain_a, num_boost_round=10)
    model_a_path = f"scratch_e2e_12/model_credit_risk_{ts}.json"
    booster_a.save_model(model_a_path)

    # -------------------------------------------------------------
    # LINEAGE B: Fraud Detection Model (Persistent Degradation & Critical Risk)
    # -------------------------------------------------------------
    f_amount_base = np.random.exponential(100, n)
    f_velocity_base = np.random.poisson(3, n)
    device_id = [f"DEV_{i:05d}" for i in range(n)]
    region = np.random.choice(["NA", "EU", "APAC"], n, p=[0.5, 0.3, 0.2])
    y_b = np.random.choice([0, 1], n, p=[0.90, 0.10])
    pred_prob_base_b = np.clip(np.where(y_b == 1, 0.85, 0.10) + np.random.normal(0, 0.05, n), 0.01, 0.99)

    df_base_b = pd.DataFrame({
        "amount": f_amount_base,
        "velocity": f_velocity_base,
        "device_id": device_id,
        "region": region,
        "is_fraud": y_b,
        "pred_prob": pred_prob_base_b
    })
    base_b_csv = "scratch_e2e_12/base_fraud_detection.csv"
    df_base_b.to_csv(base_b_csv, index=False)

    # Lineage B Run 1: Extreme drift on amount + velocity
    f_amount_b1 = np.random.exponential(450, n)
    f_amount_b1[0:50] = 5000.0  # Massive fraud surge outlier
    pred_prob_b1 = np.clip(np.where(y_b == 1, 0.50, 0.50) + np.random.normal(0, 0.20, n), 0.01, 0.99)
    df_b1 = pd.DataFrame({
        "amount": f_amount_b1,
        "velocity": f_velocity_base,
        "device_id": device_id,
        "region": region,
        "is_fraud": y_b,
        "pred_prob": pred_prob_b1
    })
    eval_b1_csv = "scratch_e2e_12/eval_b1.csv"
    df_b1.to_csv(eval_b1_csv, index=False)

    # Lineage B Run 2: Continued severe drift & performance collapse
    f_amount_b2 = np.random.exponential(600, n)
    f_amount_b2[0:80] = 9000.0
    pred_prob_b2 = np.clip(np.where(y_b == 1, 0.40, 0.60) + np.random.normal(0, 0.25, n), 0.01, 0.99)
    df_b2 = pd.DataFrame({
        "amount": f_amount_b2,
        "velocity": f_velocity_base,
        "device_id": device_id,
        "region": region,
        "is_fraud": y_b,
        "pred_prob": pred_prob_b2
    })
    eval_b2_csv = "scratch_e2e_12/eval_b2.csv"
    df_b2.to_csv(eval_b2_csv, index=False)

    # Train Model B
    X_train_b = pd.DataFrame({"amount": f_amount_base, "velocity": f_velocity_base})
    dtrain_b = xgb.DMatrix(X_train_b, label=y_b)
    booster_b = xgb.train({"max_depth": 3, "objective": "binary:logistic"}, dtrain_b, num_boost_round=10)
    model_b_path = f"scratch_e2e_12/model_fraud_detection_{ts}.json"
    booster_b.save_model(model_b_path)

    return (
        base_a_csv, eval_a1_csv, eval_a2_csv, eval_a3_csv, eval_a4_exp_csv, model_a_path,
        base_b_csv, eval_b1_csv, eval_b2_csv, model_b_path
    )

def test_phase12_end_to_end():
    log("Starting Phase 12 End-to-End Test (Model Reliability Governance & Fleet Intelligence)...")
    wait_for_backend()

    (
        base_a_csv, eval_a1, eval_a2, eval_a3, eval_a4_exp, model_a_path,
        base_b_csv, eval_b1, eval_b2, model_b_path
    ) = setup_phase12_artifacts()

    # Upload helpers
    def upload_model(path):
        with open(path, "rb") as f:
            r = requests.post(f"{BASE_URL}/api/artifacts/models", files={"file": f}, data={"framework": "xgboost", "taskType": "binary_classification"})
        assert r.status_code == 201, f"Failed model upload: {r.text}"
        return r.json()["id"]

    def upload_dataset(path):
        with open(path, "rb") as f:
            r = requests.post(f"{BASE_URL}/api/artifacts/datasets", files={"file": f})
        assert r.status_code == 201, f"Failed dataset upload {path}: {r.text}"
        return r.json()["id"]

    log("1. Ingesting model and dataset artifacts for multiple lineages...")
    mod_a_id = upload_model(model_a_path)
    mod_b_id = upload_model(model_b_path)

    base_a_id = upload_dataset(base_a_csv)
    eval_a1_id = upload_dataset(eval_a1)
    eval_a2_id = upload_dataset(eval_a2)
    eval_a3_id = upload_dataset(eval_a3)
    eval_a4_exp_id = upload_dataset(eval_a4_exp)

    base_b_id = upload_dataset(base_b_csv)
    eval_b1_id = upload_dataset(eval_b1)
    eval_b2_id = upload_dataset(eval_b2)

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

    def trigger_run(model_id, eval_art_id, base_art_id, target_col, pred_col, protected_col, exec_mode, label):
        log(f"Launching diagnostic run: {label} (Mode: {exec_mode})...")
        payload = {
            "modelArtifactId": model_id,
            "evaluationDatasetArtifactId": eval_art_id,
            "baselineDatasetArtifactId": base_art_id,
            "targetColumn": target_col,
            "predictionColumn": pred_col,
            "protectedAttribute": protected_col,
            "executionMode": exec_mode,
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

    # -------------------------------------------------------------
    # Step 2: Execute Operational Baseline & Experiment Runs for Lineage A
    # -------------------------------------------------------------
    log("2. Executing operational runs for Lineage A (Credit Risk)...")
    run_a1_id = trigger_run(mod_a_id, eval_a1_id, base_a_id, "is_default", "pred_prob", "gender", "REAL", "Lineage A Run 1 (Nominal)")
    run_a2_id = trigger_run(mod_a_id, eval_a2_id, base_a_id, "is_default", "pred_prob", "gender", "REAL", "Lineage A Run 2 (Drifted)")
    run_a3_id = trigger_run(mod_a_id, eval_a3_id, base_a_id, "is_default", "pred_prob", "gender", "REAL", "Lineage A Run 3 (Recovered)")
    run_a4_exp_id = trigger_run(mod_a_id, eval_a4_exp_id, base_a_id, "is_default", "pred_prob", "gender", "EXPERIMENT", "Lineage A Run 4 (Experiment)")

    # -------------------------------------------------------------
    # Step 3: Execute Operational Runs for Lineage B
    # -------------------------------------------------------------
    log("3. Executing operational runs for Lineage B (Fraud Detection)...")
    run_b1_id = trigger_run(mod_b_id, eval_b1_id, base_b_id, "is_fraud", "pred_prob", "region", "REAL", "Lineage B Run 1 (Severe Drift)")
    run_b2_id = trigger_run(mod_b_id, eval_b2_id, base_b_id, "is_fraud", "pred_prob", "region", "REAL", "Lineage B Run 2 (Critical Drift)")

    # -------------------------------------------------------------
    # Step 4: Trigger Monitoring and Incidents across runs
    # -------------------------------------------------------------
    log("4. Triggering Phase 10 Continuous Monitoring & Phase 11 Incident correlation...")
    for rid in [run_a1_id, run_a2_id, run_a3_id, run_b1_id, run_b2_id]:
        requests.post(f"{BASE_URL}/api/diagnostics/{rid}/monitoring/recalculate")
        requests.post(f"{BASE_URL}/api/diagnostics/{rid}/incidents/recalculate")

    # Resolve all incidents in Lineage A to simulate verified operational recovery
    r_inc_a = requests.get(f"{BASE_URL}/api/diagnostics/{run_a3_id}/incidents?includeResolved=true")
    if r_inc_a.status_code == 200:
        r_run_info = requests.get(f"{BASE_URL}/api/diagnostics/{run_a3_id}")
        mod_name = r_run_info.json().get("modelArtifact", {}).get("originalFilename", "model_credit_risk")
        for inc in r_inc_a.json():
            requests.post(f"{BASE_URL}/api/models/{mod_name}/incidents/{inc['id']}/resolve", json={"actor": "ops", "resolutionReason": "Pipeline data stream normalized"})

    # -------------------------------------------------------------
    # Step 5: Trigger Phase 12 Reliability Recalculation
    # -------------------------------------------------------------
    log("5. Triggering Phase 12 Reliability Recalculation for Lineage A and Lineage B...")
    r_recalc_a = requests.post(f"{BASE_URL}/api/diagnostics/{run_a3_id}/reliability/recalculate")
    assert r_recalc_a.status_code == 200, f"Recalculate A failed: {r_recalc_a.text}"
    resp_a = r_recalc_a.json()
    assert resp_a["success"] is True

    r_recalc_b = requests.post(f"{BASE_URL}/api/diagnostics/{run_b2_id}/reliability/recalculate")
    assert r_recalc_b.status_code == 200, f"Recalculate B failed: {r_recalc_b.text}"
    resp_b = r_recalc_b.json()
    assert resp_b["success"] is True

    # -------------------------------------------------------------
    # Step 6: Verify Reliability Profile & Score for Lineage A
    # -------------------------------------------------------------
    log("6. Verifying Lineage A Reliability Profile...")
    r_prof_a = requests.get(f"{BASE_URL}/api/diagnostics/{run_a3_id}/reliability")
    assert r_prof_a.status_code == 200
    prof_a = r_prof_a.json()

    lineage_a_id = prof_a["modelLineageId"]
    assert prof_a["reliabilityScore"] >= 0 and prof_a["reliabilityScore"] <= 100, "Score must be bounded [0, 100]"
    assert prof_a["reliabilityState"] in ["RELIABILITY_HEALTHY", "RELIABILITY_STABLE", "RELIABILITY_RECOVERING", "RELIABILITY_DEGRADED", "RELIABILITY_AT_RISK", "RELIABILITY_CRITICAL"]
    assert prof_a["reliabilityConfidence"] in ["HIGH", "MEDIUM", "LOW", "INSUFFICIENT"]
    assert prof_a["governanceRecommendation"] in ["NORMAL_OPERATION", "MONITOR", "REVIEW_REQUIRED", "PRIORITY_REVIEW", "ESCALATE", "INSUFFICIENT_EVIDENCE"]
    log(f"Lineage A: Score={prof_a['reliabilityScore']}, State={prof_a['reliabilityState']}, Grade={prof_a['grade']}, Recommendation={prof_a['governanceRecommendation']}")

    # Verify score breakdown is deterministic and traceable
    breakdown_a = prof_a["scoreBreakdown"]
    assert breakdown_a["baseScore"] == 100
    assert breakdown_a["netScore"] == prof_a["reliabilityScore"]
    assert isinstance(breakdown_a["items"], list)

    # -------------------------------------------------------------
    # Step 7: Verify Authoritative Trajectory and Experiment Separation
    # -------------------------------------------------------------
    log("7. Verifying Trajectory & Experiment Separation on Lineage A...")
    r_history_a = requests.get(f"{BASE_URL}/api/models/{lineage_a_id}/reliability/history")
    assert r_history_a.status_code == 200
    history_a = r_history_a.json()

    # Trajectory must strictly contain BASELINE runs only (at least 3 runs: a1, a2, a3)
    assert len(history_a) >= 3, f"Expected at least 3 authoritative baseline trajectory points, got {len(history_a)}"
    for pt in history_a:
        assert pt.get("runType") == "BASELINE" or pt.get("executionMode") == "REAL"
        assert pt["runId"] != run_a4_exp_id, "EXPERIMENT run must NEVER be present in authoritative reliability history"
    log("Authoritative trajectory validated: strictly BASELINE runs present.")

    # -------------------------------------------------------------
    # Step 8: Verify Recovery Analysis and Remediation Durability
    # -------------------------------------------------------------
    log("8. Verifying Recovery Analysis and Remediation Durability...")
    recovery_a = prof_a["recoveryProfile"]
    assert "degradationEventsCount" in recovery_a
    assert "recoveredEventsCount" in recovery_a
    assert "recoveryRate" in recovery_a
    log(f"Lineage A Recovery Profile: Degraded={recovery_a['degradationEventsCount']}, Recovered={recovery_a['recoveredEventsCount']}, Rate={recovery_a['recoveryRate']}%")

    # -------------------------------------------------------------
    # Step 9: Verify Lineage B Profile & Risk
    # -------------------------------------------------------------
    log("9. Verifying Lineage B Reliability Profile...")
    r_prof_b = requests.get(f"{BASE_URL}/api/diagnostics/{run_b2_id}/reliability")
    assert r_prof_b.status_code == 200
    prof_b = r_prof_b.json()
    lineage_b_id = prof_b["modelLineageId"]

    log(f"Lineage B: Score={prof_b['reliabilityScore']}, State={prof_b['reliabilityState']}, Grade={prof_b['grade']}, Recommendation={prof_b['governanceRecommendation']}")
    assert prof_b["reliabilityState"] in ["RELIABILITY_DEGRADED", "RELIABILITY_AT_RISK", "RELIABILITY_CRITICAL"]

    # -------------------------------------------------------------
    # Step 10: Verify Fleet Intelligence & Risk Ranking
    # -------------------------------------------------------------
    log("10. Verifying Fleet Risk Ranking...")
    r_risk = requests.get(f"{BASE_URL}/api/models/reliability/fleet/risk")
    assert r_risk.status_code == 200
    risk_ranks = r_risk.json()
    assert len(risk_ranks) >= 2, f"Expected at least 2 models in fleet ranking, got {len(risk_ranks)}"

    # Lineage B should be higher risk rank (#1) than Lineage A
    rank_1 = risk_ranks[0]
    log(f"Fleet Rank #1: {rank_1['modelName']} (Risk Tier: {rank_1['riskTier']}, Score: {rank_1['reliabilityScore']}, State: {rank_1['reliabilityState']})")
    assert len(rank_1["rankingReasons"]) > 0, "Top risk model must have explicit governance ranking reasons"

    # -------------------------------------------------------------
    # Step 11: Verify Fleet Recurring Patterns (Cross-Model Intelligence)
    # -------------------------------------------------------------
    log("11. Verifying Fleet Recurring Patterns...")
    r_patterns = requests.get(f"{BASE_URL}/api/models/reliability/fleet/patterns")
    assert r_patterns.status_code == 200
    patterns = r_patterns.json()
    log(f"Discovered {len(patterns)} cross-model recurring pattern(s).")
    for pat in patterns:
        log(f"Pattern: {pat['patternType']} across {pat['affectedLineagesCount']} models (Disclaimer: {pat['nonCausalDisclaimer']})")
        assert "non-causal" in pat["nonCausalDisclaimer"].lower() or "causal" in pat["nonCausalDisclaimer"].lower()
        assert pat["totalIncidentsCount"] >= 1

    # -------------------------------------------------------------
    # Step 12: Verify Side-by-Side Model Comparison
    # -------------------------------------------------------------
    log("12. Verifying Side-by-Side Model Comparison...")
    r_compare = requests.get(f"{BASE_URL}/api/models/reliability/compare?left={lineage_a_id}&right={lineage_b_id}")
    assert r_compare.status_code == 200
    comp = r_compare.json()
    assert comp["left"]["modelLineageId"] == lineage_a_id
    assert comp["right"]["modelLineageId"] == lineage_b_id
    assert "governanceComparisonSummary" in comp
    log(f"Comparison: Left Score={comp['left']['reliabilityScore']} vs Right Score={comp['right']['reliabilityScore']}")
    log(f"Comparison Summary: {comp['governanceComparisonSummary']}")

    # -------------------------------------------------------------
    # Step 13: Verify Idempotency on Repeated Recalculations
    # -------------------------------------------------------------
    log("13. Verifying Idempotency on repeated recalculations...")
    # Get current events count
    r_ev1 = requests.get(f"{BASE_URL}/api/models/{lineage_a_id}/reliability/events")
    events_count_1 = len(r_ev1.json()) if r_ev1.status_code == 200 else 0

    # Recalculate again
    r_recalc_a2 = requests.post(f"{BASE_URL}/api/models/{lineage_a_id}/reliability/recalculate")
    assert r_recalc_a2.status_code == 200
    r_prof_a2 = requests.get(f"{BASE_URL}/api/models/{lineage_a_id}/reliability")
    prof_a2 = r_prof_a2.json()

    # Verify score, state, recommendation are perfectly identical
    assert prof_a2["reliabilityScore"] == prof_a["reliabilityScore"], "Idempotency check: Score must not change on identical data"
    assert prof_a2["reliabilityState"] == prof_a["reliabilityState"], "Idempotency check: State must not change"
    assert prof_a2["governanceRecommendation"] == prof_a["governanceRecommendation"], "Idempotency check: Recommendation must not change"

    # Verify events count did not duplicate
    r_ev2 = requests.get(f"{BASE_URL}/api/models/{lineage_a_id}/reliability/events")
    events_count_2 = len(r_ev2.json()) if r_ev2.status_code == 200 else 0
    assert events_count_2 == events_count_1, f"Idempotency check: Event count should remain {events_count_1}, got {events_count_2}"

    log("Idempotency verified successfully!")
    log("=== PHASE 12 E2E TEST COMPLETED SUCCESSFULLY ===")

if __name__ == "__main__":
    test_phase12_end_to_end()
