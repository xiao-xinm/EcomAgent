package com.smartcs.agent.common.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Standard page query.
 */
public record PageQuery(
        @Min(1) int pageNo,
        @Min(1) @Max(200) int pageSize
) {
}
