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
    print(f"[E2E Phase 8] {msg}", flush=True)

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

def setup_phase8_test_artifacts():
    os.makedirs("scratch_e2e_8", exist_ok=True)
    np.random.seed(42)
    n = 600
    
    # 1. Feature with extreme values & drift for transformation testing
    f_income_base = np.random.normal(50000, 15000, n)
    f_income_eval = np.random.normal(52000, 18000, n)
    # Add extreme outliers
    f_income_eval[0:10] = 500000
    
    # 2. Fragile feature with missingness
    f_credit_score = np.random.uniform(300, 850, n)
    null_idx = np.random.choice(n, int(n * 0.15), replace=False)
    f_credit_score_eval = f_credit_score.copy()
    f_credit_score_eval[null_idx] = np.nan
    
    # 3. Leakage suspect identifier
    account_id = [f"ACC_{i:05d}" for i in range(n)]
    
    # 4. Target variable
    y = np.random.choice([0, 1], n, p=[0.60, 0.40])
    
    # 5. Protected attribute
    gender = np.random.choice(["Male", "Female"], n, p=[0.55, 0.45])
    
    # 6. Predictor probabilities
    pred_prob = np.zeros(n)
    for i in range(n):
        base_p = 0.85 if y[i] == 1 else 0.15
        if f_income_eval[i] > 100000:
            base_p = 0.90 if y[i] == 1 else 0.40
        pred_prob[i] = np.clip(base_p + np.random.normal(0, 0.05), 0.01, 0.99)
    
    df_eval = pd.DataFrame({
        "income": f_income_eval,
        "credit_score": f_credit_score_eval,
        "account_id": account_id,
        "gender": gender,
        "is_default": y,
        "pred_prob": pred_prob
    })
    
    df_base = pd.DataFrame({
        "income": f_income_base,
        "credit_score": f_credit_score,
        "account_id": account_id,
        "gender": gender,
        "is_default": y,
        "pred_prob": pred_prob
    })
    
    eval_csv = "scratch_e2e_8/eval_p8_baseline.csv"
    base_csv = "scratch_e2e_8/base_p8_baseline.csv"
    df_eval.to_csv(eval_csv, index=False)
    df_base.to_csv(base_csv, index=False)
    
    # Train XGBoost model artifact
    X_train = pd.DataFrame({
        "income": np.nan_to_num(f_income_eval, nan=50000.0),
        "credit_score": np.nan_to_num(f_credit_score_eval, nan=600.0)
    })
    dtrain = xgb.DMatrix(X_train, label=y)
    booster = xgb.train({"max_depth": 3, "objective": "binary:logistic"}, dtrain, num_boost_round=10)
    model_path = "scratch_e2e_8/model_p8_baseline.json"
    booster.save_model(model_path)
    
    return eval_csv, base_csv, model_path

