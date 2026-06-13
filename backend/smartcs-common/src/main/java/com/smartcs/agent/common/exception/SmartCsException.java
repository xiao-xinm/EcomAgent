package com.smartcs.agent.common.exception;

import com.smartcs.agent.common.enums.ErrorCode;
import java.util.Map;

/**
 * Base runtime exception for service-level failures.
 */
public class SmartCsException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> details;

    public SmartCsException(ErrorCode errorCode) {
        this(errorCode, errorCode.message(), Map.of(), null);
    }

    public SmartCsException(ErrorCode errorCode, String message) {
        this(errorCode, message, Map.of(), null);
    }

    public SmartCsException(ErrorCode errorCode, String message, Map<String, Object> details) {
        this(errorCode, message, details, null);
    }

    public SmartCsException(ErrorCode errorCode, String message, Map<String, Object> details, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Map<String, Object> details() {
        return details;
    }
}
