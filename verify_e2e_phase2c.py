import requests
import json
import time
import sys

SPRING_BOOT_URL = "http://localhost:8080"
PYTHON_ML_URL = "http://localhost:8000"

def log_section(title):
    print(f"\n{'='*70}\n{title}\n{'='*70}")

def run_phase2c_e2e():
    log_section("PHASE 2C — CORE DIAGNOSTIC PIPELINE E2E VERIFICATION")
    
    # Check Python ML Engine
    print("Checking Python ML Engine at", PYTHON_ML_URL)
    try:
        r = requests.get(f"{PYTHON_ML_URL}/health", timeout=3)
        assert r.status_code == 200, f"Python health returned {r.status_code}"
        print("  [OK] Python ML Engine is healthy:", r.json())
    except Exception as e:
        print("  [ERROR] Python ML Engine unreachable:", e)
        sys.exit(1)
        
    # Check Spring Boot Orchestrator
    print("Checking Spring Boot Orchestrator at", SPRING_BOOT_URL)
    try:
        r = requests.get(f"{SPRING_BOOT_URL}/actuator/health", timeout=3)
        assert r.status_code == 200, f"Spring Boot health returned {r.status_code}"
        print("  [OK] Spring Boot is healthy:", r.json())
    except Exception as e:
        print("  [ERROR] Spring Boot unreachable:", e)
        sys.exit(1)

    # -------------------------------------------------------------
    # Scenario A: Full Successful Run (All 7 Core Modules)
    # -------------------------------------------------------------
    log_section("SCENARIO A: Full 7-Module Diagnostic Run (All Core Engines)")
    payload_all7 = {
        "model": {
            "name": "fraud_classifier_v17",
            "framework": "xgboost",
            "taskType": "binary_classification",
            "storageUri": "models/fraud_classifier_v17.json"
        },
        "evaluationDataset": "synthetic_fraud_eval.csv",
        "baselineDataset": "synthetic_fraud_ref.csv",
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
            "ROBUSTNESS"
        ]
    }
    
    create_resp = requests.post(f"{SPRING_BOOT_URL}/api/diagnostics", json=payload_all7)
    assert create_resp.status_code == 201, f"Failed to create run: {create_resp.text}"
    run_a = create_resp.json()
    run_id_a = run_a["id"]
    print(f"  [1] Created Run: ID={run_id_a}, Status={run_a['status']}, Modules={len(run_a['selectedModules'])}")
    assert run_a["status"] == "CREATED"
    assert len(run_a["selectedModules"]) == 7

    start_resp = requests.post(f"{SPRING_BOOT_URL}/api/diagnostics/{run_id_a}/run")
    assert start_resp.status_code == 200, f"Failed to start run: {start_resp.text}"
    print(f"  [2] Dispatched Run: Status={start_resp.json().get('status')}")

    # Poll for completion
    for i in range(30):
        time.sleep(0.5)
        get_resp = requests.get(f"{SPRING_BOOT_URL}/api/diagnostics/{run_id_a}")
        assert get_resp.status_code == 200
        run_record = get_resp.json()
        status = run_record.get("status")
        if status in ["COMPLETED", "PARTIAL", "FAILED"]:
            break

    print(f"  [3] Execution Finished: Final Status = {status}")
    assert status == "COMPLETED", f"Expected COMPLETED status, got {status} (Error: {run_record.get('errorMessage')})"

    # Verify Results
    results_resp = requests.get(f"{SPRING_BOOT_URL}/api/diagnostics/{run_id_a}/results")
    assert results_resp.status_code == 200
    res_data = results_resp.json()
    results = res_data["results"]
    print(f"  [4] Persisted Results: {len(results)} module results retrieved")
    assert len(results) == 7

    modules_found = {r["module"]: r for r in results}
    expected_modules = ["DATA_QUALITY", "LEAKAGE", "DRIFT", "PERFORMANCE", "EXPLAINABILITY", "BIAS", "ROBUSTNESS"]
    for mod_name in expected_modules:
        assert mod_name in modules_found, f"Missing module {mod_name} in results"
        mod_res = modules_found[mod_name]
        assert mod_res["status"] == "COMPLETED", f"Module {mod_name} was {mod_res['status']}"
        assert mod_res["result"] is not None and len(mod_res["result"]) > 0, f"Module {mod_name} result empty"
        print(f"      + {mod_name:<16}: Status={mod_res['status']} | Payload keys: {list(mod_res['result'].keys())}")

    print("  [PASS] Scenario A: All 7 Core Diagnostic Engines executed and persisted successfully.")

    # -------------------------------------------------------------
    # Scenario B: Deferred Module -> PARTIAL Status
    # -------------------------------------------------------------
    log_section("SCENARIO B: Deferred Module Execution -> PARTIAL Lifecycle")
    payload_partial = {
        "model": {
            "name": "fraud_classifier_v17",
            "framework": "xgboost",
            "taskType": "binary_classification"
        },
        "evaluationDataset": "synthetic_fraud_eval.csv",
        "targetColumn": "is_fraud",
        "predictionColumn": "pred_prob",
        "modules": ["DATA_QUALITY", "PERFORMANCE", "EXPERIMENTS"]
    }

    create_b = requests.post(f"{SPRING_BOOT_URL}/api/diagnostics", json=payload_partial)
    assert create_b.status_code == 201
    run_id_b = create_b.json()["id"]
    print(f"  [1] Created Run with Deferred Module: ID={run_id_b}")

    start_b = requests.post(f"{SPRING_BOOT_URL}/api/diagnostics/{run_id_b}/run")
    assert start_b.status_code == 200

    for _ in range(20):
        time.sleep(0.5)
        rec_b = requests.get(f"{SPRING_BOOT_URL}/api/diagnostics/{run_id_b}").json()
        if rec_b.get("status") in ["COMPLETED", "PARTIAL", "FAILED"]:
            break

    print(f"  [2] Final Lifecycle Status for Run with Deferred Module: {rec_b.get('status')}")
    assert rec_b.get("status") == "PARTIAL", f"Expected PARTIAL status, got {rec_b.get('status')}"

    res_b = requests.get(f"{SPRING_BOOT_URL}/api/diagnostics/{run_id_b}/results").json()
    b_mod_statuses = {r["module"]: r["status"] for r in res_b["results"]}
    print(f"  [3] Individual Module Statuses: {b_mod_statuses}")
    assert b_mod_statuses.get("DATA_QUALITY") == "COMPLETED"
    assert b_mod_statuses.get("PERFORMANCE") == "COMPLETED"
    assert b_mod_statuses.get("EXPERIMENTS") == "NOT_IMPLEMENTED"
    print("  [PASS] Scenario B: Deferred module correctly transitions run to PARTIAL status.")

    # -------------------------------------------------------------
    # Scenario C: Duplicate Execution Rejected (409 Conflict)
    # -------------------------------------------------------------
    log_section("SCENARIO C: Duplicate Execution Rejection")
    dup_resp = requests.post(f"{SPRING_BOOT_URL}/api/diagnostics/{run_id_a}/run")
    print(f"  [1] Second execution attempt on completed run returned HTTP {dup_resp.status_code}")
    assert dup_resp.status_code == 409, f"Expected HTTP 409 Conflict, got {dup_resp.status_code}: {dup_resp.text}"
    dup_error = dup_resp.json()
    print(f"  [2] Rejection details: {dup_error}")
    assert "Cannot execute diagnostic run" in dup_error.get("message", "")
    print("  [PASS] Scenario C: Duplicate run execution safely rejected with HTTP 409.")

    # -------------------------------------------------------------
    # Scenario D: Unknown Run Lookup (404 Not Found)
    # -------------------------------------------------------------
    log_section("SCENARIO D: Unknown Run Lookup (404 Handling)")
    unknown_run_id = "run_nonexistent_99999999"
    r_404_get = requests.get(f"{SPRING_BOOT_URL}/api/diagnostics/{unknown_run_id}")
    print(f"  [1] GET /api/diagnostics/{unknown_run_id} -> HTTP {r_404_get.status_code}")
    assert r_404_get.status_code == 404

    r_404_res = requests.get(f"{SPRING_BOOT_URL}/api/diagnostics/{unknown_run_id}/results")
    print(f"  [2] GET /api/diagnostics/{unknown_run_id}/results -> HTTP {r_404_res.status_code}")
    assert r_404_res.status_code == 404
    print("  [PASS] Scenario D: Nonexistent run requests return structured 404.")

    # -------------------------------------------------------------
    # Scenario E: Duplicate Module Request Validation (400 Bad Request)
    # -------------------------------------------------------------
    log_section("SCENARIO E: Request Validation Hardening (400 Bad Request)")
    dup_mod_payload = {
        "model": {"name": "fraud_classifier_v17"},
        "evaluationDataset": "synthetic_fraud_eval.csv",
        "targetColumn": "is_fraud",
        "modules": ["DATA_QUALITY", "DATA_QUALITY"]
    }
    r_dup_mod = requests.post(f"{SPRING_BOOT_URL}/api/diagnostics", json=dup_mod_payload)
    print(f"  [1] Duplicate modules payload -> HTTP {r_dup_mod.status_code}")
    assert r_dup_mod.status_code == 400
    print(f"  [2] Validation response: {r_dup_mod.json()}")
    assert "Duplicate diagnostic modules" in r_dup_mod.json().get("message", "")
    print("  [PASS] Scenario E: Duplicate module registration rejected with HTTP 400.")

    log_section("ALL 5 PHASE 2C E2E SCENARIOS VERIFIED SUCCESSFULLY!")

if __name__ == "__main__":
    run_phase2c_e2e()
