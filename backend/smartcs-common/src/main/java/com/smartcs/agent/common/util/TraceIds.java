package com.smartcs.agent.common.util;

import java.util.UUID;

/**
 * Minimal trace id helper shared by service entry points.
 */
public final class TraceIds {

    private TraceIds() {
    }

    public static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
