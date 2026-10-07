import os
import logging
from pathlib import Path
from typing import Optional
import numpy as np
import pandas as pd

logger = logging.getLogger(__name__)


def generate_synthetic_fraud_dataset(
    n_samples: int = 5000,
    is_baseline: bool = False,
    random_seed: int = 42,
) -> pd.DataFrame:
    """
    Generates a realistic deterministic tabular fraud detection dataset
    specifically constructed with real statistical properties:
    - Missing values in device_trust_score and user_income
    - Constant feature (system_environment)
    - Numeric outliers in transaction_amount (IQR method will detect)
    - Target leakage in transaction_id_hash and post_decision_risk_score
    - Categorical & protected attributes (is_foreign_ip, card_network)
    - Injected duplicate rows
    """
    rng = np.random.RandomState(random_seed if not is_baseline else random_seed + 100)

    # 1. Ground Truth Target (binary fraud label, ~6% fraud base rate)
    is_fraud = rng.binomial(1, 0.06 if not is_baseline else 0.05, size=n_samples)

    # 2. Leaked Feature 1: Post-decision risk score (strongly correlated with target)
    # Adds very high mutual information (> 0.85)
    post_decision_risk_score = is_fraud * rng.uniform(0.75, 0.99, size=n_samples) + (1 - is_fraud) * rng.uniform(0.01, 0.25, size=n_samples)

    # 3. Leaked Feature 2: Transaction ID hash proxy (contains target signal in hash suffix)
    # High association with target
    leakage_proxy_signal = (is_fraud * 0.92 + rng.normal(0, 0.15, size=n_samples)).clip(0, 1)

    # 4. Standard features
    user_age = rng.randint(18, 80, size=n_samples).astype(float)
    user_income = rng.normal(65000, 25000, size=n_samples).clip(15000, 300000)
    
    # Transaction amount with heavy-tailed distribution and numeric outliers
    base_amount = rng.exponential(scale=120, size=n_samples) + 5
    # Inject 40 extreme outliers
    outlier_indices = rng.choice(n_samples, size=40, replace=False)
    base_amount[outlier_indices] = rng.uniform(8000, 25000, size=40)
    transaction_amount = np.round(base_amount, 2)

    # Velocity and device features
    user_velocity_6h = rng.poisson(lam=2.5 if not is_baseline else 1.8, size=n_samples).astype(float)
    device_trust_score = rng.beta(a=8, b=2, size=n_samples) * 100

    # Categorical & Protected features
    card_networks = ["VISA", "MASTERCARD", "AMEX", "DISCOVER"]
    card_network = rng.choice(card_networks, p=[0.50, 0.35, 0.10, 0.05], size=n_samples)
    is_foreign_ip = rng.binomial(1, 0.12, size=n_samples)

    # 5. Prediction Probability (for binary classification evaluation)
    logits = -2.2 + is_fraud * 2.8 + (user_velocity_6h - 2.0) * 0.35 + rng.normal(0, 0.4, size=n_samples)
    pred_prob = np.round(np.clip(1.0 / (1.0 + np.exp(-logits)), 0.001, 0.999), 4)

    # 6. Constant Feature (Zero variance)
    system_environment = np.array(["PRODUCTION_SECURE_V2"] * n_samples)

    # Construct DataFrame
    df = pd.DataFrame({
        "transaction_amount": transaction_amount,
        "user_age": user_age,
        "user_income": user_income,
        "user_velocity_6h": user_velocity_6h,
        "device_trust_score": device_trust_score,
        "card_network": card_network,
        "is_foreign_ip": is_foreign_ip,
        "system_environment": system_environment,
        "transaction_id_hash": leakage_proxy_signal,
        "post_decision_risk_score": post_decision_risk_score,
        "pred_prob": pred_prob,
        "is_fraud": is_fraud,
    })

    # 6. Inject intentional missing values:
    # 4.2% missing in device_trust_score
    missing_trust_idx = rng.choice(n_samples, size=int(n_samples * 0.042), replace=False)
    df.loc[missing_trust_idx, "device_trust_score"] = np.nan

    # 1.5% missing in user_income
    missing_income_idx = rng.choice(n_samples, size=int(n_samples * 0.015), replace=False)
    df.loc[missing_income_idx, "user_income"] = np.nan

    # 7. Inject intentional duplicate rows (23 duplicates)
    dup_rows = df.iloc[:23].copy()
    df = pd.concat([df, dup_rows], ignore_index=True)

    return df


