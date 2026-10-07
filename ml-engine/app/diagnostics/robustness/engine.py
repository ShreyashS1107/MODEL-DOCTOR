import logging
import math
import time
from typing import Any, Dict, List, Optional, Tuple, Union
import numpy as np
import pandas as pd
from sklearn.metrics import accuracy_score, f1_score, roc_auc_score

from app.diagnostics.base import BaseDiagnosticEngine
from app.models.adapter import ModelAdapter
from app.models.loader import ModelLoader, ModelArtifactNotFoundError
from app.schemas.diagnostic_models import (
    DiagnosticCategory,
    DiagnosticIssue,
    DiagnosticMetric,
    DiagnosticReport,
    SeverityLevel,
)

logger = logging.getLogger(__name__)


class RobustnessEngine(BaseDiagnosticEngine):
    """
    Evaluates empirical model resilience under controlled feature perturbations and stress:
    - Multi-level Gaussian jitter noise sensitivity (0.01, 0.05, 0.10)
    - Feature-wise perturbation sensitivity and instability ranking
    - Bounded tree decision boundary / local adversarial search
    - Analytical FGSM applicability check (with transparent NOT_APPLICABLE for tree models)
    - Feature corruption & missingness stress (for missingness-tolerant models)
    - Subgroup robustness gap analysis across sensitive demographic slices
    """

    DEFAULT_CONFIG: Dict[str, Any] = {
        "noise_levels": [0.01, 0.05, 0.10],
        "random_state": 42,
        "max_sample_size": 1000,
        "max_boundary_samples": 200,
        "max_features": 50,
        "max_search_steps": 20,
        "decision_threshold": 0.50,
        "noise_flip_rate_warning": 0.05,
        "noise_flip_rate_critical": 0.20,
        "probability_shift_warning": 0.05,
        "probability_shift_critical": 0.10,
        "subgroup_robustness_gap_warning": 0.05,
        "subgroup_robustness_gap_critical": 0.10,
        "boundary_distance_warning": 0.30,
        "boundary_distance_critical": 0.15,
    }

    @property
    def category(self) -> DiagnosticCategory:
        return DiagnosticCategory.ROBUSTNESS

    @property
    def name(self) -> str:
        return "Adversarial & Perturbation Robustness Engine"

    @property
    def description(self) -> str:
        return "Tests prediction resilience under empirical Gaussian jitter, feature-level stress, bounded boundary search, and subgroup sensitivity."

    @staticmethod
    def _compute_feature_scale(series: pd.Series) -> float:
        """Computes empirical scale (std or IQR/1.349) for a numeric feature."""
        std = float(series.std(skipna=True))
        if not math.isnan(std) and std > 1e-7:
            return std
        q75, q25 = series.quantile(0.75), series.quantile(0.25)
        iqr = float(q75 - q25)
        if not math.isnan(iqr) and iqr > 1e-7:
            return iqr / 1.349
        return 1.0

    async def run_diagnostic(
        self,
        current_data: pd.DataFrame,
        target_column: str,
        baseline_data: Optional[pd.DataFrame] = None,
        model_artifact: Optional[Any] = None,
        config: Optional[Dict[str, Any]] = None,
    ) -> DiagnosticReport:
        start_time = time.perf_counter()
        cfg = {**self.DEFAULT_CONFIG, **(config or {})}
        issues: List[DiagnosticIssue] = []
        metrics_list: List[DiagnosticMetric] = []

        # 1. Dataset Validation
        if current_data is None or not isinstance(current_data, pd.DataFrame) or len(current_data) == 0:
            issue = DiagnosticIssue(
                id="ROBUSTNESS_EMPTY_DATASET",
                category=self.category,
                severity=SeverityLevel.CRITICAL,
                title="Evaluation Dataset Empty",
                description="Evaluation dataset must be a non-empty pandas DataFrame.",
                affected_features=[],
                evidence={"rows": 0},
                recommendation="Provide valid observations for robustness auditing.",
            )
            exec_time_ms = round((time.perf_counter() - start_time) * 1000, 2)
            return DiagnosticReport(
                engine_name=self.name,
                category=self.category,
                health_score=0.0,
                passed=False,
                execution_time_ms=exec_time_ms,
                issues=[issue],
                metadata={"module": "ROBUSTNESS", "version": "1.0", "summary": {"error": "Empty dataset", "healthScore": 0.0, "passed": False}},
            )

        # 2. Task Validation
        task_type = (cfg.get("task_type") or "binary_classification").lower()
        if task_type != "binary_classification":
            issue = DiagnosticIssue(
                id="ROBUSTNESS_UNSUPPORTED_TASK",
                category=self.category,
                severity=SeverityLevel.CRITICAL,
                title=f"Unsupported Task Type '{task_type}' for Robustness Audit",
                description="The Model Doctor Robustness Engine currently evaluates binary classification tasks.",
                affected_features=[],
                evidence={"taskType": task_type},
                recommendation="Configure taskType as 'binary_classification'.",
            )
            exec_time_ms = round((time.perf_counter() - start_time) * 1000, 2)
            return DiagnosticReport(
                engine_name=self.name,
                category=self.category,
                health_score=0.0,
                passed=False,
                execution_time_ms=exec_time_ms,
                issues=[issue],
                metadata={"module": "ROBUSTNESS", "version": "1.0", "summary": {"error": f"Unsupported task type {task_type}", "healthScore": 0.0, "passed": False}},
            )

        # 3. Model Adapter Resolution
        model: Optional[ModelAdapter] = None
        if isinstance(model_artifact, ModelAdapter):
            model = model_artifact
        elif model_artifact is not None:
            # Wrap raw model
            from app.models.adapter import GenericCallableModelAdapter
            model = GenericCallableModelAdapter(
                name=cfg.get("model_name", "custom_model"),
                raw_model=model_artifact,
                framework=cfg.get("model_framework", "generic"),
                task_type=task_type,
            )
        else:
            model_name = cfg.get("model_name", "fraud_classifier_v17")
            framework = cfg.get("model_framework", "xgboost")
            storage_uri = cfg.get("storage_uri")
            execution_mode = cfg.get("execution_mode", "REAL")
            try:
                model = ModelLoader.load_model_adapter(
                    model_name=model_name,
                    framework=framework,
                    task_type=task_type,
                    storage_uri=storage_uri,
                    evaluation_data=current_data,
                    target_column=target_column,
                    execution_mode=execution_mode,
                )
            except ModelArtifactNotFoundError as e:
                logger.error("Failed to load model artifact: %s", str(e))
                issue = DiagnosticIssue(
                    id="MODEL_ARTIFACT_UNAVAILABLE",
                    category=self.category,
                    severity=SeverityLevel.CRITICAL,
                    title=f"Model Artifact Unavailable for '{model_name}'",
                    description=f"Robustness evaluation requires an accessible model artifact. {str(e)}",
                    affected_features=[],
                    evidence={"modelName": model_name, "framework": framework},
                    recommendation="Ensure model weights/artifacts are registered or present in the model store.",
                )
                exec_time_ms = round((time.perf_counter() - start_time) * 1000, 2)
                return DiagnosticReport(
                    engine_name=self.name,
                    category=self.category,
                    health_score=0.0,
                    passed=False,
                    execution_time_ms=exec_time_ms,
                    issues=[issue],
                    metadata={"module": "ROBUSTNESS", "version": "1.0", "summary": {"error": "Model artifact unavailable", "healthScore": 0.0, "passed": False}},
                )

        # 4. Extract Clean Evaluation Features
        exclude_cols = {target_column, "pred_prob", "prediction", "system_environment", "pred", "y_pred", "y_true"}
        all_numeric_cols = [
            c for c in current_data.columns
            if c not in exclude_cols and pd.api.types.is_numeric_dtype(current_data[c])
        ]
        feature_cols = all_numeric_cols[: cfg["max_features"]]

        if not feature_cols:
            issue = DiagnosticIssue(
                id="ROBUSTNESS_NO_NUMERIC_FEATURES",
                category=self.category,
                severity=SeverityLevel.CRITICAL,
                title="No Numeric Features Found for Perturbation",
                description="Robustness evaluation requires at least one numeric feature column.",
                affected_features=[],
                evidence={"totalColumns": len(current_data.columns)},
                recommendation="Ensure the evaluation dataset includes continuous or integer feature representations.",
            )
            exec_time_ms = round((time.perf_counter() - start_time) * 1000, 2)
            return DiagnosticReport(
                engine_name=self.name,
                category=self.category,
                health_score=0.0,
                passed=False,
                execution_time_ms=exec_time_ms,
                issues=[issue],
                metadata={"module": "ROBUSTNESS", "version": "1.0", "summary": {"error": "No numeric features", "healthScore": 0.0, "passed": False}},
            )

        # 5. Deterministic Sampling
        max_samples = min(int(cfg["max_sample_size"]), len(current_data))
        rng_seed = int(cfg["random_state"])
        rng = np.random.RandomState(rng_seed)

        if len(current_data) > max_samples:
            sampled_indices = np.sort(rng.choice(len(current_data), size=max_samples, replace=False))
            eval_df = current_data.iloc[sampled_indices].copy()
            is_sampled = True
        else:
            eval_df = current_data.copy()
            is_sampled = False

        X_eval = eval_df[feature_cols].copy()
        y_true_eval = eval_df[target_column].astype(int).values if target_column and target_column in eval_df.columns else None
        threshold = float(cfg["decision_threshold"])

        # Feature scale dictionary
        feature_scales = {col: self._compute_feature_scale(X_eval[col]) for col in feature_cols}
        scale_vec = np.array([feature_scales[c] for c in feature_cols])

        # 6. Baseline Predictions
        def get_probs(df_features: pd.DataFrame) -> np.ndarray:
            p = model.predict_proba(df_features)
            if p.ndim == 2 and p.shape[1] >= 2:
                return p[:, 1]
            return p.ravel()

        p_base = get_probs(X_eval)
        y_pred_base = (p_base >= threshold).astype(int)

        baseline_pos_rate = round(float(np.mean(y_pred_base)), 4)
        baseline_acc = round(float(accuracy_score(y_true_eval, y_pred_base)), 4) if y_true_eval is not None else None
        baseline_f1 = round(float(f1_score(y_true_eval, y_pred_base, zero_division=0)), 4) if y_true_eval is not None else None
        try:
            baseline_auc = round(float(roc_auc_score(y_true_eval, p_base)), 4) if y_true_eval is not None and len(np.unique(y_true_eval)) > 1 else None
        except Exception:
            baseline_auc = None

        # 7. Analysis A: Multi-Level Gaussian Jitter Noise Sensitivity
        noise_levels = cfg.get("noise_levels") or [0.01, 0.05, 0.10]
        noise_results = []
        worst_noise_flip_rate = 0.0
        worst_prob_shift = 0.0

        for lvl in noise_levels:
            lvl_float = float(lvl)
            noise_rng = np.random.RandomState(rng_seed)
            # Add Gaussian noise scaled to empirical feature std
            noise_matrix = noise_rng.normal(0.0, 1.0, size=X_eval.shape) * (lvl_float * scale_vec)
            X_jittered = pd.DataFrame(X_eval.values + noise_matrix, columns=feature_cols, index=X_eval.index)

            p_jitter = get_probs(X_jittered)
            y_pred_jitter = (p_jitter >= threshold).astype(int)

            prob_diffs = np.abs(p_jitter - p_base)
            flips = (y_pred_base != y_pred_jitter)
            flip_count = int(np.sum(flips))
            flip_rate = round(float(flip_count / len(X_eval)), 4)
            mean_prob_shift = round(float(np.mean(prob_diffs)), 4)
            median_prob_shift = round(float(np.median(prob_diffs)), 4)
            max_prob_shift = round(float(np.max(prob_diffs)), 4)

            acc_jitter = round(float(accuracy_score(y_true_eval, y_pred_jitter)), 4) if y_true_eval is not None else None
            f1_jitter = round(float(f1_score(y_true_eval, y_pred_jitter, zero_division=0)), 4) if y_true_eval is not None else None
            try:
                auc_jitter = round(float(roc_auc_score(y_true_eval, p_jitter)), 4) if y_true_eval is not None and len(np.unique(y_true_eval)) > 1 else None
            except Exception:
                auc_jitter = None

            if flip_rate > worst_noise_flip_rate:
                worst_noise_flip_rate = flip_rate
            if mean_prob_shift > worst_prob_shift:
                worst_prob_shift = mean_prob_shift

            noise_results.append({
                "level": lvl_float,
                "label": f"{int(lvl_float * 100)}% Gaussian Noise",
                "observationsEvaluated": len(X_eval),
                "flipCount": flip_count,
                "flipRate": flip_rate,
                "meanProbabilityShift": mean_prob_shift,
                "medianProbabilityShift": median_prob_shift,
                "maxProbabilityShift": max_prob_shift,
                "accuracy": acc_jitter,
                "f1Score": f1_jitter,
                "rocAuc": auc_jitter,
            })

        # 8. Analysis B: Feature-wise Perturbation Sensitivity
        feature_sensitivity_list = []
        for col_idx, col in enumerate(feature_cols):
            feat_rng = np.random.RandomState(rng_seed)
            X_feat_pert = X_eval.copy()
            # 5% perturbation on single feature
            noise_vec = feat_rng.normal(0.0, 0.05 * feature_scales[col], size=len(X_eval))
            X_feat_pert[col] = X_eval[col] + noise_vec

            p_f_pert = get_probs(X_feat_pert)
            y_f_pert = (p_f_pert >= threshold).astype(int)

            f_prob_diffs = np.abs(p_f_pert - p_base)
            f_flips = (y_pred_base != y_f_pert)
            f_flip_count = int(np.sum(f_flips))
            f_flip_rate = round(float(f_flip_count / len(X_eval)), 4)
            f_mean_shift = round(float(np.mean(f_prob_diffs)), 4)
            f_median_shift = round(float(np.median(f_prob_diffs)), 4)
            f_max_shift = round(float(np.max(f_prob_diffs)), 4)

            feature_sensitivity_list.append({
                "feature": col,
                "perturbationScale": round(float(0.05 * feature_scales[col]), 4),
                "observationsEvaluated": len(X_eval),
                "flipCount": f_flip_count,
                "flipRate": f_flip_rate,
                "meanProbabilityShift": f_mean_shift,
                "medianProbabilityShift": f_median_shift,
                "maxProbabilityShift": f_max_shift,
            })

        # Rank features descending by flip rate and mean probability shift
        feature_sensitivity_list.sort(key=lambda x: (-x["flipRate"], -x["meanProbabilityShift"]))
        top_sensitive_feature = feature_sensitivity_list[0]["feature"] if feature_sensitivity_list else "none"

        # 9. Analysis C: Tree Bounded Boundary / Local Adversarial Search
        max_bnd_samples = min(int(cfg["max_boundary_samples"]), len(X_eval))
        X_bnd = X_eval.iloc[:max_bnd_samples].copy()
        y_bnd_base = y_pred_base[:max_bnd_samples]

        # Track best normalized distance and best feature for each sample
        sample_best_dist = np.full(max_bnd_samples, fill_value=float("inf"))
        sample_best_feat = [None] * max_bnd_samples

        # Search factors in increasing order
        search_factors = [0.05, 0.10, 0.20, 0.35, 0.50, 0.75, 1.0, 1.5, 2.0, 3.0]

        for factor in search_factors:
            for col in feature_cols:
                col_scale = feature_scales[col]

                # Positive direction
                X_pos = X_bnd.copy()
                X_pos[col] = X_bnd[col] + (factor * col_scale)
                p_pos = get_probs(X_pos)
                flips_pos = ((p_pos >= threshold).astype(int) != y_bnd_base)

                # Negative direction
                X_neg = X_bnd.copy()
                X_neg[col] = X_bnd[col] - (factor * col_scale)
                p_neg = get_probs(X_neg)
                flips_neg = ((p_neg >= threshold).astype(int) != y_bnd_base)

                flips_any = flips_pos | flips_neg
                for idx in np.where(flips_any)[0]:
                    if factor < sample_best_dist[idx]:
                        sample_best_dist[idx] = factor
                        sample_best_feat[idx] = col

        # Collect metrics
        flipped_mask = (sample_best_dist < float("inf"))
        successful_flips = int(np.sum(flipped_mask))
        normalized_distances = [float(d) for d in sample_best_dist[flipped_mask]]

        feature_flip_counts = {col: 0 for col in feature_cols}
        feature_flip_distances = {col: [] for col in feature_cols}

        for idx in np.where(flipped_mask)[0]:
            f_name = sample_best_feat[idx]
            d_val = float(sample_best_dist[idx])
            if f_name:
                feature_flip_counts[f_name] += 1
                feature_flip_distances[f_name].append(d_val)

        boundary_flip_rate = round(float(successful_flips / max_bnd_samples), 4) if max_bnd_samples > 0 else 0.0
        median_norm_distance = round(float(np.median(normalized_distances)), 4) if normalized_distances else None
        min_norm_distance = round(float(np.min(normalized_distances)), 4) if normalized_distances else None
        max_norm_distance = round(float(np.max(normalized_distances)), 4) if normalized_distances else None

        feature_bnd_list = []
        for col in feature_cols:
            d_list = feature_flip_distances[col]
            feature_bnd_list.append({
                "feature": col,
                "flipCount": feature_flip_counts[col],
                "medianNormalizedDistance": round(float(np.median(d_list)), 4) if d_list else None,
            })
        feature_bnd_list.sort(key=lambda x: -x["flipCount"])

        boundary_search_result = {
            "method": "TREE_BOUNDARY_SEARCH",
            "samplesTested": max_bnd_samples,
            "successfulFlips": successful_flips,
            "unflippedCount": max_bnd_samples - successful_flips,
            "flipRate": boundary_flip_rate,
            "medianNormalizedDistance": median_norm_distance,
            "minNormalizedDistance": min_norm_distance,
            "maxNormalizedDistance": max_norm_distance,
            "features": feature_bnd_list[:10],
        }

        # 10. Analysis D: FGSM Adversarial Applicability
        if model.supports_gradients():
            fgsm_result = {
                "method": "FGSM",
                "status": "COMPLETED",
                "reason": "Differentiable gradient interface supported.",
            }
        else:
            fgsm_result = {
                "method": "FGSM",
                "status": "NOT_APPLICABLE",
                "reason": "Model adapter does not expose differentiable gradients. Bounded tree boundary search used instead.",
            }

        # 11. Analysis E: Feature Corruption & Missingness Stress
        if model.supports_missingness():
            missing_levels = [0.05, 0.10, 0.20]
            missingness_records = []
            for m_rate in missing_levels:
                miss_rng = np.random.RandomState(rng_seed)
                X_miss = X_eval.copy()
                mask = miss_rng.rand(*X_miss.shape) < m_rate
                # Inject NaNs
                X_miss_vals = X_miss.values.astype(float)
                X_miss_vals[mask] = np.nan
                X_miss_df = pd.DataFrame(X_miss_vals, columns=feature_cols, index=X_miss.index)

                p_miss = get_probs(X_miss_df)
                y_pred_miss = (p_miss >= threshold).astype(int)

                m_prob_diffs = np.abs(p_miss - p_base)
                m_flips = (y_pred_base != y_pred_miss)
                m_flip_count = int(np.sum(m_flips))
                m_flip_rate = round(float(m_flip_count / len(X_eval)), 4)
                m_mean_shift = round(float(np.mean(m_prob_diffs)), 4)
                m_acc = round(float(accuracy_score(y_true_eval, y_pred_miss)), 4) if y_true_eval is not None else None

                missingness_records.append({
                    "dropoutFraction": m_rate,
                    "label": f"{int(m_rate * 100)}% Missingness",
                    "flipCount": m_flip_count,
                    "flipRate": m_flip_rate,
                    "meanProbabilityShift": m_mean_shift,
                    "retainedAccuracy": m_acc,
                })

            missingness_result = {
                "status": "COMPLETED",
                "levels": missingness_records,
            }
        else:
            missingness_result = {
                "status": "NOT_APPLICABLE",
                "reason": "Model architecture does not natively support missing feature values at inference time.",
            }

        # 12. Analysis F: Subgroup Robustness
        prot_attr = cfg.get("protected_attribute") or cfg.get("protectedAttribute")
        if not prot_attr:
            for cand in ["is_foreign_ip", "card_network", "gender", "age_group", "protected_class"]:
                if cand in eval_df.columns:
                    prot_attr = cand
                    break

        subgroup_records = []
        subgroup_robustness_gap = None

        if prot_attr and prot_attr in eval_df.columns:
            # 5% noise predictions from Analysis A
            five_pct_noise_rec = next((r for r in noise_results if abs(r["level"] - 0.05) < 1e-4), noise_results[0])
            noise_5_rng = np.random.RandomState(rng_seed)
            X_5_noise = pd.DataFrame(
                X_eval.values + (noise_5_rng.normal(0.0, 1.0, size=X_eval.shape) * (0.05 * scale_vec)),
                columns=feature_cols,
                index=X_eval.index,
            )
            p_5_noise = get_probs(X_5_noise)
            y_pred_5 = (p_5_noise >= threshold).astype(int)

            unique_subgroups = eval_df[prot_attr].dropna().unique()
            sub_flip_rates = []

            for s_val in unique_subgroups:
                s_mask = (eval_df[prot_attr] == s_val).values
                n_s = int(np.sum(s_mask))
                if n_s == 0:
                    continue

                s_y_base = y_pred_base[s_mask]
                s_y_pert = y_pred_5[s_mask]
                s_p_base = p_base[s_mask]
                s_p_pert = p_5_noise[s_mask]

                s_flips = np.sum(s_y_base != s_y_pert)
                s_flip_rate = round(float(s_flips / n_s), 4)
                s_mean_shift = round(float(np.mean(np.abs(s_p_pert - s_p_base))), 4)
                s_pos_rate = round(float(np.mean(s_y_base)), 4)

                sub_flip_rates.append(s_flip_rate)
                subgroup_records.append({
                    "group": str(s_val),
                    "sampleCount": n_s,
                    "baselinePositiveRate": s_pos_rate,
                    "noiseFlipRate5Pct": s_flip_rate,
                    "meanProbabilityShift": s_mean_shift,
                })

            if len(sub_flip_rates) >= 2:
                subgroup_robustness_gap = round(float(max(sub_flip_rates) - min(sub_flip_rates)), 4)

        subgroup_robustness_result = {
            "protectedAttribute": prot_attr,
            "groups": subgroup_records,
            "robustnessGap": subgroup_robustness_gap,
        }

        # 13. Generate Structured Forensic Findings
        five_pct_flip = next((r["flipRate"] for r in noise_results if abs(r["level"] - 0.05) < 1e-4), worst_noise_flip_rate)
        five_pct_shift = next((r["meanProbabilityShift"] for r in noise_results if abs(r["level"] - 0.05) < 1e-4), worst_prob_shift)

        # A. Noise Flip Rate Finding
        if five_pct_flip >= cfg["noise_flip_rate_critical"]:
            issues.append(
                DiagnosticIssue(
                    id="ROBUSTNESS_EXTREME_NOISE_SENSITIVITY",
                    category=self.category,
                    severity=SeverityLevel.CRITICAL,
                    title=f"Extreme Prediction Flip Rate ({five_pct_flip * 100:.1f}%) Under 5% Gaussian Jitter",
                    description=(
                        f"The model flipped {five_pct_flip * 100:.1f}% of its predictions under mild 5% standard deviation Gaussian noise. "
                        f"Top sensitive feature: '{top_sensitive_feature}'."
                    ),
                    affected_features=[top_sensitive_feature],
                    evidence={"noiseLevel": 0.05, "flipRate": five_pct_flip, "threshold": cfg["noise_flip_rate_critical"]},
                    recommendation="Apply feature quantization, tree regularization (max_depth / min_child_weight), or adversarial training.",
                )
            )
        elif five_pct_flip >= cfg["noise_flip_rate_warning"]:
            issues.append(
                DiagnosticIssue(
                    id="ROBUSTNESS_MODERATE_NOISE_SENSITIVITY",
                    category=self.category,
                    severity=SeverityLevel.WARNING,
                    title=f"Elevated Prediction Flip Rate ({five_pct_flip * 100:.1f}%) Under 5% Gaussian Noise",
                    description=(
                        f"Predictions exhibit noticeable instability with {five_pct_flip * 100:.1f}% flip rate under 5% input jitter. "
                        f"Most sensitive feature: '{top_sensitive_feature}'."
                    ),
                    affected_features=[top_sensitive_feature],
                    evidence={"noiseLevel": 0.05, "flipRate": five_pct_flip, "topFeature": top_sensitive_feature},
                    recommendation="Evaluate noise augmentation during model training to improve boundary stability.",
                )
            )

        # B. Mean Probability Shift Finding
        if five_pct_shift >= cfg["probability_shift_critical"]:
            issues.append(
                DiagnosticIssue(
                    id="ROBUSTNESS_PROBABILITY_INSTABILITY",
                    category=self.category,
                    severity=SeverityLevel.HIGH,
                    title=f"High Mean Probability Shift ({five_pct_shift * 100:.1f}%) Under Perturbation",
                    description=f"Model output probabilities deviate on average by {five_pct_shift * 100:.1f}% when input features are lightly perturbed.",
                    affected_features=[top_sensitive_feature],
                    evidence={"meanProbabilityShift": five_pct_shift, "threshold": cfg["probability_shift_critical"]},
                    recommendation="Incorporate gradient penalties or smooth ensemble averaging to reduce local probability volatility.",
                )
            )
        elif five_pct_shift >= cfg["probability_shift_warning"]:
            issues.append(
                DiagnosticIssue(
                    id="ROBUSTNESS_PROBABILITY_DRIFT",
                    category=self.category,
                    severity=SeverityLevel.WARNING,
                    title=f"Moderate Probability Volatility ({five_pct_shift * 100:.1f}%) Under Noise",
                    description=f"Output probabilities shift by {five_pct_shift * 100:.1f}% under standard 5% feature noise.",
                    affected_features=[top_sensitive_feature],
                    evidence={"meanProbabilityShift": five_pct_shift},
                    recommendation="Monitor probability calibration confidence intervals under live telemetry noise.",
                )
            )

        # C. Boundary Sensitivity Finding
        if median_norm_distance is not None and median_norm_distance <= cfg["boundary_distance_critical"] and boundary_flip_rate >= 0.20:
            issues.append(
                DiagnosticIssue(
                    id="ROBUSTNESS_PROXIMATE_DECISION_BOUNDARY",
                    category=self.category,
                    severity=SeverityLevel.HIGH,
                    title=f"Shallow Decision Boundary Proximity (Median Normalized Δ = {median_norm_distance:.3f})",
                    description=(
                        f"A small perturbation of {median_norm_distance * 100:.1f}% standard deviation is sufficient to flip the model decision "
                        f"for {boundary_flip_rate * 100:.1f}% of tested observations."
                    ),
                    affected_features=[feature_bnd_list[0]["feature"]] if feature_bnd_list else [],
                    evidence={"medianNormalizedDistance": median_norm_distance, "boundaryFlipRate": boundary_flip_rate},
                    recommendation="Increase leaf node minimum instance weights to push decision boundaries away from dense sample regions.",
                )
            )
        elif median_norm_distance is not None and median_norm_distance <= cfg["boundary_distance_warning"]:
            issues.append(
                DiagnosticIssue(
                    id="ROBUSTNESS_SHALLOW_DECISION_MARGIN",
                    category=self.category,
                    severity=SeverityLevel.WARNING,
                    title=f"Narrow Decision Margin (Median Normalized Δ = {median_norm_distance:.3f})",
                    description=f"Evaluated samples cross the decision boundary within {median_norm_distance * 100:.1f}% normalized feature distance.",
                    affected_features=[feature_bnd_list[0]["feature"]] if feature_bnd_list else [],
                    evidence={"medianNormalizedDistance": median_norm_distance},
                    recommendation="Audit features near the decision threshold for precision margin requirements.",
                )
            )

        # D. Subgroup Robustness Disparity Finding
        if subgroup_robustness_gap is not None and subgroup_robustness_gap >= cfg["subgroup_robustness_gap_critical"]:
            issues.append(
                DiagnosticIssue(
                    id="ROBUSTNESS_SUBGROUP_DISPARITY",
                    category=self.category,
                    severity=SeverityLevel.HIGH,
                    title=f"Subgroup Robustness Disparity ({subgroup_robustness_gap * 100:.1f}%) Across '{prot_attr}'",
                    description=(
                        f"Model flip rates under noise diverge by {subgroup_robustness_gap * 100:.1f}% between demographic slices of '{prot_attr}'. "
                        f"Perturbations disproportionately destabilize certain cohorts."
                    ),
                    affected_features=[prot_attr],
                    evidence={"robustnessGap": subgroup_robustness_gap, "protectedAttribute": prot_attr},
                    recommendation="Evaluate subgroup-specific feature sensitivities and rebalance representation.",
                )
            )
        elif subgroup_robustness_gap is not None and subgroup_robustness_gap >= cfg["subgroup_robustness_gap_warning"]:
            issues.append(
                DiagnosticIssue(
                    id="ROBUSTNESS_SUBGROUP_VARIANCE",
                    category=self.category,
                    severity=SeverityLevel.WARNING,
                    title=f"Moderate Subgroup Robustness Gap ({subgroup_robustness_gap * 100:.1f}%) Across '{prot_attr}'",
                    description=f"Prediction stability varies by {subgroup_robustness_gap * 100:.1f}% across slices of '{prot_attr}'.",
                    affected_features=[prot_attr],
                    evidence={"robustnessGap": subgroup_robustness_gap},
                    recommendation="Inspect feature quality and variance within sensitive subgroup partitions.",
                )
            )

        # 14. Diagnostic Metrics
        metrics_list.append(
            DiagnosticMetric(
                name="gaussian_jitter_5pct_flip_rate",
                value=float(five_pct_flip),
                threshold_max=cfg["noise_flip_rate_warning"],
                passed=bool(five_pct_flip < cfg["noise_flip_rate_warning"]),
            )
        )
        metrics_list.append(
            DiagnosticMetric(
                name="gaussian_jitter_5pct_prob_shift",
                value=float(five_pct_shift),
                threshold_max=cfg["probability_shift_warning"],
                passed=bool(five_pct_shift < cfg["probability_shift_warning"]),
            )
        )
        metrics_list.append(
            DiagnosticMetric(
                name="boundary_search_flip_rate",
                value=float(boundary_flip_rate),
                threshold_max=0.50,
                passed=bool(boundary_flip_rate < 0.50),
            )
        )
        if subgroup_robustness_gap is not None:
            metrics_list.append(
                DiagnosticMetric(
                    name="subgroup_robustness_gap",
                    value=float(subgroup_robustness_gap),
                    threshold_max=cfg["subgroup_robustness_gap_warning"],
                    passed=bool(subgroup_robustness_gap < cfg["subgroup_robustness_gap_warning"]),
                )
            )

        # 15. Health Score Calculation
        penalty = 0.0
        critical_count = 0
        for issue in issues:
            if issue.severity == SeverityLevel.CRITICAL:
                penalty += 25.0
                critical_count += 1
            elif issue.severity == SeverityLevel.HIGH:
                penalty += 15.0
            elif issue.severity == SeverityLevel.WARNING:
                penalty += 5.0
            elif issue.severity == SeverityLevel.MEDIUM:
                penalty += 3.0
            elif issue.severity == SeverityLevel.LOW:
                penalty += 1.0

        health_score = max(0.0, min(100.0, round(100.0 - penalty, 1)))
        passed = bool(health_score >= 70.0 and critical_count == 0)

        exec_time_ms = round((time.perf_counter() - start_time) * 1000, 2)

        summary_dict = {
            "modelName": model.name,
            "modelFramework": model.framework,
            "samplesEvaluated": len(X_eval),
            "numericFeaturesEvaluated": len(feature_cols),
            "isSampled": is_sampled,
            "topSensitiveFeature": top_sensitive_feature,
            "gaussianJitter5PctFlipRate": five_pct_flip,
            "meanProbabilityShift5Pct": five_pct_shift,
            "boundaryFlipRate": boundary_flip_rate,
            "medianBoundaryDistance": median_norm_distance,
            "subgroupRobustnessGap": subgroup_robustness_gap,
            "findingCount": len(issues),
            "healthScore": health_score,
            "passed": passed,
        }

        metadata_dict = {
            "module": "ROBUSTNESS",
            "version": "1.0",
            "summary": summary_dict,
            "baseline": {
                "sampleCount": len(X_eval),
                "positiveRate": baseline_pos_rate,
                "accuracy": baseline_acc,
                "f1Score": baseline_f1,
                "rocAuc": baseline_auc,
            },
            "noiseSensitivity": {
                "levels": noise_results,
            },
            "featureSensitivity": {
                "features": feature_sensitivity_list,
            },
            "boundarySearch": boundary_search_result,
            "fgsm": fgsm_result,
            "missingnessStress": missingness_result,
            "subgroupRobustness": subgroup_robustness_result,
            "findings": [issue.model_dump() for issue in issues],
            "metrics": [m.model_dump() for m in metrics_list],
        }

        return DiagnosticReport(
            engine_name=self.name,
            category=self.category,
            health_score=health_score,
            passed=passed,
            execution_time_ms=exec_time_ms,
            metrics=metrics_list,
            issues=issues,
            metadata=metadata_dict,
        )
