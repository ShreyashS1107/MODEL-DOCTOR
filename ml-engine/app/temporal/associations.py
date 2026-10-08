from typing import List, Dict, Any, Optional
import numpy as np
from scipy import stats


def compute_temporal_lag_correlation(
    series_a: List[float],
    series_b: List[float],
    signal_a_name: str = "signal_a",
    signal_b_name: str = "signal_b",
    max_lag: int = 2,
    min_observations: int = 4
) -> List[Dict[str, Any]]:
    """
    Computes cross-lag temporal correlations between two time-series signals.
    Evaluates whether changes in signal_a precede changes in signal_b at lag 0, 1, or 2.
    """
    n = min(len(series_a), len(series_b))
    if n < min_observations:
        return []

    a = np.array(series_a[:n], dtype=float)
    b = np.array(series_b[:n], dtype=float)

    associations = []

    for lag in range(0, min(max_lag + 1, n - 2)):
        if lag == 0:
            x = a
            y = b
        else:
            x = a[:-lag]
            y = b[lag:]

        if len(x) < 3:
            continue

        # Check for constant variance
        if np.std(x) < 1e-6 or np.std(y) < 1e-6:
            continue

        r, p_val = stats.pearsonr(x, y)
        if np.isnan(r) or np.isnan(p_val):
            continue

        r = float(r)
        p_val = float(p_val)

        if abs(r) >= 0.40:
            associations.append({
                "signalA": signal_a_name,
                "signalB": signal_b_name,
                "lag": lag,
                "correlation": round(r, 4),
                "pValue": round(p_val, 5),
                "pairedObservationCount": len(x),
                "strength": "STRONG" if abs(r) >= 0.70 else "MODERATE",
                "relationship": f"{signal_a_name} at t-{lag} is {'positively' if r > 0 else 'negatively'} associated with {signal_b_name} at t (r={r:.2f}, p={p_val:.4f})"
            })

    return associations
