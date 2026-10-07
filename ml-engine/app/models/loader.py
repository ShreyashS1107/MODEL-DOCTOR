import os
import logging
from pathlib import Path
from typing import Any, Dict, List, Optional
import numpy as np
import pandas as pd
import xgboost as xgb
import joblib

from app.models.adapter import (
    ModelAdapter,
    XGBoostModelAdapter,
    SklearnTreeModelAdapter,
    GenericCallableModelAdapter,
)

logger = logging.getLogger(__name__)


class ModelArtifactNotFoundError(Exception):
    """Raised when a requested model artifact cannot be found at any configured path."""
    pass


class ModelLoader:
    """
    Manages loading, serialization, and adapter instantiation for trained ML model artifacts.
    Supports XGBoost (JSON, UBJSON, binary) and Scikit-Learn (Joblib, Pickle).
    """

    BENCHMARK_MODELS = {
        "fraud_classifier_v17",
        "credit_default_xgb",
        "credit_default_v2",
        "default_benchmark_model",
        "churn_predictor_nn",
    }

    @classmethod
    def get_models_dir(cls) -> Path:
        base_dir = Path(__file__).resolve().parent.parent.parent
        models_dir = base_dir / "models"
        models_dir.mkdir(parents=True, exist_ok=True)
        return models_dir

    @classmethod
    def load_model_adapter(
        cls,
        model_name: str,
        framework: Optional[str] = "xgboost",
        task_type: str = "binary_classification",
        storage_uri: Optional[str] = None,
        evaluation_data: Optional[pd.DataFrame] = None,
        target_column: Optional[str] = None,
        execution_mode: str = "REAL",
    ) -> ModelAdapter:
        """
        Loads a ModelAdapter for the specified model artifact.
        If execution_mode is REAL, strictly loads from disk / storage_uri.
        Benchmark model generation is only permitted in BENCHMARK / TEST execution modes.
        """
        clean_name = (model_name or "default_model").strip()
        models_dir = cls.get_models_dir()
        framework_lower = (framework or "xgboost").lower()
        clean_mode = (execution_mode or "REAL").upper()

        # 1. Check explicit storageUri if provided
        candidate_paths: List[Path] = []
        if storage_uri and storage_uri.strip():
            clean_uri = storage_uri.strip()
            if clean_uri.startswith("file://"):
                clean_uri = clean_uri[7:]
                if clean_uri.startswith("/") and len(clean_uri) > 2 and clean_uri[2] == ":":
                    clean_uri = clean_uri[1:]

            p = Path(clean_uri)
            candidate_paths.append(p)
            if not p.is_absolute():
                # Check relative to workspace/repo root (parent of ml-engine)
                repo_root = Path(__file__).resolve().parent.parent.parent.parent
                candidate_paths.append(repo_root / p)
                candidate_paths.append(models_dir / p.name)

        # 2. Check standard model filenames in models/ directory
        candidate_paths.extend([
            models_dir / f"{clean_name}.json",
            models_dir / f"{clean_name}.xgb",
            models_dir / f"{clean_name}.joblib",
            models_dir / f"{clean_name}.pkl",
            models_dir / f"{clean_name}.bin",
        ])

        for path in candidate_paths:
            if path.exists() and path.is_file():
                logger.info("Found model artifact at %s", path)
                return cls._load_from_path(path, clean_name, framework_lower, task_type)

        # 3. If benchmark or test execution mode, generate benchmark model if requested
        if clean_mode in ["BENCHMARK", "TEST"] and (clean_name.lower() in cls.BENCHMARK_MODELS or "fraud" in clean_name.lower() or "credit" in clean_name.lower()):
            logger.info("Benchmark model '%s' requested in mode %s. Initializing benchmark XGBoost artifact...", clean_name, clean_mode)
            return cls._train_and_save_benchmark_model(clean_name, models_dir, evaluation_data, target_column)

        raise ModelArtifactNotFoundError(
            f"No model artifact found for model '{clean_name}' (storage_uri='{storage_uri}', execution_mode='{clean_mode}'). "
            f"Checked paths: {[str(p) for p in candidate_paths]}"
        )

    @classmethod
    def _load_from_path(
        cls,
        path: Path,
        model_name: str,
        framework: str,
        task_type: str,
    ) -> ModelAdapter:
        suffix = path.suffix.lower()
        if suffix in [".json", ".xgb", ".bin"] or framework == "xgboost":
            booster = xgb.Booster()
            booster.load_model(str(path))
            feature_names = booster.feature_names
            return XGBoostModelAdapter(
                name=model_name,
                raw_model=booster,
                task_type=task_type,
                feature_names=feature_names,
            )
        elif suffix in [".joblib", ".pkl"]:
            obj = joblib.load(str(path))
            if hasattr(obj, "get_booster"):
                return XGBoostModelAdapter(name=model_name, raw_model=obj, task_type=task_type)
            elif any(t in type(obj).__name__.lower() for t in ["tree", "forest", "gradientboosting"]):
                return SklearnTreeModelAdapter(name=model_name, raw_model=obj, task_type=task_type)
            else:
                return GenericCallableModelAdapter(name=model_name, raw_model=obj, framework=framework, task_type=task_type)
        else:
            raise ValueError(f"Unsupported model artifact format: {path.suffix}")

    @classmethod
    def _train_and_save_benchmark_model(
        cls,
        model_name: str,
        models_dir: Path,
        evaluation_data: Optional[pd.DataFrame],
        target_column: Optional[str],
    ) -> XGBoostModelAdapter:
        from app.data.loader import generate_synthetic_fraud_dataset

        if evaluation_data is not None and target_column and target_column in evaluation_data.columns:
            train_df = evaluation_data.copy()
            target_col = target_column
        else:
            train_df = generate_synthetic_fraud_dataset(n_samples=2500, is_baseline=True, random_seed=42)
            target_col = "is_fraud"

        # Exclude non-feature columns
        exclude_cols = {target_col, "pred_prob", "prediction", "system_environment"}
        feature_cols = [c for c in train_df.columns if c not in exclude_cols and pd.api.types.is_numeric_dtype(train_df[c])]
        
        X = train_df[feature_cols].fillna(0)
        y = train_df[target_col].astype(int)

        clf = xgb.XGBClassifier(
            n_estimators=30,
            max_depth=4,
            learning_rate=0.1,
            random_state=42,
            eval_metric="logloss",
        )
        clf.fit(X, y)

        # Save model artifact to disk
        save_path = models_dir / f"{model_name}.json"
        clf.save_model(str(save_path))
        logger.info("Persisted benchmark XGBoost model artifact to: %s", save_path)

        return XGBoostModelAdapter(
            name=model_name,
            raw_model=clf,
            task_type="binary_classification",
            feature_names=feature_cols,
        )
