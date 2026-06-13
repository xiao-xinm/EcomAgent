package com.smartcs.agent.skill.controller;

import com.smartcs.agent.common.dto.ApiResponse;
import com.smartcs.agent.common.enums.ErrorCode;
import com.smartcs.agent.common.util.TraceIds;
import com.smartcs.agent.skill.executor.SkillOperationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Converts expected skill engine failures to the shared response envelope.
 */
@RestControllerAdvice
public class SkillExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SkillExceptionHandler.class);

    @ExceptionHandler(SkillOperationException.class)
    public ApiResponse<Void> handleSkillOperationException(SkillOperationException exception) {
        return ApiResponse.failure(exception.errorCode(), TraceIds.newTraceId());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, IllegalArgumentException.class})
    public ApiResponse<Void> handleBadRequest(Exception exception) {
        return ApiResponse.failure(ErrorCode.BAD_REQUEST, TraceIds.newTraceId());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleUnexpected(Exception exception) {
        LOGGER.warn("Unexpected skill engine error", exception);
        return ApiResponse.failure(ErrorCode.INTERNAL_ERROR, TraceIds.newTraceId());
    }
}
