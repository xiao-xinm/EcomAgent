package com.smartcs.agent.common.enums;

/**
 * Stable error codes shared by all SmartCS services.
 */
public enum ErrorCode {

    SUCCESS("0000", "success"),

    BAD_REQUEST("1001", "bad request"),
    UNAUTHORIZED("1002", "unauthorized"),
    FORBIDDEN("1003", "forbidden"),
    NOT_FOUND("1004", "resource not found"),
    RATE_LIMITED("1005", "rate limited"),
    INTERNAL_ERROR("1006", "internal error"),

    AGENT_LOW_CONFIDENCE("2001", "agent confidence is too low"),
    RISK_REVIEW_REQUIRED("2002", "human review is required"),
    RISK_TAKEOVER_REQUIRED("2003", "human takeover is required"),

    SKILL_NOT_FOUND("3001", "skill not found"),
    SKILL_EXECUTION_FAILED("3002", "skill execution failed"),

    TICKET_NOT_FOUND("4001", "ticket not found"),
    TICKET_ACTION_REJECTED("4002", "ticket action rejected");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }
}
