package com.modeldoctor.domain;

/**
 * Deterministic operator-facing governance recommendation based on synthesized evidence.
 */
public enum GovernanceRecommendation {
    NORMAL_OPERATION,
    MONITOR,
    REVIEW_REQUIRED,
    PRIORITY_REVIEW,
    ESCALATE,
    INSUFFICIENT_EVIDENCE
}
