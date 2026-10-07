import requests
import json
import time

SPRING_BOOT_URL = "http://localhost:8080"
PYTHON_ML_URL = "http://localhost:8000"

def run_tests():
    print("=== MODEL DOCTOR PHASE 2B.4 END-TO-END VERIFICATION ===")
    
    # 1. Health checks
    print("\n1. Verifying Engine Health...")
    r = requests.get(f"{PYTHON_ML_URL}/health")
    assert r.status_code == 200, f"Python ML Engine unhealthy: {r.text}"
    print(f"   [PASS] Python ML Engine Health: {r.json()}")
    
    # Check registered engines
    eng_resp = requests.get(f"{PYTHON_ML_URL}/api/v1/diagnostics/engines")
    assert eng_resp.status_code == 200
    engines = eng_resp.json()
    exp_eng = next(e for e in engines if e["id"] == "explainability")
    print(f"   [PASS] Explainability Engine Status in Registry: {exp_eng['status']} (Phase {exp_eng['phase']})")
    assert exp_eng["status"] == "READY"
    
    # 2. Test 5-module run: DQ + LEAKAGE + DRIFT + PERFORMANCE + EXPLAINABILITY -> COMPLETED
    print("\n2. Executing 5-module Diagnostic Run (DQ + LEAKAGE + DRIFT + PERFORMANCE + EXPLAINABILITY)...")
    payload_5mod = {
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
        "modules": ["DATA_QUALITY", "LEAKAGE", "DRIFT", "PERFORMANCE", "EXPLAINABILITY"]
    }
    
    create_resp = requests.post(f"{SPRING_BOOT_URL}/api/diagnostics", json=payload_5mod)
    assert create_resp.status_code == 201, f"Failed to create run: {create_resp.text}"
    run_data = create_resp.json()
    run_id = run_data["id"]
    print(f"   Created Run ID: {run_id}")
    
    # Start the run
    start_resp = requests.post(f"{SPRING_BOOT_URL}/api/diagnostics/{run_id}/run")
    assert start_resp.status_code == 200, f"Failed to start run: {start_resp.text}"
    print(f"   Started Run: Status={start_resp.json().get('status')}")
    
    # Poll for completion
    for _ in range(20):
        get_resp = requests.get(f"{SPRING_BOOT_URL}/api/diagnostics/{run_id}")
        run_record = get_resp.json()
        status = run_record.get("status")
        if status in ["COMPLETED", "PARTIAL", "FAILED"]:
            break
        time.sleep(0.5)
        
    print(f"   Run Lifecycle Status: {status}")
    assert status == "COMPLETED", f"Expected COMPLETED status for 5 implemented modules, got {status}"
    
    # Fetch results
    results_resp = requests.get(f"{SPRING_BOOT_URL}/api/diagnostics/{run_id}/results")
    assert results_resp.status_code == 200, f"Failed to get results: {results_resp.text}"
    results_dto = results_resp.json()
    results_list = results_dto["results"]
    modules_in_result = {r["module"] for r in results_list}
    print(f"   Persisted Result Modules: {modules_in_result}")
    assert {"DATA_QUALITY", "LEAKAGE", "DRIFT", "PERFORMANCE", "EXPLAINABILITY"}.issubset(modules_in_result)
    
    # Inspect EXPLAINABILITY result
    explain_record = next(r for r in results_list if r["module"] == "EXPLAINABILITY")
    assert explain_record["status"] == "COMPLETED", f"EXPLAINABILITY status was {explain_record['status']}"
    exp_data = explain_record["result"]
    assert exp_data["module"] == "EXPLAINABILITY"
    
    summary = exp_data["summary"]
    context = exp_data["explanationContext"]
    print("\n   === EXPLAINABILITY DIAGNOSTIC REPORT SUMMARY ===")
    print(f"   - Method: {summary['method']} (Output Space: {context['modelOutput']}, Base Value: {context['baseValue']})")
    print(f"   - Samples Evaluated: {summary['sampleCount']} (Raw Samples: {summary['rawSampleCount']}, Features: {summary['featureCount']})")
    print(f"   - Top Feature: '{summary['topFeature']}' (mean |SHAP| = {summary['topFeatureMeanAbsShap']:.4f})")
    print(f"   - Attribution Concentration: Top-1={summary['top1AttributionShare'] * 100:.1f}%, Top-3={summary['top3AttributionShare'] * 100:.1f}%, Top-5={summary['top5AttributionShare'] * 100:.1f}%, Entropy H={summary['normalizedEntropy']:.2f}")
    print(f"   - Importance Agreement (Spearman rho): {summary['importanceAgreementSpearman']}")
    print(f"   - Health Score: {summary['healthScore']}/100 (Passed: {summary['passed']})")
    
    print("\n   - Global Feature Importance (Top 5):")
    for feat_item in exp_data["globalImportance"][:5]:
        print(f"     #{feat_item['rank']} {feat_item['feature']}: mean |SHAP|={feat_item['meanAbsShap']:.4f}, mean signed={feat_item['meanSignedShap']:.4f}, (+{feat_item['positiveContributionRate'] * 100:.0f}% / -{feat_item['negativeContributionRate'] * 100:.0f}%)")
    
    print("\n   - Permutation Importance (Top 3):")
    for p_item in exp_data["permutationImportance"][:3]:
        print(f"     #{p_item['rank']} {p_item['feature']}: drop={p_item['importanceMean']:.4f} (±{p_item['importanceStd']:.4f})")
        
    print(f"\n   - Local Explanations ({len(exp_data['localExplanations'])} representative observations):")
    for local_obs in exp_data["localExplanations"]:
        print(f"     * [{local_obs['observationType']}] Row #{local_obs['rowIndex']}: Pred={local_obs['prediction'] * 100:.1f}%, Actual={local_obs['actual']}, Top contributor: {local_obs['topContributors'][0]['feature']} ({local_obs['topContributors'][0]['shapValue']})")
        
    print(f"\n   - Findings Generated ({len(exp_data['findings'])}):")
    for f in exp_data["findings"]:
        print(f"     * [{f['severity']}] {f['id']}: {f['title']}")
        
    assert len(exp_data["globalImportance"]) > 0
    assert len(exp_data["localExplanations"]) >= 3
    print("   [PASS] 5-Module Run and Real Explainability calculations verified.")
    
    # 3. Test PARTIAL run: DQ + PERFORMANCE + EXPLAINABILITY + ROBUSTNESS -> PARTIAL
    print("\n3. Executing Partial Run (DQ + PERFORMANCE + EXPLAINABILITY + ROBUSTNESS)...")
    payload_partial = {
        "model": {
            "name": "fraud_classifier_v17",
            "framework": "xgboost",
            "taskType": "binary_classification",
            "storageUri": "models/fraud_classifier_v17.json"
        },
        "evaluationDataset": "synthetic_fraud_eval.csv",
        "targetColumn": "is_fraud",
        "predictionColumn": "pred_prob",
        "modules": ["DATA_QUALITY", "PERFORMANCE", "EXPLAINABILITY", "ROBUSTNESS"]
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
    assert p_mods.get("EXPLAINABILITY") == "COMPLETED"
    assert p_mods.get("ROBUSTNESS") == "NOT_IMPLEMENTED"
    print("   [PASS] PARTIAL lifecycle status and module isolation verified.")
    
    print("\n=== ALL END-TO-END VERIFICATIONS PASSED SUCCESSFULLY! ===")

if __name__ == "__main__":
    run_tests()
