package com.smartcs.agent.notification.controller;

import com.smartcs.agent.common.dto.ApiResponse;
import com.smartcs.agent.common.enums.ErrorCode;
import com.smartcs.agent.common.exception.SmartCsException;
import com.smartcs.agent.common.util.TraceIds;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Converts expected notification API failures to the shared response envelope.
 */
@RestControllerAdvice
public class NotificationExceptionHandler {

    @ExceptionHandler(SmartCsException.class)
    public ApiResponse<Void> handleSmartCsException(SmartCsException exception) {
        return ApiResponse.failure(exception.errorCode(), TraceIds.newTraceId());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleBadRequest(IllegalArgumentException exception) {
        return ApiResponse.failure(ErrorCode.BAD_REQUEST, TraceIds.newTraceId());
    }
}
