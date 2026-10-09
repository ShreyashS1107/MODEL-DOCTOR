package com.modeldoctor.domain;

/**
 * Types of operational governance events in a model lineage reliability timeline.
 */
public enum ReliabilityEventType {
    DEGRADATION,
    RECOVERY,
    REGRESSION,
    INCIDENT_ESCALATION,
    INCIDENT_REOPEN,
    REMEDIATION_SUCCESS,
    REMEDIATION_FAILURE,
    REMEDIATION_NOT_SUSTAINED,
    CHANGE_POINT,
    HEALTH_STATE_CHANGE
}
