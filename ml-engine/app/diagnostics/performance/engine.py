import logging
import time
from typing import Any, Dict, List, Optional, Tuple
import numpy as np
import pandas as pd
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


class PerformanceEngine(BaseDiagnosticEngine):
    """
    Forensic Model Performance & Probability Calibration Engine.
    Executes real statistical calculations for binary classification models:
    - Input & probability validity auditing (finite, [0, 1] bounds, NaN/Inf detection)
    - Deterministic positive/negative class mapping and balance profiling
    - Thresholded confusion matrix (TP, TN, FP, FN) and primary metrics (accuracy, precision, recall, specificity, F1, FPR, FNR)
    - Continuous discrimination & ranking metrics (ROC-AUC, Precision-Recall AUC)
    - Continuous probabilistic scoring (Binary Log Loss, Brier Score)
    - Multi-threshold operating point sensitivity analysis (0.00 to 1.00 grid)
    - 10-bin probability reliability curve and Expected Calibration Error (ECE / MCE)
    - Calibration tendency classification (overconfident, underconfident, well-calibrated, mixed)
    - Prevalence baseline comparison against naive constant prior
    - Forensic diagnostic findings and category health index calculation
    """

    DEFAULT_CONFIG = {
        "default_threshold": 0.50,
        "roc_auc_critical": 0.60,
        "roc_auc_warning": 0.70,
        "f1_critical": 0.30,
        "f1_warning": 0.50,
        "recall_critical": 0.30,
        "recall_warning": 0.50,
        "precision_critical": 0.30,
        "precision_warning": 0.50,
        "fpr_critical": 0.40,
        "fpr_warning": 0.20,
        "fnr_critical": 0.50,
        "fnr_warning": 0.30,
        "ece_critical": 0.10,
        "ece_warning": 0.05,
        "calibration_bins": 10,
        "threshold_steps": 21,  # 0.00, 0.05, ..., 1.00
    }

    @property
    def category(self) -> DiagnosticCategory:
        return DiagnosticCategory.PERFORMANCE

    @property
    def name(self) -> str:
        return "Model Performance & Calibration Engine"

    @property
    def description(self) -> str:
        return "Calculates ROC-AUC, PR-AUC, Confusion Matrix, Brier score, Log Loss, and reliability calibration curves."

    @staticmethod
    def _map_target_to_binary(
        series: pd.Series,
        positive_class_cfg: Optional[Any] = None,
    ) -> Tuple[np.ndarray, Any, Any, List[str]]:
        """
        Maps a target series deterministically to integer binary array {0, 1}.
        Returns (binary_array, positive_class_label, negative_class_label, issues_list).
        """
        clean_s = series.dropna()
        unique_vals = clean_s.unique()

        if len(unique_vals) == 0:
            raise ValueError("Target column contains 0 non-null values.")

        if len(unique_vals) == 1:
            # Single class target edge case
            single_val = unique_vals[0]
            binary_arr = np.ones(len(clean_s), dtype=int)
            return binary_arr, single_val, None, ["SINGLE_CLASS_TARGET"]

        if len(unique_vals) > 2:
            raise ValueError(f"Performance engine currently supports binary classification. Found {len(unique_vals)} unique classes: {list(unique_vals[:5])}.")

        # Two unique classes
        val1, val2 = unique_vals[0], unique_vals[1]

        # Determine positive class
        if positive_class_cfg is not None and positive_class_cfg in unique_vals:
            pos_label = positive_class_cfg
            neg_label = val2 if val1 == pos_label else val1
        else:
            # Deterministic inference rules
            # Standard 0/1 or False/True conventions
            val_set = {val1, val2}
            if val_set == {0, 1} or val_set == {0.0, 1.0} or val_set == {"0", "1"}:
                pos_label = 1 if 1 in val_set else (1.0 if 1.0 in val_set else "1")
                neg_label = 0 if 0 in val_set else (0.0 if 0.0 in val_set else "0")
            elif val_set == {False, True} or val_set == {"False", "True"} or val_set == {"false", "true"}:
                pos_label = True if True in val_set else ("True" if "True" in val_set else "true")
                neg_label = False if False in val_set else ("False" if "False" in val_set else "false")
            elif val_set == {"N", "Y"} or val_set == {"NO", "YES"} or val_set == {"no", "yes"}:
                pos_label = "Y" if "Y" in val_set else ("YES" if "YES" in val_set else "yes")
                neg_label = "N" if "N" in val_set else ("NO" if "NO" in val_set else "no")
            elif val_set == {"negative", "positive"}:
                pos_label = "positive"
                neg_label = "negative"
            else:
                # Sort and choose the second element deterministically
                sorted_vals = sorted(list(unique_vals), key=lambda x: str(x))
                neg_label = sorted_vals[0]
                pos_label = sorted_vals[1]

        binary_arr = (clean_s == pos_label).astype(int).to_numpy()
        return binary_arr, pos_label, neg_label, []

    @staticmethod
    def _calculate_confusion_metrics(
        y_true: np.ndarray,
        y_prob: np.ndarray,
        threshold: float,
    ) -> Dict[str, Any]:
        """
        Calculates confusion matrix and derived rate metrics at a specific threshold.
        """
        y_pred = (y_prob >= threshold).astype(int)

        tp = int(np.sum((y_true == 1) & (y_pred == 1)))
        tn = int(np.sum((y_true == 0) & (y_pred == 0)))
        fp = int(np.sum((y_true == 0) & (y_pred == 1)))
        fn = int(np.sum((y_true == 1) & (y_pred == 0)))

        total = tp + tn + fp + fn
        accuracy = round(float((tp + tn) / total), 4) if total > 0 else 0.0

        precision = round(float(tp / (tp + fp)), 4) if (tp + fp) > 0 else (0.0 if (tp + fn) > 0 else 1.0)
        recall = round(float(tp / (tp + fn)), 4) if (tp + fn) > 0 else 0.0
        specificity = round(float(tn / (tn + fp)), 4) if (tn + fp) > 0 else 0.0

        f1 = round(float(2 * precision * recall / (precision + recall)), 4) if (precision + recall) > 0 else 0.0
        fpr = round(float(fp / (fp + tn)), 4) if (fp + tn) > 0 else 0.0
        fnr = round(float(fn / (fn + tp)), 4) if (fn + tp) > 0 else 0.0
        pred_pos_rate = round(float((tp + fp) / total), 4) if total > 0 else 0.0

        return {
            "threshold": round(float(threshold), 3),
            "true_positive": tp,
            "true_negative": tn,
            "false_positive": fp,
            "false_negative": fn,
            "accuracy": accuracy,
            "precision": precision,
            "recall": recall,
            "specificity": specificity,
            "f1": f1,
            "false_positive_rate": fpr,
            "false_negative_rate": fnr,
            "predicted_positive_rate": pred_pos_rate,
        }

    @staticmethod
    def _calculate_calibration_curve(
        y_true: np.ndarray,
        y_prob: np.ndarray,
        num_bins: int = 10,
    ) -> Tuple[Dict[str, Any], float, float, str]:
        """
        Calculates uniform 10-bin reliability curve, ECE, MCE, and calibration tendency.
        """
        n_samples = len(y_true)
        if n_samples == 0:
            return {"binning_method": "uniform_probability", "num_bins": num_bins, "bins": []}, 0.0, 0.0, "UNKNOWN"

        bin_edges = np.linspace(0.0, 1.0, num_bins + 1)
        bins_data: List[Dict[str, Any]] = []
        weighted_errors: List[float] = []
        max_error = 0.0
        signed_error_sum = 0.0

        for b in range(num_bins):
            lower = float(bin_edges[b])
            upper = float(bin_edges[b + 1])

            if b == num_bins - 1:
                mask = (y_prob >= lower) & (y_prob <= upper)
                range_str = f"[{lower:.1f}, {upper:.1f}]"
            else:
                mask = (y_prob >= lower) & (y_prob < upper)
                range_str = f"[{lower:.1f}, {upper:.1f})"

            bin_count = int(np.sum(mask))

            if bin_count > 0:
                bin_mean_prob = float(np.mean(y_prob[mask]))
                bin_obs_rate = float(np.mean(y_true[mask]))
                ace = abs(bin_mean_prob - bin_obs_rate)
                delta = bin_mean_prob - bin_obs_rate

                weight = bin_count / n_samples
                weighted_errors.append(weight * ace)
                signed_error_sum += weight * delta

                if ace > max_error:
                    max_error = ace

                bins_data.append({
                    "binIndex": b,
                    "binRange": range_str,
                    "binLower": round(lower, 2),
                    "binUpper": round(upper, 2),
                    "sampleCount": bin_count,
                    "meanPredictedProbability": round(bin_mean_prob, 4),
                    "observedPositiveRate": round(bin_obs_rate, 4),
                    "absoluteCalibrationError": round(ace, 4),
                    "calibrationDelta": round(delta, 4),
                })
            else:
                bins_data.append({
                    "binIndex": b,
                    "binRange": range_str,
                    "binLower": round(lower, 2),
                    "binUpper": round(upper, 2),
                    "sampleCount": 0,
                    "meanPredictedProbability": round((lower + upper) / 2, 4),
                    "observedPositiveRate": 0.0,
                    "absoluteCalibrationError": 0.0,
                    "calibrationDelta": 0.0,
                })

        ece = round(float(np.sum(weighted_errors)), 4) if weighted_errors else 0.0
        mce = round(float(max_error), 4)

        # Diagnose tendency
        if ece < 0.05:
            tendency = "WELL_CALIBRATED"
        elif signed_error_sum > 0.03:
            tendency = "OVERCONFIDENT"
        elif signed_error_sum < -0.03:
            tendency = "UNDERCONFIDENT"
        else:
            tendency = "MIXED"

        calibration_payload = {
            "binning_method": "uniform_probability",
            "num_bins": num_bins,
            "bins": bins_data,
        }

        return calibration_payload, ece, mce, tendency

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

        if current_data is None or not isinstance(current_data, pd.DataFrame):
            raise ValueError("Evaluation dataset must be a valid pandas DataFrame.")

        # 1. Target Column Validation
        if not target_column or target_column not in current_data.columns:
            issue = DiagnosticIssue(
                id="PERF-MISSING-TARGET",
                category=DiagnosticCategory.PERFORMANCE,
                severity=SeverityLevel.CRITICAL,
                title="Target Column Missing in Dataset",
                description=f"Specified target label column '{target_column}' does not exist in the evaluation dataset.",
                affected_features=[str(target_column)],
                evidence={"targetColumn": target_column, "availableColumns": list(current_data.columns)},
                recommendation="Verify the target column name matches ground-truth labels in the uploaded dataset.",
            )
            exec_time_ms = round((time.perf_counter() - start_time) * 1000, 2)
            return DiagnosticReport(
                engine_name=self.name,
                category=self.category,
                health_score=0.0,
                passed=False,
                execution_time_ms=exec_time_ms,
                issues=[issue],
                metadata={
                    "module": "PERFORMANCE",
                    "version": "1.0",
                    "summary": {"error": f"Target column '{target_column}' missing.", "healthScore": 0.0, "passed": False},
                    "findings": [issue.model_dump()],
                },
            )

        # 2. Prediction Column Identification & Validation
        pred_col = cfg.get("prediction_column")
        if not pred_col:
            # Probe candidates
            candidate_cols = ["pred_prob", "prediction", "probability", "score", "pred", "y_pred_prob", "y_score"]
            for cand in candidate_cols:
                if cand in current_data.columns:
                    pred_col = cand
                    break

        if not pred_col or pred_col not in current_data.columns:
            issue = DiagnosticIssue(
                id="PERF-MISSING-PREDICTION",
                category=DiagnosticCategory.PERFORMANCE,
                severity=SeverityLevel.CRITICAL,
                title="Prediction Column Missing in Dataset",
                description=f"Model prediction probability column '{pred_col or 'pred_prob'}' was not found in evaluation dataset.",
                affected_features=[str(pred_col or "pred_prob")],
                evidence={"predictionColumn": pred_col, "availableColumns": list(current_data.columns)},
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
                metadata={
                    "module": "PERFORMANCE",
                    "version": "1.0",
                    "summary": {"error": f"Prediction column '{pred_col}' missing.", "healthScore": 0.0, "passed": False},
                    "findings": [issue.model_dump()],
                },
            )

        # 3. Clean & Validate Pairs
        eval_subset = current_data[[target_column, pred_col]].copy()
        raw_row_count = len(eval_subset)

        # Probe prediction values
        pred_numeric = pd.to_numeric(eval_subset[pred_col], errors="coerce")
        invalid_pred_mask = pred_numeric.isna() | np.isinf(pred_numeric) | (pred_numeric < 0.0) | (pred_numeric > 1.0)
        invalid_pred_count = int(invalid_pred_mask.sum())
        invalid_pred_rate = round(invalid_pred_count / raw_row_count, 6) if raw_row_count > 0 else 0.0

        if invalid_pred_count > 0:
            issues.append(
                DiagnosticIssue(
                    id="INVALID_PREDICTIONS",
                    category=DiagnosticCategory.PERFORMANCE,
                    severity=SeverityLevel.CRITICAL if invalid_pred_rate > 0.05 else SeverityLevel.HIGH,
                    title="Invalid or Out-of-Bounds Model Predictions Detected",
                    description=f"Found {invalid_pred_count} observation(s) ({invalid_pred_rate * 100:.2f}%) with non-finite or out-of-range probabilities (< 0.0 or > 1.0).",
                    affected_features=[pred_col],
                    evidence={
                        "invalidCount": invalid_pred_count,
                        "invalidRate": invalid_pred_rate,
                        "predictionColumn": pred_col,
                    },
                    recommendation="Ensure model inference applies proper sigmoid/softmax normalization without clipping or NaN generation.",
                )
            )

        # Target missingness check
        target_missing_count = int(eval_subset[target_column].isna().sum())

        # Valid clean subset
        valid_mask = ~invalid_pred_mask & ~eval_subset[target_column].isna()
        valid_df = eval_subset[valid_mask]
        valid_sample_count = len(valid_df)

        if valid_sample_count < 2:
            issue = DiagnosticIssue(
                id="PERF-INSUFFICIENT-SAMPLES",
                category=DiagnosticCategory.PERFORMANCE,
                severity=SeverityLevel.CRITICAL,
                title="Insufficient Valid Observations for Performance Audit",
                description=f"Evaluation dataset has only {valid_sample_count} valid paired observation(s). Minimum 2 required.",
                affected_features=[target_column, pred_col],
                evidence={"validSamples": valid_sample_count, "rawRows": raw_row_count},
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
                metadata={
                    "module": "PERFORMANCE",
                    "version": "1.0",
                    "summary": {"error": "Insufficient valid observations.", "healthScore": 0.0, "passed": False},
                    "findings": [issue.model_dump()],
                },
            )

        # 4. Target Mapping & Validation
        try:
            y_true, pos_class, neg_class, target_anomalies = self._map_target_to_binary(
                valid_df[target_column],
                positive_class_cfg=cfg.get("positive_class"),
            )
        except Exception as e:
            issue = DiagnosticIssue(
                id="PERF-TARGET-ERROR",
                category=DiagnosticCategory.PERFORMANCE,
                severity=SeverityLevel.CRITICAL,
                title="Target Label Mapping Error",
                description=str(e),
                affected_features=[target_column],
                evidence={"error": str(e)},
                recommendation="Verify target column conforms to binary classification requirements.",
            )
            exec_time_ms = round((time.perf_counter() - start_time) * 1000, 2)
            return DiagnosticReport(
                engine_name=self.name,
                category=self.category,
                health_score=0.0,
                passed=False,
                execution_time_ms=exec_time_ms,
                issues=[issue],
                metadata={
                    "module": "PERFORMANCE",
                    "version": "1.0",
                    "summary": {"error": str(e), "healthScore": 0.0, "passed": False},
                    "findings": [issue.model_dump()],
                },
            )

        y_prob = valid_df[pred_col].to_numpy(dtype=float)

        positive_count = int(np.sum(y_true == 1))
        negative_count = int(np.sum(y_true == 0))
        positive_rate = round(positive_count / valid_sample_count, 6) if valid_sample_count > 0 else 0.0

        if "SINGLE_CLASS_TARGET" in target_anomalies or positive_count == 0 or negative_count == 0:
            issue = DiagnosticIssue(
                id="SINGLE_CLASS_TARGET",
                category=DiagnosticCategory.PERFORMANCE,
                severity=SeverityLevel.CRITICAL,
                title="Single Class Present in Evaluation Partition",
                description=f"Target column '{target_column}' contains only positive (or negative) instances ({positive_count} pos, {negative_count} neg). Discrimination metrics are undefined.",
                affected_features=[target_column],
                evidence={"positiveCount": positive_count, "negativeCount": negative_count},
                recommendation="Provide an evaluation dataset containing both positive and negative class instances.",
            )
            issues.append(issue)
            exec_time_ms = round((time.perf_counter() - start_time) * 1000, 2)
            return DiagnosticReport(
                engine_name=self.name,
                category=self.category,
                health_score=0.0,
                passed=False,
                execution_time_ms=exec_time_ms,
                issues=issues,
                metadata={
                    "module": "PERFORMANCE",
                    "version": "1.0",
                    "summary": {
                        "sampleCount": valid_sample_count,
                        "positiveCount": positive_count,
                        "negativeCount": negative_count,
                        "positiveRate": positive_rate,
                        "rocAuc": None,
                        "prAuc": None,
                        "healthScore": 0.0,
                        "passed": False,
                    },
                    "findings": [i.model_dump() for i in issues],
                },
            )

        # 5. Primary Classification & Confusion Matrix Metrics at default threshold (0.50)
        default_th = float(cfg["default_threshold"])
        cm_metrics = self._calculate_confusion_metrics(y_true, y_prob, threshold=default_th)

        accuracy = cm_metrics["accuracy"]
        precision = cm_metrics["precision"]
        recall = cm_metrics["recall"]
        specificity = cm_metrics["specificity"]
        f1 = cm_metrics["f1"]
        fpr = cm_metrics["false_positive_rate"]
        fnr = cm_metrics["false_negative_rate"]

        # 6. Continuous Discrimination, Ranking & Loss Metrics
        try:
            roc_auc = round(float(metrics.roc_auc_score(y_true, y_prob)), 4)
        except Exception as e:
            logger.warning("ROC-AUC calculation exception: %s", str(e))
            roc_auc = None

        try:
            pr_auc = round(float(metrics.average_precision_score(y_true, y_prob)), 4)
        except Exception as e:
            logger.warning("PR-AUC calculation exception: %s", str(e))
            pr_auc = None

        try:
            log_loss_val = round(float(metrics.log_loss(y_true, y_prob)), 4)
        except Exception as e:
            logger.warning("Log loss calculation exception: %s", str(e))
            log_loss_val = None

        try:
            brier_score_val = round(float(metrics.brier_score_loss(y_true, y_prob)), 4)
        except Exception as e:
            logger.warning("Brier score calculation exception: %s", str(e))
            brier_score_val = None

        # 7. Threshold Sensitivity Grid Analysis (0.00 to 1.00)
        th_steps = int(cfg["threshold_steps"])
        th_grid = np.linspace(0.0, 1.0, th_steps)
        threshold_table: List[Dict[str, Any]] = []

        best_f1_val = -1.0
        best_f1_th = 0.50
        best_bacc_val = -1.0
        best_bacc_th = 0.50

        for th in th_grid:
            m = self._calculate_confusion_metrics(y_true, y_prob, threshold=float(th))
            threshold_table.append(m)

            if m["f1"] > best_f1_val:
                best_f1_val = m["f1"]
                best_f1_th = m["threshold"]

            bacc = (m["recall"] + m["specificity"]) / 2.0
            if bacc > best_bacc_val:
                best_bacc_val = bacc
                best_bacc_th = m["threshold"]

        # 8. Probability Calibration & Reliability Curve
        calib_payload, ece, mce, calib_tendency = self._calculate_calibration_curve(
            y_true,
            y_prob,
            num_bins=int(cfg["calibration_bins"]),
        )

        # 9. Prevalence Baseline Comparison
        p_prior = positive_rate
        p_prior_safe = np.clip(p_prior, 1e-12, 1.0 - 1e-12)
        baseline_log_loss = round(float(- (p_prior_safe * np.log(p_prior_safe) + (1.0 - p_prior_safe) * np.log(1.0 - p_prior_safe))), 4)
        baseline_brier_score = round(float(p_prior * (1.0 - p_prior)), 4)

        beats_log_loss = bool(log_loss_val is not None and log_loss_val < baseline_log_loss)
        beats_brier = bool(brier_score_val is not None and brier_score_val < baseline_brier_score)
        beats_baseline = bool(beats_log_loss and beats_brier)

        baseline_comparison = {
            "positiveRate": positive_rate,
            "baselineLogLoss": baseline_log_loss,
            "baselineBrierScore": baseline_brier_score,
            "modelLogLoss": log_loss_val,
            "modelBrierScore": brier_score_val,
            "beatsPrevalenceLogLoss": beats_log_loss,
            "beatsPrevalenceBrier": beats_brier,
            "beatsPrevalenceBaseline": beats_baseline,
        }

        # 10. Generate Forensic Findings & Diagnostic Issues
        # A. Discrimination Findings
        if roc_auc is not None:
            if roc_auc < cfg["roc_auc_critical"]:
                issues.append(
                    DiagnosticIssue(
                        id="LOW_MODEL_DISCRIMINATION",
                        category=DiagnosticCategory.PERFORMANCE,
                        severity=SeverityLevel.CRITICAL,
                        title="Critical Deficiency in Model Discrimination (ROC-AUC < 0.60)",
                        description=f"Model achieves ROC-AUC of {roc_auc:.4f}, demonstrating severe inability to separate positive from negative observations.",
                        affected_features=[pred_col, target_column],
                        evidence={"rocAuc": roc_auc, "threshold": cfg["roc_auc_critical"]},
                        recommendation="Re-evaluate feature representation, label signal fidelity, or model architecture.",
                    )
                )
            elif roc_auc < cfg["roc_auc_warning"]:
                issues.append(
                    DiagnosticIssue(
                        id="SUBPAR_DISCRIMINATION",
                        category=DiagnosticCategory.PERFORMANCE,
                        severity=SeverityLevel.WARNING,
                        title="Subpar Model Discrimination (ROC-AUC 0.60 - 0.70)",
                        description=f"Model achieves ROC-AUC of {roc_auc:.4f}, performing below standard acceptable discrimination baseline.",
                        affected_features=[pred_col, target_column],
                        evidence={"rocAuc": roc_auc, "threshold": cfg["roc_auc_warning"]},
                        recommendation="Audit informative predictive features or tune hyper-parameters.",
                    )
                )

        # B. False Negative Rate / Recall Findings
        if fnr >= cfg["fnr_critical"]:
            issues.append(
                DiagnosticIssue(
                    id="HIGH_FALSE_NEGATIVE_RATE",
                    category=DiagnosticCategory.PERFORMANCE,
                    severity=SeverityLevel.HIGH,
                    title=f"Severe False Negative Rate ({fnr * 100:.1f}%) at Threshold {default_th}",
                    description=f"Model misses {cm_metrics['false_negative']} positive cases out of {positive_count} total positive observations (Recall={recall:.4f}).",
                    affected_features=[pred_col],
                    evidence={"falseNegativeRate": fnr, "recall": recall, "threshold": default_th},
                    recommendation=f"Lower classification decision threshold below {default_th} (suggested optimal F1 threshold: {best_f1_th}) or apply cost-sensitive weighting.",
                )
            )
        elif fnr >= cfg["fnr_warning"]:
            issues.append(
                DiagnosticIssue(
                    id="HIGH_FALSE_NEGATIVE_RATE",
                    category=DiagnosticCategory.PERFORMANCE,
                    severity=SeverityLevel.WARNING,
                    title=f"Elevated False Negative Rate ({fnr * 100:.1f}%) at Threshold {default_th}",
                    description=f"Model misses {cm_metrics['false_negative']} positive cases (Recall={recall:.4f}).",
                    affected_features=[pred_col],
                    evidence={"falseNegativeRate": fnr, "recall": recall, "threshold": default_th},
                    recommendation="Adjust decision threshold towards higher sensitivity operating points.",
                )
            )

        # C. False Positive Rate / Precision Findings
        if fpr >= cfg["fpr_critical"]:
            issues.append(
                DiagnosticIssue(
                    id="HIGH_FALSE_POSITIVE_RATE",
                    category=DiagnosticCategory.PERFORMANCE,
                    severity=SeverityLevel.HIGH,
                    title=f"Excessive False Positive Rate ({fpr * 100:.1f}%) at Threshold {default_th}",
                    description=f"Model incorrectly triggers false alarms on {cm_metrics['false_positive']} negative observations (Precision={precision:.4f}).",
                    affected_features=[pred_col],
                    evidence={"falsePositiveRate": fpr, "precision": precision, "threshold": default_th},
                    recommendation="Raise classification decision threshold or add negative hard-mining regularization.",
                )
            )

        # D. F1 Score Findings
        if f1 < cfg["f1_critical"]:
            issues.append(
                DiagnosticIssue(
                    id="LOW_F1_SCORE",
                    category=DiagnosticCategory.PERFORMANCE,
                    severity=SeverityLevel.HIGH,
                    title=f"Critically Low F1 Score ({f1:.4f}) at Threshold {default_th}",
                    description=f"Harmonic mean of precision ({precision:.4f}) and recall ({recall:.4f}) is severely degraded.",
                    affected_features=[pred_col],
                    evidence={"f1": f1, "precision": precision, "recall": recall},
                    recommendation=f"Tune operating threshold to {best_f1_th} (which maximizes F1 to {best_f1_val:.4f}).",
                )
            )

        # E. Calibration Findings
        if ece >= cfg["ece_critical"]:
            issues.append(
                DiagnosticIssue(
                    id="HIGH_CALIBRATION_ERROR",
                    category=DiagnosticCategory.PERFORMANCE,
                    severity=SeverityLevel.HIGH,
                    title=f"Severe Probability Miscalibration (ECE={ece:.4f}, Tendency: {calib_tendency})",
                    description=(
                        f"Predicted probabilities deviate significantly from empirical event frequencies "
                        f"(ECE={ece:.4f}, Maximum Error MCE={mce:.4f}). Model exhibits {calib_tendency.lower()} tendencies."
                    ),
                    affected_features=[pred_col],
                    evidence={"ece": ece, "mce": mce, "tendency": calib_tendency},
                    recommendation="Apply post-hoc probability calibration (Platt Scaling / Logistic Calibration or Isotonic Regression).",
                )
            )
        elif ece >= cfg["ece_warning"]:
            issues.append(
                DiagnosticIssue(
                    id="MODERATE_CALIBRATION_ERROR",
                    category=DiagnosticCategory.PERFORMANCE,
                    severity=SeverityLevel.WARNING,
                    title=f"Moderate Calibration Error (ECE={ece:.4f}, Tendency: {calib_tendency})",
                    description=f"Predicted probabilities diverge moderately from observed positive rates (ECE={ece:.4f}).",
                    affected_features=[pred_col],
                    evidence={"ece": ece, "mce": mce, "tendency": calib_tendency},
                    recommendation="Verify probability calibration curves before using raw scores for thresholding or financial decisions.",
                )
            )

        # F. Baseline Comparison Finding
        if not beats_baseline and log_loss_val is not None:
            issues.append(
                DiagnosticIssue(
                    id="FAILS_PREVALENCE_BASELINE",
                    category=DiagnosticCategory.PERFORMANCE,
                    severity=SeverityLevel.HIGH,
                    title="Model Underperforms Naive Prevalence Baseline",
                    description=f"Model log loss ({log_loss_val:.4f}) or Brier score ({brier_score_val:.4f}) fails to outperform a naive constant prior baseline (LogLoss: {baseline_log_loss:.4f}, Brier: {baseline_brier_score:.4f}).",
                    affected_features=[pred_col],
                    evidence=baseline_comparison,
                    recommendation="Model predictions are worse than guessing historical class prevalence. Retrain model.",
                )
            )

        # 11. Diagnostic Metrics Summary
        if roc_auc is not None:
            metrics_list.append(
                DiagnosticMetric(
                    name="roc_auc",
                    value=float(roc_auc),
                    threshold_min=cfg["roc_auc_warning"],
                    passed=bool(roc_auc >= cfg["roc_auc_warning"]),
                )
            )
        if pr_auc is not None:
            metrics_list.append(
                DiagnosticMetric(
                    name="pr_auc",
                    value=float(pr_auc),
                    threshold_min=positive_rate,
                    passed=bool(pr_auc >= positive_rate),
                )
            )
        metrics_list.append(
            DiagnosticMetric(
                name="f1_score",
                value=float(f1),
                threshold_min=cfg["f1_warning"],
                passed=bool(f1 >= cfg["f1_warning"]),
            )
        )
        metrics_list.append(
            DiagnosticMetric(
                name="expected_calibration_error",
                value=float(ece),
                threshold_max=cfg["ece_warning"],
                passed=bool(ece < cfg["ece_warning"]),
            )
        )

        # 12. Health Score Calculation
        penalty = 0.0
        critical_count = 0
        for issue in issues:
            if issue.severity == SeverityLevel.CRITICAL:
                penalty += 25.0
                critical_count += 1
            elif issue.severity in [SeverityLevel.HIGH, SeverityLevel.WARNING]:
                penalty += 10.0
            elif issue.severity == SeverityLevel.MEDIUM:
                penalty += 4.0
            elif issue.severity == SeverityLevel.LOW:
                penalty += 1.0

        health_score = max(0.0, min(100.0, round(100.0 - penalty, 1)))
        passed = bool(health_score >= 70.0 and critical_count == 0 and (roc_auc is None or roc_auc >= 0.60))

        exec_time_ms = round((time.perf_counter() - start_time) * 1000, 2)

        summary_dict = {
            "sampleCount": valid_sample_count,
            "rawRowCount": raw_row_count,
            "invalidPredictionCount": invalid_pred_count,
            "invalidPredictionRate": invalid_pred_rate,
            "positiveCount": positive_count,
            "negativeCount": negative_count,
            "positiveRate": positive_rate,
            "positiveClassLabel": str(pos_class),
            "negativeClassLabel": str(neg_class),
            "threshold": default_th,
            "accuracy": accuracy,
            "precision": precision,
            "recall": recall,
            "specificity": specificity,
            "f1": f1,
            "falsePositiveRate": fpr,
            "falseNegativeRate": fnr,
            "rocAuc": roc_auc,
            "prAuc": pr_auc,
            "logLoss": log_loss_val,
            "brierScore": brier_score_val,
            "expectedCalibrationError": ece,
            "maximumCalibrationError": mce,
            "calibrationTendency": calib_tendency,
            "bestF1Threshold": best_f1_th,
            "bestF1Value": best_f1_val,
            "bestBalancedAccuracyThreshold": best_bacc_th,
            "healthScore": health_score,
            "passed": passed,
        }

        metadata_dict = {
            "module": "PERFORMANCE",
            "version": "1.0",
            "summary": summary_dict,
            "confusionMatrix": {
                "threshold": default_th,
                "trueNegative": cm_metrics["true_negative"],
                "falsePositive": cm_metrics["false_positive"],
                "falseNegative": cm_metrics["false_negative"],
                "truePositive": cm_metrics["true_positive"],
            },
            "thresholdAnalysis": threshold_table,
            "calibration": calib_payload,
            "baselineComparison": baseline_comparison,
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
