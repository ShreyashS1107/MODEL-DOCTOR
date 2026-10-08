from typing import List, Dict, Any, Optional
import math
import numpy as np
from scipy import stats


def mann_kendall_test(values: List[float]) -> Dict[str, Any]:
    """
    Perform non-parametric Mann-Kendall trend test.
    Requires at least 5 observations for meaningful normal approximation.
    """
    n = len(values)
    if n < 5:
        return {
            "tau": 0.0,
            "s_statistic": 0,
            "p_value": 1.0,
            "z_statistic": 0.0,
            "trend_direction": "INSUFFICIENT_DATA",
            "is_significant": False
        }

    s = 0
    for k in range(n - 1):
        for j in range(k + 1, n):
            diff = values[j] - values[k]
            if diff > 1e-9:
                s += 1
            elif diff < -1e-9:
                s -= 1

    tau = (2.0 * s) / (n * (n - 1))

    # Tie adjustment for variance
    unique_vals, counts = np.unique(values, return_counts=True)
    tie_term = sum(t * (t - 1) * (2 * t + 5) for t in counts if t > 1)
    var_s = (n * (n - 1) * (2 * n + 5) - tie_term) / 18.0

    if var_s <= 0:
        var_s = 1e-9

    if s > 0:
        z = (s - 1) / math.sqrt(var_s)
    elif s < 0:
        z = (s + 1) / math.sqrt(var_s)
    else:
        z = 0.0

    # Two-sided p-value from standard normal
    p_value = 2.0 * (1.0 - stats.norm.cdf(abs(z)))

    if p_value < 0.05:
        trend_direction = "INCREASING" if s > 0 else "DECREASING"
        is_significant = True
    else:
        trend_direction = "NO_TREND"
        is_significant = False

    return {
        "tau": round(float(tau), 4),
        "s_statistic": int(s),
        "p_value": round(float(p_value), 5),
        "z_statistic": round(float(z), 4),
        "trend_direction": trend_direction,
        "is_significant": is_significant
    }


def analyze_metric_trend(
    values: List[float],
    timestamps: Optional[List[str]] = None,
    higher_is_better: bool = True,
    min_samples: int = 3
) -> Dict[str, Any]:
    """
    Deterministic trend analysis computing linear slope, R², summary stats,
    Mann-Kendall test, and technical directional state.
    """
    n = len(values)
    if n < min_samples:
        return {
            "observationCount": n,
            "direction": "INSUFFICIENT_DATA",
            "slope": 0.0,
            "rSquared": 0.0,
            "latestValue": values[-1] if n > 0 else 0.0,
            "previousValue": values[-2] if n > 1 else (values[-1] if n > 0 else 0.0),
            "absoluteDelta": (values[-1] - values[-2]) if n > 1 else 0.0,
            "relativeDelta": ((values[-1] - values[-2]) / max(1e-6, abs(values[-2]))) if n > 1 else 0.0,
            "mean": round(float(np.mean(values)), 4) if n > 0 else 0.0,
            "median": round(float(np.median(values)), 4) if n > 0 else 0.0,
            "standardDeviation": round(float(np.std(values)), 4) if n > 0 else 0.0,
            "coefficientOfVariation": 0.0,
            "minimum": round(float(np.min(values)), 4) if n > 0 else 0.0,
            "maximum": round(float(np.max(values)), 4) if n > 0 else 0.0,
            "mannKendall": mann_kendall_test(values)
        }

    x = np.arange(n, dtype=float)
    y = np.array(values, dtype=float)

    # Linear regression
    slope, intercept, r_value, p_value, std_err = stats.linregress(x, y)
    r_squared = float(r_value ** 2) if not np.isnan(r_value) else 0.0
    slope = float(slope) if not np.isnan(slope) else 0.0

    mean_val = float(np.mean(y))
    median_val = float(np.median(y))
    std_val = float(np.std(y))
    cv = float(std_val / abs(mean_val)) if abs(mean_val) > 1e-6 else 0.0

    latest_val = float(y[-1])
    prev_val = float(y[-2])
    abs_delta = latest_val - prev_val
    rel_delta = abs_delta / max(1e-6, abs(prev_val))

    # Evaluate direction
    # High variance / volatility check
    if cv > 0.30 and r_squared < 0.25 and (max(values) - min(values)) > 0.10:
        direction = "VOLATILE"
    elif higher_is_better:
        if slope > 0.005:
            direction = "IMPROVING"
        elif slope < -0.005:
            direction = "DEGRADING"
        else:
            direction = "STABLE"
    else:
        # Lower is better (e.g. PSI, LogLoss, ErrorRate)
        if slope < -0.005:
            direction = "IMPROVING"
        elif slope > 0.005:
            direction = "DEGRADING"
        else:
            direction = "STABLE"

    mk = mann_kendall_test(values)

    return {
        "observationCount": n,
        "direction": direction,
        "slope": round(slope, 6),
        "rSquared": round(r_squared, 4),
        "latestValue": round(latest_val, 4),
        "previousValue": round(prev_val, 4),
        "absoluteDelta": round(abs_delta, 4),
        "relativeDelta": round(rel_delta, 4),
        "mean": round(mean_val, 4),
        "median": round(median_val, 4),
        "standardDeviation": round(std_val, 4),
        "coefficientOfVariation": round(cv, 4),
        "minimum": round(float(np.min(y)), 4),
        "maximum": round(float(np.max(y)), 4),
        "mannKendall": mk
    }
