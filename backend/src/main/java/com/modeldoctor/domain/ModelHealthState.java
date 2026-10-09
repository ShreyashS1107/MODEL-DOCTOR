package com.modeldoctor.domain;

/**
 * Overall operational health state of a machine learning model lineage.
 */
public enum ModelHealthState {
    HEALTHY,
    DEGRADED,
    CRITICAL,
    RECOVERING,
    UNKNOWN
}
