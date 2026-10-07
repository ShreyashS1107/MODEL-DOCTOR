import asyncio
import pytest
import numpy as np
import pandas as pd

from app.diagnostics.data_quality.engine import DataQualityEngine
from app.schemas.diagnostic_models import DiagnosticCategory, SeverityLevel


@pytest.fixture
def deterministic_dq_dataset() -> pd.DataFrame:
    """
    Creates a deterministic test dataset with known, verifiable properties:
    - 100 rows, 7 columns
    - missing values in 'age' (10 missing = 10%)
    - duplicate rows (5 duplicate rows = 5%)
    - constant column ('env' has all 'PROD')
    - near-constant column ('country' has 99 'US' and 1 'CA')
    - numeric outliers in 'salary' (Q1=50, Q3=100, IQR=50, lower=-25, upper=175; 4 values are 500 = 4 outliers)
    - non-finite values in 'ratio' (2 values are +inf, 1 is -inf)
    - categorical column ('dept')
    - boolean column ('is_active')
    """
    rng = np.random.RandomState(42)
    n = 100

    age = rng.uniform(20, 60, size=n)
    # Inject exactly 10 missing values in age
    age[:10] = np.nan

    salary = np.array([50.0] * 25 + [70.0] * 25 + [80.0] * 25 + [100.0] * 21 + [500.0] * 4)

    ratio = np.linspace(1.0, 10.0, n)
    ratio[0] = np.inf
    ratio[1] = np.inf
    ratio[2] = -np.inf

    env = ["PROD"] * n
    country = ["US"] * 99 + ["CA"]
    dept = ["ENG"] * 40 + ["SALES"] * 30 + ["HR"] * 30
    is_active = [1, 0] * 50

    df = pd.DataFrame({
        "age": age,
        "salary": salary,
        "ratio": ratio,
        "env": env,
        "country": country,
        "dept": dept,
        "is_active": is_active,
    })

    # Inject exactly 5 duplicate rows
    dup_rows = df.iloc[:5].copy()
    df = pd.concat([df, dup_rows], ignore_index=True)
    return df


def test_data_quality_dataset_level_stats(deterministic_dq_dataset):
    engine = DataQualityEngine()
    report = asyncio.run(
        engine.run_diagnostic(
            current_data=deterministic_dq_dataset,
            target_column="is_active",
        )
    )

    assert report.category == DiagnosticCategory.DATA_QUALITY
    assert report.metadata["summary"]["rowCount"] == 105  # 100 + 5 dups
    assert report.metadata["summary"]["columnCount"] == 7
    assert report.metadata["summary"]["duplicateRowCount"] == 5
    assert report.metadata["summary"]["duplicateRowRate"] == round(5 / 105, 6)
    assert report.metadata["summary"]["memoryUsageBytes"] > 0
    assert report.metadata["summary"]["constantColumnsCount"] == 1


def test_data_quality_missing_value_analysis(deterministic_dq_dataset):
    engine = DataQualityEngine()
    report = asyncio.run(
        engine.run_diagnostic(
            current_data=deterministic_dq_dataset,
            target_column="is_active",
        )
    )

    columns = {c["name"]: c for c in report.metadata["columns"]}
    # Age has 10 missing in first 100 + dup of first 5 rows (all missing) = 15 missing
    assert columns["age"]["missingCount"] == 15
    assert columns["age"]["missingRate"] == round(15 / 105, 6)
    assert columns["salary"]["missingCount"] == 0
    assert columns["salary"]["missingRate"] == 0.0

    # Summary metrics
    assert report.metadata["summary"]["totalMissingCells"] == 15
    assert report.metadata["summary"]["columnsWithMissingValues"] == 1


def test_data_quality_constant_and_near_constant(deterministic_dq_dataset):
    engine = DataQualityEngine()
    report = asyncio.run(
        engine.run_diagnostic(
            current_data=deterministic_dq_dataset,
            target_column="is_active",
        )
    )

    columns = {c["name"]: c for c in report.metadata["columns"]}
    # 'env' is constant ('PROD')
    assert columns["env"]["isConstant"] is True
    assert columns["env"]["uniqueCount"] == 1
    assert columns["env"]["dominantValue"] == "PROD"
    assert columns["env"]["dominantValueRate"] == 1.0

    # 'country' is near-constant (>= 98%)
    assert columns["country"]["isConstant"] is False
    assert columns["country"]["isNearConstant"] is True
    assert columns["country"]["dominantValue"] == "US"


def test_data_quality_dtype_inference(deterministic_dq_dataset):
    engine = DataQualityEngine()
    report = asyncio.run(
        engine.run_diagnostic(
            current_data=deterministic_dq_dataset,
            target_column="is_active",
        )
    )

    columns = {c["name"]: c for c in report.metadata["columns"]}
    assert columns["age"]["inferredType"] == "NUMERIC"
    assert columns["salary"]["inferredType"] == "NUMERIC"
    assert columns["dept"]["inferredType"] == "CATEGORICAL"
    assert columns["is_active"]["inferredType"] == "BOOLEAN"


def test_data_quality_outlier_detection(deterministic_dq_dataset):
    engine = DataQualityEngine()
    report = asyncio.run(
        engine.run_diagnostic(
            current_data=deterministic_dq_dataset,
            target_column="is_active",
        )
    )

    columns = {c["name"]: c for c in report.metadata["columns"]}
    # salary contains 4 outliers at 500.0
    assert columns["salary"]["outlierCount"] == 4
    assert columns["salary"]["outlierRate"] == round(4 / 105, 6)
    assert columns["salary"]["upperBound"] is not None


def test_data_quality_non_finite_detection(deterministic_dq_dataset):
    engine = DataQualityEngine()
    report = asyncio.run(
        engine.run_diagnostic(
            current_data=deterministic_dq_dataset,
            target_column="is_active",
        )
    )

    columns = {c["name"]: c for c in report.metadata["columns"]}
    # ratio has 3 infs in first 100 rows + dup of first 5 rows (3 infs duplicated) = 6 non-finite
    assert columns["ratio"]["nonFiniteCount"] == 6
    assert columns["ratio"]["nonFiniteRate"] == round(6 / 105, 6)


def test_data_quality_findings_generation(deterministic_dq_dataset):
    engine = DataQualityEngine()
    report = asyncio.run(
        engine.run_diagnostic(
            current_data=deterministic_dq_dataset,
            target_column="is_active",
        )
    )

    findings = report.issues
    finding_types = [f.id for f in findings]

    # Verify constant column finding
    assert any("DQ-CONST-env" in fid for fid in finding_types)
    # Verify non-finite finding
    assert any("DQ-NONFINITE-ratio" in fid for fid in finding_types)
    # Verify missing rate warning
    assert any("DQ-MISS" in fid for fid in finding_types)


def test_data_quality_empty_and_single_row_datasets():
    engine = DataQualityEngine()

    # Empty dataset
    empty_df = pd.DataFrame()
    report_empty = asyncio.run(engine.run_diagnostic(empty_df, target_column="target"))
    assert report_empty.passed is False
    assert report_empty.health_score == 0.0
    assert len(report_empty.issues) > 0

    # Single row dataset
    single_df = pd.DataFrame({"col_a": [10], "col_b": ["val"], "target": [1]})
    report_single = asyncio.run(engine.run_diagnostic(single_df, target_column="target"))
    assert report_single.metadata["summary"]["rowCount"] == 1
    assert report_single.metadata["summary"]["columnCount"] == 3
