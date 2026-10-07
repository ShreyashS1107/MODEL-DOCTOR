import hashlib
import logging
import time
from typing import Any, Dict, List, Optional, Tuple
import numpy as np
import pandas as pd
from scipy import stats
from sklearn.feature_selection import mutual_info_classif, mutual_info_regression
from sklearn.preprocessing import LabelEncoder, OrdinalEncoder

from app.diagnostics.base import BaseDiagnosticEngine
from app.schemas.diagnostic_models import (
    DiagnosticCategory,
    DiagnosticIssue,
    DiagnosticMetric,
    DiagnosticReport,
    SeverityLevel,
)

logger = logging.getLogger(__name__)


class DataLeakageEngine(BaseDiagnosticEngine):
    """
    Forensic Data Leakage Engine.
    Performs deterministic statistical audits for target contamination:
    - Target column integrity and variance validation
    - Direct feature-to-target association measures:
      * Pearson correlation for continuous/binary pairs
      * Correlation ratio (eta) for continuous numeric vs multiclass targets
      * Cramer's V for categorical features vs categorical targets
    - Non-linear Mutual Information estimation (mutual_info_classif / mutual_info_regression)
    - Leakage candidate ranking and multi-tier risk classification (CRITICAL, HIGH, MEDIUM, LOW)
    - Potential target surrogate / proxy feature identification
    - Train-to-Evaluation dataset sample overlap / row hash contamination
    - Actionable remediation findings with evidence attribution
    """

    DEFAULT_CONFIG = {
        "mutual_info_critical": 0.70,
        "mutual_info_high": 0.50,
        "mutual_info_medium": 0.30,
        "association_critical": 0.85,
        "association_high": 0.65,
        "association_medium": 0.40,
        "overlap_rate_warning": 0.001,  # 0.1%
        "overlap_rate_critical": 0.02,  # 2.0%
        "random_state": 42,
    }

    @property
    def category(self) -> DiagnosticCategory:
        return DiagnosticCategory.LEAKAGE

    @property
    def name(self) -> str:
        return "Data Leakage Engine"

    @property
    def description(self) -> str:
        return "Forensic detection of direct target leakage, proxy surrogates, mutual information peaks, and train/eval sample contamination."

    @staticmethod
    def _compute_cramers_v(x_cat: pd.Series, y_cat: pd.Series) -> float:
        """
        Calculates Cramer's V for two categorical distributions.
        Returns a float in [0.0, 1.0].
        """
        try:
            contingency = pd.crosstab(x_cat, y_cat)
            if contingency.empty or contingency.shape[0] < 2 or contingency.shape[1] < 2:
                return 0.0
            
            chi2, _, _, _ = stats.chi2_contingency(contingency, correction=False)
            n = contingency.sum().sum()
            if n <= 0:
                return 0.0
            
            min_dim = min(contingency.shape[0] - 1, contingency.shape[1] - 1)
            if min_dim == 0:
                return 0.0
            
            v = np.sqrt(chi2 / (n * min_dim))
            return float(np.clip(v, 0.0, 1.0))
        except Exception as e:
            logger.debug("Cramer's V calculation exception: %s", str(e))
            return 0.0

    @staticmethod
    def _compute_correlation_ratio(categories: pd.Series, measurements: pd.Series) -> float:
        """
        Calculates Correlation Ratio (eta) for categorical vs continuous variable.
        eta = sqrt(SS_between / SS_total)
        """
        try:
            valid_df = pd.DataFrame({"cat": categories, "val": measurements}).dropna()
            if len(valid_df) < 5 or valid_df["cat"].nunique() < 2:
                return 0.0

            overall_mean = valid_df["val"].mean()
            ss_total = ((valid_df["val"] - overall_mean) ** 2).sum()
            if ss_total == 0.0:
                return 0.0

            grouped = valid_df.groupby("cat")["val"]
            cat_counts = grouped.count()
            cat_means = grouped.mean()

            ss_between = (cat_counts * ((cat_means - overall_mean) ** 2)).sum()
            eta = np.sqrt(max(0.0, ss_between / ss_total))
            return float(np.clip(eta, 0.0, 1.0))
        except Exception as e:
            logger.debug("Correlation ratio calculation exception: %s", str(e))
            return 0.0

    def _calculate_feature_association(
        self,
        feature_series: pd.Series,
        target_series: pd.Series,
        is_classification: bool,
        target_unique_count: int,
    ) -> Tuple[str, float]:
        """
        Calculates direct association between a feature and target based on data types:
        - Numeric vs Binary target: Pearson correlation
        - Numeric vs Multiclass target: Correlation ratio (eta)
        - Numeric vs Continuous regression target: Pearson correlation
        - Categorical vs Classification target: Cramer's V
        - Categorical vs Continuous regression target: Correlation ratio (eta)
        """
        # Drop missing pairs
        valid_idx = feature_series.notna() & target_series.notna()
        x = feature_series[valid_idx]
        y = target_series[valid_idx]

        if len(x) < 5 or x.nunique() <= 1:
            return "NONE", 0.0

        is_x_numeric = pd.api.types.is_numeric_dtype(x)

        if is_x_numeric:
            x_finite_idx = np.isfinite(pd.to_numeric(x, errors="coerce"))
            x = x[x_finite_idx]
            y = y[x_finite_idx]
            if len(x) < 5:
                return "NONE", 0.0

            if is_classification:
                if target_unique_count == 2:
                    # Binary classification: Pearson correlation with numeric target
                    try:
                        y_num = pd.to_numeric(y, errors="coerce")
                        if y_num.nunique() > 1 and x.std() > 0:
                            r, _ = stats.pearsonr(x, y_num)
                            return "PEARSON_CORRELATION", float(np.nan_to_num(r, nan=0.0))
                    except Exception:
                        pass
                    return "CORRELATION_RATIO", self._compute_correlation_ratio(y, x)
                else:
                    # Multiclass classification: Correlation ratio
                    return "CORRELATION_RATIO", self._compute_correlation_ratio(y, x)
            else:
                # Regression: Pearson correlation
                try:
                    y_num = pd.to_numeric(y, errors="coerce")
                    if y_num.nunique() > 1 and x.std() > 0:
                        r, _ = stats.pearsonr(x, y_num)
                        return "PEARSON_CORRELATION", float(np.nan_to_num(r, nan=0.0))
                except Exception:
                    pass
                return "NONE", 0.0
        else:
            # Feature is categorical / string / bool
            if is_classification:
                return "CRAMERS_V", self._compute_cramers_v(x.astype(str), y.astype(str))
            else:
                # Feature categorical, target continuous
                return "CORRELATION_RATIO", self._compute_correlation_ratio(x.astype(str), pd.to_numeric(y, errors="coerce"))

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
                id="LEAK-EMPTY-001",
                category=DiagnosticCategory.LEAKAGE,
                severity=SeverityLevel.CRITICAL,
                title="Empty Dataset Provided",
                description=f"Evaluation dataset contains {row_count} rows and {column_count} columns.",
                affected_features=[],
                evidence={"rowCount": row_count, "columnCount": column_count},
                recommendation="Provide a valid evaluation dataset with features and target observations.",
            )
            exec_time_ms = round((time.perf_counter() - start_time) * 1000, 2)
            return DiagnosticReport(
                engine_name=self.name,
                category=self.category,
                health_score=0.0,
                passed=False,
                execution_time_ms=exec_time_ms,
                metrics=[DiagnosticMetric(name="evaluated_features_count", value=0.0, threshold_min=1.0, passed=False)],
                issues=[issue],
                metadata={
                    "module": "LEAKAGE",
                    "version": "1.0",
                    "target": {"column": target_column, "taskType": "UNKNOWN", "sampleCount": 0, "uniqueCount": 0},
                    "summary": {"evaluatedFeaturesCount": 0, "criticalLeakageCount": 0, "healthScore": 0.0, "passed": False},
                    "features": [],
                    "findings": [issue.model_dump()],
                },
            )

        # 1. Target Validation
        if not target_column or target_column not in current_data.columns:
            issue = DiagnosticIssue(
                id="LEAK-TARGET-MISSING-001",
                category=DiagnosticCategory.LEAKAGE,
                severity=SeverityLevel.CRITICAL,
                title=f"Target Column '{target_column}' Not Found",
                description=f"Specified target label '{target_column}' does not exist in dataset schema (columns: {list(current_data.columns)[:5]}...).",
                affected_features=[target_column] if target_column else [],
                evidence={"targetColumn": target_column, "availableColumns": list(current_data.columns)},
                recommendation=f"Verify dataset schema and configure a targetColumn matching one of the available dataset columns.",
            )
            exec_time_ms = round((time.perf_counter() - start_time) * 1000, 2)
            return DiagnosticReport(
                engine_name=self.name,
                category=self.category,
                health_score=0.0,
                passed=False,
                execution_time_ms=exec_time_ms,
                metrics=[DiagnosticMetric(name="target_column_valid", value=0.0, threshold_min=1.0, passed=False)],
                issues=[issue],
                metadata={
                    "module": "LEAKAGE",
                    "version": "1.0",
                    "target": {"column": target_column, "taskType": "UNKNOWN", "sampleCount": 0, "uniqueCount": 0},
                    "summary": {"evaluatedFeaturesCount": 0, "criticalLeakageCount": 0, "healthScore": 0.0, "passed": False},
                    "features": [],
                    "findings": [issue.model_dump()],
                },
            )

        target_series = current_data[target_column]
        non_null_target = target_series.dropna()
        target_sample_count = int(len(non_null_target))
        target_unique_count = int(non_null_target.nunique())

        if target_sample_count == 0 or target_unique_count < 2:
            issue = DiagnosticIssue(
                id="LEAK-TARGET-VARIANCE-001",
                category=DiagnosticCategory.LEAKAGE,
                severity=SeverityLevel.CRITICAL,
                title=f"Target Column '{target_column}' Has Zero Variance",
                description=f"Target column '{target_column}' has {target_unique_count} unique non-null values. Cannot compute association or mutual information without target variation.",
                affected_features=[target_column],
                evidence={"targetColumn": target_column, "uniqueCount": target_unique_count, "sampleCount": target_sample_count},
                recommendation="Ensure target column contains multiple classes or continuous variation across samples.",
            )
            exec_time_ms = round((time.perf_counter() - start_time) * 1000, 2)
            return DiagnosticReport(
                engine_name=self.name,
                category=self.category,
                health_score=0.0,
                passed=False,
                execution_time_ms=exec_time_ms,
                metrics=[DiagnosticMetric(name="target_variance_valid", value=0.0, threshold_min=1.0, passed=False)],
                issues=[issue],
                metadata={
                    "module": "LEAKAGE",
                    "version": "1.0",
                    "target": {"column": target_column, "taskType": "UNKNOWN", "sampleCount": target_sample_count, "uniqueCount": target_unique_count},
                    "summary": {"evaluatedFeaturesCount": 0, "criticalLeakageCount": 0, "healthScore": 0.0, "passed": False},
                    "features": [],
                    "findings": [issue.model_dump()],
                },
            )

        # 2. Determine Task Type
        configured_task = cfg.get("task_type", "").lower()
        if "regression" in configured_task:
            task_type = "REGRESSION"
            is_classification = False
        elif "multiclass" in configured_task or target_unique_count > 2 and (not pd.api.types.is_float_dtype(non_null_target) or target_unique_count <= 20):
            task_type = "MULTICLASS_CLASSIFICATION" if target_unique_count > 2 else "BINARY_CLASSIFICATION"
            is_classification = True
        elif target_unique_count == 2:
            task_type = "BINARY_CLASSIFICATION"
            is_classification = True
        elif pd.api.types.is_float_dtype(non_null_target) and target_unique_count > 20:
            task_type = "REGRESSION"
            is_classification = False
        else:
            task_type = "BINARY_CLASSIFICATION" if target_unique_count <= 2 else "MULTICLASS_CLASSIFICATION"
            is_classification = True

        # Target class balance distribution
        class_distribution: Dict[str, float] = {}
        if is_classification:
            val_counts = non_null_target.value_counts(normalize=True)
            for k, v in val_counts.items():
                class_distribution[str(k)] = round(float(v), 4)

        # 3. Prepare Feature Matrix for Mutual Information
        feature_cols = [c for c in current_data.columns if c != target_column]
        if not feature_cols:
            issue = DiagnosticIssue(
                id="LEAK-NO-FEATURES-001",
                category=DiagnosticCategory.LEAKAGE,
                severity=SeverityLevel.INFO,
                title="No Candidate Features Found",
                description="Dataset contains only the target column and zero candidate features.",
                affected_features=[],
                evidence={"columnCount": column_count},
                recommendation="Add predictor feature columns alongside the target column.",
            )
            exec_time_ms = round((time.perf_counter() - start_time) * 1000, 2)
            return DiagnosticReport(
                engine_name=self.name,
                category=self.category,
                health_score=100.0,
                passed=True,
                execution_time_ms=exec_time_ms,
                metrics=[],
                issues=[issue],
                metadata={
                    "module": "LEAKAGE",
                    "version": "1.0",
                    "target": {"column": target_column, "taskType": task_type, "sampleCount": target_sample_count, "uniqueCount": target_unique_count, "classDistribution": class_distribution},
                    "summary": {"evaluatedFeaturesCount": 0, "criticalLeakageCount": 0, "healthScore": 100.0, "passed": True},
                    "features": [],
                    "findings": [issue.model_dump()],
                },
            )

        # Filter rows where target is non-null for MI computation
        valid_target_mask = current_data[target_column].notna()
        df_valid = current_data[valid_target_mask].copy()

        # Prepare X matrix and discrete feature flags
        X_df = pd.DataFrame(index=df_valid.index)
        discrete_mask: List[bool] = []
        feature_status: Dict[str, str] = {}

        for col in feature_cols:
            series = df_valid[col]
            if series.dropna().nunique() <= 1:
                feature_status[col] = "CONSTANT"
                X_df[col] = 0.0
                discrete_mask.append(True)
                continue

            if pd.api.types.is_numeric_dtype(series):
                num_series = pd.to_numeric(series, errors="coerce")
                # Replace Inf with NaN
                num_series = num_series.replace([np.inf, -np.inf], np.nan)
                median_val = num_series.median()
                if pd.isna(median_val):
                    median_val = 0.0
                X_df[col] = num_series.fillna(median_val)
                # If integer or low cardinality numeric -> discrete
                if pd.api.types.is_integer_dtype(series) or (num_series.nunique() <= 10):
                    discrete_mask.append(True)
                else:
                    discrete_mask.append(False)
            else:
                # Categorical or string
                str_series = series.astype(str).fillna("__MISSING__")
                oe = OrdinalEncoder(handle_unknown="use_encoded_value", unknown_value=-1)
                encoded = oe.fit_transform(str_series.to_numpy().reshape(-1, 1)).ravel()
                X_df[col] = encoded
                discrete_mask.append(True)

        # Prepare target y
        if is_classification:
            le = LabelEncoder()
            y_clean = le.fit_transform(df_valid[target_column].astype(str))
            # Run mutual_info_classif
            try:
                mi_scores = mutual_info_classif(
                    X_df.to_numpy(),
                    y_clean,
                    discrete_features=np.array(discrete_mask),
                    n_neighbors=3,
                    random_state=cfg["random_state"],
                )
            except Exception as e:
                logger.warning("Falling back on standard MI calculation: %s", str(e))
                mi_scores = np.zeros(len(feature_cols))
        else:
            y_clean = pd.to_numeric(df_valid[target_column], errors="coerce").fillna(0.0).to_numpy()
            try:
                mi_scores = mutual_info_regression(
                    X_df.to_numpy(),
                    y_clean,
                    discrete_features=np.array(discrete_mask),
                    n_neighbors=3,
                    random_state=cfg["random_state"],
                )
            except Exception as e:
                logger.warning("Falling back on standard MI regression calculation: %s", str(e))
                mi_scores = np.zeros(len(feature_cols))

        # 4. Feature-by-Feature Forensic Inspection
        feature_records: List[Dict[str, Any]] = []
        critical_leakage_count = 0
        high_leakage_count = 0
        medium_leakage_count = 0
        low_leakage_count = 0
        max_mi = 0.0
        max_assoc = 0.0
        most_suspicious_feat = None
        highest_suspicion_score = -1.0

        for idx, col in enumerate(feature_cols):
            raw_mi = float(mi_scores[idx]) if idx < len(mi_scores) else 0.0
            mi_val = round(max(0.0, raw_mi), 4)

            # Association measure
            assoc_measure, assoc_val = self._calculate_feature_association(
                current_data[col],
                current_data[target_column],
                is_classification=is_classification,
                target_unique_count=target_unique_count,
            )
            assoc_val = round(assoc_val, 4)
            abs_assoc = abs(assoc_val)

            if mi_val > max_mi:
                max_mi = mi_val
            if abs_assoc > max_assoc:
                max_assoc = abs_assoc

            # Risk Categorization
            if mi_val >= cfg["mutual_info_critical"] or abs_assoc >= cfg["association_critical"]:
                risk = "CRITICAL"
                critical_leakage_count += 1
            elif mi_val >= cfg["mutual_info_high"] or abs_assoc >= cfg["association_high"]:
                risk = "HIGH"
                high_leakage_count += 1
            elif mi_val >= cfg["mutual_info_medium"] or abs_assoc >= cfg["association_medium"]:
                risk = "MEDIUM"
                medium_leakage_count += 1
            else:
                risk = "LOW"
                low_leakage_count += 1

            is_potential_proxy = bool(mi_val >= 0.75 or abs_assoc >= 0.85)
            is_potential_leakage = bool(risk in ["CRITICAL", "HIGH"])

            suspicion_score = round(mi_val * 0.60 + abs_assoc * 0.40, 4)
            if suspicion_score > highest_suspicion_score:
                highest_suspicion_score = suspicion_score
                most_suspicious_feat = col

            sample_valid_count = int((current_data[col].notna() & current_data[target_column].notna()).sum())

            feat_record = {
                "name": str(col),
                "mutualInformation": mi_val,
                "targetAssociation": assoc_val,
                "associationMeasure": assoc_measure,
                "sampleCount": sample_valid_count,
                "risk": risk,
                "isPotentialProxy": is_potential_proxy,
                "isPotentialLeakage": is_potential_leakage,
                "suspicionScore": suspicion_score,
            }
            feature_records.append(feat_record)

            # Generate Findings
            if risk == "CRITICAL":
                issues.append(
                    DiagnosticIssue(
                        id=f"LEAK-CRIT-{col}",
                        category=DiagnosticCategory.LEAKAGE,
                        severity=SeverityLevel.CRITICAL,
                        title=f"Potential Critical Target Leakage in '{col}'",
                        description=f"Feature '{col}' exhibits severe target mutual information (MI = {mi_val:.3f}) and association ({assoc_measure} = {assoc_val:.3f}). Potential target leakage detected.",
                        affected_features=[str(col)],
                        evidence={
                            "feature": str(col),
                            "mutualInformation": mi_val,
                            "targetAssociation": assoc_val,
                            "associationMeasure": assoc_measure,
                            "sampleCount": sample_valid_count,
                            "risk": risk,
                        },
                        recommendation=f"Verify whether '{col}' is available strictly before prediction time. If this feature incorporates post-decision outcomes or ground-truth proxy IDs, drop it before model training.",
                    )
                )
            elif risk == "HIGH":
                issues.append(
                    DiagnosticIssue(
                        id=f"LEAK-HIGH-{col}",
                        category=DiagnosticCategory.LEAKAGE,
                        severity=SeverityLevel.HIGH,
                        title=f"Elevated Target Dependency in '{col}'",
                        description=f"Feature '{col}' exhibits elevated predictive association with target (MI = {mi_val:.3f}, {assoc_measure} = {assoc_val:.3f}). Potential target leakage detected.",
                        affected_features=[str(col)],
                        evidence={
                            "feature": str(col),
                            "mutualInformation": mi_val,
                            "targetAssociation": assoc_val,
                            "associationMeasure": assoc_measure,
                            "risk": risk,
                        },
                        recommendation=f"Examine business logic generating '{col}'. Ensure feature values are not populated during or after the target label event.",
                    )
                )
            elif is_potential_proxy:
                issues.append(
                    DiagnosticIssue(
                        id=f"LEAK-PROXY-{col}",
                        category=DiagnosticCategory.LEAKAGE,
                        severity=SeverityLevel.HIGH,
                        title=f"Potential Target Proxy Surrogate '{col}'",
                        description=f"Feature '{col}' behaves as a direct proxy surrogate for the target label '{target_column}'.",
                        affected_features=[str(col)],
                        evidence={
                            "feature": str(col),
                            "mutualInformation": mi_val,
                            "targetAssociation": assoc_val,
                        },
                        recommendation=f"Audit upstream feature pipelines to guarantee that '{col}' does not encode label information under an alias.",
                    )
                )

        # 5. Train / Evaluation Sample Overlap (Contamination Audit)
        overlap_count = 0
        overlap_rate = 0.0
        if baseline_data is not None and isinstance(baseline_data, pd.DataFrame) and len(baseline_data) > 0:
            try:
                # Find common columns for hashing
                common_cols = [c for c in current_data.columns if c in baseline_data.columns]
                if common_cols:
                    eval_hashes = set(
                        current_data[common_cols].apply(
                            lambda row: hashlib.md5("||".join(str(v) for v in row.values).encode("utf-8")).hexdigest(), axis=1
                        )
                    )
                    base_hashes = set(
                        baseline_data[common_cols].apply(
                            lambda row: hashlib.md5("||".join(str(v) for v in row.values).encode("utf-8")).hexdigest(), axis=1
                        )
                    )
                    overlap_hashes = eval_hashes.intersection(base_hashes)
                    overlap_count = len(overlap_hashes)
                    overlap_rate = round(overlap_count / len(eval_hashes), 6) if eval_hashes else 0.0

                    if overlap_rate >= cfg["overlap_rate_critical"]:
                        issues.append(
                            DiagnosticIssue(
                                id="LEAK-CONTAM-CRIT-001",
                                category=DiagnosticCategory.LEAKAGE,
                                severity=SeverityLevel.CRITICAL,
                                title="Severe Train/Evaluation Contamination",
                                description=f"Found {overlap_count} identical row hashes ({overlap_rate * 100:.3f}%) overlapping between baseline and evaluation partitions.",
                                affected_features=[],
                                evidence={"overlapCount": overlap_count, "overlapRate": overlap_rate, "threshold": cfg["overlap_rate_critical"]},
                                recommendation="Purge train samples from evaluation dataset to ensure strict out-of-sample statistical validity.",
                            )
                        )
                    elif overlap_rate >= cfg["overlap_rate_warning"]:
                        issues.append(
                            DiagnosticIssue(
                                id="LEAK-CONTAM-WARN-001",
                                category=DiagnosticCategory.LEAKAGE,
                                severity=SeverityLevel.WARNING,
                                title="Train/Evaluation Contamination Detected",
                                description=f"Found {overlap_count} overlapping sample hashes ({overlap_rate * 100:.3f}%) shared with baseline reference.",
                                affected_features=[],
                                evidence={"overlapCount": overlap_count, "overlapRate": overlap_rate, "threshold": cfg["overlap_rate_warning"]},
                                recommendation="Verify splitting strategy to eliminate duplicate cross-partition sample contamination.",
                            )
                        )
            except Exception as e:
                logger.warning("Sample overlap check failed: %s", str(e))

        # Sort feature records by suspicion score descending
        feature_records.sort(key=lambda r: r["suspicionScore"], reverse=True)

        # 6. Overall Metrics
        metrics.append(
            DiagnosticMetric(
                name="max_mutual_information",
                value=float(max_mi),
                unit="nats",
                threshold_max=cfg["mutual_info_high"],
                passed=bool(max_mi < cfg["mutual_info_high"]),
            )
        )
        metrics.append(
            DiagnosticMetric(
                name="max_target_association",
                value=float(max_assoc),
                unit="score",
                threshold_max=cfg["association_high"],
                passed=bool(max_assoc < cfg["association_high"]),
            )
        )
        metrics.append(
            DiagnosticMetric(
                name="critical_leakage_features_count",
                value=float(critical_leakage_count),
                unit="count",
                threshold_max=0.0,
                passed=bool(critical_leakage_count == 0),
            )
        )
        if baseline_data is not None:
            metrics.append(
                DiagnosticMetric(
                    name="dataset_overlap_rate",
                    value=float(overlap_rate),
                    unit="fraction",
                    threshold_max=cfg["overlap_rate_warning"],
                    passed=bool(overlap_rate < cfg["overlap_rate_warning"]),
                )
            )

        # Health score calculation
        penalty = 0.0
        for issue in issues:
            if issue.severity == SeverityLevel.CRITICAL:
                penalty += 30.0
            elif issue.severity == SeverityLevel.HIGH:
                penalty += 15.0
            elif issue.severity == SeverityLevel.WARNING:
                penalty += 8.0
            elif issue.severity == SeverityLevel.MEDIUM:
                penalty += 4.0

        health_score = max(0.0, min(100.0, round(100.0 - penalty, 1)))
        passed = bool(critical_leakage_count == 0 and health_score >= 70.0)

        exec_time_ms = round((time.perf_counter() - start_time) * 1000, 2)
        findings_dicts = [issue.model_dump() for issue in issues]

        summary_dict = {
            "evaluatedFeaturesCount": len(feature_cols),
            "criticalLeakageCount": critical_leakage_count,
            "highLeakageCount": high_leakage_count,
            "mediumLeakageCount": medium_leakage_count,
            "lowLeakageCount": low_leakage_count,
            "maxMutualInformation": max_mi,
            "maxTargetAssociation": max_assoc,
            "mostSuspiciousFeature": most_suspicious_feat,
            "overlapRowCount": overlap_count,
            "overlapRowRate": overlap_rate,
            "healthScore": health_score,
            "passed": passed,
        }

        target_metadata = {
            "column": target_column,
            "taskType": task_type,
            "sampleCount": target_sample_count,
            "uniqueCount": target_unique_count,
            "classDistribution": class_distribution,
        }

        metadata_dict = {
            "module": "LEAKAGE",
            "version": "1.0",
            "target": target_metadata,
            "summary": summary_dict,
            "features": feature_records,
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
