import abc
import logging
from typing import Any, List, Optional, Tuple, Union
import numpy as np
import pandas as pd

logger = logging.getLogger(__name__)


class ModelAdapter(abc.ABC):
    """
    Abstract model wrapper standardizing prediction interfaces,
    feature extraction, and TreeSHAP compatibility across ML frameworks.
    """

    def __init__(
        self,
        name: str,
        framework: str,
        task_type: str = "binary_classification",
        raw_model: Optional[Any] = None,
        feature_names: Optional[List[str]] = None,
    ):
        self.name = name
        self.framework = framework.lower()
        self.task_type = task_type.lower()
        self.raw_model = raw_model
        self.feature_names = feature_names

    @abc.abstractmethod
    def predict(self, X: pd.DataFrame) -> np.ndarray:
        """Generates hard class predictions or continuous scores."""
        pass

    @abc.abstractmethod
    def predict_proba(self, X: pd.DataFrame) -> np.ndarray:
        """
        Generates probability predictions.
        For binary classification, returns array of shape (N,) or (N, 2)
        representing the probability of the positive class.
        """
        pass

    @abc.abstractmethod
    def supports_tree_shap(self) -> bool:
        """Returns True if the underlying model exposes a tree structure for TreeExplainer."""
        pass

    @abc.abstractmethod
    def get_tree_model(self) -> Any:
        """Returns the underlying tree booster / estimator object for TreeExplainer."""
        pass

    def supports_gradients(self) -> bool:
        """Returns True if the model exposes analytical gradients w.r.t input features for FGSM."""
        return False

    def gradient(self, X: pd.DataFrame, y: np.ndarray) -> Optional[np.ndarray]:
        """Calculates loss gradient w.r.t inputs X for genuine differentiable models."""
        return None

    def supports_missingness(self) -> bool:
        """Returns True if the model natively tolerates NaN / missing feature values at inference."""
        return False


class XGBoostModelAdapter(ModelAdapter):
    """
    Adapter for XGBoost Booster and XGBClassifier models.
    Supports TreeExplainer natively.
    """

    def __init__(
        self,
        name: str,
        raw_model: Any,
        task_type: str = "binary_classification",
        feature_names: Optional[List[str]] = None,
    ):
        super().__init__(
            name=name,
            framework="xgboost",
            task_type=task_type,
            raw_model=raw_model,
            feature_names=feature_names,
        )

    def predict(self, X: pd.DataFrame) -> np.ndarray:
        probs = self.predict_proba(X)
        if probs.ndim == 2:
            return (probs[:, 1] >= 0.5).astype(int)
        return (probs >= 0.5).astype(int)

    def predict_proba(self, X: pd.DataFrame) -> np.ndarray:
        import xgboost as xgb

        if hasattr(self.raw_model, "predict_proba"):
            # XGBClassifier
            return self.raw_model.predict_proba(X)
        elif isinstance(self.raw_model, xgb.Booster):
            expected_features = self.feature_names or self.raw_model.feature_names
            if expected_features and isinstance(X, pd.DataFrame):
                # Filter down or reorder to expected features if all exist in X
                if all(f in X.columns for f in expected_features):
                    X_input = X[expected_features]
                else:
                    # Filter only the subset of columns present
                    common_cols = [c for c in X.columns if c in expected_features]
                    X_input = X[common_cols] if common_cols else X
            else:
                X_input = X
            dmat = xgb.DMatrix(X_input)
            probs = self.raw_model.predict(dmat)
            if probs.ndim == 1:
                # Binary classification margin transformed to probability
                return np.column_stack([1.0 - probs, probs])
            return probs
        else:
            raise ValueError(f"Unsupported XGBoost model object type: {type(self.raw_model)}")

    def supports_tree_shap(self) -> bool:
        return True

    def get_tree_model(self) -> Any:
        return self.raw_model

    def supports_missingness(self) -> bool:
        # XGBoost natively supports missing values at inference
        return True


class SklearnTreeModelAdapter(ModelAdapter):
    """
    Adapter for Scikit-Learn tree ensembles (RandomForestClassifier, GradientBoostingClassifier, etc.).
    Supports TreeExplainer.
    """

    def __init__(
        self,
        name: str,
        raw_model: Any,
        task_type: str = "binary_classification",
        feature_names: Optional[List[str]] = None,
    ):
        super().__init__(
            name=name,
            framework="sklearn",
            task_type=task_type,
            raw_model=raw_model,
            feature_names=feature_names,
        )

    def predict(self, X: pd.DataFrame) -> np.ndarray:
        return self.raw_model.predict(X)

    def predict_proba(self, X: pd.DataFrame) -> np.ndarray:
        if hasattr(self.raw_model, "predict_proba"):
            return self.raw_model.predict_proba(X)
        elif hasattr(self.raw_model, "decision_function"):
            df_vals = self.raw_model.decision_function(X)
            probs = 1.0 / (1.0 + np.exp(-df_vals))
            return np.column_stack([1.0 - probs, probs])
        else:
            preds = self.raw_model.predict(X).astype(float)
            return np.column_stack([1.0 - preds, preds])

    def supports_tree_shap(self) -> bool:
        # Check if model is a recognized tree ensemble
        type_name = type(self.raw_model).__name__.lower()
        return any(t in type_name for t in ["tree", "forest", "gradientboosting", "extratrees", "histgradientboosting"])

    def get_tree_model(self) -> Any:
        return self.raw_model

    def supports_missingness(self) -> bool:
        type_name = type(self.raw_model).__name__.lower()
        return "histgradientboosting" in type_name


class GenericCallableModelAdapter(ModelAdapter):
    """
    Model-agnostic wrapper for any black-box model callable.
    Used for Permutation Importance fallback when TreeSHAP is unavailable.
    """

    def __init__(
        self,
        name: str,
        raw_model: Any,
        framework: str = "generic",
        task_type: str = "binary_classification",
        feature_names: Optional[List[str]] = None,
    ):
        super().__init__(
            name=name,
            framework=framework,
            task_type=task_type,
            raw_model=raw_model,
            feature_names=feature_names,
        )

    def predict(self, X: pd.DataFrame) -> np.ndarray:
        if hasattr(self.raw_model, "predict"):
            return self.raw_model.predict(X)
        elif callable(self.raw_model):
            return self.raw_model(X)
        raise ValueError("Generic model has no predict method or callable implementation.")

    def predict_proba(self, X: pd.DataFrame) -> np.ndarray:
        if hasattr(self.raw_model, "predict_proba"):
            return self.raw_model.predict_proba(X)
        elif hasattr(self.raw_model, "predict"):
            preds = self.raw_model.predict(X).astype(float)
            if preds.ndim == 1:
                return np.column_stack([1.0 - preds, preds])
            return preds
        elif callable(self.raw_model):
            preds = np.asarray(self.raw_model(X), dtype=float)
            if preds.ndim == 1:
                return np.column_stack([1.0 - preds, preds])
            return preds
        raise ValueError("Generic model cannot compute probability predictions.")

    def supports_tree_shap(self) -> bool:
        return False

    def get_tree_model(self) -> Any:
        return None
