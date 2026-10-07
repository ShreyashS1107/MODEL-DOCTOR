import httpx
import json

client = httpx.Client(base_url="http://localhost:8080", timeout=30.0)

print("=== TEST 1: DQ + LEAKAGE + DRIFT -> COMPLETED ===")
req_body = {
    "model": {"name": "fraud_classifier_v17", "framework": "xgboost", "taskType": "binary_classification"},
    "evaluationDataset": "synthetic_eval_benchmark.csv",
    "baselineDataset": "synthetic_baseline_benchmark.csv",
    "targetColumn": "is_fraud",
    "predictionColumn": "pred_prob",
    "protectedAttribute": "is_foreign_ip",
    "modules": ["DATA_QUALITY", "LEAKAGE", "DRIFT"],
}
r1 = client.post("/api/diagnostics", json=req_body)
print("Create Status:", r1.status_code, r1.json()["id"], r1.json()["status"])
run_id = r1.json()["id"]

r2 = client.post(f"/api/diagnostics/{run_id}/run")
res2_json = r2.json()
print("Execute Status:", r2.status_code, res2_json["status"])
assert res2_json["status"] == "COMPLETED", f"Expected COMPLETED, got {res2_json['status']}"

r3 = client.get(f"/api/diagnostics/{run_id}/results")
print("Results Status:", r3.status_code, "Result count:", len(r3.json()["results"]))
modules = {m["module"]: m for m in r3.json()["results"]}
assert "DATA_QUALITY" in modules and modules["DATA_QUALITY"]["status"] == "COMPLETED"
assert "LEAKAGE" in modules and modules["LEAKAGE"]["status"] == "COMPLETED"
assert "DRIFT" in modules and modules["DRIFT"]["status"] == "COMPLETED"

drift_res = modules["DRIFT"]["result"]
print("Drift summary:", json.dumps(drift_res.get("summary", {}), indent=2))
print("Drift feature count:", len(drift_res.get("features", [])))
print("Drift findings count:", len(drift_res.get("findings", [])))
print("Drift maxPsiFeature:", drift_res.get("summary", {}).get("maxPsiFeature"))
print("Drift maxPsi:", drift_res.get("summary", {}).get("maxPsi"))

print("\n=== TEST 2: DQ + DRIFT + PERFORMANCE -> PARTIAL ===")
req_body2 = {
    "model": {"name": "fraud_classifier_v17", "framework": "xgboost", "taskType": "binary_classification"},
    "evaluationDataset": "synthetic_eval_benchmark.csv",
    "baselineDataset": "synthetic_baseline_benchmark.csv",
    "targetColumn": "is_fraud",
    "modules": ["DATA_QUALITY", "DRIFT", "PERFORMANCE"],
}
r4 = client.post("/api/diagnostics", json=req_body2)
run_id2 = r4.json()["id"]
r5 = client.post(f"/api/diagnostics/{run_id2}/run")
res5_json = r5.json()
print("Execute Status 2:", r5.status_code, res5_json["status"])
assert res5_json["status"] == "PARTIAL", f"Expected PARTIAL, got {res5_json['status']}"

r6 = client.get(f"/api/diagnostics/{run_id2}/results")
modules2 = {m["module"]: m["status"] for m in r6.json()["results"]}
print("Module statuses:", modules2)
assert modules2["DATA_QUALITY"] == "COMPLETED"
assert modules2["DRIFT"] == "COMPLETED"
assert modules2["PERFORMANCE"] == "NOT_IMPLEMENTED"

print("\nALL END-TO-END VERIFICATIONS PASSED!")
