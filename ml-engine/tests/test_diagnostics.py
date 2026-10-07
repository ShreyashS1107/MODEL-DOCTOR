import pytest
from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)


def test_list_engines_metadata():
    response = client.get("/api/v1/diagnostics/engines")
    assert response.status_code == 200
    engines = response.json()
    assert len(engines) >= 7

    fairness_engine = next((e for e in engines if e["id"] == "fairness"), None)
    assert fairness_engine is not None
    assert fairness_engine["status"] == "READY"
    assert fairness_engine["phase"] == "2B.5"

    robustness_engine = next((e for e in engines if e["id"] == "robustness"), None)
    assert robustness_engine is not None
    assert robustness_engine["status"] == "READY"
    assert robustness_engine["phase"] == "2B.6"


def test_diagnostics_run_seven_modules_completed():
    payload = {
        "runId": "run_test_001",
        "modelName": "fraud_classifier_v17",
        "modelFramework": "xgboost",
        "taskType": "binary_classification",
        "evaluationDataset": "synthetic_eval_benchmark.csv",
        "baselineDataset": "synthetic_baseline_benchmark.csv",
        "targetColumn": "is_fraud",
        "predictionColumn": "pred_prob",
        "protectedAttribute": "is_foreign_ip",
        "modules": ["DATA_QUALITY", "LEAKAGE", "DRIFT", "PERFORMANCE", "EXPLAINABILITY", "BIAS", "ROBUSTNESS"],
    }
    response = client.post("/api/v1/diagnostics/run", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert data["runId"] == "run_test_001"
    assert data["status"] == "COMPLETED"
    assert len(data["modules"]) == 7

    # Verify DATA_QUALITY module result
    dq_mod = next(m for m in data["modules"] if m["module"] == "DATA_QUALITY")
    assert dq_mod["status"] == "COMPLETED"
    assert dq_mod["result"]["module"] == "DATA_QUALITY"
    assert dq_mod["result"]["summary"]["rowCount"] > 0
    assert len(dq_mod["result"]["columns"]) > 0
    assert "findings" in dq_mod["result"]

    # Verify LEAKAGE module result
    leak_mod = next(m for m in data["modules"] if m["module"] == "LEAKAGE")
    assert leak_mod["status"] == "COMPLETED"
    assert leak_mod["result"]["module"] == "LEAKAGE"
    assert leak_mod["result"]["target"]["column"] == "is_fraud"
    assert len(leak_mod["result"]["features"]) > 0
    assert "findings" in leak_mod["result"]

    # Verify DRIFT module result
    drift_mod = next(m for m in data["modules"] if m["module"] == "DRIFT")
    assert drift_mod["status"] == "COMPLETED"
    assert drift_mod["result"]["module"] == "DRIFT"
    assert drift_mod["result"]["summary"]["featuresEvaluated"] > 0
    assert len(drift_mod["result"]["features"]) > 0
    assert "schema" in drift_mod["result"]
    assert "findings" in drift_mod["result"]

    # Verify PERFORMANCE module result
    perf_mod = next(m for m in data["modules"] if m["module"] == "PERFORMANCE")
    assert perf_mod["status"] == "COMPLETED"
    assert perf_mod["result"]["module"] == "PERFORMANCE"
    assert perf_mod["result"]["summary"]["sampleCount"] > 0
    assert perf_mod["result"]["summary"]["rocAuc"] is not None
    assert "confusionMatrix" in perf_mod["result"]
    assert "calibration" in perf_mod["result"]
    assert "thresholdAnalysis" in perf_mod["result"]
    assert "baselineComparison" in perf_mod["result"]
    assert "findings" in perf_mod["result"]

    # Verify EXPLAINABILITY module result
    explain_mod = next(m for m in data["modules"] if m["module"] == "EXPLAINABILITY")
    assert explain_mod["status"] == "COMPLETED"
    assert explain_mod["result"]["module"] == "EXPLAINABILITY"
    assert explain_mod["result"]["summary"]["sampleCount"] > 0
    assert explain_mod["result"]["summary"]["featureCount"] > 0
    assert len(explain_mod["result"]["globalImportance"]) > 0
    assert "concentration" in explain_mod["result"]
    assert "localExplanations" in explain_mod["result"]
    assert "permutationImportance" in explain_mod["result"]
    assert "importanceAgreement" in explain_mod["result"]
    assert "findings" in explain_mod["result"]

    # Verify BIAS module result
    bias_mod = next(m for m in data["modules"] if m["module"] == "BIAS")
    assert bias_mod["status"] == "COMPLETED"
    assert bias_mod["result"]["module"] == "BIAS"
    assert bias_mod["result"]["summary"]["totalEvaluatedRows"] > 0
    assert bias_mod["result"]["summary"]["groupCount"] >= 2
    assert "groups" in bias_mod["result"]
    assert "disparity" in bias_mod["result"]
    assert "worstGroups" in bias_mod["result"]
    assert "findings" in bias_mod["result"]

    # Verify ROBUSTNESS module result
    robust_mod = next(m for m in data["modules"] if m["module"] == "ROBUSTNESS")
    assert robust_mod["status"] == "COMPLETED"
    assert robust_mod["result"]["module"] == "ROBUSTNESS"
    assert robust_mod["result"]["summary"]["samplesEvaluated"] > 0
    assert robust_mod["result"]["summary"]["numericFeaturesEvaluated"] > 0
    assert "noiseSensitivity" in robust_mod["result"]
    assert len(robust_mod["result"]["noiseSensitivity"]["levels"]) >= 3
    assert "featureSensitivity" in robust_mod["result"]
    assert len(robust_mod["result"]["featureSensitivity"]["features"]) > 0
    assert "boundarySearch" in robust_mod["result"]
    assert robust_mod["result"]["boundarySearch"]["method"] == "TREE_BOUNDARY_SEARCH"
    assert "fgsm" in robust_mod["result"]
    assert robust_mod["result"]["fgsm"]["status"] == "NOT_APPLICABLE"
    assert "missingnessStress" in robust_mod["result"]
    assert "subgroupRobustness" in robust_mod["result"]
    assert "findings" in robust_mod["result"]


def test_diagnostics_run_partial_execution():
    payload = {
        "runId": "run_test_partial_001",
        "modelName": "fraud_classifier_v17",
        "modelFramework": "xgboost",
        "taskType": "binary_classification",
        "evaluationDataset": "synthetic_eval_benchmark.csv",
        "baselineDataset": "synthetic_baseline_benchmark.csv",
        "targetColumn": "is_fraud",
        "predictionColumn": "pred_prob",
        "protectedAttribute": "is_foreign_ip",
        "modules": ["DATA_QUALITY", "PERFORMANCE", "EXPLAINABILITY", "BIAS", "ROBUSTNESS", "EXPERIMENTS"],
    }
    response = client.post("/api/v1/diagnostics/run", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert data["runId"] == "run_test_partial_001"
    assert data["status"] == "PARTIAL"
    assert len(data["modules"]) == 6

    dq_mod = next(m for m in data["modules"] if m["module"] == "DATA_QUALITY")
    assert dq_mod["status"] == "COMPLETED"

    perf_mod = next(m for m in data["modules"] if m["module"] == "PERFORMANCE")
    assert perf_mod["status"] == "COMPLETED"

    explain_mod = next(m for m in data["modules"] if m["module"] == "EXPLAINABILITY")
    assert explain_mod["status"] == "COMPLETED"

    bias_mod = next(m for m in data["modules"] if m["module"] == "BIAS")
    assert bias_mod["status"] == "COMPLETED"

    robust_mod = next(m for m in data["modules"] if m["module"] == "ROBUSTNESS")
    assert robust_mod["status"] == "COMPLETED"

    exp_mod = next(m for m in data["modules"] if m["module"] == "EXPERIMENTS")
    assert exp_mod["status"] == "NOT_IMPLEMENTED"


def test_diagnostics_run_unsupported_module():
    payload = {
        "runId": "run_test_002",
        "modelName": "fraud_classifier_v17",
        "modelFramework": "xgboost",
        "taskType": "binary_classification",
        "executionMode": "BENCHMARK",
        "evaluationDataset": "synthetic_eval_benchmark.csv",
        "targetColumn": "is_fraud",
        "modules": ["NON_EXISTENT_MODULE"],
    }
    response = client.post("/api/v1/diagnostics/run", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert data["runId"] == "run_test_002"
    assert data["status"] == "FAILED"
    assert len(data["modules"]) == 1
    assert data["modules"][0]["status"] == "FAILED"
    assert "not supported" in data["modules"][0]["message"]


def test_diagnostics_real_mode_missing_dataset_rejected(tmp_path):
    non_existent = str(tmp_path / "does_not_exist.csv")
    payload = {
        "runId": "run_real_missing_001",
        "modelName": "my_model",
        "executionMode": "REAL",
        "evaluationDataset": non_existent,
        "targetColumn": "is_fraud",
        "modules": ["DATA_QUALITY", "LEAKAGE"],
    }
    response = client.post("/api/v1/diagnostics/run", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "FAILED"
    assert "Failed to load evaluation dataset" in data["error"]
    assert all(m["status"] == "FAILED" for m in data["modules"])


def test_diagnostics_real_mode_with_actual_files(tmp_path):
    import pandas as pd
    import numpy as np
    import xgboost as xgb

    # 1. Create real CSV dataset
    df = pd.DataFrame({
        "feature_a": [1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0] * 10,
        "feature_b": [10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0, 90.0, 100.0] * 10,
        "is_foreign": [0, 1, 0, 1, 0, 1, 0, 1, 0, 1] * 10,
        "is_fraud": [0, 0, 0, 0, 0, 1, 0, 1, 1, 1] * 10,
        "pred_prob": [0.1, 0.15, 0.2, 0.25, 0.3, 0.8, 0.35, 0.85, 0.9, 0.95] * 10,
    })
    csv_path = tmp_path / "eval_real.csv"
    df.to_csv(csv_path, index=False)

    # 2. Train and save real XGBoost model
    X = df[["feature_a", "feature_b", "is_foreign"]]
    y = df["is_fraud"]
    dtrain = xgb.DMatrix(X, label=y)
    booster = xgb.train({"max_depth": 2, "objective": "binary:logistic"}, dtrain, num_boost_round=5)
    model_path = tmp_path / "real_model.json"
    booster.save_model(str(model_path))

    # 3. Run diagnostics with REAL execution mode
    payload = {
        "runId": "run_real_e2e_001",
        "modelName": "real_model",
        "modelFramework": "xgboost",
        "taskType": "binary_classification",
        "modelStorageUri": str(model_path),
        "executionMode": "REAL",
        "evaluationDataset": str(csv_path),
        "targetColumn": "is_fraud",
        "predictionColumn": "pred_prob",
        "protectedAttribute": "is_foreign",
        "modules": ["DATA_QUALITY", "LEAKAGE", "PERFORMANCE", "EXPLAINABILITY", "BIAS", "ROBUSTNESS"],
    }
    response = client.post("/api/v1/diagnostics/run", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "COMPLETED"
    assert len(data["modules"]) == 6
    for mod in data["modules"]:
        assert mod["status"] == "COMPLETED", f"Module {mod['module']} failed: {mod.get('error')}"

