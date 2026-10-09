package com.modeldoctor.domain;

/**
 * Controlled taxonomy for operational model diagnostic incidents.
 */
public enum IncidentCategory {
    DATA_QUALITY_INCIDENT,
    LEAKAGE_INCIDENT,
    DRIFT_INCIDENT,
    PERFORMANCE_INCIDENT,
    CALIBRATION_INCIDENT,
    ERROR_INCIDENT,
    FAIRNESS_INCIDENT,
    ROBUSTNESS_INCIDENT,
    TEMPORAL_DEGRADATION,
    MULTI_MODULE_INCIDENT
}
