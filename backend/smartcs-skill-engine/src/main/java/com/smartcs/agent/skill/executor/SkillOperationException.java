package com.smartcs.agent.skill.executor;

import com.smartcs.agent.common.enums.ErrorCode;

/**
 * Expected skill operation failure with a stable API error code.
 */
public class SkillOperationException extends RuntimeException {

    private final ErrorCode errorCode;

    public SkillOperationException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
