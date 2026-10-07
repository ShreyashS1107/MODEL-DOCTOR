from abc import ABC, abstractmethod
from typing import Any, Dict, Optional
import pandas as pd
from app.schemas.diagnostic_models import DiagnosticCategory, DiagnosticReport


class BaseDiagnosticEngine(ABC):
    """
    Abstract Base Class for all Model Doctor diagnostic engines.
    Every specialized forensic engine (data quality, leakage, drift, fairness, etc.)
    must inherit from this class and implement run_diagnostic.
    """

    @property
    @abstractmethod
    def category(self) -> DiagnosticCategory:
        """The diagnostic category domain this engine specializes in."""
        pass

    @property
    @abstractmethod
    def name(self) -> str:
        """Human-readable identifier for the engine."""
        pass

    @property
    @abstractmethod
    def description(self) -> str:
        """Technical description of diagnostic checks performed."""
        pass

    @abstractmethod
    async def run_diagnostic(
        self,
        current_data: pd.DataFrame,
        target_column: str,
        baseline_data: Optional[pd.DataFrame] = None,
        model_artifact: Optional[Any] = None,
        config: Optional[Dict[str, Any]] = None,
    ) -> DiagnosticReport:
        """
        Executes forensic analysis and produces a structured DiagnosticReport.
        
        Args:
            current_data: Primary evaluation or test dataset
            target_column: Name of the ground-truth label
            baseline_data: Optional reference/training baseline dataset
            model_artifact: Optional trained model instance or wrapper
            config: Engine-specific hyper-parameters and thresholds
            
        Returns:
            DiagnosticReport containing health score, metrics, and forensic evidence
        """
        pass
