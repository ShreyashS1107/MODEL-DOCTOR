import numpy as np
import pandas as pd
from typing import Dict, Any, List, Optional
from sklearn.metrics import confusion_matrix, precision_score, recall_score, f1_score, accuracy_score, brier_score_loss
from sklearn.linear_model import LogisticRegression
from sklearn.isotonic import IsotonicRegression

def evaluate_threshold_grid(
    y_true: np.ndarray,
    y_prob: np.ndarray,
    baseline_threshold: float = 0.50,
    candidate_threshold: float = 0.50
) -> Dict[str, Any]:
    """
    Evaluates classification performance across a 21-point threshold grid (0.00 to 1.00)
    and computes detailed comparative deltas for the candidate threshold vs baseline threshold.
    """
    y_true_clean = np.array(y_true).astype(int)
    y_prob_clean = np.array(y_prob).astype(float)
    
    thresholds = [round(t, 2) for t in np.linspace(0.0, 1.0, 21)]
    grid_points = []

    for t in thresholds:
        preds = (y_prob_clean >= t).astype(int)
        tn, fp, fn, tp = confusion_matrix(y_true_clean, preds, labels=[0, 1]).ravel()
        
        total_p = tp + fn
        total_n = tn + fp
        
        tpr = float(tp / total_p) if total_p > 0 else 0.0
        fpr = float(fp / total_n) if total_n > 0 else 0.0
        fnr = float(fn / total_p) if total_p > 0 else 0.0
        precision = float(tp / (tp + fp)) if (tp + fp) > 0 else 0.0
        recall = tpr
        f1 = float(2 * precision * recall / (precision + recall)) if (precision + recall) > 0 else 0.0
        acc = float((tp + tn) / len(y_true_clean)) if len(y_true_clean) > 0 else 0.0

        grid_points.append({
            "threshold": t,
            "tp": int(tp),
            "fp": int(fp),
            "tn": int(tn),
            "fn": int(fn),
            "accuracy": acc,
            "precision": precision,
            "recall": recall,
            "f1": f1,
            "fpr": fpr,
            "fnr": fnr
        })

    def calc_metrics_at(t_val: float):
        preds = (y_prob_clean >= t_val).astype(int)
        tn, fp, fn, tp = confusion_matrix(y_true_clean, preds, labels=[0, 1]).ravel()
        tot_p = tp + fn
        tot_n = tn + fp
        tpr_v = float(tp / tot_p) if tot_p > 0 else 0.0
        fpr_v = float(fp / tot_n) if tot_n > 0 else 0.0
        fnr_v = float(fn / tot_p) if tot_p > 0 else 0.0
        prec_v = float(tp / (tp + fp)) if (tp + fp) > 0 else 0.0
        rec_v = tpr_v
        f1_v = float(2 * prec_v * rec_v / (prec_v + rec_v)) if (prec_v + rec_v) > 0 else 0.0
        acc_v = float((tp + tn) / len(y_true_clean)) if len(y_true_clean) > 0 else 0.0
        return {
            "threshold": t_val,
            "tp": int(tp), "fp": int(fp), "tn": int(tn), "fn": int(fn),
            "accuracy": acc_v, "precision": prec_v, "recall": rec_v,
            "f1": f1_v, "fpr": fpr_v, "fnr": fnr_v
        }

    base_m = calc_metrics_at(baseline_threshold)
    cand_m = calc_metrics_at(candidate_threshold)

    return {
        "gridPoints": grid_points,
        "baselineThreshold": baseline_threshold,
        "candidateThreshold": candidate_threshold,
        "baselineMetrics": base_m,
        "candidateMetrics": cand_m,
        "deltas": {
            "f1": cand_m["f1"] - base_m["f1"],
            "accuracy": cand_m["accuracy"] - base_m["accuracy"],
            "precision": cand_m["precision"] - base_m["precision"],
            "recall": cand_m["recall"] - base_m["recall"],
            "fpr": cand_m["fpr"] - base_m["fpr"],
            "fnr": cand_m["fnr"] - base_m["fnr"]
        }
    }


