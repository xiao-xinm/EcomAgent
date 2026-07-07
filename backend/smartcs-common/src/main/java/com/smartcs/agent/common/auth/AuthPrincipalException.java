package com.smartcs.agent.common.auth;

import com.smartcs.agent.common.enums.ErrorCode;

/**
 * Raised when a request cannot be mapped to an allowed trusted identity.
 */
public class AuthPrincipalException extends RuntimeException {

    private final ErrorCode errorCode;

    public AuthPrincipalException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
