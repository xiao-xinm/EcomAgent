package com.smartcs.agent.common.dto;

import java.util.List;

/**
 * Standard page result.
 */
public record PageResult<T>(
        List<T> records,
        long total,
        int pageNo,
        int pageSize
) {
}