def evaluate_calibration_counterfactual(
    y_eval_true: np.ndarray,
    y_eval_prob: np.ndarray,
    y_calib_true: Optional[np.ndarray] = None,
    y_calib_prob: Optional[np.ndarray] = None,
    method: str = "PLATT" # "PLATT" or "ISOTONIC"
) -> Dict[str, Any]:
    """
    Evaluates probability calibration without modifying base classifier weights.
    Requires an independent calibration dataset. If not supplied, marks NOT_EXECUTABLE.
    """
    if y_calib_true is None or y_calib_prob is None or len(y_calib_true) < 20:
        return {
            "executable": False,
            "reason": "Independent calibration dataset with >= 20 samples is required to evaluate calibration without data leakage."
        }

    y_calib_true = np.array(y_calib_true).astype(int)
    y_calib_prob = np.array(y_calib_prob).astype(float)
    y_eval_true = np.array(y_eval_true).astype(int)
    y_eval_prob = np.array(y_eval_prob).astype(float)

    # Compute baseline ECE
    def compute_ece(y_t, y_p, n_bins=10):
        bin_edges = np.linspace(0.0, 1.0, n_bins + 1)
        ece = 0.0
        n = len(y_t)
        for i in range(n_bins):
            mask = (y_p >= bin_edges[i]) & (y_p < bin_edges[i+1]) if i < n_bins - 1 else (y_p >= bin_edges[i]) & (y_p <= bin_edges[i+1])
            if mask.sum() > 0:
                bin_acc = np.mean(y_t[mask])
                bin_conf = np.mean(y_p[mask])
                ece += (mask.sum() / n) * abs(bin_acc - bin_conf)
        return float(ece)

    baseline_ece = compute_ece(y_eval_true, y_eval_prob)
    baseline_brier = float(brier_score_loss(y_eval_true, y_eval_prob))

    if method == "ISOTONIC":
        iso = IsotonicRegression(out_of_bounds="clip")
        iso.fit(y_calib_prob, y_calib_true)
        calibrated_probs = iso.predict(y_eval_prob)
    else: # PLATT
        # Log-odds
        eps = 1e-6
        clipped_p = np.clip(y_calib_prob, eps, 1 - eps)
        log_odds_calib = np.log(clipped_p / (1 - clipped_p)).reshape(-1, 1)
        lr = LogisticRegression()
        lr.fit(log_odds_calib, y_calib_true)

        clipped_eval = np.clip(y_eval_prob, eps, 1 - eps)
        log_odds_eval = np.log(clipped_eval / (1 - clipped_eval)).reshape(-1, 1)
        calibrated_probs = lr.predict_proba(log_odds_eval)[:, 1]

    candidate_ece = compute_ece(y_eval_true, calibrated_probs)
    candidate_brier = float(brier_score_loss(y_eval_true, calibrated_probs))

    return {
        "executable": True,
        "method": method,
        "calibrationSampleSize": len(y_calib_true),
        "evalSampleSize": len(y_eval_true),
        "baselineEce": baseline_ece,
        "candidateEce": candidate_ece,
        "eceDelta": candidate_ece - baseline_ece,
        "baselineBrier": baseline_brier,
        "candidateBrier": candidate_brier,
        "brierDelta": candidate_brier - baseline_brier,
        "calibratedProbabilities": calibrated_probs.tolist()
    }


def evaluate_subgroup_counterfactual(
    subgroups: np.ndarray,
    y_true: np.ndarray,
    baseline_preds: np.ndarray,
    candidate_preds: np.ndarray,
    baseline_probs: Optional[np.ndarray] = None,
    candidate_probs: Optional[np.ndarray] = None
) -> Dict[str, Any]:
    """
    Evaluates subgroup performance, sample sizes, and deltas between baseline and candidate predictions.
    """
    subgroups = np.array(subgroups)
    y_true = np.array(y_true).astype(int)
    baseline_preds = np.array(baseline_preds).astype(int)
    candidate_preds = np.array(candidate_preds).astype(int)

    unique_groups = np.unique(subgroups)
    group_results = []

    for g in unique_groups:
        mask = (subgroups == g)
        n_g = int(mask.sum())
        if n_g == 0:
            continue

        y_g = y_true[mask]
        b_p_g = baseline_preds[mask]
        c_p_g = candidate_preds[mask]

        b_acc = float(accuracy_score(y_g, b_p_g))
        c_acc = float(accuracy_score(y_g, c_p_g))
        b_f1 = float(f1_score(y_g, b_p_g, zero_division=0))
        c_f1 = float(f1_score(y_g, c_p_g, zero_division=0))

        # Flips in this subgroup
        flips = int((b_p_g != c_p_g).sum())
        flip_rate = float(flips / n_g)

        group_results.append({
            "group": str(g),
            "sampleSize": n_g,
            "positiveCount": int((y_g == 1).sum()),
            "negativeCount": int((y_g == 0).sum()),
            "baselineAccuracy": b_acc,
            "candidateAccuracy": c_acc,
            "accuracyDelta": c_acc - b_acc,
            "baselineF1": b_f1,
            "candidateF1": c_f1,
            "f1Delta": c_f1 - b_f1,
            "predictionFlips": flips,
            "flipRate": flip_rate
        })

    return {
        "subgroups": group_results,
        "totalGroups": len(group_results)
    }
