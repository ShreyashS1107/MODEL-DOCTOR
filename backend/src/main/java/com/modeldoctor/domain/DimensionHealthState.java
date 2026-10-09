package com.modeldoctor.domain;

/**
 * Health state for an individual diagnostic dimension.
 */
public enum DimensionHealthState {
    HEALTHY,
    WARNING,
    DEGRADED,
    CRITICAL,
    UNKNOWN
}