def test_phase8_end_to_end():
    log("Starting Phase 8 End-to-End Test (Experimental Validation & Counterfactual Evaluation Layer)...")
    wait_for_backend()
    
    eval_csv, base_csv, model_path = setup_phase8_test_artifacts()
    
    # 1. Upload Artifacts
    log("1. Ingesting artifacts...")
    with open(model_path, "rb") as f:
        r_mod = requests.post(f"{BASE_URL}/api/artifacts/models", files={"file": f}, data={"framework": "xgboost", "taskType": "binary_classification"})
    assert r_mod.status_code == 201, f"Failed model upload: {r_mod.text}"
    model_artifact_id = r_mod.json()["id"]

    with open(eval_csv, "rb") as f:
        r_eval = requests.post(f"{BASE_URL}/api/artifacts/datasets", files={"file": f})
    assert r_eval.status_code == 201, f"Failed eval upload: {r_eval.text}"
    eval_artifact_id = r_eval.json()["id"]
    
    with open(base_csv, "rb") as f:
        r_base = requests.post(f"{BASE_URL}/api/artifacts/datasets", files={"file": f})
    assert r_base.status_code == 201, f"Failed base upload: {r_base.text}"
    base_artifact_id = r_base.json()["id"]
    
    # 2. Create and Run Baseline Diagnostics
    log("2. Launching baseline diagnostic run across 8 modules...")
    run_payload = {
        "modelArtifactId": model_artifact_id,
        "evaluationDatasetArtifactId": eval_artifact_id,
        "baselineDatasetArtifactId": base_artifact_id,
        "targetColumn": "is_default",
        "predictionColumn": "pred_prob",
        "protectedAttribute": "gender",
        "executionMode": "REAL",
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
    
    r_create = requests.post(f"{BASE_URL}/api/diagnostics", json=run_payload)
    assert r_create.status_code == 201, f"Failed to create run: {r_create.text}"
    baseline_run_id = r_create.json()["id"]
    log(f"Baseline Run created: {baseline_run_id}")
    
    r_start = requests.post(f"{BASE_URL}/api/diagnostics/{baseline_run_id}/run")
    assert r_start.status_code == 200, f"Run start failed: {r_start.text}"
    
    status, run_data = wait_for_run_completion(baseline_run_id)
    log(f"Baseline Run finished with status: {status}")
    assert status in ["COMPLETED", "PARTIAL"], f"Baseline run failed: {status}"
    
    # 3. Retrieve Phase 7 Remediations
    log("3. Retrieving Phase 7 remediation candidates...")
    r_rems = requests.post(f"{BASE_URL}/api/diagnostics/{baseline_run_id}/remediations/recalculate")
    assert r_rems.status_code == 200
    remediations = r_rems.json()
    log(f"Generated {len(remediations)} remediation candidates.")
    assert len(remediations) > 0, "Expected at least 1 remediation candidate"
    
    target_rem = remediations[0]
    rem_id = target_rem["id"]
    log(f"Selected target remediation ID {rem_id}: {target_rem['title']}")
    
    # 4. Create and Execute Experiment 1: FEATURE_TRANSFORMATION
    log("4. Creating Experiment 1: FEATURE_TRANSFORMATION (Clipping on income)...")
    exp1_request = {
        "remediationId": rem_id,
        "experimentType": "FEATURE_TRANSFORMATION",
        "title": "Winsorize Income Outliers",
        "description": "Deterministic 0.01 - 0.99 quantile clipping on income feature",
        "targetType": "FEATURE",
        "targetKey": "FEATURE::income",
        "intervention": {
            "feature": "income",
            "transformation": "CLIP",
            "lowerQuantile": 0.01,
            "upperQuantile": 0.99
        },
        "requestedModules": ["PERFORMANCE", "DRIFT", "ERROR_FORENSICS", "ROBUSTNESS"],
        "acceptanceCriteria": ["PSI < 0.10", "F1 does not regress by > 0.02"],
        "regressionGuards": ["high-confidence error rate does not increase by > 0.05"],
        "deterministicSeed": 42
    }
    
    r_exp1 = requests.post(f"{BASE_URL}/api/diagnostics/{baseline_run_id}/experiments", json=exp1_request)
    assert r_exp1.status_code == 201, f"Failed to create experiment: {r_exp1.text}"
    exp1 = r_exp1.json()
    exp1_id = exp1["id"]
    log(f"Experiment 1 created with ID {exp1_id}, status={exp1['status']}")
    assert exp1["status"] == "PROPOSED"
    assert exp1["baselineRunId"] == baseline_run_id
    assert exp1["remediationId"] == rem_id
    assert "income" in json.dumps(exp1["interventionConfig"])
    
    # 5. Execute Experiment 1
    log(f"5. Executing Experiment 1 ({exp1_id})...")
    r_exec1 = requests.post(f"{BASE_URL}/api/diagnostics/{baseline_run_id}/experiments/{exp1_id}/execute")
    assert r_exec1.status_code == 200, f"Failed to execute experiment: {r_exec1.text}"
    exp1_result = r_exec1.json()
    
    log(f"Experiment 1 Completed with status={exp1_result['status']}, conclusion={exp1_result['conclusion']}")
    assert exp1_result["status"] == "COMPLETED"
    assert exp1_result["conclusion"] in ["VALIDATED", "PARTIALLY_VALIDATED", "REJECTED", "INCONCLUSIVE"]
    assert exp1_result["candidateRunId"] is not None
    assert len(exp1_result["acceptanceResults"]) >= 1
    assert len(exp1_result["regressionResults"]) >= 1
    assert exp1_result["statisticalEvidence"] is not None
    assert "predictionFlipRate" in exp1_result["statisticalEvidence"]
    assert exp1_result["statisticalEvidence"]["sampleSize"] == 600
    
    log(f"Candidate Run ID: {exp1_result['candidateRunId']}")
    log(f"Statistical Evidence: flipRate={exp1_result['statisticalEvidence'].get('predictionFlipRate')}, McNemar pVal={exp1_result['statisticalEvidence'].get('mcNemarPValue')}")
    log(f"Acceptance Results: {json.dumps(exp1_result['acceptanceResults'])}")
    log(f"Regression Results: {json.dumps(exp1_result['regressionResults'])}")
    
    # 6. Verify Experiment Dossier & Comparison Integration
    log("6. Verifying Experiment Dossier & Run Comparison integration...")
    r_get_exp1 = requests.get(f"{BASE_URL}/api/diagnostics/{baseline_run_id}/experiments/{exp1_id}")
    assert r_get_exp1.status_code == 200
    assert r_get_exp1.json()["id"] == exp1_id
    
    r_comp = requests.get(f"{BASE_URL}/api/diagnostics/{baseline_run_id}/experiments/{exp1_id}/comparison")
    assert r_comp.status_code == 200
    comp_data = r_comp.json()
    assert comp_data["baselineRunId"] == baseline_run_id
    assert comp_data["candidateRunId"] == exp1_result["candidateRunId"]
    assert len(comp_data["metricDeltas"]) > 0
    log(f"Experiment comparison returned {len(comp_data['metricDeltas'])} metric deltas with conclusion: {comp_data['conclusion']}")
    
    # Also verify RunComparisonService endpoint
    r_run_comp = requests.get(f"{BASE_URL}/api/diagnostics/{baseline_run_id}/comparison/{exp1_result['candidateRunId']}")
    assert r_run_comp.status_code == 200, f"Run comparison failed: {r_run_comp.text}"
    run_comp_data = r_run_comp.json()
    assert len(run_comp_data["metricComparisons"]) > 0
    log(f"RunComparison produced {len(run_comp_data['metricComparisons'])} comparisons with assessment: {run_comp_data['overallAssessment']}")
    
    # 7. Create and Execute Experiment 2: THRESHOLD_COUNTERFACTUAL
    log("7. Testing Experiment 2: THRESHOLD_COUNTERFACTUAL (21-point evaluation grid)...")
    exp2_request = {
        "experimentType": "THRESHOLD_COUNTERFACTUAL",
        "title": "Evaluate Decision Thresholds",
        "description": "Counterfactual evaluation across 21 threshold points from 0.00 to 1.00",
        "targetType": "MODEL",
        "targetKey": "MODEL::DECISION_THRESHOLD",
        "intervention": {
            "targetThreshold": 0.40
        },
        "requestedModules": ["PERFORMANCE"],
        "acceptanceCriteria": ["F1 does not regress by > 0.05"],
        "regressionGuards": ["FNR decreases"],
        "deterministicSeed": 42
    }
    
    r_exp2 = requests.post(f"{BASE_URL}/api/diagnostics/{baseline_run_id}/experiments", json=exp2_request)
    assert r_exp2.status_code == 201
    exp2_id = r_exp2.json()["id"]
    
    r_exec2 = requests.post(f"{BASE_URL}/api/diagnostics/{baseline_run_id}/experiments/{exp2_id}/execute")
    assert r_exec2.status_code == 200
    exp2_result = r_exec2.json()
    assert exp2_result["status"] == "COMPLETED"
    assert exp2_result["conclusion"] in ["VALIDATED", "PARTIALLY_VALIDATED", "REJECTED"]
    assert "thresholdGrid" in exp2_result["candidateMetrics"]
    grid = exp2_result["candidateMetrics"]["thresholdGrid"]
    log(f"Threshold Counterfactual produced {len(grid)} threshold grid evaluations (0.00 to 1.00)")
    assert len(grid) == 21, f"Expected 21 threshold points, got {len(grid)}"
    
    # 8. Test Experiment 3: MISSING_VALUE_STRESS
    log("8. Testing Experiment 3: MISSING_VALUE_STRESS (Controlled missingness perturbation)...")
    exp3_request = {
        "experimentType": "MISSING_VALUE_STRESS",
        "title": "Missingness Stress Test on credit_score",
        "description": "Inject 10% controlled missingness into credit_score",
        "targetType": "FEATURE",
        "targetKey": "FEATURE::credit_score",
        "intervention": {
            "feature": "credit_score",
            "missingRate": 0.10
        },
        "requestedModules": ["ROBUSTNESS", "PERFORMANCE"],
        "deterministicSeed": 42
    }
    
    r_exp3 = requests.post(f"{BASE_URL}/api/diagnostics/{baseline_run_id}/experiments", json=exp3_request)
    assert r_exp3.status_code == 201
    exp3_id = r_exp3.json()["id"]
    
    r_exec3 = requests.post(f"{BASE_URL}/api/diagnostics/{baseline_run_id}/experiments/{exp3_id}/execute")
    assert r_exec3.status_code == 200
    exp3_result = r_exec3.json()
    assert exp3_result["status"] == "COMPLETED"
    assert exp3_result["candidateRunId"] is not None
    log(f"Missingness Stress test completed with conclusion: {exp3_result['conclusion']}")
    
    # 9. Test Experiment 4: CALIBRATION_COUNTERFACTUAL
    log("9. Testing Experiment 4: CALIBRATION_COUNTERFACTUAL (Platt Scaling probability calibration on independent baseline partition)...")
    exp4_request = {
        "experimentType": "CALIBRATION_COUNTERFACTUAL",
        "title": "Platt Scaling Calibration",
        "description": "Deterministic probability calibration on independent baseline partition",
        "targetType": "MODEL",
        "targetKey": "MODEL::CALIBRATION",
        "intervention": {
            "method": "PLATT"
        },
        "requestedModules": ["PERFORMANCE"],
        "acceptanceCriteria": ["ECE decreases"],
        "deterministicSeed": 42
    }
    
    r_exp4 = requests.post(f"{BASE_URL}/api/diagnostics/{baseline_run_id}/experiments", json=exp4_request)
    assert r_exp4.status_code == 201
    exp4_id = r_exp4.json()["id"]
    
    r_exec4 = requests.post(f"{BASE_URL}/api/diagnostics/{baseline_run_id}/experiments/{exp4_id}/execute")
    assert r_exec4.status_code == 200
    exp4_result = r_exec4.json()
    assert exp4_result["status"] == "COMPLETED"
    assert exp4_result["conclusion"] == "VALIDATED"
    log(f"Calibration Counterfactual completed successfully with conclusion: {exp4_result['conclusion']}")
    
    # 10. Test Idempotency & List Endpoints
    log("10. Testing Idempotency & Experiments List...")
    r_list = requests.get(f"{BASE_URL}/api/diagnostics/{baseline_run_id}/experiments")
    assert r_list.status_code == 200
    all_experiments = r_list.json()
    log(f"Total experiments listed for run {baseline_run_id}: {len(all_experiments)}")
    assert len(all_experiments) >= 4
    
    # Test Repeated Creation Idempotency
    r_exp1_repeat = requests.post(f"{BASE_URL}/api/diagnostics/{baseline_run_id}/experiments", json=exp1_request)
    assert r_exp1_repeat.status_code in [200, 201], f"Expected 200/201 on duplicate creation, got {r_exp1_repeat.status_code}"
    repeat_id = r_exp1_repeat.json()["id"]
    assert repeat_id == exp1_id, "Expected existing experiment to be returned for identical configuration"
    log("Idempotent experiment creation confirmed.")
    
    # Test Cancellation
    exp5_request = {
        "experimentType": "FEATURE_ABLATION",
        "title": "Ablate Account ID Leakage Suspect",
        "description": "Remove identifier feature",
        "targetType": "FEATURE",
        "targetKey": "FEATURE::account_id",
        "intervention": {
            "feature": "account_id"
        },
        "requestedModules": ["PERFORMANCE"]
    }
    r_exp5 = requests.post(f"{BASE_URL}/api/diagnostics/{baseline_run_id}/experiments", json=exp5_request)
    assert r_exp5.status_code == 201
    exp5_id = r_exp5.json()["id"]
    
    r_cancel = requests.post(f"{BASE_URL}/api/diagnostics/{baseline_run_id}/experiments/{exp5_id}/cancel")
    assert r_cancel.status_code == 200
    assert r_cancel.json()["status"] == "CANCELLED"
    log("Experiment cancellation confirmed.")
    
    log("\n=======================================================")
    log(" PHASE 8 E2E VALIDATION PASSED SUCCESSFULLY!")
    log("=======================================================\n")

if __name__ == "__main__":
    test_phase8_end_to_end()
