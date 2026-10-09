package com.modeldoctor.domain;

/**
 * Deterministic operator decision recommendation states for an incident.
 * NOTE: Represents an operational recommendation, NOT autonomous action.
 */
public enum IncidentDecisionState {
    NO_ACTION,
    INVESTIGATE,
    REVIEW_REMEDIATION,
    VALIDATE_REMEDIATION,
    MONITOR,
    REOPEN_INVESTIGATION,
    ESCALATE,
    INSUFFICIENT_EVIDENCE
}
