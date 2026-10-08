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
    print(f"[E2E Phase 6] {msg}", flush=True)

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

def setup_phase6_test_artifacts():
    os.makedirs("scratch_e2e_6", exist_ok=True)
    np.random.seed(42)
    n = 500
    
    # Feature 1: Drifted feature strongly correlated with prediction errors and high SHAP importance
    f_drift_base = np.random.normal(0, 1, n)
    f_drift_eval = np.random.normal(2.6, 1.3, n) # severe shift -> PSI > 0.25
    
    # Feature 2: High importance / high robustness sensitivity feature
    f_important = np.random.uniform(10, 100, n)
    
    # Feature 3: Continuous feature with moderate leakage / correlation
    f_amount = np.random.exponential(50, n)
    
    # Target
    y = np.random.choice([0, 1], n, p=[0.65, 0.35])
    
    # Subgroup with disparity in error rates
    subgroup = np.random.choice(["north", "south"], n, p=[0.7, 0.3])
    
    # Predictions: Introduce intentional mistakes where f_drift_eval is elevated or subgroup == south
    pred_prob = np.zeros(n)
    for i in range(n):
        base_p = 0.88 if y[i] == 1 else 0.12
        if f_drift_eval[i] > 2.7:
            # Concentrated error region
            base_p = 1.0 - base_p
        if subgroup[i] == "south" and np.random.rand() < 0.35:
            base_p = 1.0 - base_p
        pred_prob[i] = np.clip(base_p + np.random.normal(0, 0.06), 0.01, 0.99)
    
    df_eval = pd.DataFrame({
        "drifted_feature": f_drift_eval,
        "important_feature": f_important,
        "amount": f_amount,
        "subgroup": subgroup,
        "target": y,
        "prediction": pred_prob
    })
    
    df_base = pd.DataFrame({
        "drifted_feature": f_drift_base,
        "important_feature": f_important,
        "amount": f_amount,
        "subgroup": subgroup,
        "target": y,
        "prediction": pred_prob
    })
    
    eval_csv = "scratch_e2e_6/eval_p6.csv"
    base_csv = "scratch_e2e_6/base_p6.csv"
    df_eval.to_csv(eval_csv, index=False)
    df_base.to_csv(base_csv, index=False)
    
    # Train XGBoost model
    X_train = pd.DataFrame({
        "drifted_feature": f_drift_eval,
        "important_feature": f_important,
        "amount": f_amount
    })
    dtrain = xgb.DMatrix(X_train, label=y)
    booster = xgb.train({"max_depth": 3, "objective": "binary:logistic"}, dtrain, num_boost_round=15)
    model_path = "scratch_e2e_6/model_p6.json"
    booster.save_model(model_path)
    
    # Ingest model
    with open(model_path, "rb") as f:
        r_mdl = requests.post(f"{BASE_URL}/api/artifacts/models", files={"file": f}, data={
            "framework": "xgboost",
            "taskType": "binary_classification",
            "description": "Phase 6 Root-Cause E2E Model"
        })
        assert r_mdl.status_code == 201, f"Model ingest failed: {r_mdl.text}"
        model_id = r_mdl.json()["id"]
        
    # Ingest datasets
    with open(eval_csv, "rb") as f:
        r_eval = requests.post(f"{BASE_URL}/api/artifacts/datasets", files={"file": f})
        assert r_eval.status_code == 201, f"Eval ingest failed: {r_eval.text}"
        eval_id = r_eval.json()["id"]
        
    with open(base_csv, "rb") as f:
        r_base = requests.post(f"{BASE_URL}/api/artifacts/datasets", files={"file": f})
        assert r_base.status_code == 201, f"Base ingest failed: {r_base.text}"
        base_id = r_base.json()["id"]
        
    return model_id, eval_id, base_id

