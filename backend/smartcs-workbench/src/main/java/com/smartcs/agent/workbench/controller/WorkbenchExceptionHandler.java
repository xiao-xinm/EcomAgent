package com.smartcs.agent.workbench.controller;

import com.smartcs.agent.common.dto.ApiResponse;
import com.smartcs.agent.common.auth.AuthPrincipalException;
import com.smartcs.agent.common.auth.JwtPrincipalException;
import com.smartcs.agent.common.enums.ErrorCode;
import com.smartcs.agent.common.util.TraceIds;
import com.smartcs.agent.workbench.ticket.WorkbenchOperationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Converts expected workbench failures to the shared response envelope.
 */
@RestControllerAdvice
public class WorkbenchExceptionHandler {

    @ExceptionHandler(WorkbenchOperationException.class)
    public ApiResponse<Void> handleWorkbenchOperationException(WorkbenchOperationException exception) {
        return ApiResponse.failure(exception.errorCode(), TraceIds.newTraceId());
    }

    @ExceptionHandler(JwtPrincipalException.class)
    public ApiResponse<Void> handleJwtPrincipalException(JwtPrincipalException exception) {
        return ApiResponse.failure(ErrorCode.UNAUTHORIZED, TraceIds.newTraceId());
    }

    @ExceptionHandler(AuthPrincipalException.class)
    public ApiResponse<Void> handleAuthPrincipalException(AuthPrincipalException exception) {
        return ApiResponse.failure(exception.errorCode(), TraceIds.newTraceId());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, IllegalArgumentException.class})
    public ApiResponse<Void> handleBadRequest(Exception exception) {
        return ApiResponse.failure(ErrorCode.BAD_REQUEST, TraceIds.newTraceId());
    }
}
