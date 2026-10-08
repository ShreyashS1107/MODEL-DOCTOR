import logging
import math
import time
from typing import Any, Dict, List, Optional, Tuple, Union
import numpy as np
import pandas as pd
from scipy import stats
from sklearn import metrics

from app.diagnostics.base import BaseDiagnosticEngine
from app.schemas.diagnostic_models import (
    DiagnosticCategory,
    DiagnosticIssue,
    DiagnosticMetric,
    DiagnosticReport,
    SeverityLevel,
)

logger = logging.getLogger(__name__)


class ErrorForensicsEngine(BaseDiagnosticEngine):
    """
    Forensic Performance & Prediction Error Diagnostic Engine.
    Performs deterministic record-level and segment-level failure decomposition:
    - Canonical record evaluation table (TP, TN, FP, FN, confidence, probability error)
    - Error type distribution (counts, rates, confusion validation)
    - Confidence-error analysis (mean/median/p90/max confidence, high-confidence error share)
    - False Positive forensics (FP vs TN feature separation, Cohen's d, Mann-Whitney U, FDR)
    - False Negative forensics (FN vs TP feature separation, Cohen's d, Mann-Whitney U, FDR)
    - Feature-error association (Point-biserial r, Cramer's V, Benjamini-Hochberg FDR)
    - Error rate by feature quantile ranges (5 quantile bins with min sample guards)
    - Bounded high-priority error records (ranked by forensic priority score)
    - Threshold sensitivity forensics (21-point operating grid trade-offs)
    - Probability calibration error forensics (10-bin reliability vs error concentration)
    - Subgroup error forensics (95% Wilson confidence intervals across protected slices)
    """

    DEFAULT_CONFIG: Dict[str, Any] = {
        "default_threshold": 0.50,
        "high_confidence_threshold": 0.75,
        "min_bin_sample_size": 30,
        "max_analyzed_features": 50,
        "max_error_records": 50,
        "num_calibration_bins": 10,
        "num_quantile_bins": 5,
        "high_conf_error_rate_critical": 0.20,
        "high_conf_error_rate_warning": 0.10,
        "error_rate_critical": 0.40,
        "error_rate_warning": 0.20,
        "subgroup_disparity_critical": 1.50,
        "subgroup_disparity_warning": 1.25,
    }

    @property
    def category(self) -> DiagnosticCategory:
        return DiagnosticCategory.ERROR_FORENSICS

    @property
    def name(self) -> str:
        return "Performance & Error Forensics Engine"

    @property
    def description(self) -> str:
        return (
            "Performs record-level and segment-level failure decomposition, confidence-error analysis, "
            "FP/FN feature separation, quantile error ranges, threshold tradeoffs, and subgroup error disparities."
        )

    # -------------------------------------------------------------------------
    # Statistical Utility Functions
    # -------------------------------------------------------------------------

    @staticmethod
    def _map_target_to_binary(series: pd.Series, positive_class_cfg: Optional[Any] = None) -> Tuple[np.ndarray, Any, Any]:
        """Maps target series deterministically to integer binary array {0, 1}."""
        clean_s = series.dropna()
        unique_vals = clean_s.unique()
        if len(unique_vals) == 0:
            raise ValueError("Target column contains 0 non-null values.")
        if len(unique_vals) == 1:
            return np.ones(len(clean_s), dtype=int), unique_vals[0], None

        val1, val2 = unique_vals[0], unique_vals[1]
        if positive_class_cfg is not None and positive_class_cfg in unique_vals:
            pos_label = positive_class_cfg
            neg_label = val2 if val1 == pos_label else val1
        else:
            val_set = {val1, val2}
            if val_set in [{0, 1}, {0.0, 1.0}, {"0", "1"}]:
                pos_label = 1 if 1 in val_set else (1.0 if 1.0 in val_set else "1")
                neg_label = 0 if 0 in val_set else (0.0 if 0.0 in val_set else "0")
            elif val_set in [{False, True}, {"False", "True"}, {"false", "true"}]:
                pos_label = True if True in val_set else ("True" if "True" in val_set else "true")
                neg_label = False if False in val_set else ("False" if "False" in val_set else "false")
            elif val_set in [{"N", "Y"}, {"NO", "YES"}, {"no", "yes"}]:
                pos_label = "Y" if "Y" in val_set else ("YES" if "YES" in val_set else "yes")
                neg_label = "N" if "N" in val_set else ("NO" if "NO" in val_set else "no")
            elif val_set == {"negative", "positive"}:
                pos_label = "positive"
                neg_label = "negative"
            else:
                sorted_vals = sorted(list(unique_vals), key=lambda x: str(x))
                neg_label = sorted_vals[0]
                pos_label = sorted_vals[1]

        binary_arr = (clean_s == pos_label).astype(int).to_numpy()
        return binary_arr, pos_label, neg_label

    @staticmethod
    def _benjamini_hochberg_fdr(p_values: List[float]) -> List[float]:
        """Calculates Benjamini-Hochberg FDR adjusted p-values."""
        m = len(p_values)
        if m == 0:
            return []
        indexed_p = sorted(enumerate(p_values), key=lambda x: x[1])
        adj_p = [1.0] * m
        min_adj = 1.0
        for rank_idx in range(m - 1, -1, -1):
            orig_idx, p_val = indexed_p[rank_idx]
            rank = rank_idx + 1
            calculated = min(1.0, (p_val * m) / rank)
            min_adj = min(min_adj, calculated)
            adj_p[orig_idx] = round(float(min_adj), 6)
        return adj_p

    @staticmethod
    def _calculate_wilson_ci(k: int, n: int, z: float = 1.95996) -> Dict[str, float]:
        """Calculates 95% Wilson score confidence interval for a binomial proportion."""
        if n <= 0:
            return {"lower": 0.0, "upper": 0.0}
        p = k / n
        denominator = 1.0 + (z ** 2) / n
        center = (p + (z ** 2) / (2 * n)) / denominator
        margin = (z * math.sqrt((p * (1.0 - p) / n) + ((z ** 2) / (4 * (n ** 2))))) / denominator
        return {
            "lower": round(max(0.0, center - margin), 4),
            "upper": round(min(1.0, center + margin), 4),
        }

    @staticmethod
    def _cohens_d(group1: np.ndarray, group2: np.ndarray) -> float:
        """Calculates standardized mean difference (Cohen's d)."""
        n1, n2 = len(group1), len(group2)
        if n1 < 2 or n2 < 2:
            return 0.0
        m1, m2 = np.mean(group1), np.mean(group2)
        v1, v2 = np.var(group1, ddof=1), np.var(group2, ddof=1)
        pooled_std = math.sqrt(((n1 - 1) * v1 + (n2 - 1) * v2) / (n1 + n2 - 2))
        if pooled_std < 1e-9:
            return 0.0
        return round(float((m1 - m2) / pooled_std), 4)

    @staticmethod
    def _cramers_v(cat_series: pd.Series, binary_target: np.ndarray) -> float:
        """Calculates Cramer's V association between a categorical feature and binary target."""
        valid_mask = ~cat_series.isna()
        if valid_mask.sum() < 2:
            return 0.0
        contingency = pd.crosstab(cat_series[valid_mask], binary_target[valid_mask])
        if contingency.size == 0 or contingency.shape[0] < 2:
            return 0.0
        try:
            chi2, _, _, _ = stats.chi2_contingency(contingency, correction=False)
            n = contingency.sum().sum()
            min_dim = min(contingency.shape) - 1
            if n == 0 or min_dim == 0:
                return 0.0
            v = math.sqrt(chi2 / (n * min_dim))
            return round(min(1.0, float(v)), 4)
        except Exception:
            return 0.0

    # -------------------------------------------------------------------------
    # Main Forensic Diagnostic Execution
    # -------------------------------------------------------------------------

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

        # 1. Dataset & Target Column Validation
        if current_data is None or not isinstance(current_data, pd.DataFrame) or len(current_data) == 0:
            issue = DiagnosticIssue(
                id="ERR-EMPTY-DATASET",
                category=self.category,
                severity=SeverityLevel.CRITICAL,
                title="Evaluation Dataset is Empty or Null",
                description="Error forensics requires a populated evaluation dataset with ground truth and model predictions.",
                affected_features=[target_column],
                evidence={"rowCount": 0},
                recommendation="Provide a valid, non-empty evaluation dataset partition.",
            )
            exec_time_ms = round((time.perf_counter() - start_time) * 1000, 2)
            return DiagnosticReport(
                engine_name=self.name,
                category=self.category,
                health_score=0.0,
                passed=False,
                execution_time_ms=exec_time_ms,
                issues=[issue],
                metadata={"module": "ERROR_FORENSICS", "summary": {"error": "Empty dataset", "healthScore": 0.0, "passed": False}},
            )

        if target_column not in current_data.columns:
            issue = DiagnosticIssue(
                id="ERR-TARGET-MISSING",
                category=self.category,
                severity=SeverityLevel.CRITICAL,
                title=f"Target Column '{target_column}' Missing",
                description=f"Specified target column '{target_column}' was not found in evaluation dataset.",
                affected_features=[target_column],
                evidence={"availableColumns": list(current_data.columns[:10])},
                recommendation=f"Verify that column '{target_column}' is present in evaluation dataset.",
            )
            exec_time_ms = round((time.perf_counter() - start_time) * 1000, 2)
            return DiagnosticReport(
                engine_name=self.name,
                category=self.category,
                health_score=0.0,
                passed=False,
                execution_time_ms=exec_time_ms,
                issues=[issue],
                metadata={"module": "ERROR_FORENSICS", "summary": {"error": "Missing target column", "healthScore": 0.0, "passed": False}},
            )

        # 2. Prediction Column Resolution
        pred_col = cfg.get("prediction_column")
        if not pred_col:
            candidate_cols = ["pred_prob", "prediction", "probability", "score", "pred", "y_pred_prob", "y_score"]
            for cand in candidate_cols:
                if cand in current_data.columns:
                    pred_col = cand
                    break

        if not pred_col or pred_col not in current_data.columns:
            issue = DiagnosticIssue(
                id="ERR-MISSING-PREDICTION",
                category=self.category,
                severity=SeverityLevel.CRITICAL,
                title="Model Prediction Column Missing in Dataset",
                description=f"Model prediction probability column '{pred_col or 'pred_prob'}' was not found in evaluation dataset.",
                affected_features=[str(pred_col or "pred_prob")],
                evidence={"predictionColumn": pred_col, "availableColumns": list(current_data.columns[:10])},
                recommendation="Ensure evaluation partition contains continuous model probability predictions (e.g., 'pred_prob').",
            )
            exec_time_ms = round((time.perf_counter() - start_time) * 1000, 2)
            return DiagnosticReport(
                engine_name=self.name,
                category=self.category,
                health_score=0.0,
                passed=False,
                execution_time_ms=exec_time_ms,
                issues=[issue],
                metadata={"module": "ERROR_FORENSICS", "summary": {"error": "Missing prediction column", "healthScore": 0.0, "passed": False}},
            )

        # 3. Clean & Validate Observation Pairs
        target_series = current_data[target_column]
        pred_numeric = pd.to_numeric(current_data[pred_col], errors="coerce")
        valid_mask = ~target_series.isna() & ~pred_numeric.isna() & ~np.isinf(pred_numeric) & (pred_numeric >= 0.0) & (pred_numeric <= 1.0)
        df_valid = current_data[valid_mask].copy().reset_index(drop=False)
        valid_sample_count = len(df_valid)

        if valid_sample_count < 2:
            issue = DiagnosticIssue(
                id="ERR-INSUFFICIENT-SAMPLES",
                category=self.category,
                severity=SeverityLevel.CRITICAL,
                title="Insufficient Valid Observations for Error Forensics",
                description=f"Evaluation dataset has only {valid_sample_count} valid paired observation(s). Minimum 2 required.",
                affected_features=[target_column, pred_col],
                evidence={"validSamples": valid_sample_count, "rawRows": len(current_data)},
                recommendation="Provide non-empty evaluation observations containing valid ground truth and predictions.",
            )
            exec_time_ms = round((time.perf_counter() - start_time) * 1000, 2)
            return DiagnosticReport(
                engine_name=self.name,
                category=self.category,
                health_score=0.0,
                passed=False,
                execution_time_ms=exec_time_ms,
                issues=[issue],
                metadata={"module": "ERROR_FORENSICS", "summary": {"error": "Insufficient valid observations", "healthScore": 0.0, "passed": False}},
            )

        # 4. Map Target and Construct Canonical Evaluation Table
        y_true, pos_label, neg_label = self._map_target_to_binary(df_valid[target_column])
        y_prob = df_valid[pred_col].to_numpy(dtype=float)
        decision_threshold = float(cfg.get("default_threshold", 0.50))
        y_pred = (y_prob >= decision_threshold).astype(int)
        is_error = (y_true != y_pred).astype(int)
        is_correct = (y_true == y_pred)

        # Confidence: max(p, 1-p) for binary classifier
        confidences = np.where(y_pred == 1, y_prob, 1.0 - y_prob)
        prob_errors = np.abs(y_true - y_prob)
        margin_distances = np.abs(y_prob - decision_threshold)

        # Assign error type labels
        error_types = []
        for yt, yp in zip(y_true, y_pred):
            if yt == 1 and yp == 1:
                error_types.append("TRUE_POSITIVE")
            elif yt == 0 and yp == 0:
                error_types.append("TRUE_NEGATIVE")
            elif yt == 0 and yp == 1:
                error_types.append("FALSE_POSITIVE")
            else:
                error_types.append("FALSE_NEGATIVE")

        # 5. Error Type Distribution & Primary Metrics
        tp_count = int(np.sum((y_true == 1) & (y_pred == 1)))
        tn_count = int(np.sum((y_true == 0) & (y_pred == 0)))
        fp_count = int(np.sum((y_true == 0) & (y_pred == 1)))
        fn_count = int(np.sum((y_true == 1) & (y_pred == 0)))
        total_errors = fp_count + fn_count
        overall_error_rate = round(float(total_errors / valid_sample_count), 4)
        accuracy = round(float((tp_count + tn_count) / valid_sample_count), 4)

        pos_ground_truth_count = tp_count + fn_count
        neg_ground_truth_count = tn_count + fp_count

        fp_rate = round(float(fp_count / neg_ground_truth_count), 4) if neg_ground_truth_count > 0 else 0.0
        fn_rate = round(float(fn_count / pos_ground_truth_count), 4) if pos_ground_truth_count > 0 else 0.0
        precision = round(float(tp_count / (tp_count + fp_count)), 4) if (tp_count + fp_count) > 0 else 0.0
        recall = round(float(tp_count / pos_ground_truth_count), 4) if pos_ground_truth_count > 0 else 0.0
        f1_score = round(float(2 * precision * recall / (precision + recall)), 4) if (precision + recall) > 0 else 0.0

        error_summary = {
            "sampleCount": valid_sample_count,
            "totalErrors": total_errors,
            "overallErrorRate": overall_error_rate,
            "accuracy": accuracy,
            "truePositive": {
                "count": tp_count,
                "rateOfTotal": round(tp_count / valid_sample_count, 4),
                "rateOfActualPositive": round(tp_count / pos_ground_truth_count, 4) if pos_ground_truth_count > 0 else 0.0,
            },
            "trueNegative": {
                "count": tn_count,
                "rateOfTotal": round(tn_count / valid_sample_count, 4),
                "rateOfActualNegative": round(tn_count / neg_ground_truth_count, 4) if neg_ground_truth_count > 0 else 0.0,
            },
            "falsePositive": {
                "count": fp_count,
                "rateOfTotal": round(fp_count / valid_sample_count, 4),
                "falsePositiveRate": fp_rate,
                "meanProbability": round(float(np.mean(y_prob[(y_true == 0) & (y_pred == 1)])), 4) if fp_count > 0 else 0.0,
            },
            "falseNegative": {
                "count": fn_count,
                "rateOfTotal": round(fn_count / valid_sample_count, 4),
                "falseNegativeRate": fn_rate,
                "meanProbability": round(float(np.mean(y_prob[(y_true == 1) & (y_pred == 0)])), 4) if fn_count > 0 else 0.0,
            },
            "decisionThreshold": decision_threshold,
            "positiveLabel": str(pos_label),
            "negativeLabel": str(neg_label),
        }

        # 6. Confidence-Error Decomposition
        err_mask = (is_error == 1)
        corr_mask = (is_error == 0)

        err_conf = confidences[err_mask] if total_errors > 0 else np.array([])
        corr_conf = confidences[corr_mask] if (valid_sample_count - total_errors) > 0 else np.array([])

        mean_err_conf = round(float(np.mean(err_conf)), 4) if len(err_conf) > 0 else 0.0
        median_err_conf = round(float(np.median(err_conf)), 4) if len(err_conf) > 0 else 0.0
        p90_err_conf = round(float(np.percentile(err_conf, 90)), 4) if len(err_conf) > 0 else 0.0
        max_err_conf = round(float(np.max(err_conf)), 4) if len(err_conf) > 0 else 0.0

        # Confidence tiers for errors
        high_conf_thresh = float(cfg.get("high_confidence_threshold", 0.75))
        hce_mask = err_mask & (confidences >= high_conf_thresh)
        hce_count = int(np.sum(hce_mask))
        hce_rate = round(float(hce_count / valid_sample_count), 4)
        hce_share = round(float(hce_count / total_errors), 4) if total_errors > 0 else 0.0

        conf_bands = [
            {"tier": "LOW", "minConfidence": 0.50, "maxConfidence": 0.60, "errorCount": int(np.sum(err_mask & (confidences >= 0.50) & (confidences < 0.60)))},
            {"tier": "MODERATE", "minConfidence": 0.60, "maxConfidence": 0.75, "errorCount": int(np.sum(err_mask & (confidences >= 0.60) & (confidences < 0.75)))},
            {"tier": "HIGH", "minConfidence": 0.75, "maxConfidence": 0.90, "errorCount": int(np.sum(err_mask & (confidences >= 0.75) & (confidences < 0.90)))},
            {"tier": "VERY_HIGH", "minConfidence": 0.90, "maxConfidence": 1.00, "errorCount": int(np.sum(err_mask & (confidences >= 0.90) & (confidences <= 1.00)))},
        ]
        for b in conf_bands:
            b["errorShare"] = round(float(b["errorCount"] / total_errors), 4) if total_errors > 0 else 0.0

        confidence_analysis = {
            "meanErrorConfidence": mean_err_conf,
            "medianErrorConfidence": median_err_conf,
            "p90ErrorConfidence": p90_err_conf,
            "maxErrorConfidence": max_err_conf,
            "meanCorrectConfidence": round(float(np.mean(corr_conf)), 4) if len(corr_conf) > 0 else 0.0,
            "highConfidenceThreshold": high_conf_thresh,
            "highConfidenceErrorCount": hce_count,
            "highConfidenceErrorRate": hce_rate,
            "highConfidenceErrorShare": hce_share,
            "confidenceBands": conf_bands,
        }

        # 7. FP Forensics (FP vs TN) & FN Forensics (FN vs TP)
        candidate_features = [
            c for c in df_valid.columns
            if c not in [target_column, pred_col, "index", "level_0"]
        ][: int(cfg.get("max_analyzed_features", 50))]

        fp_records_mask = (y_true == 0) & (y_pred == 1)
        tn_records_mask = (y_true == 0) & (y_pred == 0)
        fn_records_mask = (y_true == 1) & (y_pred == 0)
        tp_records_mask = (y_true == 1) & (y_pred == 1)

        fp_feature_diffs: List[Dict[str, Any]] = []
        fn_feature_diffs: List[Dict[str, Any]] = []
        overall_feature_assocs: List[Dict[str, Any]] = []

        raw_fp_pvals = []
        raw_fn_pvals = []
        raw_assoc_pvals = []

        for feat in candidate_features:
            col_series = df_valid[feat]
            is_numeric = pd.api.types.is_numeric_dtype(col_series)

            # --- FP vs TN Separation ---
            if fp_count > 0 and tn_count > 0:
                if is_numeric:
                    fp_vals = col_series[fp_records_mask].dropna().to_numpy()
                    tn_vals = col_series[tn_records_mask].dropna().to_numpy()
                    if len(fp_vals) >= 2 and len(tn_vals) >= 2:
                        d = self._cohens_d(fp_vals, tn_vals)
                        try:
                            _, mwu_p = stats.mannwhitneyu(fp_vals, tn_vals, alternative="two-sided")
                        except Exception:
                            mwu_p = 1.0
                        raw_fp_pvals.append(mwu_p)
                        fp_feature_diffs.append({
                            "feature": feat,
                            "type": "numeric",
                            "fpMean": round(float(np.mean(fp_vals)), 4),
                            "tnMean": round(float(np.mean(tn_vals)), 4),
                            "standardizedDifference": d,
                            "absoluteSeparation": abs(d),
                            "rawPValue": round(float(mwu_p), 6),
                        })
                else:
                    # Categorical distribution difference
                    v = self._cramers_v(col_series[fp_records_mask | tn_records_mask], (y_pred[fp_records_mask | tn_records_mask] == 1).astype(int))
                    raw_fp_pvals.append(1.0)
                    fp_feature_diffs.append({
                        "feature": feat,
                        "type": "categorical",
                        "cramersV": v,
                        "absoluteSeparation": v,
                        "rawPValue": 1.0,
                    })

            # --- FN vs TP Separation ---
            if fn_count > 0 and tp_count > 0:
                if is_numeric:
                    fn_vals = col_series[fn_records_mask].dropna().to_numpy()
                    tp_vals = col_series[tp_records_mask].dropna().to_numpy()
                    if len(fn_vals) >= 2 and len(tp_vals) >= 2:
                        d = self._cohens_d(fn_vals, tp_vals)
                        try:
                            _, mwu_p = stats.mannwhitneyu(fn_vals, tp_vals, alternative="two-sided")
                        except Exception:
                            mwu_p = 1.0
                        raw_fn_pvals.append(mwu_p)
                        fn_feature_diffs.append({
                            "feature": feat,
                            "type": "numeric",
                            "fnMean": round(float(np.mean(fn_vals)), 4),
                            "tpMean": round(float(np.mean(tp_vals)), 4),
                            "standardizedDifference": d,
                            "absoluteSeparation": abs(d),
                            "rawPValue": round(float(mwu_p), 6),
                        })
                else:
                    v = self._cramers_v(col_series[fn_records_mask | tp_records_mask], (y_pred[fn_records_mask | tp_records_mask] == 0).astype(int))
                    raw_fn_pvals.append(1.0)
                    fn_feature_diffs.append({
                        "feature": feat,
                        "type": "categorical",
                        "cramersV": v,
                        "absoluteSeparation": v,
                        "rawPValue": 1.0,
                    })

            # --- Overall Feature-Error Association ---
            if total_errors > 0 and (valid_sample_count - total_errors) > 0:
                if is_numeric:
                    valid_feat_mask = ~col_series.isna()
                    f_clean = col_series[valid_feat_mask].to_numpy(dtype=float)
                    err_clean = is_error[valid_feat_mask]
                    if len(f_clean) >= 5 and np.std(f_clean) > 1e-9:
                        try:
                            r_pb, p_val = stats.pointbiserialr(err_clean, f_clean)
                            if math.isnan(r_pb):
                                r_pb, p_val = 0.0, 1.0
                        except Exception:
                            r_pb, p_val = 0.0, 1.0
                        raw_assoc_pvals.append(p_val)
                        overall_feature_assocs.append({
                            "feature": feat,
                            "type": "numeric",
                            "statistic": "point_biserial_r",
                            "value": round(float(r_pb), 4),
                            "absoluteAssociation": round(abs(float(r_pb)), 4),
                            "rawPValue": round(float(p_val), 6),
                        })
                else:
                    v = self._cramers_v(col_series, is_error)
                    raw_assoc_pvals.append(1.0)
                    overall_feature_assocs.append({
                        "feature": feat,
                        "type": "categorical",
                        "statistic": "cramers_v",
                        "value": v,
                        "absoluteAssociation": v,
                        "rawPValue": 1.0,
                    })

        # Apply Benjamini-Hochberg FDR correction
        adj_fp_pvals = self._benjamini_hochberg_fdr(raw_fp_pvals)
        for idx, item in enumerate(fp_feature_diffs):
            item["adjustedPValue"] = adj_fp_pvals[idx] if idx < len(adj_fp_pvals) else 1.0

        adj_fn_pvals = self._benjamini_hochberg_fdr(raw_fn_pvals)
        for idx, item in enumerate(fn_feature_diffs):
            item["adjustedPValue"] = adj_fn_pvals[idx] if idx < len(adj_fn_pvals) else 1.0

        adj_assoc_pvals = self._benjamini_hochberg_fdr(raw_assoc_pvals)
        for idx, item in enumerate(overall_feature_assocs):
            item["adjustedPValue"] = adj_assoc_pvals[idx] if idx < len(adj_assoc_pvals) else 1.0

        # Deterministic sorting
        fp_feature_diffs.sort(key=lambda x: (-x.get("absoluteSeparation", 0.0), x["feature"]))
        fn_feature_diffs.sort(key=lambda x: (-x.get("absoluteSeparation", 0.0), x["feature"]))
        overall_feature_assocs.sort(key=lambda x: (-x.get("absoluteAssociation", 0.0), x["feature"]))

        # 8. Error Rate by Numeric Feature Ranges (Quantile Bins)
        feature_ranges: List[Dict[str, Any]] = []
        top_range_features = [f["feature"] for f in overall_feature_assocs if f["type"] == "numeric"][:5]

        for feat in top_range_features:
            series = df_valid[feat].dropna()
            if len(series) < 30 or series.nunique() < 5:
                continue

            try:
                # 5 quantile bins
                quantiles = np.linspace(0, 1, int(cfg.get("num_quantile_bins", 5)) + 1)
                bin_edges = np.unique(np.quantile(series, quantiles))
                if len(bin_edges) < 3:
                    continue

                for b_idx in range(len(bin_edges) - 1):
                    low, high = bin_edges[b_idx], bin_edges[b_idx + 1]
                    if b_idx == len(bin_edges) - 2:
                        bin_mask = (df_valid[feat] >= low) & (df_valid[feat] <= high)
                        r_str = f"[{low:.2f}, {high:.2f}]"
                    else:
                        bin_mask = (df_valid[feat] >= low) & (df_valid[feat] < high)
                        r_str = f"[{low:.2f}, {high:.2f})"

                    n_bin = int(bin_mask.sum())
                    if n_bin == 0:
                        continue

                    bin_errors = int(is_error[bin_mask].sum())
                    bin_err_rate = round(float(bin_errors / n_bin), 4)
                    bin_fp = int(np.sum(bin_mask & fp_records_mask))
                    bin_fn = int(np.sum(bin_mask & fn_records_mask))
                    bin_mean_prob = round(float(np.mean(y_prob[bin_mask])), 4)
                    bin_mean_conf = round(float(np.mean(confidences[bin_mask])), 4)

                    is_enriched = (bin_err_rate >= overall_error_rate * 1.35) if overall_error_rate > 0 else False
                    insufficient_sample = (n_bin < int(cfg.get("min_bin_sample_size", 30)))

                    feature_ranges.append({
                        "feature": feat,
                        "binIndex": b_idx,
                        "rangeDisplay": r_str,
                        "lowerBound": round(float(low), 4),
                        "upperBound": round(float(high), 4),
                        "sampleCount": n_bin,
                        "errorCount": bin_errors,
                        "errorRate": bin_err_rate,
                        "falsePositiveCount": bin_fp,
                        "falseNegativeCount": bin_fn,
                        "meanPredictedProbability": bin_mean_prob,
                        "meanConfidence": bin_mean_conf,
                        "isErrorEnriched": is_enriched,
                        "insufficientSample": insufficient_sample,
                    })
            except Exception as e:
                logger.debug("Failed quantile binning for feature '%s': %s", feat, str(e))

        # 9. Bounded High-Priority Forensic Error Records
        error_records_list: List[Dict[str, Any]] = []
        top_corr_features = [f["feature"] for f in overall_feature_assocs[:4]]

        for i in range(valid_sample_count):
            if is_error[i] == 1:
                p_val = float(y_prob[i])
                conf = float(confidences[i])
                # Priority score: Higher confidence error = higher investigative priority
                priority_score = round(conf * 50.0 + abs(p_val - decision_threshold) * 30.0 + prob_errors[i] * 20.0, 2)
                
                key_feat_dict = {}
                for kf in top_corr_features:
                    val = df_valid[kf].iloc[i]
                    if pd.isna(val):
                        key_feat_dict[kf] = None
                    elif isinstance(val, (int, np.integer)):
                        key_feat_dict[kf] = int(val)
                    elif isinstance(val, (float, np.floating)):
                        key_feat_dict[kf] = round(float(val), 4)
                    else:
                        key_feat_dict[kf] = str(val)

                original_idx = int(df_valid["index"].iloc[i]) if "index" in df_valid.columns else i
                error_records_list.append({
                    "rowIndex": original_idx,
                    "actual": int(y_true[i]),
                    "predicted": int(y_pred[i]),
                    "probability": round(p_val, 4),
                    "confidence": round(conf, 4),
                    "errorType": error_types[i],
                    "priorityScore": priority_score,
                    "probabilityError": round(float(prob_errors[i]), 4),
                    "keyFeatures": key_feat_dict,
                })

        # Sort error records deterministically by priority score descending
        error_records_list.sort(key=lambda x: (-x["priorityScore"], x["rowIndex"]))
        bounded_error_records = error_records_list[: int(cfg.get("max_error_records", 50))]

        # 10. Threshold Operating Point Forensics (21-point grid)
        threshold_grid: List[Dict[str, Any]] = []
        for t in np.linspace(0.0, 1.0, 21):
            t_val = round(float(t), 2)
            y_t_pred = (y_prob >= t_val).astype(int)
            t_tp = int(np.sum((y_true == 1) & (y_t_pred == 1)))
            t_tn = int(np.sum((y_true == 0) & (y_t_pred == 0)))
            t_fp = int(np.sum((y_true == 0) & (y_t_pred == 1)))
            t_fn = int(np.sum((y_true == 1) & (y_t_pred == 0)))
            t_prec = round(float(t_tp / (t_tp + t_fp)), 4) if (t_tp + t_fp) > 0 else (1.0 if t_val >= 0.99 else 0.0)
            t_rec = round(float(t_tp / pos_ground_truth_count), 4) if pos_ground_truth_count > 0 else 0.0
            t_f1 = round(float(2 * t_prec * t_rec / (t_prec + t_rec)), 4) if (t_prec + t_rec) > 0 else 0.0
            t_fpr = round(float(t_fp / neg_ground_truth_count), 4) if neg_ground_truth_count > 0 else 0.0
            t_fnr = round(float(t_fn / pos_ground_truth_count), 4) if pos_ground_truth_count > 0 else 0.0

            threshold_grid.append({
                "threshold": t_val,
                "truePositive": t_tp,
                "trueNegative": t_tn,
                "falsePositive": t_fp,
                "falseNegative": t_fn,
                "precision": t_prec,
                "recall": t_rec,
                "f1Score": t_f1,
                "falsePositiveRate": t_fpr,
                "falseNegativeRate": t_fnr,
            })

        # 11. Calibration-Error Forensics (10 uniform probability bins)
        num_cal_bins = int(cfg.get("num_calibration_bins", 10))
        cal_bin_edges = np.linspace(0.0, 1.0, num_cal_bins + 1)
        calibration_forensics_list: List[Dict[str, Any]] = []
        total_abs_cal_error = 0.0
        cal_bins_evaluated = 0

        for b in range(num_cal_bins):
            lower, upper = float(cal_bin_edges[b]), float(cal_bin_edges[b + 1])
            if b == num_cal_bins - 1:
                b_mask = (y_prob >= lower) & (y_prob <= upper)
                b_str = f"[{lower:.1f}, {upper:.1f}]"
            else:
                b_mask = (y_prob >= lower) & (y_prob < upper)
                b_str = f"[{lower:.1f}, {upper:.1f})"

            b_n = int(b_mask.sum())
            if b_n > 0:
                b_pos = int(y_true[b_mask].sum())
                b_obs_rate = round(float(b_pos / b_n), 4)
                b_mean_p = round(float(np.mean(y_prob[b_mask])), 4)
                b_cal_gap = round(abs(b_mean_p - b_obs_rate), 4)
                b_errors = int(is_error[b_mask].sum())
                b_err_rate = round(float(b_errors / b_n), 4)

                total_abs_cal_error += b_cal_gap * b_n
                cal_bins_evaluated += 1

                calibration_forensics_list.append({
                    "binIndex": b,
                    "rangeDisplay": b_str,
                    "sampleCount": b_n,
                    "meanPredictedProbability": b_mean_p,
                    "observedPositiveRate": b_obs_rate,
                    "absoluteCalibrationGap": b_cal_gap,
                    "errorCount": b_errors,
                    "errorRate": b_err_rate,
                    "isSevereGap": (b_cal_gap >= 0.10 and b_n >= 10),
                })

        expected_calibration_error = round(float(total_abs_cal_error / valid_sample_count), 4) if valid_sample_count > 0 else 0.0

        # 12. Subgroup Error Forensics
        subgroup_results: List[Dict[str, Any]] = []
        prot_attr = cfg.get("protected_attribute")
        max_subgroup_err_rate = overall_error_rate
        min_subgroup_err_rate = overall_error_rate

        if prot_attr and prot_attr in df_valid.columns:
            subgroup_err_rates = []
            for group_val, grp_df in df_valid.groupby(prot_attr):
                g_n = len(grp_df)
                if g_n < 5:
                    continue
                g_idx = grp_df.index
                g_y_true = y_true[g_idx]
                g_y_pred = y_pred[g_idx]
                g_errors = int(np.sum(g_y_true != g_y_pred))
                g_err_rate = round(float(g_errors / g_n), 4)
                subgroup_err_rates.append(g_err_rate)

                g_fp = int(np.sum((g_y_true == 0) & (g_y_pred == 1)))
                g_fn = int(np.sum((g_y_true == 1) & (g_y_pred == 0)))
                g_hce = int(np.sum((g_y_true != g_y_pred) & (confidences[g_idx] >= high_conf_thresh)))
                ci = self._calculate_wilson_ci(g_errors, g_n)

                subgroup_results.append({
                    "subgroup": str(group_val),
                    "sampleCount": g_n,
                    "errorCount": g_errors,
                    "errorRate": g_err_rate,
                    "falsePositiveCount": g_fp,
                    "falseNegativeCount": g_fn,
                    "highConfidenceErrorCount": g_hce,
                    "errorRateConfidenceInterval": ci,
                })

            if subgroup_err_rates:
                max_subgroup_err_rate = max(subgroup_err_rates)
                min_subgroup_err_rate = min(subgroup_err_rates)

        subgroup_disparity_ratio = round(float(max_subgroup_err_rate / (min_subgroup_err_rate if min_subgroup_err_rate > 0 else 0.01)), 2)

        # 13. Deterministic Forensic Findings & Health Score
        health_penalty = 0.0

        # Finding: High-Confidence Errors
        if hce_rate >= float(cfg.get("high_conf_error_rate_critical", 0.20)):
            health_penalty += 30.0
            issues.append(
                DiagnosticIssue(
                    id="ERR-HIGH-CONF-001",
                    category=self.category,
                    severity=SeverityLevel.CRITICAL,
                    title="Critical Concentration of High-Confidence Prediction Errors",
                    description=(
                        f"Found {hce_count} high-confidence errors ({hce_rate * 100:.1f}% of evaluated cohort). "
                        f"High-confidence mistakes account for {hce_share * 100:.1f}% of all observed errors."
                    ),
                    affected_features=[pred_col],
                    evidence={
                        "highConfidenceErrorCount": hce_count,
                        "highConfidenceErrorRate": hce_rate,
                        "highConfidenceErrorShare": hce_share,
                        "confidenceThreshold": high_conf_thresh,
                    },
                    recommendation="Audit high-confidence error cases to verify whether model is making aggressive decisions on noisy or out-of-distribution records.",
                )
            )
        elif hce_rate >= float(cfg.get("high_conf_error_rate_warning", 0.10)):
            health_penalty += 15.0
            issues.append(
                DiagnosticIssue(
                    id="ERR-HIGH-CONF-001",
                    category=self.category,
                    severity=SeverityLevel.WARNING,
                    title="Elevated Rate of High-Confidence Prediction Errors",
                    description=(
                        f"Observed {hce_count} high-confidence error(s) ({hce_rate * 100:.1f}% rate). "
                        f"Represents {hce_share * 100:.1f}% of total misclassifications."
                    ),
                    affected_features=[pred_col],
                    evidence={
                        "highConfidenceErrorCount": hce_count,
                        "highConfidenceErrorRate": hce_rate,
                        "highConfidenceErrorShare": hce_share,
                    },
                    recommendation="Inspect probability calibration and investigate feature combinations leading to false confidence.",
                )
            )

        # Finding: Top FP Feature Separation
        if fp_feature_diffs and fp_feature_diffs[0].get("absoluteSeparation", 0.0) >= 0.50:
            top_fp_feat = fp_feature_diffs[0]
            issues.append(
                DiagnosticIssue(
                    id="ERR-FP-SEPARATION-001",
                    category=self.category,
                    severity=SeverityLevel.WARNING,
                    title=f"Distinct Feature Separation in False Positive Records: {top_fp_feat['feature']}",
                    description=(
                        f"False positive records exhibit material separation along feature '{top_fp_feat['feature']}' "
                        f"(Standardized Effect Size: {top_fp_feat['absoluteSeparation']:.2f}, Adj p-val: {top_fp_feat.get('adjustedPValue', 1.0):.4f})."
                    ),
                    affected_features=[top_fp_feat["feature"]],
                    evidence=top_fp_feat,
                    recommendation="Examine whether feature values in False Positive records represent borderline cases or mislabeled ground truth.",
                )
            )

        # Finding: Top FN Feature Separation
        if fn_feature_diffs and fn_feature_diffs[0].get("absoluteSeparation", 0.0) >= 0.50:
            top_fn_feat = fn_feature_diffs[0]
            issues.append(
                DiagnosticIssue(
                    id="ERR-FN-SEPARATION-001",
                    category=self.category,
                    severity=SeverityLevel.WARNING,
                    title=f"Distinct Feature Separation in False Negative Records: {top_fn_feat['feature']}",
                    description=(
                        f"False negative records exhibit material separation along feature '{top_fn_feat['feature']}' "
                        f"(Standardized Effect Size: {top_fn_feat['absoluteSeparation']:.2f}, Adj p-val: {top_fn_feat.get('adjustedPValue', 1.0):.4f})."
                    ),
                    affected_features=[top_fn_feat["feature"]],
                    evidence=top_fn_feat,
                    recommendation="Investigate why positive cases with this feature profile are failing to activate model decision boundaries.",
                )
            )

        # Finding: Error-Associated Features
        sig_error_features = [f for f in overall_feature_assocs if f.get("adjustedPValue", 1.0) < 0.05 and f.get("absoluteAssociation", 0.0) >= 0.15]
        if sig_error_features:
            top_assoc = sig_error_features[0]
            issues.append(
                DiagnosticIssue(
                    id="ERR-FEATURE-ASSOCIATION-001",
                    category=self.category,
                    severity=SeverityLevel.HIGH if top_assoc["absoluteAssociation"] >= 0.30 else SeverityLevel.WARNING,
                    title=f"Statistically Significant Error Association for '{top_assoc['feature']}'",
                    description=(
                        f"Prediction errors are systematically concentrated along feature '{top_assoc['feature']}' "
                        f"(Association Metric: {top_assoc['statistic']} = {top_assoc['value']:.4f}, Benjamini-Hochberg FDR p < 0.05)."
                    ),
                    affected_features=[top_assoc["feature"]],
                    evidence=top_assoc,
                    recommendation="Feature is strongly associated with prediction failures; prioritize for error range inspection and cross-module drift/robustness analysis.",
                )
            )

        # Finding: Subgroup Disparity
        if subgroup_results and subgroup_disparity_ratio >= float(cfg.get("subgroup_disparity_critical", 1.50)):
            health_penalty += 20.0
            issues.append(
                DiagnosticIssue(
                    id="ERR-SUBGROUP-DISPARITY-001",
                    category=self.category,
                    severity=SeverityLevel.CRITICAL,
                    title="Severe Subgroup Error Disparity Detected",
                    description=(
                        f"Subgroup error rate disparity ratio is {subgroup_disparity_ratio:.2f}x between highest and lowest performing groups "
                        f"(Max Subgroup Error: {max_subgroup_err_rate * 100:.1f}%, Min: {min_subgroup_err_rate * 100:.1f}%)."
                    ),
                    affected_features=[str(prot_attr)],
                    evidence={
                        "protectedAttribute": prot_attr,
                        "disparityRatio": subgroup_disparity_ratio,
                        "maxErrorRate": max_subgroup_err_rate,
                        "minErrorRate": min_subgroup_err_rate,
                    },
                    recommendation="Review BIAS and fairness metrics to cross-examine subgroup error concentration and demographic parity gaps.",
                )
            )

        # Calculate category health score
        base_health = 100.0 - (overall_error_rate * 50.0) - health_penalty
        health_score = round(max(0.0, min(100.0, base_health)), 1)
        passed = health_score >= 60.0 and total_errors < (valid_sample_count * 0.50)

        # Telemetry metrics
        metrics_list.append(DiagnosticMetric(name="overall_error_rate", value=overall_error_rate, threshold_max=0.30, passed=overall_error_rate <= 0.30))
        metrics_list.append(DiagnosticMetric(name="high_confidence_error_rate", value=hce_rate, threshold_max=0.10, passed=hce_rate <= 0.10))
        metrics_list.append(DiagnosticMetric(name="expected_calibration_error", value=expected_calibration_error, threshold_max=0.10, passed=expected_calibration_error <= 0.10))

        exec_time_ms = round((time.perf_counter() - start_time) * 1000, 2)

        metadata_dict = {
            "module": "ERROR_FORENSICS",
            "version": "1.0",
            "sampleCount": valid_sample_count,
            "errorSummary": error_summary,
            "confidenceAnalysis": confidence_analysis,
            "falsePositiveAnalysis": {
                "count": fp_count,
                "rate": fp_rate,
                "topFeatureDifferences": fp_feature_diffs[:10],
            },
            "falseNegativeAnalysis": {
                "count": fn_count,
                "rate": fn_rate,
                "topFeatureDifferences": fn_feature_diffs[:10],
            },
            "featureAssociations": overall_feature_assocs[:20],
            "featureRanges": feature_ranges,
            "highConfidenceErrors": bounded_error_records,
            "thresholdAnalysis": threshold_grid,
            "calibrationForensics": {
                "expectedCalibrationError": expected_calibration_error,
                "bins": calibration_forensics_list,
            },
            "subgroupAnalysis": subgroup_results,
            "summary": {
                "sampleCount": valid_sample_count,
                "totalErrors": total_errors,
                "overallErrorRate": overall_error_rate,
                "highConfidenceErrorCount": hce_count,
                "highConfidenceErrorRate": hce_rate,
                "fpCount": fp_count,
                "fnCount": fn_count,
                "topErrorAssociatedFeature": overall_feature_assocs[0]["feature"] if overall_feature_assocs else None,
                "worstSubgroupDisparityRatio": subgroup_disparity_ratio if subgroup_results else None,
                "healthScore": health_score,
                "passed": passed,
            },
            "findings": [issue.model_dump() for issue in issues],
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
