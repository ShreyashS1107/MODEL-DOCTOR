import logging
import math
import time
from typing import Any, Dict, List, Optional, Tuple, Union
import numpy as np
import pandas as pd

from app.diagnostics.base import BaseDiagnosticEngine
from app.schemas.diagnostic_models import (
    DiagnosticCategory,
    DiagnosticIssue,
    DiagnosticMetric,
    DiagnosticReport,
    SeverityLevel,
)

logger = logging.getLogger(__name__)


class FairnessEngine(BaseDiagnosticEngine):
    """
    Computes algorithmic fairness and demographic disparity diagnostics:
    - Demographic / Statistical Parity & Selection Rate differences
    - Disparate Impact Ratio with 80% four-fifths rule heuristic
    - Equal Opportunity (True Positive Rate parity)
    - Equalized Odds (TPR and FPR parity across groups)
    - Group-wise Confusion Matrices & Performance Rates
    - Group Calibration Gaps (Predicted probability vs Empirical base rate)
    - 95% Wilson score binomial confidence intervals
    - Small sample safety guards and cardinality limits
    """

    DEFAULT_CONFIG: Dict[str, Any] = {
        "prediction_threshold": 0.50,
        "minimum_group_size": 30,
        "max_cardinality_warning": 20,
        "max_cardinality_critical": 50,
        "disparate_impact_low_critical": 0.70,
        "disparate_impact_low_warning": 0.80,
        "disparate_impact_high_warning": 1.25,
        "disparate_impact_high_critical": 1.43,
        "equal_opportunity_gap_warning": 0.05,
        "equal_opportunity_gap_critical": 0.10,
        "equalized_odds_gap_warning": 0.05,
        "equalized_odds_gap_critical": 0.10,
        "demographic_parity_gap_warning": 0.10,
        "demographic_parity_gap_critical": 0.20,
        "group_calibration_gap_warning": 0.05,
        "group_calibration_gap_critical": 0.10,
    }

    @property
    def category(self) -> DiagnosticCategory:
        return DiagnosticCategory.FAIRNESS

    @property
    def name(self) -> str:
        return "Bias & Fairness Engine"

    @property
    def description(self) -> str:
        return "Audits demographic parity, equalized odds, disparate impact, and group calibration across sensitive feature slices."

    @staticmethod
    def _calculate_wilson_ci(k: int, n: int, z: float = 1.95996) -> Dict[str, float]:
        """Calculates 95% Wilson score confidence interval for binomial proportion."""
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
    def _safe_div(num: Union[int, float], denom: Union[int, float]) -> Optional[float]:
        """Safely computes division returning None if denominator is zero."""
        if denom == 0 or math.isnan(denom):
            return None
        res = num / denom
        return round(float(res), 4) if not math.isnan(res) else None

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
                id="BIAS_EMPTY_DATASET",
                category=self.category,
                severity=SeverityLevel.CRITICAL,
                title="Evaluation Dataset Empty",
                description="Evaluation dataset must be a non-empty pandas DataFrame.",
                affected_features=[],
                evidence={"rows": 0},
                recommendation="Provide valid observations for fairness auditing.",
            )
            exec_time_ms = round((time.perf_counter() - start_time) * 1000, 2)
            return DiagnosticReport(
                engine_name=self.name,
                category=self.category,
                health_score=0.0,
                passed=False,
                execution_time_ms=exec_time_ms,
                issues=[issue],
                metadata={"module": "BIAS", "version": "1.0", "summary": {"error": "Empty dataset", "healthScore": 0.0, "passed": False}},
            )

        # 2. Task Validation
        task_type = (cfg.get("task_type") or "binary_classification").lower()
        if task_type != "binary_classification":
            issue = DiagnosticIssue(
                id="BIAS_UNSUPPORTED_TASK",
                category=self.category,
                severity=SeverityLevel.CRITICAL,
                title=f"Unsupported Task Type '{task_type}' for Fairness Audit",
                description="The Model Doctor Fairness Engine currently supports binary classification tasks.",
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
                metadata={"module": "BIAS", "version": "1.0", "summary": {"error": f"Unsupported task type {task_type}", "healthScore": 0.0, "passed": False}},
            )

        # 3. Target Column Validation
        if not target_column or target_column not in current_data.columns:
            issue = DiagnosticIssue(
                id="BIAS_MISSING_TARGET",
                category=self.category,
                severity=SeverityLevel.CRITICAL,
                title=f"Target Column '{target_column}' Missing in Dataset",
                description=f"Ground truth target column '{target_column}' is required for group performance calculations.",
                affected_features=[target_column] if target_column else [],
                evidence={"availableColumns": list(current_data.columns[:10])},
                recommendation="Provide valid ground truth target column.",
            )
            exec_time_ms = round((time.perf_counter() - start_time) * 1000, 2)
            return DiagnosticReport(
                engine_name=self.name,
                category=self.category,
                health_score=0.0,
                passed=False,
                execution_time_ms=exec_time_ms,
                issues=[issue],
                metadata={"module": "BIAS", "version": "1.0", "summary": {"error": "Missing target column", "healthScore": 0.0, "passed": False}},
            )

        # 4. Protected Attribute Resolution
        prot_attr = cfg.get("protected_attribute") or cfg.get("protectedAttribute")
        if not prot_attr:
            # Check common demographic candidate names if not explicitly configured
            candidates = ["is_foreign_ip", "card_network", "gender", "age_group", "protected_class", "race", "ethnicity"]
            for cand in candidates:
                if cand in current_data.columns:
                    prot_attr = cand
                    break

        if not prot_attr or prot_attr not in current_data.columns:
            issue = DiagnosticIssue(
                id="BIAS_PROTECTED_ATTRIBUTE_MISSING",
                category=self.category,
                severity=SeverityLevel.CRITICAL,
                title="Protected Demographic Attribute Not Specified or Missing",
                description="Fairness and disparate impact analysis requires a valid sensitive / protected demographic attribute column.",
                affected_features=[],
                evidence={"configuredAttribute": prot_attr, "columns": list(current_data.columns[:10])},
                recommendation="Specify 'protected_attribute' in diagnostic configuration.",
            )
            exec_time_ms = round((time.perf_counter() - start_time) * 1000, 2)
            return DiagnosticReport(
                engine_name=self.name,
                category=self.category,
                health_score=0.0,
                passed=False,
                execution_time_ms=exec_time_ms,
                issues=[issue],
                metadata={"module": "BIAS", "version": "1.0", "summary": {"error": "Missing protected attribute", "healthScore": 0.0, "passed": False}},
            )

        # 5. Predictions Resolution
        pred_col = cfg.get("prediction_column")
        if not pred_col:
            for cand in ["pred_prob", "prediction", "prediction_probability", "score", "y_pred"]:
                if cand in current_data.columns:
                    pred_col = cand
                    break

        raw_total_rows = len(current_data)
        threshold = float(cfg["prediction_threshold"])

        # 6. Extract Clean Paired Subset & Handle Missing Values
        clean_mask = (
            current_data[target_column].notna() &
            current_data[prot_attr].notna()
        )
        if pred_col and pred_col in current_data.columns:
            clean_mask = clean_mask & current_data[pred_col].notna()

        df_clean = current_data[clean_mask].copy()
        evaluated_rows = len(df_clean)
        excluded_rows = raw_total_rows - evaluated_rows
        excluded_rate = round(excluded_rows / raw_total_rows, 4) if raw_total_rows > 0 else 0.0

        if evaluated_rows < 2:
            issue = DiagnosticIssue(
                id="BIAS_INSUFFICIENT_OBSERVATIONS",
                category=self.category,
                severity=SeverityLevel.CRITICAL,
                title="Insufficient Valid Observations for Fairness Audit",
                description=f"Evaluation dataset has only {evaluated_rows} valid rows after removing nulls. Minimum 2 required.",
                affected_features=[prot_attr, target_column],
                evidence={"validRows": evaluated_rows, "excludedRows": excluded_rows},
                recommendation="Provide non-empty evaluation observations.",
            )
            exec_time_ms = round((time.perf_counter() - start_time) * 1000, 2)
            return DiagnosticReport(
                engine_name=self.name,
                category=self.category,
                health_score=0.0,
                passed=False,
                execution_time_ms=exec_time_ms,
                issues=[issue],
                metadata={"module": "BIAS", "version": "1.0", "summary": {"error": "Insufficient valid observations", "healthScore": 0.0, "passed": False}},
            )

        # 7. Normalize Target and Predictions
        y_true = df_clean[target_column].astype(int).values
        has_probabilities = False
        if pred_col and pred_col in df_clean.columns:
            raw_preds = df_clean[pred_col].values
            if np.issubdtype(raw_preds.dtype, np.number) and np.nanmin(raw_preds) >= 0.0 and np.nanmax(raw_preds) <= 1.0 and (raw_preds > 0.0).any() and (raw_preds < 1.0).any():
                y_prob = raw_preds.astype(float)
                y_pred = (y_prob >= threshold).astype(int)
                has_probabilities = True
            else:
                y_pred = raw_preds.astype(int)
                y_prob = y_pred.astype(float)
        else:
            # Fallback to target as proxy if no predictions
            y_pred = y_true.copy()
            y_prob = y_true.astype(float)

        df_clean["__y_true"] = y_true
        df_clean["__y_pred"] = y_pred
        df_clean["__y_prob"] = y_prob

        # 8. Check Protected Attribute Cardinality
        unique_groups = df_clean[prot_attr].unique()
        group_count = len(unique_groups)

        if group_count > cfg["max_cardinality_critical"]:
            issue = DiagnosticIssue(
                id="BIAS_HIGH_CARDINALITY",
                category=self.category,
                severity=SeverityLevel.CRITICAL,
                title=f"Excessive Cardinality ({group_count} groups) in Protected Attribute '{prot_attr}'",
                description=f"Protected attribute '{prot_attr}' contains {group_count} distinct categories. Maximum allowable limit is {cfg['max_cardinality_critical']}.",
                affected_features=[prot_attr],
                evidence={"uniqueGroups": group_count, "limit": cfg["max_cardinality_critical"]},
                recommendation="Bin continuous attributes into categorical buckets or select a valid demographic slice.",
            )
            exec_time_ms = round((time.perf_counter() - start_time) * 1000, 2)
            return DiagnosticReport(
                engine_name=self.name,
                category=self.category,
                health_score=0.0,
                passed=False,
                execution_time_ms=exec_time_ms,
                issues=[issue],
                metadata={"module": "BIAS", "version": "1.0", "summary": {"error": "Excessive cardinality", "healthScore": 0.0, "passed": False}},
            )
        elif group_count > cfg["max_cardinality_warning"]:
            issues.append(
                DiagnosticIssue(
                    id="BIAS_HIGH_CARDINALITY_WARNING",
                    category=self.category,
                    severity=SeverityLevel.WARNING,
                    title=f"High Group Cardinality ({group_count} groups) in Protected Attribute '{prot_attr}'",
                    description=f"Protected attribute '{prot_attr}' contains {group_count} distinct groups, which may dilute per-group statistical power.",
                    affected_features=[prot_attr],
                    evidence={"uniqueGroups": group_count},
                    recommendation="Ensure sample sizes within individual groups are sufficient for statistical reliability.",
                )
            )

        # 9. Reference Group Resolution
        configured_ref = cfg.get("reference_group") or cfg.get("referenceGroup")
        group_counts = df_clean[prot_attr].value_counts().to_dict()
        
        ref_selection_method = "configured"
        if configured_ref and configured_ref in group_counts:
            ref_group = configured_ref
        else:
            # Deterministic fallback: largest group by sample count
            ref_group = df_clean[prot_attr].value_counts().index[0]
            ref_selection_method = "largest_group"

        # 10. Compute Group-Wise Metrics
        min_group_sz = int(cfg["minimum_group_size"])
        group_records: List[Dict[str, Any]] = []
        small_group_count = 0

        for g_val in unique_groups:
            g_str = str(g_val)
            g_df = df_clean[df_clean[prot_attr] == g_val]
            n_g = len(g_df)

            g_y_true = g_df["__y_true"].values
            g_y_pred = g_df["__y_pred"].values
            g_y_prob = g_df["__y_prob"].values

            tp = int(np.sum((g_y_pred == 1) & (g_y_true == 1)))
            tn = int(np.sum((g_y_pred == 0) & (g_y_true == 0)))
            fp = int(np.sum((g_y_pred == 1) & (g_y_true == 0)))
            fn = int(np.sum((g_y_pred == 0) & (g_y_true == 1)))

            pos_pred_count = tp + fp
            act_pos_count = tp + fn

            sel_rate = round(pos_pred_count / n_g, 4) if n_g > 0 else 0.0
            act_pos_rate = round(act_pos_count / n_g, 4) if n_g > 0 else 0.0

            tpr = self._safe_div(tp, tp + fn)
            fnr = self._safe_div(fn, tp + fn)
            tnr = self._safe_div(tn, tn + fp)
            fpr = self._safe_div(fp, tn + fp)
            ppv = self._safe_div(tp, tp + fp)
            npv = self._safe_div(tn, tn + fn)
            acc = self._safe_div(tp + tn, n_g)

            mean_pred_prob = round(float(np.mean(g_y_prob)), 4) if has_probabilities and n_g > 0 else None
            calib_gap = round(mean_pred_prob - act_pos_rate, 4) if mean_pred_prob is not None else None

            is_small = n_g < min_group_sz
            if is_small:
                small_group_count += 1

            ci_sel = self._calculate_wilson_ci(pos_pred_count, n_g)
            ci_tpr = self._calculate_wilson_ci(tp, tp + fn) if (tp + fn) > 0 else {"lower": 0.0, "upper": 0.0}
            ci_fpr = self._calculate_wilson_ci(fp, tn + fp) if (tn + fp) > 0 else {"lower": 0.0, "upper": 0.0}

            group_records.append({
                "group": g_str,
                "sampleCount": n_g,
                "isReference": bool(g_val == ref_group),
                "isSmallSample": is_small,
                "confusionMatrix": {"tp": tp, "tn": tn, "fp": fp, "fn": fn},
                "positivePredictionCount": pos_pred_count,
                "positivePredictionRate": sel_rate,
                "actualPositiveCount": act_pos_count,
                "actualPositiveRate": act_pos_rate,
                "tpr": tpr,
                "fnr": fnr,
                "tnr": tnr,
                "fpr": fpr,
                "ppv": ppv,
                "npv": npv,
                "accuracy": acc,
                "meanPredictedProbability": mean_pred_prob,
                "calibrationGap": calib_gap,
                "confidenceIntervals": {
                    "selectionRate": ci_sel,
                    "tpr": ci_tpr,
                    "fpr": ci_fpr,
                },
            })

        # Sort group records: reference group first, then descending by sample count
        group_records.sort(key=lambda r: (not r["isReference"], -r["sampleCount"]))

        # 11. Reference Record Extraction
        ref_rec = next((r for r in group_records if r["isReference"]), group_records[0])
        ref_sel_rate = ref_rec["positivePredictionRate"]
        ref_tpr = ref_rec["tpr"]
        ref_fpr = ref_rec["fpr"]

        # 12. Demographic Parity & Disparate Impact Comparisons
        dp_comparisons: List[Dict[str, Any]] = []
        di_comparisons: List[Dict[str, Any]] = []
        eo_comparisons: List[Dict[str, Any]] = []
        calib_comparisons: List[Dict[str, Any]] = []

        all_sel_rates = [r["positivePredictionRate"] for r in group_records]
        valid_tprs = [r["tpr"] for r in group_records if r["tpr"] is not None]
        valid_fprs = [r["fpr"] for r in group_records if r["fpr"] is not None]
        valid_calibs = [abs(r["calibrationGap"]) for r in group_records if r["calibrationGap"] is not None]

        max_dp_gap = round(max(all_sel_rates) - min(all_sel_rates), 4) if all_sel_rates else 0.0
        max_tpr_gap = round(max(valid_tprs) - min(valid_tprs), 4) if len(valid_tprs) >= 2 else 0.0
        max_fpr_gap = round(max(valid_fprs) - min(valid_fprs), 4) if len(valid_fprs) >= 2 else 0.0
        max_eo_odds_gap = round(max(max_tpr_gap, max_fpr_gap), 4)
        worst_calib_gap = round(max(valid_calibs), 4) if valid_calibs else 0.0

        worst_dir_val = 1.0
        worst_dir_group = str(ref_rec["group"])

        for rec in group_records:
            g_name = rec["group"]
            g_sel = rec["positivePredictionRate"]
            g_tpr = rec["tpr"]
            g_fpr = rec["fpr"]
            g_cal = rec["calibrationGap"]

            # Demographic Parity Difference
            dp_diff = round(g_sel - ref_sel_rate, 4)
            dp_comparisons.append({
                "group": g_name,
                "selectionRate": g_sel,
                "referenceRate": ref_sel_rate,
                "difference": dp_diff,
                "absoluteDifference": abs(dp_diff),
            })

            # Disparate Impact Ratio
            if ref_sel_rate > 0:
                dir_val = round(g_sel / ref_sel_rate, 4)
            else:
                dir_val = None

            di_comparisons.append({
                "group": g_name,
                "selectionRate": g_sel,
                "referenceRate": ref_sel_rate,
                "disparateImpactRatio": dir_val,
                "violates80Rule": bool(dir_val is not None and (dir_val < cfg["disparate_impact_low_warning"] or dir_val > cfg["disparate_impact_high_warning"])),
            })

            if dir_val is not None and not rec["isReference"] and dir_val < worst_dir_val:
                worst_dir_val = dir_val
                worst_dir_group = g_name

            # Equal Opportunity TPR difference
            tpr_diff = round(g_tpr - ref_tpr, 4) if g_tpr is not None and ref_tpr is not None else None
            eo_comparisons.append({
                "group": g_name,
                "tpr": g_tpr,
                "referenceTpr": ref_tpr,
                "difference": tpr_diff,
                "absoluteDifference": abs(tpr_diff) if tpr_diff is not None else None,
            })

            if g_cal is not None:
                calib_comparisons.append({
                    "group": g_name,
                    "meanPredictedProbability": rec["meanPredictedProbability"],
                    "actualPositiveRate": rec["actualPositiveRate"],
                    "calibrationGap": g_cal,
                    "absoluteGap": abs(g_cal),
                })

        # 13. Worst-Performing Groups
        lowest_sel_group = min(group_records, key=lambda r: r["positivePredictionRate"])["group"]
        lowest_tpr_group = min([r for r in group_records if r["tpr"] is not None], key=lambda r: r["tpr"])["group"] if valid_tprs else "N/A"
        highest_fpr_group = max([r for r in group_records if r["fpr"] is not None], key=lambda r: r["fpr"])["group"] if valid_fprs else "N/A"

        worst_groups = {
            "lowestSelectionRate": lowest_sel_group,
            "lowestTruePositiveRate": lowest_tpr_group,
            "highestFalsePositiveRate": highest_fpr_group,
            "worstDisparateImpact": worst_dir_group,
        }

        # 14. Generate Structured Forensic Findings
        # A. Disparate Impact 80% Rule Findings
        for di_item in di_comparisons:
            d_val = di_item["disparateImpactRatio"]
            grp = di_item["group"]
            if d_val is not None and not any(r["group"] == grp and r["isReference"] for r in group_records):
                if d_val < cfg["disparate_impact_low_critical"]:
                    issues.append(
                        DiagnosticIssue(
                            id=f"BIAS_CRITICAL_DISPARATE_IMPACT_{grp}",
                            category=self.category,
                            severity=SeverityLevel.HIGH,
                            title=f"Substantial Selection Disparity in '{grp}' (DIR = {d_val:.3f})",
                            description=(
                                f"The positive selection rate for group '{grp}' ({di_item['selectionRate'] * 100:.1f}%) "
                                f"is substantially below the reference group '{ref_group}' ({ref_sel_rate * 100:.1f}%), "
                                f"resulting in a Disparate Impact Ratio of {d_val:.3f} (exceeds the 70% threshold heuristic)."
                            ),
                            affected_features=[prot_attr],
                            evidence={"group": grp, "disparateImpactRatio": d_val, "selectionRate": di_item["selectionRate"], "referenceRate": ref_sel_rate},
                            recommendation="Inspect feature representations and decision threshold sensitivity across demographic slices.",
                        )
                    )
                elif d_val < cfg["disparate_impact_low_warning"]:
                    issues.append(
                        DiagnosticIssue(
                            id=f"BIAS_DISPARATE_IMPACT_WARNING_{grp}",
                            category=self.category,
                            severity=SeverityLevel.WARNING,
                            title=f"Selection Rate Disparity in '{grp}' (DIR = {d_val:.3f} < 0.80)",
                            description=(
                                f"Positive selection rate for group '{grp}' ({di_item['selectionRate'] * 100:.1f}%) "
                                f"falls below 80% of the reference rate ({ref_sel_rate * 100:.1f}%)."
                            ),
                            affected_features=[prot_attr],
                            evidence={"group": grp, "disparateImpactRatio": d_val, "threshold": 0.80},
                            recommendation="Evaluate whether disparate outcomes stem from historical base rate differences or model attribution proxies.",
                        )
                    )
                elif d_val > cfg["disparate_impact_high_critical"]:
                    issues.append(
                        DiagnosticIssue(
                            id=f"BIAS_ELEVATED_SELECTION_RATE_{grp}",
                            category=self.category,
                            severity=SeverityLevel.HIGH,
                            title=f"Elevated Positive Selection Rate in '{grp}' (DIR = {d_val:.3f})",
                            description=f"Group '{grp}' receives positive outcomes at {d_val:.2f}x the rate of reference group '{ref_group}'.",
                            affected_features=[prot_attr],
                            evidence={"group": grp, "disparateImpactRatio": d_val},
                            recommendation="Verify thresholding consistency and check for over-prediction tendencies.",
                        )
                    )

        # B. Demographic Parity Gap Finding
        if max_dp_gap >= cfg["demographic_parity_gap_critical"]:
            issues.append(
                DiagnosticIssue(
                    id="BIAS_DEMOGRAPHIC_PARITY_CRITICAL",
                    category=self.category,
                    severity=SeverityLevel.HIGH,
                    title=f"High Demographic Parity Gap ({max_dp_gap * 100:.1f}%) Across '{prot_attr}'",
                    description=(
                        f"The maximum difference in positive selection rates between groups is {max_dp_gap * 100:.1f}% "
                        f"(Min: {min(all_sel_rates) * 100:.1f}%, Max: {max(all_sel_rates) * 100:.1f}%)."
                    ),
                    affected_features=[prot_attr],
                    evidence={"demographicParityGap": max_dp_gap, "rates": all_sel_rates},
                    recommendation="Review representation balance and evaluate post-processing group threshold adjustments.",
                )
            )
        elif max_dp_gap >= cfg["demographic_parity_gap_warning"]:
            issues.append(
                DiagnosticIssue(
                    id="BIAS_DEMOGRAPHIC_PARITY_WARNING",
                    category=self.category,
                    severity=SeverityLevel.WARNING,
                    title=f"Moderate Demographic Parity Gap ({max_dp_gap * 100:.1f}%) Across '{prot_attr}'",
                    description=f"Observed positive selection rates differ moderately across slices of '{prot_attr}'.",
                    affected_features=[prot_attr],
                    evidence={"demographicParityGap": max_dp_gap},
                    recommendation="Monitor group selection rates across operational deployment windows.",
                )
            )

        # C. Equal Opportunity Gap Finding
        if max_tpr_gap >= cfg["equal_opportunity_gap_critical"]:
            issues.append(
                DiagnosticIssue(
                    id="BIAS_EQUAL_OPPORTUNITY_CRITICAL",
                    category=self.category,
                    severity=SeverityLevel.HIGH,
                    title=f"Severe Equal Opportunity Disparity (TPR Gap = {max_tpr_gap * 100:.1f}%)",
                    description=(
                        f"True Positive Rates diverge by {max_tpr_gap * 100:.1f}% across groups of '{prot_attr}' "
                        f"(Lowest TPR group: '{lowest_tpr_group}'). Model exhibits differential recall for qualified positive cases."
                    ),
                    affected_features=[prot_attr],
                    evidence={"tprGap": max_tpr_gap, "lowestTprGroup": lowest_tpr_group},
                    recommendation="Audit subgroup feature recall and evaluate group-specific threshold calibration.",
                )
            )
        elif max_tpr_gap >= cfg["equal_opportunity_gap_warning"]:
            issues.append(
                DiagnosticIssue(
                    id="BIAS_EQUAL_OPPORTUNITY_WARNING",
                    category=self.category,
                    severity=SeverityLevel.WARNING,
                    title=f"Moderate Equal Opportunity Gap (TPR Gap = {max_tpr_gap * 100:.1f}%)",
                    description=f"True Positive Rates vary by {max_tpr_gap * 100:.1f}% across slices of '{prot_attr}'.",
                    affected_features=[prot_attr],
                    evidence={"tprGap": max_tpr_gap},
                    recommendation="Inspect false negative distributions across demographic subgroups.",
                )
            )

        # D. Equalized Odds Gap Finding
        if max_fpr_gap >= cfg["equalized_odds_gap_critical"]:
            issues.append(
                DiagnosticIssue(
                    id="BIAS_EQUALIZED_ODDS_FPR_CRITICAL",
                    category=self.category,
                    severity=SeverityLevel.HIGH,
                    title=f"High False Positive Rate Disparity (FPR Gap = {max_fpr_gap * 100:.1f}%)",
                    description=(
                        f"False Positive Rates diverge by {max_fpr_gap * 100:.1f}% across subgroups of '{prot_attr}' "
                        f"(Highest FPR group: '{highest_fpr_group}'). Model triggers disproportionate false alarms on this slice."
                    ),
                    affected_features=[prot_attr],
                    evidence={"fprGap": max_fpr_gap, "highestFprGroup": highest_fpr_group},
                    recommendation="Evaluate negative-class regularizations to minimize disproportionate false alarms.",
                )
            )

        # E. Small Group Sample Warning
        if small_group_count > 0:
            small_names = [r["group"] for r in group_records if r["isSmallSample"]]
            issues.append(
                DiagnosticIssue(
                    id="BIAS_SMALL_GROUP_SAMPLE",
                    category=self.category,
                    severity=SeverityLevel.WARNING,
                    title=f"{small_group_count} Protected Slice(s) Below Minimum Sample Size (N < {min_group_sz})",
                    description=(
                        f"Subgroups ({', '.join(small_names)}) contain fewer than {min_group_sz} observations. "
                        f"Fairness rate estimates on small cohorts exhibit high statistical variance."
                    ),
                    affected_features=[prot_attr],
                    evidence={"smallGroups": small_names, "minimumSize": min_group_sz},
                    recommendation="Collect additional evaluation observations for under-represented demographic slices.",
                )
            )

        # F. Group Calibration Disparity Finding
        if worst_calib_gap >= cfg["group_calibration_gap_critical"]:
            issues.append(
                DiagnosticIssue(
                    id="BIAS_GROUP_CALIBRATION_DISPARITY",
                    category=self.category,
                    severity=SeverityLevel.WARNING,
                    title=f"Group Calibration Gap ({worst_calib_gap * 100:.1f}%) Across Protected Slices",
                    description=f"Model probability outputs diverge from empirical positive event rates by up to {worst_calib_gap * 100:.1f}% across groups.",
                    affected_features=[prot_attr],
                    evidence={"worstCalibrationGap": worst_calib_gap},
                    recommendation="Apply group-aware probability calibration to align predicted probabilities with empirical event frequencies.",
                )
            )

        # 15. Diagnostic Metrics List
        metrics_list.append(
            DiagnosticMetric(
                name="demographic_parity_gap",
                value=float(max_dp_gap),
                threshold_max=cfg["demographic_parity_gap_warning"],
                passed=bool(max_dp_gap < cfg["demographic_parity_gap_warning"]),
            )
        )
        metrics_list.append(
            DiagnosticMetric(
                name="worst_disparate_impact_ratio",
                value=float(worst_dir_val),
                threshold_min=cfg["disparate_impact_low_warning"],
                passed=bool(worst_dir_val >= cfg["disparate_impact_low_warning"]),
            )
        )
        metrics_list.append(
            DiagnosticMetric(
                name="equal_opportunity_tpr_gap",
                value=float(max_tpr_gap),
                threshold_max=cfg["equal_opportunity_gap_warning"],
                passed=bool(max_tpr_gap < cfg["equal_opportunity_gap_warning"]),
            )
        )
        metrics_list.append(
            DiagnosticMetric(
                name="equalized_odds_max_gap",
                value=float(max_eo_odds_gap),
                threshold_max=cfg["equalized_odds_gap_warning"],
                passed=bool(max_eo_odds_gap < cfg["equalized_odds_gap_warning"]),
            )
        )

        # 16. Health Score Calculation
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
        passed = bool(health_score >= 70.0 and critical_count == 0 and worst_dir_val >= 0.70)

        exec_time_ms = round((time.perf_counter() - start_time) * 1000, 2)

        summary_dict = {
            "protectedAttribute": prot_attr,
            "groupCount": group_count,
            "referenceGroup": str(ref_group),
            "referenceSelection": ref_selection_method,
            "totalEvaluatedRows": evaluated_rows,
            "rawTotalRows": raw_total_rows,
            "excludedMissingProtectedAttribute": excluded_rows,
            "excludedMissingRate": excluded_rate,
            "smallGroupsCount": small_group_count,
            "demographicParityGap": max_dp_gap,
            "worstDisparateImpactRatio": worst_dir_val,
            "equalOpportunityGap": max_tpr_gap,
            "equalizedOdds": {
                "tprGap": max_tpr_gap,
                "fprGap": max_fpr_gap,
                "maxGap": max_eo_odds_gap,
            },
            "worstCalibrationGap": worst_calib_gap if has_probabilities else None,
            "hasProbabilities": has_probabilities,
            "findingCount": len(issues),
            "healthScore": health_score,
            "passed": passed,
        }

        metadata_dict = {
            "module": "BIAS",
            "version": "1.0",
            "summary": summary_dict,
            "configuration": {
                "protectedAttribute": prot_attr,
                "referenceGroup": str(ref_group),
                "referenceSelection": ref_selection_method,
                "threshold": threshold,
                "minimumGroupSize": min_group_sz,
            },
            "groups": group_records,
            "disparity": {
                "demographicParity": dp_comparisons,
                "disparateImpact": di_comparisons,
                "equalOpportunity": eo_comparisons,
                "equalizedOdds": {
                    "tprGap": max_tpr_gap,
                    "fprGap": max_fpr_gap,
                    "maxGap": max_eo_odds_gap,
                },
                "calibration": calib_comparisons,
            },
            "worstGroups": worst_groups,
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
