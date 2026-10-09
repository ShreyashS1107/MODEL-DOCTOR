package com.modeldoctor.domain;

/**
 * Trajectory and direction of operational model reliability over observation history.
 */
public enum ReliabilityTrend {
    IMPROVING,
    STABLE,
    DEGRADING,
    VOLATILE,
    INSUFFICIENT_DATA
}
