package org.codeflow.veterineryproject.common.api;

public class ConflictException extends RuntimeException {

    private final String code;
    private final transient java.util.Map<String, Object> details;

    public ConflictException(String message) {
        this(message, "CONFLICT", java.util.Map.of());
    }

    public ConflictException(String message, String code, java.util.Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = details;
    }

    public String code() {
        return code;
    }

    public java.util.Map<String, Object> details() {
        return details;
    }
}
