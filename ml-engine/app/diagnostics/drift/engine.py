import logging
import time
from typing import Any, Dict, List, Optional, Tuple
import numpy as np
import pandas as pd
from scipy import stats
from statsmodels.stats.multitest import multipletests

from app.diagnostics.base import BaseDiagnosticEngine
from app.schemas.diagnostic_models import (
    DiagnosticCategory,
    DiagnosticIssue,
    DiagnosticMetric,
    DiagnosticReport,
    SeverityLevel,
)

logger = logging.getLogger(__name__)


class DataDriftEngine(BaseDiagnosticEngine):
    """
    Forensic Distribution Drift Diagnostic Engine.
    Executes real statistical and distribution shift audits between baseline (reference/training)
    and evaluation (production/current) datasets:
    - Comprehensive schema comparison (row/col count delta, missing/new columns, dtype mismatch)
    - Two-sample Kolmogorov-Smirnov (KS) test for numerical continuous features
    - Scale-normalized Wasserstein-1 (Earth Mover's) distance with baseline IQR normalization
    - Quantile-binned Population Stability Index (PSI) with identical bin edges and zero-epsilon smoothing
    - Chi-Square test of independence on unified categorical contingency tables
    - Categorical PSI across category proportions
    - Benjamini-Hochberg False Discovery Rate (FDR) multiple-testing correction
    - Grounded separation of Statistical Significance (adjusted p < 0.05) vs Practical Drift magnitude (KS, PSI, Wasserstein)
    - Deterministic findings generation and category health index calculation
    """

    DEFAULT_CONFIG = {
        # Practical drift magnitude thresholds
        "psi_warning": 0.10,          # PSI 0.10 - 0.25: Moderate shift
        "psi_critical": 0.25,         # PSI >= 0.25: Significant/High shift
        "psi_severe": 0.40,           # PSI >= 0.40: Severe/Critical divergence
        "ks_stat_warning": 0.10,      # KS statistic 0.10 - 0.20: Moderate distribution difference
        "ks_stat_critical": 0.20,     # KS statistic >= 0.20: High distribution difference
        "alpha_significance": 0.05,   # Adjusted p-value significance threshold
        "num_quantile_bins": 10,      # Number of target quantile bins for numeric PSI
        "epsilon": 1e-4,              # Zero-frequency smoothing constant for PSI
    }

    @property
    def category(self) -> DiagnosticCategory:
        return DiagnosticCategory.DRIFT

    @property
    def name(self) -> str:
        return "Distribution Drift Engine"

    @property
    def description(self) -> str:
        return "Calculates Kolmogorov-Smirnov tests, Wasserstein-1 distances, Chi-Square contingency, and PSI to detect covariate and schema drift."

    @staticmethod
    def infer_feature_type(series: pd.Series) -> str:
        """
        Classifies feature as NUMERIC or CATEGORICAL for drift testing.
        """
        non_null = series.dropna()
        if non_null.empty:
            return "UNKNOWN"

        if pd.api.types.is_bool_dtype(series):
            return "CATEGORICAL"

        if non_null.nunique() == 2:
            unique_set = set(non_null.unique())
            if unique_set.issubset({0, 1, 0.0, 1.0, "0", "1", "true", "false", "True", "False", "T", "F", "Y", "N"}):
                return "CATEGORICAL"

        if pd.api.types.is_numeric_dtype(series):
            # If numeric but has extremely low cardinality (e.g. 2 or 3 distinct integers in 1000 rows),
            # check if it behaves as categorical or numeric
            if non_null.nunique() <= 5 and len(non_null) > 50 and np.all(np.equal(np.mod(non_null, 1), 0)):
                # Keep low-cardinality integers as NUMERIC if they represent ordinal scores, but can be treated as NUMERIC
                return "NUMERIC"
            return "NUMERIC"

        return "CATEGORICAL"

    @classmethod
    def calculate_numeric_psi(
        cls,
        baseline_vals: np.ndarray,
        eval_vals: np.ndarray,
        num_bins: int = 10,
        epsilon: float = 1e-4,
    ) -> Tuple[float, Dict[str, Any]]:
        """
        Calculates Population Stability Index (PSI) for continuous numeric distributions:
        1. Derives quantile bin edges strictly from the baseline reference dataset.
        2. Handles duplicate quantile edges from low-cardinality or constant features.
        3. Applies exact same bin edges to both baseline and evaluation.
        4. Calculates expected and actual bin proportions with epsilon smoothing.
        5. PSI = sum( (actual - expected) * ln(actual / expected) )
        """
        if len(baseline_vals) == 0 or len(eval_vals) == 0:
            return 0.0, {"binning_method": "unavailable", "num_bins": 0, "bins": []}

        # Calculate baseline quantile bin edges
        quantiles = np.linspace(0.0, 1.0, num_bins + 1)
        raw_edges = np.percentile(baseline_vals, quantiles * 100)
        unique_edges = np.unique(raw_edges)

        # If zero variance / constant in baseline
        if len(unique_edges) < 2:
            # Baseline is constant
            b_val = baseline_vals[0]
            e_same = np.sum(eval_vals == b_val)
            e_diff = len(eval_vals) - e_same
            if e_diff == 0:
                return 0.0, {
                    "binning_method": "constant_distribution",
                    "num_bins": 1,
                    "bins": [{
                        "binIndex": 0,
                        "binRange": f"= {b_val:.2f}",
                        "baselineDensity": 1.0,
                        "currentDensity": 1.0,
                        "psiDelta": 0.0,
                    }],
                }
            else:
                diff_rate = e_diff / len(eval_vals)
                psi_val = float(min(10.0, diff_rate * 5.0))
                return psi_val, {
                    "binning_method": "constant_divergence",
                    "num_bins": 2,
                    "bins": [
                        {
                            "binIndex": 0,
                            "binRange": f"= {b_val:.2f}",
                            "baselineDensity": 1.0,
                            "currentDensity": round(e_same / len(eval_vals), 4),
                            "psiDelta": round(psi_val / 2, 4),
                        },
                        {
                            "binIndex": 1,
                            "binRange": f"!= {b_val:.2f}",
                            "baselineDensity": 0.0,
                            "currentDensity": round(e_diff / len(eval_vals), 4),
                            "psiDelta": round(psi_val / 2, 4),
                        },
                    ],
                }

        # Expand outer edges to cover +/- infinity
        edges = unique_edges.copy()
        edges[0] = -np.inf
        edges[-1] = np.inf

        b_counts, _ = np.histogram(baseline_vals, bins=edges)
        e_counts, _ = np.histogram(eval_vals, bins=edges)

        n_b = np.sum(b_counts)
        n_e = np.sum(e_counts)

        n_bins_actual = len(b_counts)
        expected_props = (b_counts + epsilon) / (n_b + epsilon * n_bins_actual)
        actual_props = (e_counts + epsilon) / (n_e + epsilon * n_bins_actual)

        psi_components = (actual_props - expected_props) * np.log(actual_props / expected_props)
        total_psi = float(np.sum(psi_components))

        bins_detail = []
        for i in range(n_bins_actual):
            lower_str = "-inf" if np.isneginf(edges[i]) else f"{edges[i]:.2f}"
            upper_str = "+inf" if np.isposinf(edges[i + 1]) else f"{edges[i + 1]:.2f}"
            bin_range_str = f"[{lower_str}, {upper_str})" if i < n_bins_actual - 1 else f"[{lower_str}, {upper_str}]"
            
            raw_b_prop = float(b_counts[i] / n_b) if n_b > 0 else 0.0
            raw_e_prop = float(e_counts[i] / n_e) if n_e > 0 else 0.0
            delta_psi = float(psi_components[i])

            bins_detail.append({
                "binIndex": i,
                "binRange": bin_range_str,
                "baselineDensity": round(raw_b_prop, 4),
                "currentDensity": round(raw_e_prop, 4),
                "psiDelta": round(delta_psi, 4),
            })

        return round(total_psi, 6), {
            "binning_method": "baseline_quantile",
            "num_bins": n_bins_actual,
            "bins": bins_detail,
        }

    @classmethod
    def calculate_categorical_psi(
        cls,
        baseline_counts: np.ndarray,
        eval_counts: np.ndarray,
        categories: List[str],
        epsilon: float = 1e-4,
    ) -> Tuple[float, List[Dict[str, Any]]]:
        """
        Calculates Categorical PSI across common & distinct categories:
        expected = baseline category proportions (with epsilon smoothing)
        actual = evaluation category proportions (with epsilon smoothing)
        """
        n_b = np.sum(baseline_counts)
        n_e = np.sum(eval_counts)
        k = len(categories)

        if k == 0 or n_b == 0 or n_e == 0:
            return 0.0, []

        expected_props = (baseline_counts + epsilon) / (n_b + epsilon * k)
        actual_props = (eval_counts + epsilon) / (n_e + epsilon * k)

        psi_components = (actual_props - expected_props) * np.log(actual_props / expected_props)
        total_psi = float(np.sum(psi_components))

        category_details = []
        for i, cat in enumerate(categories):
            b_cnt = int(baseline_counts[i])
            e_cnt = int(eval_counts[i])
            b_prop = round(b_cnt / n_b, 4) if n_b > 0 else 0.0
            e_prop = round(e_cnt / n_e, 4) if n_e > 0 else 0.0
            p_delta = round(e_prop - b_prop, 4)
            cat_psi = round(float(psi_components[i]), 4)

            category_details.append({
                "category": str(cat),
                "baselineCount": b_cnt,
                "baselineProportion": b_prop,
                "evaluationCount": e_cnt,
                "evaluationProportion": e_prop,
                "proportionDelta": p_delta,
                "psiDelta": cat_psi,
            })

        return round(total_psi, 6), category_details

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
        metrics: List[DiagnosticMetric] = []

        # 1. Validate baseline dataset availability
        if baseline_data is None or not isinstance(baseline_data, pd.DataFrame):
            issue = DiagnosticIssue(
                id="DRIFT-NO-BASELINE",
                category=DiagnosticCategory.DRIFT,
                severity=SeverityLevel.CRITICAL,
                title="Baseline Reference Dataset Missing",
                description="Distribution drift requires a baseline reference dataset (training or historical golden distribution) to calculate statistical divergence.",
                affected_features=[],
                evidence={"baselineAvailable": False},
                recommendation="Provide a baseline dataset reference (e.g., training partition) when submitting the diagnostic run request.",
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
                    "module": "DRIFT",
                    "version": "1.0",
                    "summary": {
                        "error": "Baseline dataset is required for drift analysis.",
                        "healthScore": 0.0,
                        "passed": False,
                    },
                    "features": [],
                    "findings": [issue.model_dump()],
                },
            )

        if current_data is None or not isinstance(current_data, pd.DataFrame):
            raise ValueError("Evaluation dataset must be a valid pandas DataFrame.")

        # 2. Schema Comparison & Topology
        baseline_row_count = int(len(baseline_data))
        eval_row_count = int(len(current_data))
        baseline_columns = [str(c) for c in baseline_data.columns]
        eval_columns = [str(c) for c in current_data.columns]

        common_columns = [c for c in baseline_columns if c in eval_columns]
        missing_in_eval = [c for c in baseline_columns if c not in eval_columns]
        new_in_eval = [c for c in eval_columns if c not in baseline_columns]

        dtype_mismatches: List[Dict[str, str]] = []
        for col in common_columns:
            b_dtype = str(baseline_data[col].dtype)
            e_dtype = str(current_data[col].dtype)
            b_type = self.infer_feature_type(baseline_data[col])
            e_type = self.infer_feature_type(current_data[col])
            if b_type != e_type:
                dtype_mismatches.append({
                    "column": col,
                    "baselineDtype": b_dtype,
                    "evaluationDtype": e_dtype,
                    "baselineInferred": b_type,
                    "evaluationInferred": e_type,
                })

        # Generate Schema Findings
        if missing_in_eval:
            issues.append(
                DiagnosticIssue(
                    id="SCHEMA_MISSING_IN_EVALUATION",
                    category=DiagnosticCategory.DRIFT,
                    severity=SeverityLevel.CRITICAL,
                    title="Schema Drift: Baseline Columns Missing in Evaluation",
                    description=f"{len(missing_in_eval)} column(s) present in the reference baseline dataset are missing from the evaluation dataset partition: {missing_in_eval}.",
                    affected_features=missing_in_eval,
                    evidence={
                        "missingColumns": missing_in_eval,
                        "missingCount": len(missing_in_eval),
                    },
                    recommendation="Ensure the ingestion and feature pipeline supplies all required input features before feeding downstream models.",
                )
            )

        if new_in_eval:
            issues.append(
                DiagnosticIssue(
                    id="SCHEMA_NEW_IN_EVALUATION",
                    category=DiagnosticCategory.DRIFT,
                    severity=SeverityLevel.INFO,
                    title="Schema Drift: New Columns Detected in Evaluation",
                    description=f"{len(new_in_eval)} column(s) are present in the evaluation partition that did not exist in the reference baseline: {new_in_eval}.",
                    affected_features=new_in_eval,
                    evidence={
                        "newColumns": new_in_eval,
                        "newCount": len(new_in_eval),
                    },
                    recommendation="Verify whether newly added features are intended for future model iterations or represent pipeline schema drift.",
                )
            )

        if dtype_mismatches:
            mismatched_cols = [m["column"] for m in dtype_mismatches]
            issues.append(
                DiagnosticIssue(
                    id="SCHEMA_TYPE_MISMATCH",
                    category=DiagnosticCategory.DRIFT,
                    severity=SeverityLevel.HIGH,
                    title="Schema Drift: Semantic Data Type Mismatches",
                    description=f"Inferred data types differ between baseline and evaluation for {len(dtype_mismatches)} column(s).",
                    affected_features=mismatched_cols,
                    evidence={"mismatches": dtype_mismatches},
                    recommendation="Align feature encoding pipelines to prevent type casting failures during model inference.",
                )
            )

        # 3. Separate Features by Inferred Type for Analysis
        # Only common features with compatible types enter statistical testing
        compatible_features = [c for c in common_columns if c not in [m["column"] for m in dtype_mismatches]]

        numeric_features_to_test: List[str] = []
        categorical_features_to_test: List[str] = []

        for col in compatible_features:
            f_type = self.infer_feature_type(baseline_data[col])
            if f_type == "NUMERIC":
                numeric_features_to_test.append(col)
            else:
                categorical_features_to_test.append(col)

        # 4. Numeric Drift Calculations
        raw_numeric_results: Dict[str, Dict[str, Any]] = {}
        numeric_p_values: List[float] = []
        numeric_tested_features: List[str] = []
        untestable_features: List[str] = []

        for col in numeric_features_to_test:
            # Extract and sanitize numeric series
            b_num = pd.to_numeric(baseline_data[col], errors="coerce").dropna()
            e_num = pd.to_numeric(current_data[col], errors="coerce").dropna()

            b_finite = b_num[np.isfinite(b_num)].to_numpy(dtype=float)
            e_finite = e_num[np.isfinite(e_num)].to_numpy(dtype=float)

            b_count = len(b_finite)
            e_count = len(e_finite)

            if b_count < 2 or e_count < 2:
                untestable_features.append(col)
                issues.append(
                    DiagnosticIssue(
                        id=f"FEATURE_UNTESTABLE_{col}",
                        category=DiagnosticCategory.DRIFT,
                        severity=SeverityLevel.WARNING,
                        title=f"Feature '{col}' Untestable Due to Insufficient Valid Samples",
                        description=f"Feature '{col}' contains {b_count} valid baseline and {e_count} valid evaluation observations (minimum 2 required).",
                        affected_features=[col],
                        evidence={"baselineCount": b_count, "evaluationCount": e_count},
                        recommendation="Inspect data missingness or non-finite values in this feature.",
                    )
                )
                raw_numeric_results[col] = {
                    "feature": col,
                    "type": "NUMERIC",
                    "baselineCount": b_count,
                    "evaluationCount": e_count,
                    "status": "UNTESTABLE",
                    "ks": {"statistic": 0.0, "p_value": 1.0, "adjusted_p_value": 1.0, "correction_method": "none"},
                    "wasserstein": {"distance": 0.0, "normalized_distance": None, "normalization": "unavailable"},
                    "psi": 0.0,
                    "statistically_significant": False,
                    "practical_drift": "LOW",
                    "severity": "LOW",
                    "binning": {"binning_method": "unavailable", "num_bins": 0, "bins": []},
                }
                continue

            # A. Two-Sample Kolmogorov-Smirnov Test
            ks_res = stats.ks_2samp(b_finite, e_finite)
            ks_stat = float(ks_res.statistic)
            ks_pval = float(ks_res.pvalue)

            # B. Wasserstein-1 Distance & Scale Normalization
            w_dist = float(stats.wasserstein_distance(b_finite, e_finite))
            q75, q25 = np.percentile(b_finite, [75, 25])
            b_iqr = float(q75 - q25)

            if b_iqr > 0.0:
                norm_w_dist = round(w_dist / b_iqr, 6)
                w_norm_method = "baseline_iqr"
            else:
                b_std = float(np.std(b_finite))
                if b_std > 0.0:
                    norm_w_dist = round(w_dist / b_std, 6)
                    w_norm_method = "baseline_std"
                else:
                    norm_w_dist = None
                    w_norm_method = "unavailable_zero_variance"

            # C. Population Stability Index (PSI)
            psi_val, binning_info = self.calculate_numeric_psi(
                baseline_vals=b_finite,
                eval_vals=e_finite,
                num_bins=cfg["num_quantile_bins"],
                epsilon=cfg["epsilon"],
            )

            numeric_tested_features.append(col)
            numeric_p_values.append(ks_pval)

            raw_numeric_results[col] = {
                "feature": col,
                "type": "NUMERIC",
                "baselineCount": b_count,
                "evaluationCount": e_count,
                "status": "EVALUATED",
                "ks": {
                    "statistic": round(ks_stat, 6),
                    "p_value": ks_pval,
                    "adjusted_p_value": ks_pval,  # will be updated by FDR correction below
                    "correction_method": "benjamini_hochberg",
                },
                "wasserstein": {
                    "distance": round(w_dist, 6),
                    "normalized_distance": norm_w_dist,
                    "normalization": w_norm_method,
                },
                "psi": psi_val,
                "binning": binning_info,
            }

        # Apply Benjamini-Hochberg FDR correction on numeric KS p-values
        if len(numeric_p_values) >= 2:
            try:
                _, adj_pvals, _, _ = multipletests(numeric_p_values, alpha=cfg["alpha_significance"], method="fdr_bh")
                for i, col in enumerate(numeric_tested_features):
                    raw_numeric_results[col]["ks"]["adjusted_p_value"] = float(adj_pvals[i])
            except Exception as e:
                logger.warning("Failed to execute multipletests on numeric KS p-values: %s", str(e))
        elif len(numeric_p_values) == 1:
            raw_numeric_results[numeric_tested_features[0]]["ks"]["adjusted_p_value"] = numeric_p_values[0]

        # 5. Categorical Drift Calculations
        raw_categorical_results: Dict[str, Dict[str, Any]] = {}
        categorical_p_values: List[float] = []
        categorical_tested_features: List[str] = []

        for col in categorical_features_to_test:
            b_cat = baseline_data[col].dropna().astype(str)
            e_cat = current_data[col].dropna().astype(str)

            b_count = len(b_cat)
            e_count = len(e_cat)

            if b_count < 2 or e_count < 2:
                untestable_features.append(col)
                issues.append(
                    DiagnosticIssue(
                        id=f"FEATURE_UNTESTABLE_{col}",
                        category=DiagnosticCategory.DRIFT,
                        severity=SeverityLevel.WARNING,
                        title=f"Categorical Feature '{col}' Untestable Due to Insufficient Samples",
                        description=f"Categorical feature '{col}' has {b_count} baseline and {e_count} evaluation observations.",
                        affected_features=[col],
                        evidence={"baselineCount": b_count, "evaluationCount": e_count},
                        recommendation="Verify categorical encoding or check for excessive missingness.",
                    )
                )
                raw_categorical_results[col] = {
                    "feature": col,
                    "type": "CATEGORICAL",
                    "baselineCount": b_count,
                    "evaluationCount": e_count,
                    "status": "UNTESTABLE",
                    "chi_square": {
                        "statistic": 0.0,
                        "p_value": 1.0,
                        "adjusted_p_value": 1.0,
                        "degrees_of_freedom": 0,
                        "correction_method": "none",
                    },
                    "psi": 0.0,
                    "statistically_significant": False,
                    "practical_drift": "LOW",
                    "severity": "LOW",
                    "categoryDistribution": [],
                }
                continue

            all_cats = sorted(list(set(b_cat.unique()).union(set(e_cat.unique()))))
            b_freq = b_cat.value_counts()
            e_freq = e_cat.value_counts()

            b_counts = np.array([b_freq.get(cat, 0) for cat in all_cats], dtype=int)
            e_counts = np.array([e_freq.get(cat, 0) for cat in all_cats], dtype=int)

            # A. Chi-Square Test of Independence
            if len(all_cats) >= 2 and np.sum(b_counts) > 0 and np.sum(e_counts) > 0:
                contingency_table = np.array([b_counts, e_counts])
                try:
                    chi2_res = stats.chi2_contingency(contingency_table)
                    chi2_stat = float(chi2_res.statistic)
                    chi2_pval = float(chi2_res.pvalue)
                    dof = int(chi2_res.dof)
                except Exception as e:
                    logger.debug("Chi-Square calculation exception for %s: %s", col, str(e))
                    chi2_stat = 0.0
                    chi2_pval = 1.0
                    dof = len(all_cats) - 1
            else:
                chi2_stat = 0.0
                chi2_pval = 1.0
                dof = 0

            # B. Categorical PSI
            cat_psi_val, cat_distribution = self.calculate_categorical_psi(
                baseline_counts=b_counts,
                eval_counts=e_counts,
                categories=all_cats,
                epsilon=cfg["epsilon"],
            )

            categorical_tested_features.append(col)
            categorical_p_values.append(chi2_pval)

            raw_categorical_results[col] = {
                "feature": col,
                "type": "CATEGORICAL",
                "baselineCount": b_count,
                "evaluationCount": e_count,
                "status": "EVALUATED",
                "chi_square": {
                    "statistic": round(chi2_stat, 6),
                    "p_value": chi2_pval,
                    "adjusted_p_value": chi2_pval,
                    "degrees_of_freedom": dof,
                    "correction_method": "benjamini_hochberg",
                },
                "psi": cat_psi_val,
                "categoryDistribution": cat_distribution,
            }

        # Apply Benjamini-Hochberg FDR correction on categorical Chi-Square p-values
        if len(categorical_p_values) >= 2:
            try:
                _, adj_pvals, _, _ = multipletests(categorical_p_values, alpha=cfg["alpha_significance"], method="fdr_bh")
                for i, col in enumerate(categorical_tested_features):
                    raw_categorical_results[col]["chi_square"]["adjusted_p_value"] = float(adj_pvals[i])
            except Exception as e:
                logger.warning("Failed to execute multipletests on categorical Chi-Square p-values: %s", str(e))
        elif len(categorical_p_values) == 1:
            raw_categorical_results[categorical_tested_features[0]]["chi_square"]["adjusted_p_value"] = categorical_p_values[0]

        # 6. Synthesize Practical vs Statistical Significance and Severity
        final_features_profile: List[Dict[str, Any]] = []

        sig_drift_count = 0
        practical_drift_count = 0
        critical_drift_count = 0
        high_drift_count = 0
        medium_drift_count = 0
        low_drift_count = 0

        # Process Numeric Features
        for col, res in raw_numeric_results.items():
            if res.get("status") == "UNTESTABLE":
                final_features_profile.append(res)
                low_drift_count += 1
                continue

            ks_stat = res["ks"]["statistic"]
            adj_p = res["ks"]["adjusted_p_value"]
            psi = res["psi"]
            w_norm = res["wasserstein"]["normalized_distance"]

            stat_sig = bool(adj_p < cfg["alpha_significance"])
            if stat_sig:
                sig_drift_count += 1

            # Practical drift magnitude evaluation
            if psi >= cfg["psi_critical"] or ks_stat >= cfg["ks_stat_critical"]:
                practical_drift = "HIGH"
                practical_drift_count += 1
            elif psi >= cfg["psi_warning"] or ks_stat >= cfg["ks_stat_warning"]:
                practical_drift = "MEDIUM"
            else:
                practical_drift = "LOW"

            # Multi-tier Severity synthesis
            if psi >= cfg["psi_severe"] or (practical_drift == "HIGH" and stat_sig and (ks_stat >= 0.35 or psi >= 0.35)):
                severity = SeverityLevel.CRITICAL
                critical_drift_count += 1
            elif practical_drift == "HIGH" or (practical_drift == "MEDIUM" and stat_sig):
                severity = SeverityLevel.HIGH
                high_drift_count += 1
            elif practical_drift == "MEDIUM" or (practical_drift == "LOW" and stat_sig):
                severity = SeverityLevel.WARNING
                medium_drift_count += 1
            else:
                severity = SeverityLevel.LOW
                low_drift_count += 1

            res["statistically_significant"] = stat_sig
            res["practical_drift"] = practical_drift
            res["severity"] = severity.value
            final_features_profile.append(res)

            # Generate Forensic Diagnostic Finding if severe/significant
            if severity == SeverityLevel.CRITICAL:
                issues.append(
                    DiagnosticIssue(
                        id=f"HIGH_NUMERIC_DRIFT_{col}",
                        category=DiagnosticCategory.DRIFT,
                        severity=SeverityLevel.CRITICAL,
                        title=f"Critical Distribution Shift on Continuous Feature '{col}'",
                        description=(
                            f"Continuous feature '{col}' exhibits severe distribution divergence between baseline and evaluation "
                            f"(PSI={psi:.4f}, KS={ks_stat:.4f}, FDR adj p={adj_p:.4e}, norm Wasserstein={w_norm if w_norm is not None else 'N/A'})."
                        ),
                        affected_features=[col],
                        evidence={
                            "feature": col,
                            "psi": psi,
                            "ks_statistic": ks_stat,
                            "adjusted_p_value": adj_p,
                            "wasserstein_distance": res["wasserstein"]["distance"],
                            "normalized_wasserstein": w_norm,
                        },
                        recommendation=f"Evaluation distribution differs materially from the baseline distribution for '{col}'. Inspect covariate shift and retrain or recalibrate model.",
                    )
                )
            elif severity == SeverityLevel.HIGH:
                issues.append(
                    DiagnosticIssue(
                        id=f"HIGH_NUMERIC_DRIFT_{col}",
                        category=DiagnosticCategory.DRIFT,
                        severity=SeverityLevel.HIGH,
                        title=f"High Distribution Drift on Feature '{col}'",
                        description=(
                            f"Continuous feature '{col}' exhibits substantial practical distribution shift "
                            f"(PSI={psi:.4f}, KS={ks_stat:.4f}, FDR adj p={adj_p:.4e})."
                        ),
                        affected_features=[col],
                        evidence={
                            "feature": col,
                            "psi": psi,
                            "ks_statistic": ks_stat,
                            "adjusted_p_value": adj_p,
                            "wasserstein_distance": res["wasserstein"]["distance"],
                        },
                        recommendation=f"Feature '{col}' shows notable distribution divergence. Consider domain adaptation, sample reweighting, or threshold tuning.",
                    )
                )
            elif severity == SeverityLevel.WARNING and stat_sig:
                issues.append(
                    DiagnosticIssue(
                        id=f"SIGNIFICANT_NUMERIC_DRIFT_{col}",
                        category=DiagnosticCategory.DRIFT,
                        severity=SeverityLevel.WARNING,
                        title=f"Statistically Significant Distribution Shift on '{col}'",
                        description=(
                            f"Continuous feature '{col}' shows statistically significant moderate drift "
                            f"(KS={ks_stat:.4f}, PSI={psi:.4f}, FDR adj p={adj_p:.4e})."
                        ),
                        affected_features=[col],
                        evidence={
                            "feature": col,
                            "psi": psi,
                            "ks_statistic": ks_stat,
                            "adjusted_p_value": adj_p,
                        },
                        recommendation=f"Track live telemetry on feature '{col}' to monitor if drift continues upward.",
                    )
                )

        # Process Categorical Features
        for col, res in raw_categorical_results.items():
            if res.get("status") == "UNTESTABLE":
                final_features_profile.append(res)
                low_drift_count += 1
                continue

            chi2_stat = res["chi_square"]["statistic"]
            adj_p = res["chi_square"]["adjusted_p_value"]
            psi = res["psi"]

            stat_sig = bool(adj_p < cfg["alpha_significance"])
            if stat_sig:
                sig_drift_count += 1

            if psi >= cfg["psi_critical"]:
                practical_drift = "HIGH"
                practical_drift_count += 1
            elif psi >= cfg["psi_warning"]:
                practical_drift = "MEDIUM"
            else:
                practical_drift = "LOW"

            if psi >= cfg["psi_severe"] or (practical_drift == "HIGH" and stat_sig and psi >= 0.35):
                severity = SeverityLevel.CRITICAL
                critical_drift_count += 1
            elif practical_drift == "HIGH" or (practical_drift == "MEDIUM" and stat_sig):
                severity = SeverityLevel.HIGH
                high_drift_count += 1
            elif practical_drift == "MEDIUM" or (practical_drift == "LOW" and stat_sig):
                severity = SeverityLevel.WARNING
                medium_drift_count += 1
            else:
                severity = SeverityLevel.LOW
                low_drift_count += 1

            res["statistically_significant"] = stat_sig
            res["practical_drift"] = practical_drift
            res["severity"] = severity.value
            final_features_profile.append(res)

            # Generate Findings for Categorical
            if severity == SeverityLevel.CRITICAL:
                issues.append(
                    DiagnosticIssue(
                        id=f"HIGH_CATEGORICAL_DRIFT_{col}",
                        category=DiagnosticCategory.DRIFT,
                        severity=SeverityLevel.CRITICAL,
                        title=f"Critical Categorical Shift on '{col}'",
                        description=f"Categorical feature '{col}' shows severe category proportion divergence (PSI={psi:.4f}, Chi2={chi2_stat:.2f}, FDR adj p={adj_p:.4e}).",
                        affected_features=[col],
                        evidence={"feature": col, "psi": psi, "chi_square": chi2_stat, "adjusted_p_value": adj_p},
                        recommendation=f"Audit categorical distribution shift for '{col}'. Check for new unmodeled categories or sudden population mix shifts.",
                    )
                )
            elif severity == SeverityLevel.HIGH:
                issues.append(
                    DiagnosticIssue(
                        id=f"HIGH_CATEGORICAL_DRIFT_{col}",
                        category=DiagnosticCategory.DRIFT,
                        severity=SeverityLevel.HIGH,
                        title=f"High Categorical Drift on '{col}'",
                        description=f"Categorical feature '{col}' exhibits significant distribution shift (PSI={psi:.4f}, Chi2={chi2_stat:.2f}, FDR adj p={adj_p:.4e}).",
                        affected_features=[col],
                        evidence={"feature": col, "psi": psi, "chi_square": chi2_stat, "adjusted_p_value": adj_p},
                        recommendation=f"Update categorical encodings or inspect source upstream data changes for '{col}'.",
                    )
                )
            elif severity == SeverityLevel.WARNING and stat_sig:
                issues.append(
                    DiagnosticIssue(
                        id=f"SIGNIFICANT_CATEGORICAL_DRIFT_{col}",
                        category=DiagnosticCategory.DRIFT,
                        severity=SeverityLevel.WARNING,
                        title=f"Statistically Significant Categorical Shift on '{col}'",
                        description=f"Categorical feature '{col}' exhibits statistically significant proportion shift (PSI={psi:.4f}, Chi2={chi2_stat:.2f}, FDR adj p={adj_p:.4e}).",
                        affected_features=[col],
                        evidence={"feature": col, "psi": psi, "chi_square": chi2_stat, "adjusted_p_value": adj_p},
                        recommendation=f"Monitor category proportions of '{col}' in production telemetry.",
                    )
                )

        # 7. Sort features by PSI descending (highest divergence first)
        final_features_profile.sort(key=lambda x: x.get("psi", 0.0), reverse=True)

        # Calculate max PSI and average Wasserstein across numeric evaluated features
        max_psi = 0.0
        max_psi_feature = None
        if final_features_profile:
            max_psi = final_features_profile[0].get("psi", 0.0)
            max_psi_feature = final_features_profile[0].get("feature")

        numeric_w_distances = [
            f["wasserstein"]["distance"]
            for f in final_features_profile
            if f.get("type") == "NUMERIC" and "wasserstein" in f and f["wasserstein"].get("distance") is not None
        ]
        avg_wasserstein = round(float(np.mean(numeric_w_distances)), 4) if numeric_w_distances else 0.0

        # Summary Metrics
        metrics.append(
            DiagnosticMetric(
                name="max_psi",
                value=float(max_psi),
                threshold_max=cfg["psi_critical"],
                passed=bool(max_psi < cfg["psi_critical"]),
            )
        )
        metrics.append(
            DiagnosticMetric(
                name="statistically_significant_drift_count",
                value=float(sig_drift_count),
                threshold_max=0.0,
                passed=bool(sig_drift_count == 0),
            )
        )
        metrics.append(
            DiagnosticMetric(
                name="high_drift_feature_count",
                value=float(high_drift_count + critical_drift_count),
                threshold_max=0.0,
                passed=bool((high_drift_count + critical_drift_count) == 0),
            )
        )
        metrics.append(
            DiagnosticMetric(
                name="schema_missing_columns_count",
                value=float(len(missing_in_eval)),
                threshold_max=0.0,
                passed=bool(len(missing_in_eval) == 0),
            )
        )

        # 8. Deterministic Health Score Calculation
        penalty = 0.0
        for issue in issues:
            if issue.severity == SeverityLevel.CRITICAL:
                penalty += 20.0
            elif issue.severity in [SeverityLevel.HIGH, SeverityLevel.WARNING]:
                penalty += 8.0
            elif issue.severity == SeverityLevel.MEDIUM:
                penalty += 4.0
            elif issue.severity == SeverityLevel.LOW:
                penalty += 1.0

        health_score = max(0.0, min(100.0, round(100.0 - penalty, 1)))
        passed = bool(health_score >= 70.0 and critical_drift_count == 0 and len(missing_in_eval) == 0)

        exec_time_ms = round((time.perf_counter() - start_time) * 1000, 2)

        summary_dict = {
            "baselineRowCount": baseline_row_count,
            "evaluationRowCount": eval_row_count,
            "baselineColumnCount": len(baseline_columns),
            "evaluationColumnCount": len(eval_columns),
            "featuresEvaluated": len(final_features_profile),
            "numericFeaturesEvaluated": len(numeric_tested_features),
            "categoricalFeaturesEvaluated": len(categorical_tested_features),
            "statisticallySignificantCount": sig_drift_count,
            "practicalDriftCount": practical_drift_count,
            "criticalDriftCount": critical_drift_count,
            "highDriftCount": high_drift_count,
            "mediumDriftCount": medium_drift_count,
            "lowDriftCount": low_drift_count,
            "untestableCount": len(untestable_features),
            "maxPsi": max_psi,
            "maxPsiFeature": max_psi_feature,
            "avgWasserstein": avg_wasserstein,
            "healthScore": health_score,
            "passed": passed,
        }

        schema_dict = {
            "baselineRowCount": baseline_row_count,
            "evaluationRowCount": eval_row_count,
            "baselineColumnCount": len(baseline_columns),
            "evaluationColumnCount": len(eval_columns),
            "commonColumns": common_columns,
            "missingInEvaluation": missing_in_eval,
            "newInEvaluation": new_in_eval,
            "dtypeDifferences": dtype_mismatches,
        }

        metadata_dict = {
            "module": "DRIFT",
            "version": "1.0",
            "summary": summary_dict,
            "schema": schema_dict,
            "features": final_features_profile,
            "findings": [issue.model_dump() for issue in issues],
            "metrics": [m.model_dump() for m in metrics],
        }

        return DiagnosticReport(
            engine_name=self.name,
            category=self.category,
            health_score=health_score,
            passed=passed,
            execution_time_ms=exec_time_ms,
            metrics=metrics,
            issues=issues,
            metadata=metadata_dict,
        )
