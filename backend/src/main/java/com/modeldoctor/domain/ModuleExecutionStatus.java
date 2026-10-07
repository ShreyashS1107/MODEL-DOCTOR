package com.modeldoctor.domain;

/**
 * Execution status for an individual diagnostic module within a run.
 */
public enum ModuleExecutionStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    NOT_IMPLEMENTED,
    FAILED,
    SKIPPED
}
