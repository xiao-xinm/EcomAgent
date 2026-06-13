package com.smartcs.agent.workbench.ticket;

import com.smartcs.agent.common.enums.ErrorCode;

/**
 * Expected workbench operation failure with a stable API error code.
 */
public class WorkbenchOperationException extends RuntimeException {

    private final ErrorCode errorCode;

    public WorkbenchOperationException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
