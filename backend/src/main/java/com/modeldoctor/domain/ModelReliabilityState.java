package com.modeldoctor.domain;

/**
 * Longitudinal operational reliability state of a machine learning model lineage.
 */
public enum ModelReliabilityState {
    RELIABILITY_UNKNOWN,
    RELIABILITY_HEALTHY,
    RELIABILITY_STABLE,
    RELIABILITY_DEGRADED,
    RELIABILITY_AT_RISK,
    RELIABILITY_CRITICAL,
    RELIABILITY_RECOVERING
}
