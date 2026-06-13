package com.smartcs.agent.common.dto;

import com.smartcs.agent.common.enums.ErrorCode;
import java.time.Instant;
import java.util.Map;

/**
 * Standard API response envelope.
 */
public record ApiResponse<T>(
        String code,
        String message,
        T data,
        String traceId,
        Map<String, Object> metadata,
        Instant timestamp
) {

    public static <T> ApiResponse<T> success(T data, String traceId) {
        return new ApiResponse<>(
                ErrorCode.SUCCESS.code(),
                ErrorCode.SUCCESS.message(),
                data,
                traceId,
                Map.of(),
                Instant.now());
    }

    public static <T> ApiResponse<T> failure(ErrorCode errorCode, String traceId) {
        return new ApiResponse<>(
                errorCode.code(),
                errorCode.message(),
                null,
                traceId,
                Map.of(),
                Instant.now());
    }
}
