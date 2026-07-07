package com.smartcs.agent.common.auth;

/**
 * Raised when a supplied Bearer token is present but cannot be trusted.
 */
public class JwtPrincipalException extends RuntimeException {

    public JwtPrincipalException(String message) {
        super(message);
    }

    public JwtPrincipalException(String message, Throwable cause) {
        super(message, cause);
    }
}
