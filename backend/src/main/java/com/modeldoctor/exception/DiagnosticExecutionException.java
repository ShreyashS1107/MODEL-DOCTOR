package com.modeldoctor.exception;

public class DiagnosticExecutionException extends RuntimeException {
    public DiagnosticExecutionException(String message, Throwable cause) {
        super(message, cause);
    }

    public DiagnosticExecutionException(String message) {
        super(message);
    }
}
