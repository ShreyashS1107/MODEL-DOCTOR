from typing import List, Dict, Any, Optional
import math
import numpy as np


def detect_change_points(
    values: List[float],
    timestamps: Optional[List[str]] = None,
    run_ids: Optional[List[str]] = None,
    min_split_size: int = 2,
    threshold_shift: float = 0.05
) -> List[Dict[str, Any]]:
    """
    Detects deterministic change points in a time-series using step-mean deviation.
    Identifies partition points k where |mean(after) - mean(before)| is maximized and exceeds threshold.
    """
    n = len(values)
    if n < (2 * min_split_size):
        return []

    y = np.array(values, dtype=float)
    global_std = float(np.std(y)) if np.std(y) > 1e-6 else 1e-6

    change_points = []
    max_score = 0.0
    best_k = -1

    for k in range(min_split_size, n - min_split_size + 1):
        before = y[:k]
        after = y[k:]

        mean_before = float(np.mean(before))
        mean_after = float(np.mean(after))
        abs_shift = abs(mean_after - mean_before)

        # Standardized shift score balanced by split weights
        weight = math.sqrt((len(before) * len(after)) / float(n))
        score = abs_shift * weight

        if abs_shift >= threshold_shift and score > max_score:
            max_score = score
            best_k = k

    if best_k != -1:
        before = y[:best_k]
        after = y[best_k:]
        mean_before = float(np.mean(before))
        mean_after = float(np.mean(after))
        abs_shift = float(mean_after - mean_before)
        rel_shift = float(abs_shift / max(1e-6, abs(mean_before)))

        var_pooled = (np.var(before) * len(before) + np.var(after) * len(after)) / n
        z_score = abs(abs_shift) / float(np.sqrt(max(1e-6, var_pooled)))

        confidence = "HIGH" if z_score >= 2.5 or abs(abs_shift) >= 0.15 else ("MEDIUM" if z_score >= 1.5 else "LOW")

        change_points.append({
            "changeIndex": best_k,
            "changeRunId": run_ids[best_k] if run_ids and len(run_ids) > best_k else None,
            "changeTimestamp": timestamps[best_k] if timestamps and len(timestamps) > best_k else None,
            "beforeMean": round(mean_before, 4),
            "afterMean": round(mean_after, 4),
            "absoluteShift": round(abs_shift, 4),
            "relativeShift": round(rel_shift, 4),
            "zScore": round(float(z_score), 4),
            "confidenceLevel": confidence,
            "runIdsBefore": run_ids[:best_k] if run_ids else [],
            "runIdsAfter": run_ids[best_k:] if run_ids else []
        })

    return change_points
