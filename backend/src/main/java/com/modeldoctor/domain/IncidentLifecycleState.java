package com.modeldoctor.domain;

/**
 * Operational lifecycle state for model diagnostic incidents.
 */
public enum IncidentLifecycleState {
    OPEN,
    ACKNOWLEDGED,
    INVESTIGATING,
    MITIGATION_PLANNED,
    VALIDATING,
    MONITORING,
    RESOLVED,
    REOPENED,
    SUPPRESSED;

    public boolean canTransitionTo(IncidentLifecycleState target) {
        if (target == null) return false;
        if (this == target) return true;

        return switch (this) {
            case OPEN -> target == ACKNOWLEDGED || target == INVESTIGATING || target == MITIGATION_PLANNED || target == SUPPRESSED || target == RESOLVED;
            case ACKNOWLEDGED -> target == INVESTIGATING || target == MITIGATION_PLANNED || target == SUPPRESSED || target == RESOLVED;
            case INVESTIGATING -> target == MITIGATION_PLANNED || target == VALIDATING || target == MONITORING || target == SUPPRESSED || target == RESOLVED;
            case MITIGATION_PLANNED -> target == VALIDATING || target == INVESTIGATING || target == SUPPRESSED || target == RESOLVED;
            case VALIDATING -> target == MONITORING || target == INVESTIGATING || target == MITIGATION_PLANNED || target == SUPPRESSED || target == RESOLVED;
            case MONITORING -> target == RESOLVED || target == INVESTIGATING || target == REOPENED || target == SUPPRESSED;
            case SUPPRESSED -> target == OPEN || target == REOPENED || target == INVESTIGATING || target == RESOLVED || target == MONITORING;
            case RESOLVED -> target == REOPENED;
            case REOPENED -> target == ACKNOWLEDGED || target == INVESTIGATING || target == MITIGATION_PLANNED || target == SUPPRESSED || target == RESOLVED;
        };
    }
}
