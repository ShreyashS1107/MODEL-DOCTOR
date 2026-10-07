from app.diagnostics.base import BaseDiagnosticEngine
from app.diagnostics.data_quality.engine import DataQualityEngine
from app.diagnostics.leakage.engine import DataLeakageEngine
from app.diagnostics.drift.engine import DataDriftEngine
from app.diagnostics.performance.engine import PerformanceEngine
from app.diagnostics.fairness.engine import FairnessEngine
from app.diagnostics.robustness.engine import RobustnessEngine
from app.diagnostics.explainability.engine import ExplainabilityEngine

AVAILABLE_ENGINES = {
    "data_quality": DataQualityEngine,
    "leakage": DataLeakageEngine,
    "drift": DataDriftEngine,
    "performance": PerformanceEngine,
    "fairness": FairnessEngine,
    "robustness": RobustnessEngine,
    "explainability": ExplainabilityEngine,
}

__all__ = [
    "BaseDiagnosticEngine",
    "DataQualityEngine",
    "DataLeakageEngine",
    "DataDriftEngine",
    "PerformanceEngine",
    "FairnessEngine",
    "RobustnessEngine",
    "ExplainabilityEngine",
    "AVAILABLE_ENGINES",
]