class DatasetLoader:
    """
    Abstracts dataset retrieval and ingestion into Pandas DataFrames.
    Supports local paths, CSV, Parquet, JSON, and built-in benchmark datasets.
    In REAL mode, missing files raise FileNotFoundError rather than silently generating synthetic data.
    """

    @staticmethod
    def load(dataset_reference: str, execution_mode: str = "REAL") -> pd.DataFrame:
        if not dataset_reference or not dataset_reference.strip():
            raise ValueError("Dataset reference cannot be empty or whitespace.")

        clean_ref = dataset_reference.strip()
        # Strip file:// prefix if present
        if clean_ref.startswith("file://"):
            clean_ref = clean_ref[7:]
            if clean_ref.startswith("/") and len(clean_ref) > 2 and clean_ref[2] == ":":
                # Windows path file:///C:/...
                clean_ref = clean_ref[1:]

        # 1. Check if direct file path exists on filesystem
        path = Path(clean_ref)
        if path.exists() and path.is_file():
            logger.info("Loading dataset directly from filesystem path: %s", path)
            ext = path.suffix.lower()
            if ext in [".csv", ".txt"]:
                return pd.read_csv(path)
            elif ext in [".parquet", ".pq"]:
                return pd.read_parquet(path)
            elif ext in [".json"]:
                return pd.read_json(path)
            else:
                try:
                    return pd.read_csv(path)
                except Exception:
                    return pd.read_parquet(path)

        # 2. Check in standard datasets directory
        datasets_dir = Path(__file__).resolve().parent.parent.parent / "data" / "samples"
        candidate_file = datasets_dir / Path(clean_ref).name
        if candidate_file.exists() and candidate_file.is_file():
            logger.info("Loading dataset from local sample catalog: %s", candidate_file)
            return pd.read_csv(candidate_file)

        # 3. Explicit synthetic URI scheme (for testing and benchmarks)
        if clean_ref.startswith("synthetic://") or "synthetic" in clean_ref.lower():
            lower_ref = clean_ref.lower()
            if "baseline" in lower_ref or "train" in lower_ref:
                logger.info("Generating deterministic baseline benchmark dataset for reference: %s", clean_ref)
                return generate_synthetic_fraud_dataset(n_samples=5000, is_baseline=True, random_seed=42)
            else:
                logger.info("Generating deterministic evaluation benchmark dataset for reference: %s", clean_ref)
                return generate_synthetic_fraud_dataset(n_samples=5000, is_baseline=False, random_seed=42)

        # 4. Handle benchmark / test execution modes explicitly
        clean_mode = (execution_mode or "REAL").upper()
        if clean_mode in ["BENCHMARK", "TEST"]:
            lower_ref = clean_ref.lower()
            if "baseline" in lower_ref or "train" in lower_ref:
                logger.info("Generating deterministic baseline benchmark dataset for reference in %s mode: %s", clean_mode, clean_ref)
                return generate_synthetic_fraud_dataset(n_samples=5000, is_baseline=True, random_seed=42)
            else:
                logger.info("Generating deterministic evaluation benchmark dataset for reference in %s mode: %s", clean_mode, clean_ref)
                return generate_synthetic_fraud_dataset(n_samples=5000, is_baseline=False, random_seed=42)

        # 5. Production / REAL mode must fail clearly when a required artifact is unavailable
        raise FileNotFoundError(f"Dataset artifact not found on filesystem at '{clean_ref}' (execution mode: {clean_mode}).")
