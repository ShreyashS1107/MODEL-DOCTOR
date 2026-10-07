import logging
import time
from typing import Any, Dict, List, Optional
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


class DataQualityEngine(BaseDiagnosticEngine):
    """
    Forensic Data Quality Engine.
    Executes statistical audits on tabular datasets:
    - Dataset-level metrics (rows, columns, duplicates, memory footprint)
    - Missingness topology (per-column count, missing rate, global cell loss)
    - Constant & near-constant feature detection with configurable dominance threshold
    - Inferred semantic type classification (NUMERIC, CATEGORICAL, BOOLEAN, DATETIME, TEXT, UNKNOWN)
    - Numerical outlier detection using standard Interquartile Range (IQR) bounds
    - Non-finite value inspection (+Infinity, -Infinity vs standard nulls)
    - Rule-based diagnostic findings with grounded recommendations
    """

    DEFAULT_CONFIG = {
        "missing_rate_warning": 0.02,     # 2.0%
        "missing_rate_critical": 0.20,    # 20.0%
        "outlier_rate_warning": 0.05,     # 5.0%
        "outlier_rate_critical": 0.15,    # 15.0%
        "duplicate_rate_warning": 0.01,   # 1.0%
        "duplicate_rate_critical": 0.05,  # 5.0%
        "near_constant_threshold": 0.98,  # 98.0% dominant value
    }

    @property
    def category(self) -> DiagnosticCategory:
        return DiagnosticCategory.DATA_QUALITY

    @property
    def name(self) -> str:
        return "Data Quality Engine"

    @property
    def description(self) -> str:
        return "Statistical audit of dataset integrity, missingness patterns, constant features, duplicates, and numeric outliers."

    @staticmethod
    def infer_column_type(series: pd.Series) -> str:
        """
        Infers semantic data type using deterministic statistical heuristics.
        Returns one of: NUMERIC, CATEGORICAL, BOOLEAN, DATETIME, TEXT, UNKNOWN
        """
        non_null = series.dropna()
        if non_null.empty:
            return "UNKNOWN"

        # 1. Check for boolean
        if pd.api.types.is_bool_dtype(series):
            return "BOOLEAN"
        
        # If series has exactly 2 unique values and they are standard boolean tokens / 0 & 1
        if non_null.nunique() == 2:
            unique_set = set(non_null.unique())
            if unique_set.issubset({0, 1, 0.0, 1.0, "0", "1", "true", "false", "True", "False", "T", "F", "Y", "N"}):
                return "BOOLEAN"

        # 2. Check for datetime
        if pd.api.types.is_datetime64_any_dtype(series):
            return "DATETIME"
        
        if pd.api.types.is_string_dtype(series) or pd.api.types.is_object_dtype(series):
            # Probe a small sample to see if it represents ISO or standard timestamp format
            sample = non_null.astype(str).head(20)
            parsed_count = 0
            for val in sample:
                val_str = val.strip()
                if len(val_str) >= 8 and ("-" in val_str or "/" in val_str or "T" in val_str or ":" in val_str):
                    try:
                        pd.to_datetime(val_str)
                        parsed_count += 1
                    except (ValueError, TypeError, OverflowError):
                        break
            if len(sample) > 0 and (parsed_count / len(sample)) >= 0.85:
                return "DATETIME"

        # 3. Check for numeric
        if pd.api.types.is_numeric_dtype(series):
            return "NUMERIC"

        # 4. Check for text vs categorical
        if pd.api.types.is_string_dtype(series) or pd.api.types.is_object_dtype(series):
            str_series = non_null.astype(str)
            avg_len = str_series.str.len().mean()
            unique_ratio = non_null.nunique() / len(non_null) if len(non_null) > 0 else 0.0
            if avg_len > 60 and unique_ratio > 0.4:
                return "TEXT"
            return "CATEGORICAL"

        return "CATEGORICAL"

    @staticmethod
    def calculate_outliers_iqr(series: pd.Series) -> Dict[str, Any]:
        """
        Calculates outlier bounds and counts using the standard IQR method:
        lower_bound = Q1 - 1.5 * IQR
        upper_bound = Q3 + 1.5 * IQR
        """
        # Filter to finite numeric values
        numeric_series = pd.to_numeric(series, errors="coerce")
        finite_vals = numeric_series[np.isfinite(numeric_series)].to_numpy()

        if len(finite_vals) < 4:
            return {
                "outlierCount": 0,
                "outlierRate": 0.0,
                "lowerBound": None,
                "upperBound": None,
            }

        q1 = float(np.percentile(finite_vals, 25))
        q3 = float(np.percentile(finite_vals, 75))
        iqr = q3 - q1

        if iqr <= 0.0:
            # Constant or near-constant numeric distribution
            return {
                "outlierCount": 0,
                "outlierRate": 0.0,
                "lowerBound": round(q1, 4),
                "upperBound": round(q3, 4),
            }

        lower_bound = float(q1 - 1.5 * iqr)
        upper_bound = float(q3 + 1.5 * iqr)

        outlier_mask = (finite_vals < lower_bound) | (finite_vals > upper_bound)
        outlier_count = int(np.sum(outlier_mask))
        outlier_rate = round(outlier_count / len(finite_vals), 6)

        return {
            "outlierCount": outlier_count,
            "outlierRate": outlier_rate,
            "lowerBound": round(lower_bound, 4),
            "upperBound": round(upper_bound, 4),
        }

    async def run_diagnostic(
        self,
        current_data: pd.DataFrame,
        target_column: str,
        baseline_data: Optional[pd.DataFrame] = None,
        model_artifact: Optional[Any] = None,
        config: Optional[Dict[str, Any]] = None,
    ) -> DiagnosticReport:
        start_time = time.perf_counter()

        if current_data is None or not isinstance(current_data, pd.DataFrame):
            raise ValueError("Evaluation dataset must be a valid pandas DataFrame.")

        cfg = {**self.DEFAULT_CONFIG, **(config or {})}
        issues: List[DiagnosticIssue] = []
        metrics: List[DiagnosticMetric] = []

        row_count = int(len(current_data))
        column_count = int(len(current_data.columns))

        # Handle empty dataset edge case
        if row_count == 0 or column_count == 0:
            issue = DiagnosticIssue(
                id="DQ-EMPTY-001",
                category=DiagnosticCategory.DATA_QUALITY,
                severity=SeverityLevel.CRITICAL,
                title="Empty Dataset Provided",
                description=f"Evaluation dataset contains 0 {'rows' if row_count == 0 else 'columns'}.",
                affected_features=[],
                evidence={"rowCount": row_count, "columnCount": column_count},
                recommendation="Provide a non-empty dataset partition containing observations and features.",
            )
            exec_time_ms = round((time.perf_counter() - start_time) * 1000, 2)
            return DiagnosticReport(
                engine_name=self.name,
                category=self.category,
                health_score=0.0,
                passed=False,
                execution_time_ms=exec_time_ms,
                metrics=[DiagnosticMetric(name="row_count", value=float(row_count), threshold_min=1.0, passed=False)],
                issues=[issue],
                metadata={
                    "module": "DATA_QUALITY",
                    "version": "1.0",
                    "summary": {
                        "rowCount": row_count,
                        "columnCount": column_count,
                        "duplicateRowCount": 0,
                        "duplicateRowRate": 0.0,
                        "memoryUsageBytes": 0,
                        "totalMissingCells": 0,
                        "globalMissingRate": 0.0,
                        "columnsWithMissingValues": 0,
                        "maxMissingRate": 0.0,
                        "constantColumnsCount": 0,
                        "healthScore": 0.0,
                        "passed": False,
                    },
                    "columns": [],
                    "findings": [issue.model_dump()],
                },
            )

        # 1. Dataset-level statistics
        duplicate_row_count = int(current_data.duplicated().sum())
        duplicate_row_rate = round(duplicate_row_count / row_count, 6)
        memory_usage_bytes = int(current_data.memory_usage(deep=True).sum())
        total_cells = row_count * column_count
        total_missing_cells = int(current_data.isna().sum().sum())
        global_missing_rate = round(total_missing_cells / total_cells, 6) if total_cells > 0 else 0.0

        # Check duplicate rows finding
        if duplicate_row_rate >= cfg["duplicate_rate_critical"]:
            issues.append(
                DiagnosticIssue(
                    id="DQ-DUP-001",
                    category=DiagnosticCategory.DATA_QUALITY,
                    severity=SeverityLevel.CRITICAL,
                    title="Critical Duplicate Row Rate",
                    description=f"Dataset contains {duplicate_row_count} duplicate rows ({duplicate_row_rate * 100:.2f}%), exceeding the critical threshold of {cfg['duplicate_rate_critical'] * 100:.1f}%.",
                    affected_features=[],
                    evidence={
                        "duplicateRowCount": duplicate_row_count,
                        "duplicateRowRate": duplicate_row_rate,
                        "threshold": cfg["duplicate_rate_critical"],
                    },
                    recommendation="Investigate data extraction pipeline for duplicate joins or replay anomalies. Deduplicate evaluation sample.",
                )
            )
        elif duplicate_row_rate >= cfg["duplicate_rate_warning"]:
            issues.append(
                DiagnosticIssue(
                    id="DQ-DUP-002",
                    category=DiagnosticCategory.DATA_QUALITY,
                    severity=SeverityLevel.WARNING,
                    title="Elevated Duplicate Row Rate",
                    description=f"Dataset contains {duplicate_row_count} duplicate rows ({duplicate_row_rate * 100:.2f}%), exceeding the warning threshold of {cfg['duplicate_rate_warning'] * 100:.1f}%.",
                    affected_features=[],
                    evidence={
                        "duplicateRowCount": duplicate_row_count,
                        "duplicateRowRate": duplicate_row_rate,
                        "threshold": cfg["duplicate_rate_warning"],
                    },
                    recommendation="Verify whether sample multiplicity is expected for this business domain.",
                )
            )

        columns_profile: List[Dict[str, Any]] = []
        columns_with_missing_count = 0
        constant_columns_count = 0
        max_col_missing_rate = 0.0

        # 2. Column-by-column analysis
        for col in current_data.columns:
            series = current_data[col]
            inferred_type = self.infer_column_type(series)
            dtype_str = str(series.dtype)

            # Missingness
            missing_count = int(series.isna().sum())
            missing_rate = round(missing_count / row_count, 6)
            if missing_count > 0:
                columns_with_missing_count += 1
            if missing_rate > max_col_missing_rate:
                max_col_missing_rate = missing_rate

            non_null_series = series.dropna()
            unique_count = int(non_null_series.nunique())

            # Non-finite values (for numeric columns: check +Inf / -Inf)
            non_finite_count = 0
            non_finite_rate = 0.0
            if inferred_type == "NUMERIC" or pd.api.types.is_numeric_dtype(series):
                try:
                    numeric_vals = pd.to_numeric(series, errors="coerce")
                    inf_mask = np.isinf(numeric_vals)
                    non_finite_count = int(inf_mask.sum())
                    non_finite_rate = round(non_finite_count / row_count, 6)
                except Exception:
                    non_finite_count = 0
                    non_finite_rate = 0.0

            # Constant & Near-constant checks
            is_constant = False
            is_near_constant = False
            dominant_value: Optional[str] = None
            dominant_value_rate = 0.0

            if unique_count <= 1:
                is_constant = True
                constant_columns_count += 1
                if not non_null_series.empty:
                    dominant_value = str(non_null_series.iloc[0])
                    dominant_value_rate = 1.0
            else:
                top_val = non_null_series.value_counts().index[0]
                top_cnt = int(non_null_series.value_counts().iloc[0])
                dominant_value = str(top_val)
                dominant_value_rate = round(top_cnt / len(non_null_series), 6) if len(non_null_series) > 0 else 0.0
                if dominant_value_rate >= cfg["near_constant_threshold"]:
                    is_near_constant = True

            # Outlier detection (IQR)
            outlier_info = {"outlierCount": 0, "outlierRate": 0.0, "lowerBound": None, "upperBound": None}
            col_min = None
            col_max = None
            col_mean = None
            col_std = None

            if inferred_type == "NUMERIC":
                outlier_info = self.calculate_outliers_iqr(series)
                finite_numeric = pd.to_numeric(series, errors="coerce")
                finite_numeric = finite_numeric[np.isfinite(finite_numeric)]
                if len(finite_numeric) > 0:
                    col_min = round(float(finite_numeric.min()), 4)
                    col_max = round(float(finite_numeric.max()), 4)
                    col_mean = round(float(finite_numeric.mean()), 4)
                    col_std = round(float(finite_numeric.std()), 4) if len(finite_numeric) > 1 else 0.0

            col_profile = {
                "name": str(col),
                "dtype": dtype_str,
                "inferredType": inferred_type,
                "sampleCount": row_count,
                "uniqueCount": unique_count,
                "missingCount": missing_count,
                "missingRate": missing_rate,
                "nonFiniteCount": non_finite_count,
                "nonFiniteRate": non_finite_rate,
                "isConstant": is_constant,
                "isNearConstant": is_near_constant,
                "dominantValue": dominant_value,
                "dominantValueRate": dominant_value_rate,
                "outlierCount": outlier_info["outlierCount"],
                "outlierRate": outlier_info["outlierRate"],
                "lowerBound": outlier_info["lowerBound"],
                "upperBound": outlier_info["upperBound"],
                "min": col_min,
                "max": col_max,
                "mean": col_mean,
                "std": col_std,
            }
            columns_profile.append(col_profile)

            # Generate Findings per column
            # A. Missingness Findings
            if missing_rate >= cfg["missing_rate_critical"]:
                issues.append(
                    DiagnosticIssue(
                        id=f"DQ-MISS-CRIT-{col}",
                        category=DiagnosticCategory.DATA_QUALITY,
                        severity=SeverityLevel.CRITICAL,
                        title=f"Critical Missing Rate on '{col}'",
                        description=f"Column '{col}' has {missing_count} missing values ({missing_rate * 100:.2f}%), exceeding the critical threshold of {cfg['missing_rate_critical'] * 100:.1f}%.",
                        affected_features=[str(col)],
                        evidence={
                            "column": str(col),
                            "missingCount": missing_count,
                            "missingRate": missing_rate,
                            "threshold": cfg["missing_rate_critical"],
                        },
                        recommendation=f"Evaluate root cause of data dropout in '{col}'. If missingness is unrecoverable, consider dropping this feature or applying robust categorical missing indicators.",
                    )
                )
            elif missing_rate >= cfg["missing_rate_warning"]:
                issues.append(
                    DiagnosticIssue(
                        id=f"DQ-MISS-WARN-{col}",
                        category=DiagnosticCategory.DATA_QUALITY,
                        severity=SeverityLevel.WARNING,
                        title=f"Elevated Missing Rate on '{col}'",
                        description=f"Column '{col}' contains {missing_count} missing observations ({missing_rate * 100:.2f}%), exceeding the warning threshold of {cfg['missing_rate_warning'] * 100:.1f}%.",
                        affected_features=[str(col)],
                        evidence={
                            "column": str(col),
                            "missingCount": missing_count,
                            "missingRate": missing_rate,
                            "threshold": cfg["missing_rate_warning"],
                        },
                        recommendation=f"Configure an explicit imputation strategy (median/mode or model-based) for column '{col}'.",
                    )
                )

            # B. Constant & Near-constant Findings
            if is_constant:
                issues.append(
                    DiagnosticIssue(
                        id=f"DQ-CONST-{col}",
                        category=DiagnosticCategory.DATA_QUALITY,
                        severity=SeverityLevel.WARNING,
                        title=f"Zero Variance Constant Column '{col}'",
                        description=f"Column '{col}' has only {unique_count} distinct non-null value ({dominant_value!r}), providing zero variance.",
                        affected_features=[str(col)],
                        evidence={
                            "column": str(col),
                            "uniqueCount": unique_count,
                            "dominantValue": dominant_value,
                            "dominantValueRate": dominant_value_rate,
                        },
                        recommendation=f"Drop zero-variance constant column '{col}' to prevent uninformative parameter bloat in downstream models.",
                    )
                )
            elif is_near_constant:
                issues.append(
                    DiagnosticIssue(
                        id=f"DQ-NEARCONST-{col}",
                        category=DiagnosticCategory.DATA_QUALITY,
                        severity=SeverityLevel.INFO,
                        title=f"Near-Constant Column '{col}'",
                        description=f"Column '{col}' is dominant in {dominant_value_rate * 100:.2f}% of observations with value {dominant_value!r}.",
                        affected_features=[str(col)],
                        evidence={
                            "column": str(col),
                            "dominantValue": dominant_value,
                            "dominantValueRate": dominant_value_rate,
                            "threshold": cfg["near_constant_threshold"],
                        },
                        recommendation=f"Verify if low cardinality in '{col}' satisfies model split requirements.",
                    )
                )

            # C. Non-finite values
            if non_finite_count > 0:
                issues.append(
                    DiagnosticIssue(
                        id=f"DQ-NONFINITE-{col}",
                        category=DiagnosticCategory.DATA_QUALITY,
                        severity=SeverityLevel.CRITICAL,
                        title=f"Non-Finite Values Detected in '{col}'",
                        description=f"Column '{col}' contains {non_finite_count} infinite values (+/-Inf) ({non_finite_rate * 100:.2f}%).",
                        affected_features=[str(col)],
                        evidence={
                            "column": str(col),
                            "nonFiniteCount": non_finite_count,
                            "nonFiniteRate": non_finite_rate,
                        },
                        recommendation=f"Clip or replace non-finite mathematical overflow values in '{col}' before gradient calculation.",
                    )
                )

            # D. Outliers (IQR)
            if outlier_info["outlierRate"] >= cfg["outlier_rate_critical"]:
                issues.append(
                    DiagnosticIssue(
                        id=f"DQ-OUTLIER-CRIT-{col}",
                        category=DiagnosticCategory.DATA_QUALITY,
                        severity=SeverityLevel.HIGH,
                        title=f"Severe Outlier Fraction in '{col}'",
                        description=f"Column '{col}' has {outlier_info['outlierCount']} observations ({outlier_info['outlierRate'] * 100:.2f}%) outside IQR bounds [{outlier_info['lowerBound']}, {outlier_info['upperBound']}].",
                        affected_features=[str(col)],
                        evidence={
                            "column": str(col),
                            "outlierCount": outlier_info["outlierCount"],
                            "outlierRate": outlier_info["outlierRate"],
                            "lowerBound": outlier_info["lowerBound"],
                            "upperBound": outlier_info["upperBound"],
                            "threshold": cfg["outlier_rate_critical"],
                        },
                        recommendation=f"Apply robust quantile clipping (Winsorization) or robust scaling to '{col}'.",
                    )
                )
            elif outlier_info["outlierRate"] >= cfg["outlier_rate_warning"]:
                issues.append(
                    DiagnosticIssue(
                        id=f"DQ-OUTLIER-WARN-{col}",
                        category=DiagnosticCategory.DATA_QUALITY,
                        severity=SeverityLevel.MEDIUM,
                        title=f"Moderate Outliers Detected in '{col}'",
                        description=f"Column '{col}' has {outlier_info['outlierCount']} outliers ({outlier_info['outlierRate'] * 100:.2f}%) by IQR criterion.",
                        affected_features=[str(col)],
                        evidence={
                            "column": str(col),
                            "outlierCount": outlier_info["outlierCount"],
                            "outlierRate": outlier_info["outlierRate"],
                            "lowerBound": outlier_info["lowerBound"],
                            "upperBound": outlier_info["upperBound"],
                            "threshold": cfg["outlier_rate_warning"],
                        },
                        recommendation=f"Inspect tail distribution for '{col}' to verify validity of extreme values.",
                    )
                )

        # 3. Overall Metric Summary
        metrics.append(
            DiagnosticMetric(
                name="global_missing_rate",
                value=float(global_missing_rate),
                unit="fraction",
                threshold_max=cfg["missing_rate_warning"],
                passed=bool(global_missing_rate < cfg["missing_rate_warning"]),
            )
        )
        metrics.append(
            DiagnosticMetric(
                name="max_column_missing_rate",
                value=float(max_col_missing_rate),
                unit="fraction",
                threshold_max=cfg["missing_rate_warning"],
                passed=bool(max_col_missing_rate < cfg["missing_rate_warning"]),
            )
        )
        metrics.append(
            DiagnosticMetric(
                name="duplicate_row_rate",
                value=float(duplicate_row_rate),
                unit="fraction",
                threshold_max=cfg["duplicate_rate_warning"],
                passed=bool(duplicate_row_rate < cfg["duplicate_rate_warning"]),
            )
        )
        metrics.append(
            DiagnosticMetric(
                name="constant_columns_count",
                value=float(constant_columns_count),
                unit="count",
                threshold_max=0.0,
                passed=bool(constant_columns_count == 0),
            )
        )

        # Health score calculation
        penalty = 0.0
        critical_count = 0
        for issue in issues:
            if issue.severity == SeverityLevel.CRITICAL:
                penalty += 25.0
                critical_count += 1
            elif issue.severity in [SeverityLevel.HIGH, SeverityLevel.WARNING]:
                penalty += 8.0
            elif issue.severity == SeverityLevel.MEDIUM:
                penalty += 4.0
            elif issue.severity == SeverityLevel.LOW:
                penalty += 1.0

        health_score = max(0.0, min(100.0, round(100.0 - penalty, 1)))
        passed = (health_score >= 70.0 and critical_count == 0)

        exec_time_ms = round((time.perf_counter() - start_time) * 1000, 2)

        findings_dicts = [issue.model_dump() for issue in issues]

        summary_dict = {
            "rowCount": row_count,
            "columnCount": column_count,
            "duplicateRowCount": duplicate_row_count,
            "duplicateRowRate": duplicate_row_rate,
            "memoryUsageBytes": memory_usage_bytes,
            "totalMissingCells": total_missing_cells,
            "globalMissingRate": global_missing_rate,
            "columnsWithMissingValues": columns_with_missing_count,
            "maxMissingRate": round(max_col_missing_rate, 6),
            "constantColumnsCount": constant_columns_count,
            "healthScore": health_score,
            "passed": passed,
        }

        metadata_dict = {
            "module": "DATA_QUALITY",
            "version": "1.0",
            "summary": summary_dict,
            "columns": columns_profile,
            "findings": findings_dicts,
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
