import requests
import json
import time

SPRING_BOOT_URL = "http://localhost:8080"
PYTHON_ML_URL = "http://localhost:8000"

def run_tests():
    print("=== MODEL DOCTOR PHASE 2B.3 END-TO-END VERIFICATION ===")
    
    # 1. Health checks
    print("\n1. Verifying Engine Health...")
    r = requests.get(f"{PYTHON_ML_URL}/health")
    assert r.status_code == 200, f"Python ML Engine unhealthy: {r.text}"
    print(f"   [PASS] Python ML Engine Health: {r.json()}")
    
    # 2. Test 4-module run: DQ + LEAKAGE + DRIFT + PERFORMANCE -> COMPLETED
    print("\n2. Executing 4-module Diagnostic Run (DQ + LEAKAGE + DRIFT + PERFORMANCE)...")
    payload_4mod = {
        "model": {
            "name": "credit_default_xgb",
            "framework": "xgboost",
            "taskType": "binary_classification",
            "storageUri": "models/credit_default_v2.json"
        },
        "evaluationDataset": "synthetic_fraud_eval.csv",
        "baselineDataset": "synthetic_fraud_ref.csv",
        "targetColumn": "is_fraud",
        "predictionColumn": "pred_prob",
        "modules": ["DATA_QUALITY", "LEAKAGE", "DRIFT", "PERFORMANCE"]
    }
    
    create_resp = requests.post(f"{SPRING_BOOT_URL}/api/diagnostics", json=payload_4mod)
    assert create_resp.status_code == 201, f"Failed to create run: {create_resp.text}"
    run_data = create_resp.json()
    run_id = run_data["id"]
    print(f"   Created Run ID: {run_id}")
    
    # Start the run
    start_resp = requests.post(f"{SPRING_BOOT_URL}/api/diagnostics/{run_id}/run")
    assert start_resp.status_code == 200, f"Failed to start run: {start_resp.text}"
    print(f"   Started Run: Status={start_resp.json().get('status')}")
    
    # Poll for completion
    for _ in range(15):
        get_resp = requests.get(f"{SPRING_BOOT_URL}/api/diagnostics/{run_id}")
        run_record = get_resp.json()
        status = run_record.get("status")
        if status in ["COMPLETED", "PARTIAL", "FAILED"]:
            break
        time.sleep(0.5)
        
    print(f"   Run Lifecycle Status: {status}")
    assert status == "COMPLETED", f"Expected COMPLETED status for 4 implemented modules, got {status}"
    
    # Fetch results
    results_resp = requests.get(f"{SPRING_BOOT_URL}/api/diagnostics/{run_id}/results")
    assert results_resp.status_code == 200, f"Failed to get results: {results_resp.text}"
    results_dto = results_resp.json()
    results_list = results_dto["results"]
    modules_in_result = {r["module"] for r in results_list}
    print(f"   Persisted Result Modules: {modules_in_result}")
    assert {"DATA_QUALITY", "LEAKAGE", "DRIFT", "PERFORMANCE"}.issubset(modules_in_result), f"Missing modules in results: {modules_in_result}"
    
    # Inspect PERFORMANCE result
    perf_record = next(r for r in results_list if r["module"] == "PERFORMANCE")
    assert perf_record["status"] == "COMPLETED", f"PERFORMANCE status was {perf_record['status']}"
    perf_data = perf_record["result"]
    assert perf_data["module"] == "PERFORMANCE", f"PERFORMANCE module was {perf_data['module']}"
    
    summary = perf_data["summary"]
    print("\n   === PERFORMANCE DIAGNOSTIC REPORT SUMMARY ===")
    print(f"   - Sample Count: {summary['sampleCount']} (Positive: {summary['positiveCount']}, Negative: {summary['negativeCount']}, Rate: {summary['positiveRate']:.3f})")
    print(f"   - Class Labels: Positive='{summary['positiveClassLabel']}', Negative='{summary['negativeClassLabel']}'")
    print(f"   - ROC-AUC: {summary['rocAuc']:.4f}")
    print(f"   - PR-AUC: {summary['prAuc']:.4f}")
    print(f"   - Threshold: {summary['threshold']}")
    print(f"   - Accuracy: {summary['accuracy']:.4f}")
    print(f"   - Precision: {summary['precision']:.4f}")
    print(f"   - Recall: {summary['recall']:.4f}")
    print(f"   - Specificity: {summary['specificity']:.4f}")
    print(f"   - F1 Score: {summary['f1']:.4f}")
    print(f"   - FPR: {summary['falsePositiveRate']:.4f}, FNR: {summary['falseNegativeRate']:.4f}")
    print(f"   - Log Loss: {summary['logLoss']:.4f} (Prevalence Baseline: {perf_data['baselineComparison']['baselineLogLoss']:.4f})")
    print(f"   - Brier Score: {summary['brierScore']:.4f} (Prevalence Baseline: {perf_data['baselineComparison']['baselineBrierScore']:.4f})")
    print(f"   - Calibration: ECE={summary['expectedCalibrationError']:.4f}, MCE={summary['maximumCalibrationError']:.4f}, Tendency={summary['calibrationTendency']}")
    
    cm = perf_data["confusionMatrix"]
    print(f"   - Confusion Matrix: TN={cm['trueNegative']}, FP={cm['falsePositive']}, FN={cm['falseNegative']}, TP={cm['truePositive']}")
    print(f"   - Threshold Analysis Points: {len(perf_data['thresholdAnalysis'])} entries (Best F1 @ threshold {summary['bestF1Threshold']} -> F1={summary['bestF1Value']:.4f})")
    print(f"   - Calibration Bins: {len(perf_data['calibration']['bins'])} bins evaluated")
    print(f"   - Findings Generated: {len(perf_data['findings'])}")
    for f in perf_data['findings']:
        print(f"     * [{f['severity']}] {f['id']}: {f['title']}")
    
    # Assertions on metrics
    assert 0.5 <= summary["rocAuc"] <= 1.0
    assert 0.0 <= summary["prAuc"] <= 1.0
    assert 0.0 <= summary["brierScore"] <= 1.0
    assert 0.0 <= summary["expectedCalibrationError"] <= 1.0
    print("   [PASS] 4-Module Run and Real Performance calculations verified.")
    
    # 3. Test PARTIAL run: DQ + PERFORMANCE + EXPLAINABILITY -> PARTIAL
    print("\n3. Executing Partial Run (DQ + PERFORMANCE + EXPLAINABILITY)...")
    payload_partial = {
        "model": {
            "name": "churn_predictor_nn",
            "framework": "pytorch",
            "taskType": "binary_classification",
            "storageUri": "models/churn_nn.pt"
        },
        "evaluationDataset": "synthetic_fraud_eval.csv",
        "targetColumn": "is_fraud",
        "predictionColumn": "pred_prob",
        "modules": ["DATA_QUALITY", "PERFORMANCE", "EXPLAINABILITY"]
    }
    create_p_resp = requests.post(f"{SPRING_BOOT_URL}/api/diagnostics", json=payload_partial)
    assert create_p_resp.status_code == 201
    p_run_id = create_p_resp.json()["id"]
    
    start_p_resp = requests.post(f"{SPRING_BOOT_URL}/api/diagnostics/{p_run_id}/run")
    assert start_p_resp.status_code == 200
    
    for _ in range(15):
        get_p_resp = requests.get(f"{SPRING_BOOT_URL}/api/diagnostics/{p_run_id}")
        p_record = get_p_resp.json()
        p_status = p_record.get("status")
        if p_status in ["COMPLETED", "PARTIAL", "FAILED"]:
            break
        time.sleep(0.5)
        
    print(f"   Partial Run Lifecycle Status: {p_status}")
    assert p_status == "PARTIAL", f"Expected PARTIAL status, got {p_status}"
    
    p_results_dto = requests.get(f"{SPRING_BOOT_URL}/api/diagnostics/{p_run_id}/results").json()
    p_mods = {r["module"]: r["status"] for r in p_results_dto["results"]}
    print(f"   Module statuses in Partial Run: {p_mods}")
    assert p_mods.get("DATA_QUALITY") == "COMPLETED"
    assert p_mods.get("PERFORMANCE") == "COMPLETED"
    assert p_mods.get("EXPLAINABILITY") == "NOT_IMPLEMENTED"
    print("   [PASS] PARTIAL lifecycle status and module isolation verified.")
    
    print("\n=== ALL END-TO-END VERIFICATIONS PASSED SUCCESSFULLY! ===")

if __name__ == "__main__":
    run_tests()
