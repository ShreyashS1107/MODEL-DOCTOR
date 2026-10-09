package com.modeldoctor.domain;

/**
 * Diagnostic dimensions monitored continuously for operational model health.
 */
public enum HealthDimension {
    DATA_QUALITY,
    LEAKAGE,
    DRIFT,
    PERFORMANCE,
    CALIBRATION,
    ERROR,
    FAIRNESS,
    ROBUSTNESS,
    TEMPORAL
}
