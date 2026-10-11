import json
import urllib.request
import urllib.error

BASE_URL = "http://localhost:8080"

def make_request(path, method="GET", body=None, headers=None):
    url = f"{BASE_URL}{path}"
    req_headers = {"Accept": "application/json"}
    if headers:
        req_headers.update(headers)
    
    data = None
    if body is not None:
        if isinstance(body, dict):
            data = json.dumps(body).encode("utf-8")
            req_headers["Content-Type"] = "application/json"
        elif isinstance(body, bytes):
            data = body
    
    req = urllib.request.Request(url, data=data, headers=req_headers, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            status = resp.status
            resp_body = resp.read().decode("utf-8")
            try:
                parsed = json.loads(resp_body)
            except Exception:
                parsed = resp_body
            return {"status": status, "data": parsed, "error": None}
    except urllib.error.HTTPError as e:
        err_body = e.read().decode("utf-8")
        try:
            parsed = json.loads(err_body)
        except Exception:
            parsed = err_body
        return {"status": e.code, "data": parsed, "error": str(e)}
    except Exception as e:
        return {"status": 0, "data": None, "error": str(e)}

def run_tests():
    print("=== TESTING MODEL DOCTOR LIVE BACKEND ENDPOINTS ===")
    
    # 1. Health check
    res = make_request("/api/health")
    print(f"GET /api/health: Status={res['status']}, Data={res['data']}")
    
    # 2. Get list of models
    res_models = make_request("/api/artifacts/models")
    print(f"GET /api/artifacts/models: Status={res_models['status']}, Count={len(res_models['data']) if isinstance(res_models['data'], list) else res_models['data']}")
    model_id = res_models['data'][0]['id'] if res_models['data'] and isinstance(res_models['data'], list) else None
    
    # 3. Get list of datasets
    res_ds = make_request("/api/artifacts/datasets")
    print(f"GET /api/artifacts/datasets: Status={res_ds['status']}, Count={len(res_ds['data']) if isinstance(res_ds['data'], list) else res_ds['data']}")
    
    eval_id = None
    base_id = None
    if isinstance(res_ds['data'], list):
        for d in res_ds['data']:
            if "evaluation" in d.get("filename", ""):
                eval_id = d["id"]
            elif "baseline" in d.get("filename", ""):
                base_id = d["id"]
    
    print(f"Using model_id={model_id}, eval_id={eval_id}, base_id={base_id}")
    
    # 4. Preflight Validate
    val_payload = {
        "modelArtifactId": model_id,
        "evaluationDatasetArtifactId": eval_id,
        "baselineDatasetArtifactId": base_id,
        "targetColumn": "is_fraud",
        "predictionColumn": "pred_prob",
        "protectedAttribute": "is_foreign_ip"
    }
    res_val = make_request("/api/diagnostics/validate", method="POST", body=val_payload)
    print(f"POST /api/diagnostics/validate: Status={res_val['status']}")
    if res_val['data']:
        print(f"  Valid: {res_val['data'].get('valid')}")
        print(f"  Errors: {res_val['data'].get('errors')}")
        print(f"  Warnings: {res_val['data'].get('warnings')}")

    # 5. Create Diagnostic Run
    create_payload = {
        **val_payload,
        "modelName": "Fraud Detection Native XGBoost",
        "evaluationDataset": "evaluation_dataset.csv",
        "baselineDataset": "baseline_dataset.csv",
        "modules": [
            "DATA_QUALITY",
            "LEAKAGE",
            "DRIFT",
            "PERFORMANCE",
            "BIAS",
            "EXPLAINABILITY",
            "ROBUSTNESS",
            "ERROR_FORENSICS"
        ]
    }
    res_create = make_request("/api/diagnostics", method="POST", body=create_payload)
    print(f"POST /api/diagnostics: Status={res_create['status']}")
    if res_create['status'] != 201:
        print(f"  Error creating run: {res_create['data']}")
    run_id = res_create['data'].get('id') if isinstance(res_create['data'], dict) else None
    print(f"  Created run_id={run_id}")
    
    # Now test the other failing endpoints reported by the user for this run_id!
    if run_id:
        # A. Root-cause investigations
        res_inv = make_request(f"/api/diagnostics/{run_id}/investigations")
        print(f"GET /api/diagnostics/{run_id}/investigations: Status={res_inv['status']}, Items={len(res_inv['data']) if isinstance(res_inv['data'], list) else res_inv['data']}")
        
        # B. Remediation candidates
        res_rem = make_request(f"/api/diagnostics/{run_id}/remediations")
        print(f"GET /api/diagnostics/{run_id}/remediations: Status={res_rem['status']}, Items={len(res_rem['data']) if isinstance(res_rem['data'], list) else res_rem['data']}")

        # C. Model Reliability
        res_rel = make_request(f"/api/diagnostics/{run_id}/reliability")
        print(f"GET /api/diagnostics/{run_id}/reliability: Status={res_rel['status']}, Data={type(res_rel['data'])}")

        # D. Model Reliability Recalculate
        res_rel_recalc = make_request(f"/api/diagnostics/{run_id}/reliability/recalculate", method="POST")
        print(f"POST /api/diagnostics/{run_id}/reliability/recalculate: Status={res_rel_recalc['status']}, Data={res_rel_recalc['data']}")

        # E. Incident Decisions
        res_inc = make_request(f"/api/diagnostics/{run_id}/incidents")
        print(f"GET /api/diagnostics/{run_id}/incidents: Status={res_inc['status']}, Items={len(res_inc['data']) if isinstance(res_inc['data'], list) else res_inc['data']}")

        # F. Incident Recalculate
        res_inc_recalc = make_request(f"/api/diagnostics/{run_id}/incidents/recalculate", method="POST")
        print(f"POST /api/diagnostics/{run_id}/incidents/recalculate: Status={res_inc_recalc['status']}, Data={res_inc_recalc['data']}")

        # G. Experiments
        res_exp = make_request(f"/api/diagnostics/{run_id}/experiments")
        print(f"GET /api/diagnostics/{run_id}/experiments: Status={res_exp['status']}, Items={len(res_exp['data']) if isinstance(res_exp['data'], list) else res_exp['data']}")

        # H. Temporal Intelligence
        res_temp = make_request(f"/api/diagnostics/{run_id}/temporal/history")
        print(f"GET /api/diagnostics/{run_id}/temporal/history: Status={res_temp['status']}, Data={type(res_temp['data'])}")

        res_temp_recalc = make_request(f"/api/diagnostics/{run_id}/temporal/recalculate", method="POST")
        print(f"POST /api/diagnostics/{run_id}/temporal/recalculate: Status={res_temp_recalc['status']}, Data={res_temp_recalc['data']}")

        # I. Continuous Monitoring
        res_mon = make_request(f"/api/diagnostics/{run_id}/monitoring/health")
        print(f"GET /api/diagnostics/{run_id}/monitoring/health: Status={res_mon['status']}, Data={type(res_mon['data'])}")

        res_mon_recalc = make_request(f"/api/diagnostics/{run_id}/monitoring/recalculate", method="POST")
        print(f"POST /api/diagnostics/{run_id}/monitoring/recalculate: Status={res_mon_recalc['status']}, Data={res_mon_recalc['data']}")

if __name__ == "__main__":
    run_tests()
