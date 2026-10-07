package com.modeldoctor.exception;

/**
 * Thrown when an invalid status transition is attempted on a diagnostic run
 * (e.g. attempting to run a job that is not in CREATED state).
 */
public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(String message) {
        super(message);
    }

    public InvalidStatusTransitionException(String message, Throwable cause) {
        super(message, cause);
    }
}
