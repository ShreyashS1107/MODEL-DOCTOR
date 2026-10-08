import pytest
from fastapi.testclient import TestClient
from app.main import app
from app.temporal.trends import analyze_metric_trend, mann_kendall_test
from app.temporal.change_points import detect_change_points
from app.temporal.associations import compute_temporal_lag_correlation

client = TestClient(app)


def test_mann_kendall_monotonic_increasing():
    vals = [0.10, 0.20, 0.30, 0.40, 0.50, 0.60]
    res = mann_kendall_test(vals)
    assert res["is_significant"] is True
    assert res["trend_direction"] == "INCREASING"
    assert res["tau"] > 0.9


def test_mann_kendall_monotonic_decreasing():
    vals = [0.90, 0.80, 0.70, 0.60, 0.50, 0.40]
    res = mann_kendall_test(vals)
    assert res["is_significant"] is True
    assert res["trend_direction"] == "DECREASING"
    assert res["tau"] < -0.9


def test_mann_kendall_insufficient_samples():
    vals = [0.5, 0.6, 0.7]
    res = mann_kendall_test(vals)
    assert res["trend_direction"] == "INSUFFICIENT_DATA"
    assert res["is_significant"] is False


def test_analyze_metric_trend_higher_is_better():
    # Degrading F1
    f1_vals = [0.88, 0.86, 0.84, 0.81, 0.78, 0.75]
    trend = analyze_metric_trend(f1_vals, higher_is_better=True)
    assert trend["direction"] == "DEGRADING"
    assert trend["slope"] < 0
    assert trend["latestValue"] == 0.75
    assert trend["previousValue"] == 0.78
    assert trend["observationCount"] == 6


def test_analyze_metric_trend_lower_is_better():
    # Escalating PSI
    psi_vals = [0.04, 0.08, 0.14, 0.22, 0.31]
    trend = analyze_metric_trend(psi_vals, higher_is_better=False)
    assert trend["direction"] == "DEGRADING"
    assert trend["slope"] > 0


def test_detect_change_points_step_shift():
    # Sudden jump in error rate at index 4
    vals = [0.02, 0.03, 0.02, 0.03, 0.18, 0.20, 0.19, 0.21]
    run_ids = [f"run_{i}" for i in range(len(vals))]
    cps = detect_change_points(vals, run_ids=run_ids, min_split_size=2, threshold_shift=0.05)
    assert len(cps) == 1
    cp = cps[0]
    assert cp["changeIndex"] == 4
    assert cp["changeRunId"] == "run_4"
    assert cp["beforeMean"] < 0.05
    assert cp["afterMean"] > 0.15
    assert cp["absoluteShift"] > 0.10


def test_compute_temporal_lag_correlation():
    # Signal A rises 1 step before Signal B rises
    sig_a = [0.1, 0.2, 0.5, 0.8, 0.9, 0.9]
    sig_b = [0.05, 0.1, 0.2, 0.5, 0.8, 0.9]
    assocs = compute_temporal_lag_correlation(sig_a, sig_b, signal_a_name="psi", signal_b_name="error_rate")
    assert len(assocs) >= 1
    # Check that lag 1 or lag 0 correlation is strong
    corrs = [a["correlation"] for a in assocs]
    assert max(corrs) > 0.70


def test_api_temporal_endpoints():
    # Trends endpoint
    resp = client.post("/api/v1/temporal/trends", json={
        "values": [0.85, 0.83, 0.80, 0.78, 0.74],
        "higher_is_better": True
    })
    assert resp.status_code == 200
    data = resp.json()
    assert data["direction"] == "DEGRADING"

    # Change points endpoint
    resp = client.post("/api/v1/temporal/change-points", json={
        "values": [0.02, 0.02, 0.03, 0.15, 0.16, 0.17],
        "run_ids": ["r1", "r2", "r3", "r4", "r5", "r6"]
    })
    assert resp.status_code == 200
    cps = resp.json()
    assert len(cps) >= 1

    # Associations endpoint
    resp = client.post("/api/v1/temporal/associations", json={
        "series_a": [1.0, 2.0, 3.0, 4.0, 5.0, 6.0],
        "series_b": [2.0, 4.0, 6.0, 8.0, 10.0, 12.0],
        "signal_a_name": "drift",
        "signal_b_name": "error"
    })
    assert resp.status_code == 200
    assocs = resp.json()
    assert len(assocs) >= 1
