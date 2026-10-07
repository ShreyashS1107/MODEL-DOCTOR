package com.modeldoctor.domain;

/**
 * Canonical lifecycle states of a Model Doctor diagnostic run.
 */
public enum DiagnosticStatus {
    CREATED,
    QUEUED,
    RUNNING,
    COMPLETED,
    PARTIAL,
    FAILED
}
