import logging
import math
import re
import time
from typing import Any, Dict, List, Optional, Tuple, Union
import numpy as np
import pandas as pd
import scipy.stats as stats
from sklearn.inspection import permutation_importance
import shap

from app.diagnostics.base import BaseDiagnosticEngine
from app.models.adapter import (
    ModelAdapter,
    XGBoostModelAdapter,
    SklearnTreeModelAdapter,
    GenericCallableModelAdapter,
)
from app.models.loader import ModelLoader, ModelArtifactNotFoundError
from app.schemas.diagnostic_models import (
    DiagnosticCategory,
    DiagnosticIssue,
    DiagnosticMetric,
    DiagnosticReport,
    SeverityLevel,
)

logger = logging.getLogger(__name__)


class ExplainabilityEngine(BaseDiagnosticEngine):
    """
    Computes real mathematical model explainability and feature attributions:
    - TreeSHAP for tree ensembles (XGBoost, Scikit-Learn tree models)
    - Model-agnostic Permutation Importance fallback
    - Global feature attribution ranking (mean absolute, signed, contribution rates)
    - 5 deterministic local explanation decompositions (Force/Waterfall profiles)
    - Feature importance agreement (Spearman rank correlation)
    - Attribution concentration diagnostics (Top-1, Top-3, Top-5 share & normalized entropy)
    - Forensic findings for dominant features, identifier reliance, and rank divergence
    """

    DEFAULT_CONFIG: Dict[str, Any] = {
        "max_explanation_samples": 1000,
        "random_state": 42,
        "n_permutation_repeats": 5,
        "top_feature_share_warning": 0.35,
        "top_feature_share_critical": 0.50,
        "top3_share_warning": 0.65,
        "top3_share_critical": 0.80,
        "rank_disagreement_warning": 0.30,
        "rank_disagreement_critical": 0.10,
        "identifier_share_warning": 0.15,
    }

    IDENTIFIER_PATTERNS = [
        re.compile(r".*_id$", re.IGNORECASE),
        re.compile(r"^id$", re.IGNORECASE),
        re.compile(r".*uuid.*", re.IGNORECASE),
        re.compile(r".*hash.*", re.IGNORECASE),
        re.compile(r".*guid.*", re.IGNORECASE),
        re.compile(r".*token.*", re.IGNORECASE),
    ]

    @property
    def category(self) -> DiagnosticCategory:
        return DiagnosticCategory.EXPLAINABILITY

    @property
    def name(self) -> str:
        return "Explainability & Feature Attribution Engine"

    @property
    def description(self) -> str:
        return "Evaluates TreeSHAP feature attributions, permutation importance agreement, and local predictions."

    def _calculate_permutation_importance(
        self,
        model_adapter: ModelAdapter,
        X: pd.DataFrame,
        y: np.ndarray,
        n_repeats: int = 5,
        random_state: int = 42,
    ) -> Tuple[List[Dict[str, Any]], Dict[str, int]]:
        """
        Calculates model-agnostic permutation feature importance using prediction probability degradation.
        Evaluates ROC-AUC metric degradation caused by shuffling each feature column.
        """
        rng = np.random.RandomState(random_state)
        feature_cols = list(X.columns)

        # Baseline performance score
        try:
            baseline_probs = model_adapter.predict_proba(X)
            p_pos = baseline_probs[:, 1] if baseline_probs.ndim == 2 else baseline_probs
            if len(np.unique(y)) >= 2:
                import sklearn.metrics as sk_metrics
                baseline_score = float(sk_metrics.roc_auc_score(y, p_pos))
            else:
                baseline_score = 0.5
        except Exception as e:
            logger.warning("Baseline scoring in permutation importance warning: %s", str(e))
            baseline_score = 0.5

        importances_dict: Dict[str, List[float]] = {feat: [] for feat in feature_cols}

        for r in range(n_repeats):
            for feat in feature_cols:
                X_permuted = X.copy()
                X_permuted[feat] = rng.permutation(X_permuted[feat].values)
                try:
                    perm_probs = model_adapter.predict_proba(X_permuted)
                    p_perm_pos = perm_probs[:, 1] if perm_probs.ndim == 2 else perm_probs
                    if len(np.unique(y)) >= 2:
                        import sklearn.metrics as sk_metrics
                        perm_score = float(sk_metrics.roc_auc_score(y, p_perm_pos))
                    else:
                        perm_score = 0.5
                    drop = baseline_score - perm_score
                except Exception as e:
                    drop = 0.0
                importances_dict[feat].append(drop)

        # Calculate mean, std and rankings
        perm_list: List[Dict[str, Any]] = []
        for feat in feature_cols:
            drops = importances_dict[feat]
            p_mean = round(float(np.mean(drops)), 4)
            p_std = round(float(np.std(drops)), 4)
            perm_list.append({
                "feature": feat,
                "importanceMean": p_mean,
                "importanceStd": p_std,
                "nRepeats": n_repeats,
            })

        perm_list.sort(key=lambda x: x["importanceMean"], reverse=True)
        perm_ranks: Dict[str, int] = {}
        for rank_0, item in enumerate(perm_list):
            item["rank"] = rank_0 + 1
            perm_ranks[item["feature"]] = rank_0 + 1

        return perm_list, perm_ranks

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

        if current_data is None or not isinstance(current_data, pd.DataFrame) or len(current_data) == 0:
            issue = DiagnosticIssue(
                id="EXPLAIN-EMPTY-DATASET",
                category=self.category,
                severity=SeverityLevel.CRITICAL,
                title="Evaluation Dataset Empty",
                description="Evaluation dataset must be a non-empty pandas DataFrame with valid feature columns.",
                affected_features=[],
                evidence={"rows": 0},
                recommendation="Provide valid tabular evaluation observations.",
            )
            exec_time_ms = round((time.perf_counter() - start_time) * 1000, 2)
            return DiagnosticReport(
                engine_name=self.name,
                category=self.category,
                health_score=0.0,
                passed=False,
                execution_time_ms=exec_time_ms,
                issues=[issue],
                metadata={"module": "EXPLAINABILITY", "version": "1.0", "summary": {"error": "Empty dataset", "healthScore": 0.0, "passed": False}},
            )

        # 1. Target Column Check
        if not target_column or target_column not in current_data.columns:
            issue = DiagnosticIssue(
                id="EXPLAIN-MISSING-TARGET",
                category=self.category,
                severity=SeverityLevel.CRITICAL,
                title=f"Target Column '{target_column}' Missing in Dataset",
                description=f"Ground truth target column '{target_column}' is required for permutation importance evaluation.",
                affected_features=[target_column] if target_column else [],
                evidence={"columns": list(current_data.columns[:10])},
                recommendation="Ensure target column exists in evaluation dataset.",
            )
            exec_time_ms = round((time.perf_counter() - start_time) * 1000, 2)
            return DiagnosticReport(
                engine_name=self.name,
                category=self.category,
                health_score=0.0,
                passed=False,
                execution_time_ms=exec_time_ms,
                issues=[issue],
                metadata={"module": "EXPLAINABILITY", "version": "1.0", "summary": {"error": f"Target column '{target_column}' missing", "healthScore": 0.0, "passed": False}},
            )

        # 2. Resolve Model Adapter
        model_name = cfg.get("model_name", "fraud_classifier_v17")
        model_framework = cfg.get("model_framework", "xgboost")
        task_type = cfg.get("task_type", "binary_classification")
        storage_uri = cfg.get("storage_uri")
        execution_mode = cfg.get("execution_mode", "REAL")

        model_adapter: Optional[ModelAdapter] = None
        if model_artifact is not None:
            if isinstance(model_artifact, ModelAdapter):
                model_adapter = model_artifact
            elif hasattr(model_artifact, "predict_proba") or hasattr(model_artifact, "predict"):
                model_adapter = GenericCallableModelAdapter(name=model_name, raw_model=model_artifact, framework=model_framework, task_type=task_type)
        
        if model_adapter is None:
            try:
                model_adapter = ModelLoader.load_model_adapter(
                    model_name=model_name,
                    framework=model_framework,
                    task_type=task_type,
                    storage_uri=storage_uri,
                    evaluation_data=current_data,
                    target_column=target_column,
                    execution_mode=execution_mode,
                )
            except ModelArtifactNotFoundError as e:
                issue = DiagnosticIssue(
                    id="MODEL_ARTIFACT_UNAVAILABLE",
                    category=self.category,
                    severity=SeverityLevel.CRITICAL,
                    title=f"Explainable Model Artifact Unavailable for '{model_name}'",
                    description=(
                        f"No serialized model artifact could be found at the configured location ({storage_uri or 'models/'}). "
                        f"Real explainability requires a trained model artifact to evaluate TreeSHAP or Permutation Importance."
                    ),
                    affected_features=[],
                    evidence={"modelName": model_name, "framework": model_framework, "error": str(e)},
                    recommendation="Persist the trained model artifact to the model directory or specify storageUri.",
                )
                exec_time_ms = round((time.perf_counter() - start_time) * 1000, 2)
                return DiagnosticReport(
                    engine_name=self.name,
                    category=self.category,
                    health_score=0.0,
                    passed=False,
                    execution_time_ms=exec_time_ms,
                    issues=[issue],
                    metadata={"module": "EXPLAINABILITY", "version": "1.0", "summary": {"error": "Model artifact unavailable", "healthScore": 0.0, "passed": False}},
                )
            except Exception as e:
                logger.error("Error loading model adapter: %s", str(e), exc_info=True)
                issue = DiagnosticIssue(
                    id="MODEL_LOAD_ERROR",
                    category=self.category,
                    severity=SeverityLevel.CRITICAL,
                    title="Model Artifact Ingestion Failure",
                    description=f"Failed to load model artifact '{model_name}': {str(e)}",
                    affected_features=[],
                    evidence={"error": str(e)},
                    recommendation="Verify model format and serialization compatibility.",
                )
                exec_time_ms = round((time.perf_counter() - start_time) * 1000, 2)
                return DiagnosticReport(
                    engine_name=self.name,
                    category=self.category,
                    health_score=0.0,
                    passed=False,
                    execution_time_ms=exec_time_ms,
                    issues=[issue],
                    metadata={"module": "EXPLAINABILITY", "version": "1.0", "summary": {"error": str(e), "healthScore": 0.0, "passed": False}},
                )

        # 3. Feature Column Selection & Preprocessing
        raw_row_count = len(current_data)
        exclude_cols = {target_column, "pred_prob", "prediction", "prediction_probability", "score"}
        if cfg.get("prediction_column"):
            exclude_cols.add(cfg["prediction_column"])

        # Determine features
        if model_adapter.feature_names:
            feature_cols = [c for c in model_adapter.feature_names if c in current_data.columns]
        else:
            feature_cols = [c for c in current_data.columns if c not in exclude_cols and pd.api.types.is_numeric_dtype(current_data[c])]

        if not feature_cols:
            issue = DiagnosticIssue(
                id="EXPLAIN-NO-FEATURES",
                category=self.category,
                severity=SeverityLevel.CRITICAL,
                title="No Numeric Feature Columns Available for Attribution",
                description="Evaluation dataset contains no compatible numeric feature columns for model attribution.",
                affected_features=[],
                evidence={"allColumns": list(current_data.columns)},
                recommendation="Provide valid numeric feature columns.",
            )
            exec_time_ms = round((time.perf_counter() - start_time) * 1000, 2)
            return DiagnosticReport(
                engine_name=self.name,
                category=self.category,
                health_score=0.0,
                passed=False,
                execution_time_ms=exec_time_ms,
                issues=[issue],
                metadata={"module": "EXPLAINABILITY", "version": "1.0", "summary": {"error": "No numeric feature columns", "healthScore": 0.0, "passed": False}},
            )

        # 4. Deterministic Sampling
        max_samples = int(cfg["max_explanation_samples"])
        random_state = int(cfg["random_state"])

        if raw_row_count > max_samples:
            sample_df = current_data.sample(n=max_samples, random_state=random_state).copy()
        else:
            sample_df = current_data.copy()

        sample_count = len(sample_df)
        X_sample = sample_df[feature_cols].copy()
        # Impute missing values for tree / permutation calculation
        X_sample_clean = X_sample.fillna(X_sample.median().fillna(0))
        y_sample = sample_df[target_column].astype(int).values

        # 5. Execute SHAP (TreeSHAP or Permutation fallback)
        method_used = "PERMUTATION_IMPORTANCE"
        model_output_space = "raw"
        base_value: float = 0.0
        shap_matrix: Optional[np.ndarray] = None
        tree_shap_success = False

        if model_adapter.supports_tree_shap():
            try:
                tree_model = model_adapter.get_tree_model()
                explainer = shap.TreeExplainer(tree_model)
                shap_explanation = explainer(X_sample_clean)

                # Normalize SHAP output shape to (N, M)
                raw_values = shap_explanation.values
                if raw_values.ndim == 2:
                    shap_matrix = raw_values
                elif raw_values.ndim == 3:
                    # Multi-class or binary two-output, select positive class (index 1)
                    shap_matrix = raw_values[:, :, 1]
                else:
                    raise ValueError(f"Unexpected SHAP values shape: {raw_values.shape}")

                # Extract base / expected value
                exp_val = explainer.expected_value
                if isinstance(exp_val, (list, np.ndarray)):
                    base_value = float(exp_val[1] if len(exp_val) > 1 else exp_val[0])
                else:
                    base_value = float(exp_val)

                method_used = "TREE_SHAP"
                tree_shap_success = True
                model_output_space = "raw" if "xgboost" in model_adapter.framework else "probability"
                logger.info("TreeSHAP successfully evaluated on %d samples across %d features.", sample_count, len(feature_cols))
            except Exception as e:
                logger.warning("TreeSHAP computation failed (%s). Falling back to Permutation Importance.", str(e))
                tree_shap_success = False

        # 6. Global Feature Importance (SHAP)
        global_importance_list: List[Dict[str, Any]] = []
        shap_feature_ranks: Dict[str, int] = {}
        mean_abs_shap_dict: Dict[str, float] = {}

        if tree_shap_success and shap_matrix is not None:
            mean_abs = np.mean(np.abs(shap_matrix), axis=0)
            mean_signed = np.mean(shap_matrix, axis=0)
            median_abs = np.median(np.abs(shap_matrix), axis=0)
            pos_rate = np.mean(shap_matrix > 0, axis=0)
            neg_rate = np.mean(shap_matrix < 0, axis=0)

            # Sort features descending by mean absolute SHAP
            sorted_indices = np.argsort(-mean_abs)
            for rank_0, idx in enumerate(sorted_indices):
                feat_name = feature_cols[idx]
                m_abs = round(float(mean_abs[idx]), 4)
                m_signed = round(float(mean_signed[idx]), 4)
                med_abs = round(float(median_abs[idx]), 4)
                p_rate = round(float(pos_rate[idx]), 4)
                n_rate = round(float(neg_rate[idx]), 4)
                rank = rank_0 + 1

                shap_feature_ranks[feat_name] = rank
                mean_abs_shap_dict[feat_name] = m_abs

                global_importance_list.append({
                    "feature": feat_name,
                    "meanAbsShap": m_abs,
                    "meanSignedShap": m_signed,
                    "medianAbsShap": med_abs,
                    "positiveContributionRate": p_rate,
                    "negativeContributionRate": n_rate,
                    "rank": rank,
                })

        # 7. Permutation Importance (Model-Agnostic)
        permutation_importance_list, perm_feature_ranks = self._calculate_permutation_importance(
            model_adapter=model_adapter,
            X=X_sample_clean,
            y=y_sample,
            n_repeats=int(cfg["n_permutation_repeats"]),
            random_state=random_state,
        )

        # If SHAP was not available, populate global_importance_list from permutation importance
        if not tree_shap_success and permutation_importance_list:
            for item in permutation_importance_list:
                m_val = max(0.0, item["importanceMean"])
                global_importance_list.append({
                    "feature": item["feature"],
                    "meanAbsShap": m_val,
                    "meanSignedShap": item["importanceMean"],
                    "medianAbsShap": m_val,
                    "positiveContributionRate": 1.0 if item["importanceMean"] > 0 else 0.0,
                    "negativeContributionRate": 1.0 if item["importanceMean"] < 0 else 0.0,
                    "rank": item["rank"],
                })
                mean_abs_shap_dict[item["feature"]] = m_val
                shap_feature_ranks[item["feature"]] = item["rank"]

        # 8. Feature Importance Agreement (Spearman Rank Correlation)
        spearman_corr: Optional[float] = None
        if shap_feature_ranks and perm_feature_ranks:
            common_features = [f for f in feature_cols if f in shap_feature_ranks and f in perm_feature_ranks]
            if len(common_features) >= 3:
                r1 = [shap_feature_ranks[f] for f in common_features]
                r2 = [perm_feature_ranks[f] for f in common_features]
                corr_res, _ = stats.spearmanr(r1, r2)
                if not math.isnan(corr_res):
                    spearman_corr = round(float(corr_res), 4)

        importance_agreement = {
            "methodA": method_used,
            "methodB": "PERMUTATION_IMPORTANCE",
            "spearmanCorrelation": spearman_corr,
            "commonFeatureCount": len(feature_cols),
        }

        # 9. Attribution Concentration & Normalized Entropy
        total_importance = sum(mean_abs_shap_dict.values())
        if total_importance > 1e-12:
            sorted_importances = sorted(mean_abs_shap_dict.values(), reverse=True)
            top1_share = round(sorted_importances[0] / total_importance, 4)
            top3_share = round(sum(sorted_importances[:3]) / total_importance, 4)
            top5_share = round(sum(sorted_importances[:5]) / total_importance, 4)
            top10_share = round(sum(sorted_importances[:10]) / total_importance, 4)

            # Normalized Entropy H = -sum(p * ln(p)) / ln(M)
            probs = [val / total_importance for val in sorted_importances if val > 0]
            raw_entropy = -sum(p * math.log(p) for p in probs)
            max_entropy = math.log(len(feature_cols)) if len(feature_cols) > 1 else 1.0
            norm_entropy = round(raw_entropy / max_entropy, 4) if max_entropy > 0 else 1.0
        else:
            top1_share = top3_share = top5_share = top10_share = 0.0
            norm_entropy = 1.0

        concentration = {
            "top1Share": top1_share,
            "top3Share": top3_share,
            "top5Share": top5_share,
            "top10Share": top10_share,
            "normalizedEntropy": norm_entropy,
        }

        # 10. Local Explanations (5 deterministic representative observations)
        local_explanations: List[Dict[str, Any]] = []
        try:
            preds_proba = model_adapter.predict_proba(X_sample_clean)
            pos_probs = preds_proba[:, 1] if preds_proba.ndim == 2 else preds_proba

            candidates = {}
            # 1. Max probability
            candidates["MAX_PROBABILITY"] = int(np.argmax(pos_probs))
            # 2. Min probability
            candidates["MIN_PROBABILITY"] = int(np.argmin(pos_probs))
            # 3. True Positive (highest prob among y=1)
            pos_indices = np.where((y_sample == 1) & (pos_probs >= 0.5))[0]
            if len(pos_indices) > 0:
                candidates["TRUE_POSITIVE"] = int(pos_indices[np.argmax(pos_probs[pos_indices])])
            # 4. True Negative (lowest prob among y=0)
            neg_indices = np.where((y_sample == 0) & (pos_probs < 0.5))[0]
            if len(neg_indices) > 0:
                candidates["TRUE_NEGATIVE"] = int(neg_indices[np.argmin(pos_probs[neg_indices])])
            # 5. Borderline (prob closest to 0.50)
            candidates["BORDERLINE_THRESHOLD"] = int(np.argmin(np.abs(pos_probs - 0.50)))

            chosen_indices = []
            seen_indices = set()
            for row_type, idx in candidates.items():
                if idx not in seen_indices:
                    chosen_indices.append((row_type, idx))
                    seen_indices.add(idx)

            for row_type, sample_row_idx in chosen_indices:
                actual_val = int(y_sample[sample_row_idx])
                pred_val = round(float(pos_probs[sample_row_idx]), 4)
                orig_row_idx = int(sample_df.index[sample_row_idx])

                top_contributors: List[Dict[str, Any]] = []
                if tree_shap_success and shap_matrix is not None:
                    row_shap = shap_matrix[sample_row_idx]
                    sorted_attr_idx = np.argsort(-np.abs(row_shap))[:10]
                    for a_idx in sorted_attr_idx:
                        feat = feature_cols[a_idx]
                        feat_val = X_sample_clean.iloc[sample_row_idx, a_idx]
                        s_val = round(float(row_shap[a_idx]), 4)
                        top_contributors.append({
                            "feature": feat,
                            "value": float(feat_val) if isinstance(feat_val, (int, float, np.number)) else str(feat_val),
                            "shapValue": s_val,
                            "direction": "POSITIVE" if s_val >= 0 else "NEGATIVE",
                        })
                else:
                    # Fallback top contributors from permutation importance
                    for item in permutation_importance_list[:5]:
                        feat = item["feature"]
                        feat_idx = feature_cols.index(feat)
                        feat_val = X_sample_clean.iloc[sample_row_idx, feat_idx]
                        top_contributors.append({
                            "feature": feat,
                            "value": float(feat_val) if isinstance(feat_val, (int, float, np.number)) else str(feat_val),
                            "shapValue": item["importanceMean"],
                            "direction": "POSITIVE" if item["importanceMean"] >= 0 else "NEGATIVE",
                        })

                local_explanations.append({
                    "observationType": row_type,
                    "rowIndex": orig_row_idx,
                    "sampleIndex": sample_row_idx,
                    "prediction": pred_val,
                    "actual": actual_val,
                    "baseValue": round(base_value, 4),
                    "topContributors": top_contributors,
                })
        except Exception as e:
            logger.warning("Local explanation generation warning: %s", str(e))

        # 11. Structured Forensic Findings Generation
        top_feature_name = global_importance_list[0]["feature"] if global_importance_list else "none"
        top_feature_shap = global_importance_list[0]["meanAbsShap"] if global_importance_list else 0.0

        # A. Dominant Feature Finding
        if top1_share >= cfg["top_feature_share_critical"]:
            issues.append(
                DiagnosticIssue(
                    id=f"EXPLAIN_DOMINANT_FEATURE_{top_feature_name}",
                    category=self.category,
                    severity=SeverityLevel.HIGH,
                    title=f"Excessive Attribution Concentration on '{top_feature_name}' ({top1_share * 100:.1f}%)",
                    description=(
                        f"A single feature ('{top_feature_name}') accounts for {top1_share * 100:.1f}% of total model attribution. "
                        f"This creates extreme fragility where prediction outcomes depend overwhelmingly on a single input signal."
                    ),
                    affected_features=[top_feature_name],
                    evidence={"top1Share": top1_share, "meanAbsShap": top_feature_shap, "totalImportance": total_importance},
                    recommendation="Investigate whether this feature is a target proxy or leakage artifact. Consider regularization or feature sub-sampling.",
                )
            )
        elif top1_share >= cfg["top_feature_share_warning"]:
            issues.append(
                DiagnosticIssue(
                    id=f"EXPLAIN_MODERATE_DOMINANCE_{top_feature_name}",
                    category=self.category,
                    severity=SeverityLevel.WARNING,
                    title=f"Moderate Attribution Concentration on '{top_feature_name}' ({top1_share * 100:.1f}%)",
                    description=f"Feature '{top_feature_name}' contributes {top1_share * 100:.1f}% of global model decision weight.",
                    affected_features=[top_feature_name],
                    evidence={"top1Share": top1_share, "meanAbsShap": top_feature_shap},
                    recommendation="Verify feature reliability and inspect attribution consistency across sub-populations.",
                )
            )

        # B. High Concentration Finding
        if top3_share >= cfg["top3_share_critical"]:
            top3_names = [item["feature"] for item in global_importance_list[:3]]
            issues.append(
                DiagnosticIssue(
                    id="EXPLAIN_HIGH_CONCENTRATION_TOP3",
                    category=self.category,
                    severity=SeverityLevel.HIGH,
                    title=f"Top 3 Features Account for {top3_share * 100:.1f}% of Model Attribution",
                    description=(
                        f"The model's predictions are dominated by the top 3 features ({', '.join(top3_names)}), "
                        f"leaving remaining features with negligible influence (Normalized Entropy H={norm_entropy:.2f})."
                    ),
                    affected_features=top3_names,
                    evidence={"top3Share": top3_share, "top3Features": top3_names, "normalizedEntropy": norm_entropy},
                    recommendation="Evaluate feature pruning or test whether peripheral features can be safely removed to reduce pipeline complexity.",
                )
            )
        elif top3_share >= cfg["top3_share_warning"]:
            top3_names = [item["feature"] for item in global_importance_list[:3]]
            issues.append(
                DiagnosticIssue(
                    id="EXPLAIN_MODERATE_CONCENTRATION_TOP3",
                    category=self.category,
                    severity=SeverityLevel.WARNING,
                    title=f"Top 3 Features Account for {top3_share * 100:.1f}% of Attribution",
                    description=f"Top 3 features ({', '.join(top3_names)}) hold majority decision weight in the model.",
                    affected_features=top3_names,
                    evidence={"top3Share": top3_share, "normalizedEntropy": norm_entropy},
                    recommendation="Monitor attribution distribution over time to detect shifts in feature importance.",
                )
            )

        # C. Identifier-Like Feature Dominance Finding
        for item in global_importance_list[:3]:
            feat = item["feature"]
            is_identifier = any(pattern.match(feat) for pattern in self.IDENTIFIER_PATTERNS)
            if is_identifier and (item["meanAbsShap"] / (total_importance + 1e-12)) >= cfg["identifier_share_warning"]:
                feat_share = round(item["meanAbsShap"] / (total_importance + 1e-12), 4)
                issues.append(
                    DiagnosticIssue(
                        id=f"EXPLAIN_IDENTIFIER_DOMINANCE_{feat}",
                        category=self.category,
                        severity=SeverityLevel.HIGH,
                        title=f"Identifier-Like Feature '{feat}' Exhibits High Model Attribution ({feat_share * 100:.1f}%)",
                        description=(
                            f"Feature '{feat}' matches identifier/hash nomenclature and accounts for {feat_share * 100:.1f}% of model attribution. "
                            f"Identifier features in tree models frequently indicate severe memorization, ID-based proxy behavior, or target leakage."
                        ),
                        affected_features=[feat],
                        evidence={"feature": feat, "attributionShare": feat_share, "rank": item["rank"]},
                        recommendation="Forensically audit this feature for target leakage or remove it from training feature sets.",
                    )
                )

        # D. Importance Rank Disagreement Finding
        if spearman_corr is not None:
            if spearman_corr < cfg["rank_disagreement_critical"]:
                issues.append(
                    DiagnosticIssue(
                        id="EXPLAIN_SEVERE_RANK_DISAGREEMENT",
                        category=self.category,
                        severity=SeverityLevel.HIGH,
                        title=f"Severe Disagreement Between SHAP and Permutation Importance (ρ={spearman_corr:.2f})",
                        description=(
                            f"TreeSHAP and Permutation Importance rankings exhibit low rank correlation (Spearman ρ={spearman_corr:.2f}). "
                            f"This divergence typically signals heavy feature collinearity or complex interaction effects where marginal and conditional attributions disagree."
                        ),
                        affected_features=feature_cols[:5],
                        evidence={"spearmanCorrelation": spearman_corr, "methodA": method_used, "methodB": "PERMUTATION_IMPORTANCE"},
                        recommendation="Analyze feature correlation matrix and collinear pairs to resolve attribution ambiguity.",
                    )
                )
            elif spearman_corr < cfg["rank_disagreement_warning"]:
                issues.append(
                    DiagnosticIssue(
                        id="EXPLAIN_MODERATE_RANK_DISAGREEMENT",
                        category=self.category,
                        severity=SeverityLevel.WARNING,
                        title=f"Moderate Ranking Divergence (Spearman ρ={spearman_corr:.2f})",
                        description=f"Attribution rankings diverge moderately between TreeSHAP and permutation importance (ρ={spearman_corr:.2f}).",
                        affected_features=feature_cols[:5],
                        evidence={"spearmanCorrelation": spearman_corr},
                        recommendation="Inspect collinear feature clusters for correlated attribution sharing.",
                    )
                )

        # E. Constant Feature Attribution Finding
        for item in global_importance_list:
            feat = item["feature"]
            if X_sample[feat].nunique() <= 1 and item["meanAbsShap"] > 0.01:
                issues.append(
                    DiagnosticIssue(
                        id=f"EXPLAIN_CONSTANT_FEATURE_ATTRIBUTION_{feat}",
                        category=self.category,
                        severity=SeverityLevel.WARNING,
                        title=f"Constant Feature '{feat}' Holds Model Attribution",
                        description=f"Feature '{feat}' has zero variance in the evaluation dataset but was assigned mean |SHAP| of {item['meanAbsShap']:.4f}.",
                        affected_features=[feat],
                        evidence={"feature": feat, "meanAbsShap": item["meanAbsShap"]},
                        recommendation="Prune constant features to streamline model inference.",
                    )
                )

        # 12. Diagnostic Metrics
        if global_importance_list:
            metrics_list.append(
                DiagnosticMetric(
                    name="top_feature_attribution_share",
                    value=float(top1_share),
                    threshold_max=cfg["top_feature_share_critical"],
                    passed=bool(top1_share < cfg["top_feature_share_critical"]),
                )
            )
            metrics_list.append(
                DiagnosticMetric(
                    name="top3_attribution_share",
                    value=float(top3_share),
                    threshold_max=cfg["top3_share_critical"],
                    passed=bool(top3_share < cfg["top3_share_critical"]),
                )
            )
        if spearman_corr is not None:
            metrics_list.append(
                DiagnosticMetric(
                    name="importance_agreement_spearman",
                    value=float(spearman_corr),
                    threshold_min=cfg["rank_disagreement_warning"],
                    passed=bool(spearman_corr >= cfg["rank_disagreement_warning"]),
                )
            )
        metrics_list.append(
            DiagnosticMetric(
                name="attribution_normalized_entropy",
                value=float(norm_entropy),
                threshold_min=0.30,
                passed=bool(norm_entropy >= 0.30),
            )
        )

        # 13. Health Score Calculation
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
            "method": method_used,
            "modelName": model_name,
            "modelFramework": model_adapter.framework,
            "taskType": model_adapter.task_type,
            "sampleCount": sample_count,
            "rawSampleCount": raw_row_count,
            "featureCount": len(feature_cols),
            "topFeature": top_feature_name,
            "topFeatureMeanAbsShap": top_feature_shap,
            "top1AttributionShare": top1_share,
            "top3AttributionShare": top3_share,
            "top5AttributionShare": top5_share,
            "top10AttributionShare": top10_share,
            "normalizedEntropy": norm_entropy,
            "importanceAgreementSpearman": spearman_corr,
            "baseValue": round(base_value, 4),
            "findingCount": len(issues),
            "healthScore": health_score,
            "passed": passed,
        }

        explanation_context = {
            "method": method_used,
            "modelOutput": model_output_space,
            "baseValue": round(base_value, 4),
            "sampleCount": sample_count,
            "rawSampleCount": raw_row_count,
            "samplingMethod": "deterministic_random" if raw_row_count > max_samples else "full_dataset",
            "randomState": random_state,
        }

        metadata_dict = {
            "module": "EXPLAINABILITY",
            "version": "1.0",
            "summary": summary_dict,
            "explanationContext": explanation_context,
            "featuresEvaluated": feature_cols,
            "globalImportance": global_importance_list,
            "permutationImportance": permutation_importance_list,
            "importanceAgreement": importance_agreement,
            "concentration": concentration,
            "localExplanations": local_explanations,
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
