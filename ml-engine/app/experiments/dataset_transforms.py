import numpy as np
import pandas as pd
from typing import Dict, Any, Optional, Tuple

def apply_feature_ablation(
    df: pd.DataFrame,
    feature: str,
    target_column: Optional[str] = None,
    prediction_column: Optional[str] = None,
    strategy: str = "zero" # "drop", "zero", "median"
) -> Tuple[pd.DataFrame, Dict[str, Any]]:
    """
    Applies feature ablation to the dataset.
    If strategy is "drop", removes the column completely.
    If strategy is "zero", zeroes out the feature.
    If strategy is "median", sets all feature values to its median.
    """
    df_transformed = df.copy()
    if feature not in df_transformed.columns:
        raise ValueError(f"Feature '{feature}' not found in dataset columns: {list(df_transformed.columns)}")
    
    original_shape = df_transformed.shape
    baseline_stats = {
        "original_feature_count": int(df.shape[1]),
        "feature_name": feature,
        "ablation_strategy": strategy
    }

    if strategy == "drop":
        df_transformed = df_transformed.drop(columns=[feature])
        baseline_stats["candidate_feature_count"] = int(df_transformed.shape[1])
    elif strategy == "median":
        if pd.api.types.is_numeric_dtype(df_transformed[feature]):
            med_val = float(df_transformed[feature].median())
            df_transformed[feature] = med_val
            baseline_stats["imputed_value"] = med_val
        else:
            mode_val = str(df_transformed[feature].mode().iloc[0]) if not df_transformed[feature].mode().empty else ""
            df_transformed[feature] = mode_val
            baseline_stats["imputed_value"] = mode_val
        baseline_stats["candidate_feature_count"] = int(df_transformed.shape[1])
    else: # "zero"
        if pd.api.types.is_numeric_dtype(df_transformed[feature]):
            df_transformed[feature] = 0.0
            baseline_stats["imputed_value"] = 0.0
        else:
            df_transformed[feature] = "MISSING"
            baseline_stats["imputed_value"] = "MISSING"
        baseline_stats["candidate_feature_count"] = int(df_transformed.shape[1])

    return df_transformed, baseline_stats


def apply_feature_transformation(
    df: pd.DataFrame,
    feature: str,
    transformation_type: str, # "CLIP", "WINSORIZE", "MISSING_REPLACE", "STANDARDIZE"
    lower_quantile: float = 0.01,
    upper_quantile: float = 0.99,
    replace_strategy: str = "median"
) -> Tuple[pd.DataFrame, Dict[str, Any]]:
    """
    Applies a deterministic transformation to a specific feature.
    """
    df_transformed = df.copy()
    if feature not in df_transformed.columns:
        raise ValueError(f"Feature '{feature}' not found in dataset.")

    series = df_transformed[feature]
    provenance = {
        "feature": feature,
        "transformation": transformation_type,
        "parameters": {}
    }

    if transformation_type in ["CLIP", "WINSORIZE"]:
        if not pd.api.types.is_numeric_dtype(series):
            raise ValueError(f"Transformation {transformation_type} requires a numeric feature. Got {series.dtype}")
        
        lower_bound = float(series.quantile(lower_quantile))
        upper_bound = float(series.quantile(upper_quantile))
        
        df_transformed[feature] = series.clip(lower=lower_bound, upper=upper_bound)
        provenance["parameters"] = {
            "lowerQuantile": lower_quantile,
            "upperQuantile": upper_quantile,
            "lowerBound": lower_bound,
            "upperBound": upper_bound,
            "clippedCount": int((series < lower_bound).sum() + (series > upper_bound).sum())
        }

    elif transformation_type == "MISSING_REPLACE":
        missing_count = int(series.isna().sum())
        if pd.api.types.is_numeric_dtype(series):
            if replace_strategy == "mean":
                fill_val = float(series.mean()) if not series.empty else 0.0
            elif replace_strategy == "zero":
                fill_val = 0.0
            else:
                fill_val = float(series.median()) if not series.empty else 0.0
            df_transformed[feature] = series.fillna(fill_val)
        else:
            fill_val = str(series.mode().iloc[0]) if not series.mode().empty else "UNKNOWN"
            df_transformed[feature] = series.fillna(fill_val)
        
        provenance["parameters"] = {
            "replaceStrategy": replace_strategy,
            "fillValue": fill_val,
            "replacedMissingCount": missing_count
        }

    elif transformation_type == "STANDARDIZE":
        if not pd.api.types.is_numeric_dtype(series):
            raise ValueError("STANDARDIZE requires a numeric feature.")
        mean_val = float(series.mean())
        std_val = float(series.std())
        if std_val == 0.0:
            std_val = 1.0
        df_transformed[feature] = (series - mean_val) / std_val
        provenance["parameters"] = {
            "mean": mean_val,
            "std": std_val
        }
    else:
        raise ValueError(f"Unsupported transformation type: {transformation_type}")

    return df_transformed, provenance


def inject_missingness_stress(
    df: pd.DataFrame,
    feature: str,
    rate: float = 0.10,
    seed: int = 42
) -> Tuple[pd.DataFrame, Dict[str, Any]]:
    """
    Deterministically injects missing values (NaNs) into a feature at a given rate.
    """
    df_transformed = df.copy()
    if feature not in df_transformed.columns:
        raise ValueError(f"Feature '{feature}' not found in dataset.")

    n_rows = len(df_transformed)
    if n_rows == 0:
        return df_transformed, {"feature": feature, "rate": rate, "injected_count": 0}

    rng = np.random.default_rng(seed)
    mask = rng.random(n_rows) < rate
    
    # Ensure at least 1 missing if rate > 0 and n_rows > 0
    if rate > 0 and not mask.any() and n_rows > 0:
        mask[0] = True

    df_transformed.loc[mask, feature] = np.nan
    injected_count = int(mask.sum())

    provenance = {
        "feature": feature,
        "missingnessRate": rate,
        "deterministicSeed": seed,
        "injectedMissingCount": injected_count,
        "totalRows": n_rows
    }
    return df_transformed, provenance