def test_phase6_end_to_end():
    log("Starting Phase 6 Root-Cause Investigation & Evidence Graph E2E verification...")
    wait_for_backend()
    
    model_id, eval_id, base_id = setup_phase6_test_artifacts()
    log(f"Artifacts ingested: model={model_id}, eval={eval_id}, base={base_id}")
    
    # 1. Create a full 8-module diagnostic run in REAL execution mode
    create_payload = {
        "modelArtifactId": model_id,
        "evaluationDatasetArtifactId": eval_id,
        "baselineDatasetArtifactId": base_id,
        "executionMode": "REAL",
        "targetColumn": "target",
        "predictionColumn": "prediction",
        "protectedAttribute": "subgroup",
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
    
    r_create = requests.post(f"{BASE_URL}/api/diagnostics", json=create_payload)
    assert r_create.status_code == 201, f"Run creation failed: {r_create.text}"
    run_id = r_create.json()["id"]
    log(f"Created diagnostic run {run_id}")
    
    # 2. Trigger execution
    r_exec = requests.post(f"{BASE_URL}/api/diagnostics/{run_id}/run")
    assert r_exec.status_code == 200, f"Execution trigger failed: {r_exec.text}"
    
    # 3. Await completion
    status, run_data = wait_for_run_completion(run_id, max_seconds=50)
    log(f"Run {run_id} completed with status: {status}")
    assert status == "COMPLETED", f"Expected COMPLETED status but got {status}"
    
    # 4. Verify Phase 4 correlations endpoint
    r_corrs = requests.get(f"{BASE_URL}/api/diagnostics/{run_id}/correlations")
    assert r_corrs.status_code == 200, f"Correlations fetch failed: {r_corrs.text}"
    correlations = r_corrs.json()
    assert len(correlations) > 0, "Expected at least 1 cross-module correlation finding"
    log(f"Retrieved {len(correlations)} cross-module correlation findings.")
    
    # 5. Verify Phase 6 Investigations endpoint
    r_inv = requests.get(f"{BASE_URL}/api/diagnostics/{run_id}/investigations")
    assert r_inv.status_code == 200, f"Investigations fetch failed: {r_inv.text}"
    investigations = r_inv.json()
    log(f"Retrieved {len(investigations)} ranked investigation targets.")
    assert len(investigations) > 0, "Expected at least 1 investigation target"
    
    # Verify deterministic sorting and non-causality in hypotheses
    prev_score = float("inf")
    for inv in investigations:
        assert inv["priorityScore"] <= prev_score + 1e-6, "Investigation targets must be sorted in descending priorityScore order"
        prev_score = inv["priorityScore"]
        
        # Verify canonical keys
        assert "::" in inv["targetKey"], f"Target key must follow canonical format: {inv['targetKey']}"
        assert inv["targetType"] in ["FEATURE", "SUBGROUP", "BEHAVIOR", "ERROR_TYPE"], f"Invalid targetType: {inv['targetType']}"
        assert inv["priority"] in ["CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO"], f"Invalid priority: {inv['priority']}"
        assert inv["evidenceConfidence"] in ["VERY_HIGH", "HIGH", "MEDIUM", "LOW"], f"Invalid confidence: {inv['evidenceConfidence']}"
        assert inv["supportingModuleCount"] >= 1, "Target must have at least 1 supporting module"
        
        # Verify non-causal hypothesis wording
        hypothesis_lower = inv["hypothesis"].lower()
        assert "caused by" not in hypothesis_lower, "Hypothesis must not assert causality"
        assert "causes " not in hypothesis_lower, "Hypothesis must not assert causality"
        assert "proves " not in hypothesis_lower, "Hypothesis must not assert causality"
        assert "responsible for" not in hypothesis_lower, "Hypothesis must not assert causality"
        
        # Verify next actions are present and evidence-derived
        assert len(inv["nextActions"]) > 0, "Target must contain deterministic next actions"
        for act in inv["nextActions"]:
            assert len(act.strip()) > 5, "Next action string too short"
            
    top_target = investigations[0]
    log(f"Top Investigation Target: {top_target['targetKey']} ({top_target['priority']} - score: {top_target['priorityScore']:.2f})")
    
    # 6. Verify Investigation Dossier endpoint
    r_dossier = requests.get(f"{BASE_URL}/api/diagnostics/{run_id}/investigations/{top_target['targetKey']}")
    assert r_dossier.status_code == 200, f"Dossier fetch failed: {r_dossier.text}"
    dossier = r_dossier.json()
    assert dossier["targetKey"] == top_target["targetKey"], "Dossier targetKey mismatch"
    assert dossier["displayName"] == top_target["displayName"], "Dossier displayName mismatch"
    assert len(dossier["supportingModules"]) >= 1, "Dossier must have supporting modules"
    assert len(dossier["investigationPath"]) > 0, "Dossier must have an ordered investigation path"
    
    # Verify investigation path step sequencing
    for step in dossier["investigationPath"]:
        assert step["stepNumber"] >= 1, "Step number must be >= 1"
        assert len(step["sourceModule"]) > 0, "Step must declare source module"
        assert len(step["description"]) > 0, "Step must declare description"
        
    # Verify provenance trace
    prov = dossier["provenance"]
    assert len(prov["sourceModules"]) > 0, "Provenance must specify source modules"
    assert len(prov["sourceResultIds"]) > 0, "Provenance must have source result IDs"
    assert prov.get("isImmutableResult") is True or prov.get("immutableResult") is True, "Provenance must mark results as immutable"
    log("Verified complete Investigation Dossier with ordered path and provenance.")
    
    # 7. Verify Evidence Graph endpoint
    r_graph = requests.get(f"{BASE_URL}/api/diagnostics/{run_id}/evidence-graph")
    assert r_graph.status_code == 200, f"Evidence graph fetch failed: {r_graph.text}"
    graph = r_graph.json()
    assert "nodes" in graph and "edges" in graph, "Graph payload must contain nodes and edges"
    assert len(graph["nodes"]) > 0, "Graph nodes must not be empty"
    assert len(graph["edges"]) > 0, "Graph edges must not be empty"
    
    node_types = {n["nodeType"] for n in graph["nodes"]}
    log(f"Evidence Graph Node Types: {node_types} (total nodes: {len(graph['nodes'])}, total edges: {len(graph['edges'])})")
    assert "MODULE" in node_types, "MODULE node type missing in evidence graph"
    
    # Check edges and provenance
    allowed_relations = {
        "OBSERVES", "PRODUCES", "IMPLICATES", "INVOLVES", "SUPPORTED_BY",
        "DRIFTED_IN", "INFLUENCES", "ASSOCIATED_WITH", "SENSITIVE_UNDER",
        "CONCURRENT_WITH", "CO_OCCURS_WITH"
    }
    for edge in graph["edges"]:
        rel = edge.get("relationType") or edge.get("relationship")
        assert rel in allowed_relations, f"Unexpected relation: {rel}"
        assert len(edge["sourceModule"]) > 0, "Edge must have source module"
        assert edge.get("sourceResultId") is not None or edge.get("ruleId") is not None, "Edge must contain provenance"

        
    # 8. Verify Run Summary Phase 6 fields
    r_summary = requests.get(f"{BASE_URL}/api/diagnostics/{run_id}/summary")
    assert r_summary.status_code == 200, f"Summary fetch failed: {r_summary.text}"
    summary = r_summary.json()
    assert summary.get("investigationTargetCount", 0) == len(investigations), "investigationTargetCount mismatch"
    assert summary.get("evidenceGraphNodeCount", 0) == len(graph["nodes"]), "evidenceGraphNodeCount mismatch"
    assert summary.get("evidenceGraphEdgeCount", 0) == len(graph["edges"]), "evidenceGraphEdgeCount mismatch"
    assert summary.get("topInvestigationTarget") == top_target["targetKey"], "topInvestigationTarget mismatch"
    assert summary.get("topInvestigationScore") is not None, "topInvestigationScore missing"
    log("Verified Phase 6 metrics populated in RunSummary.")
    
    # 9. Verify Idempotent Recalculation
    r_recalc = requests.post(f"{BASE_URL}/api/diagnostics/{run_id}/correlations/recalculate")
    assert r_recalc.status_code == 200, f"Recalculate failed: {r_recalc.text}"
    
    r_inv_after = requests.get(f"{BASE_URL}/api/diagnostics/{run_id}/investigations")
    assert r_inv_after.status_code == 200
    investigations_after = r_inv_after.json()
    assert len(investigations_after) == len(investigations), f"Recalculation changed target count from {len(investigations)} to {len(investigations_after)}"
    assert investigations_after[0]["targetKey"] == top_target["targetKey"], "Recalculation changed top target"
    log("Verified idempotent recalculation preserves exact investigation targets without duplicates.")
    
    # 10. Verify 404 for non-existent target key
    r_404 = requests.get(f"{BASE_URL}/api/diagnostics/{run_id}/investigations/FEATURE::non_existent_feature_123")
    assert r_404.status_code == 404, f"Expected 404 for non-existent target key, got {r_404.status_code}"
    log("Verified 404 handling for non-existent target key.")
    
    log("SUCCESS: Phase 6 Root-Cause Investigation & Evidence Graph E2E verification passed!")

if __name__ == "__main__":
    test_phase6_end_to_end()
