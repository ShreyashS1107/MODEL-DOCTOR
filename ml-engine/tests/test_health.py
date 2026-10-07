import pytest
from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)


def test_root_health_endpoint():
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "healthy"
    assert data["service"] == "model-doctor-ml-engine"
    assert "engines_registered" in data
    assert "leakage" in data["engines_registered"]
    assert "data_quality" in data["engines_registered"]
    assert "drift" in data["engines_registered"]
    assert "performance" in data["engines_registered"]
    assert "fairness" in data["engines_registered"]
    assert "robustness" in data["engines_registered"]
    assert "explainability" in data["engines_registered"]


def test_api_v1_health_endpoint():
    response = client.get("/api/v1/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "healthy"


def test_engines_list_endpoint():
    response = client.get("/api/v1/diagnostics/engines")
    assert response.status_code == 200
    data = response.json()
    assert len(data) >= 7
    categories = [engine["category"] for engine in data]
    assert "leakage" in categories
    assert "drift" in categories
