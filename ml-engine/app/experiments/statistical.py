import numpy as np
from typing import Dict, Any, Optional
from scipy.stats import chi2

def compute_paired_comparison_statistics(
    y_true: np.ndarray,
    baseline_preds: np.ndarray,
    candidate_preds: np.ndarray,
    baseline_probs: Optional[np.ndarray] = None,
    candidate_probs: Optional[np.ndarray] = None,
    seed: int = 42
) -> Dict[str, Any]:
    """
    Computes rigorous paired statistical tests between baseline and candidate predictions.
    Includes prediction flip rate, McNemar test for paired classification discordance,
    and bootstrap confidence intervals for probability shifts.
    """
    y_t = np.array(y_true).astype(int)
    b_p = np.array(baseline_preds).astype(int)
    c_p = np.array(candidate_preds).astype(int)

    n_total = len(y_t)
    if n_total == 0:
        return {"sampleSize": 0}

    n_pos = int((y_t == 1).sum())
    n_neg = int((y_t == 0).sum())

    # 1. Prediction Flips
    flips_mask = (b_p != c_p)
    n_changed = int(flips_mask.sum())
    flip_rate = float(n_changed / n_total)

    # 2. Contingency Table (Baseline vs Candidate)
    # b_p == 0 & c_p == 0
    n00 = int(((b_p == 0) & (c_p == 0)).sum())
    # b_p == 0 & c_p == 1
    n01 = int(((b_p == 0) & (c_p == 1)).sum())
    # b_p == 1 & c_p == 0
    n10 = int(((b_p == 1) & (c_p == 0)).sum())
    # b_p == 1 & c_p == 1
    n11 = int(((b_p == 1) & (c_p == 1)).sum())

    # 3. McNemar's Test for Paired Classifiers
    # Testing discordance n01 vs n10
    discordant_sum = n01 + n10
    mcnemar_stat = 0.0
    mcnemar_p_val = 1.0

    if discordant_sum > 0:
        # Continuity-corrected chi-square: (|n01 - n10| - 1)^2 / (n01 + n10)
        numerator = max(0.0, abs(n01 - n10) - 1.0) ** 2
        mcnemar_stat = float(numerator / discordant_sum)
        mcnemar_p_val = float(1.0 - chi2.cdf(mcnemar_stat, df=1))

    # 4. Probability Delta Analysis & Bootstrap CI
    mean_prob_shift = 0.0
    median_prob_shift = 0.0
    mean_abs_prob_shift = 0.0
    ci_lower = 0.0
    ci_upper = 0.0

    if baseline_probs is not None and candidate_probs is not None and len(baseline_probs) == n_total:
        b_probs = np.array(baseline_probs).astype(float)
        c_probs = np.array(candidate_probs).astype(float)
        deltas = c_probs - b_probs

        mean_prob_shift = float(np.mean(deltas))
        median_prob_shift = float(np.median(deltas))
        mean_abs_prob_shift = float(np.mean(np.abs(deltas)))

        # Deterministic Bootstrap 95% CI (1000 resamples)
        rng = np.random.default_rng(seed)
        boot_means = []
        n_boot = min(1000, max(100, n_total * 2))
        for _ in range(n_boot):
            idx = rng.choice(n_total, size=n_total, replace=True)
            boot_means.append(np.mean(deltas[idx]))
        
        ci_lower = float(np.percentile(boot_means, 2.5))
        ci_upper = float(np.percentile(boot_means, 97.5))

    return {
        "sampleSize": n_total,
        "positiveCount": n_pos,
        "negativeCount": n_neg,
        "changedPredictionsCount": n_changed,
        "predictionFlipRate": flip_rate,
        "contingencyTable": {
            "n00": n00,
            "n01": n01,
            "n10": n10,
            "n11": n11
        },
        "mcNemarStatistic": mcnemar_stat,
        "mcNemarPValue": mcnemar_p_val,
        "meanProbabilityShift": mean_prob_shift,
        "medianProbabilityShift": median_prob_shift,
        "meanAbsoluteProbabilityShift": mean_abs_prob_shift,
        "probShiftCiLower": ci_lower,
        "probShiftCiUpper": ci_upper,
        "deterministicSeed": seed
    }
