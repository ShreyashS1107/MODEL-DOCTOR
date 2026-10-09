package com.modeldoctor.domain;

/**
 * Types of cross-model recurring operational patterns detected across the fleet.
 */
public enum FleetPatternType {
    RECURRING_DRIFT,
    RECURRING_CALIBRATION,
    RECURRING_ROBUSTNESS,
    RECURRING_ERROR,
    RECURRING_FAIRNESS,
    RECURRING_REMEDIATION_FAILURE,
    RECURRING_INCIDENT_REOPEN
}
