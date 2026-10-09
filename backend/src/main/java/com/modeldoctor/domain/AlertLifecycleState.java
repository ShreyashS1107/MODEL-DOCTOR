package com.modeldoctor.domain;

/**
 * Lifecycle state for operational monitoring alerts.
 */
public enum AlertLifecycleState {
    OPEN,
    ACKNOWLEDGED,
    INVESTIGATING,
    SUPPRESSED,
    RESOLVED,
    REOPENED;

    public boolean canTransitionTo(AlertLifecycleState target) {
        if (target == null) return false;
        if (this == target) return true;

        return switch (this) {
            case OPEN -> target == ACKNOWLEDGED || target == INVESTIGATING || target == SUPPRESSED || target == RESOLVED;
            case ACKNOWLEDGED -> target == INVESTIGATING || target == SUPPRESSED || target == RESOLVED;
            case INVESTIGATING -> target == ACKNOWLEDGED || target == SUPPRESSED || target == RESOLVED;
            case SUPPRESSED -> target == OPEN || target == REOPENED || target == INVESTIGATING || target == RESOLVED;
            case RESOLVED -> target == REOPENED;
            case REOPENED -> target == ACKNOWLEDGED || target == INVESTIGATING || target == SUPPRESSED || target == RESOLVED;
        };
    }
}
