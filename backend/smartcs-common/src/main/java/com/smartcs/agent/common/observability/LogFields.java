package com.smartcs.agent.common.observability;

import java.util.StringJoiner;

/**
 * 跨服务日志字段规范，保证排查链路时字段名稳定。
 */
public final class LogFields {

    public static final String EMPTY_VALUE = "-";
    public static final String TRACE_ID = "traceId";
    public static final String SESSION_ID = "sessionId";
    public static final String USER_ID = "userId";
    public static final String TICKET_ID = "ticketId";
    public static final String OPERATOR_ID = "operatorId";
    public static final String STREAM_ID = "streamId";

    private LogFields() {
    }

    public static String chat(String traceId, String sessionId, String userId) {
        return keyValues(TRACE_ID, traceId, SESSION_ID, sessionId, USER_ID, userId);
    }

    public static String ticket(String traceId, String ticketId, String userId, String operatorId) {
        return keyValues(
                TRACE_ID,
                traceId,
                TICKET_ID,
                ticketId,
                USER_ID,
                userId,
                OPERATOR_ID,
                operatorId);
    }

    public static String stream(String streamId, String sessionId, String userId) {
        return keyValues(STREAM_ID, streamId, SESSION_ID, sessionId, USER_ID, userId);
    }

    public static String keyValues(Object... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("Log field pairs must be key-value aligned");
        }
        StringJoiner joiner = new StringJoiner(" ");
        for (int index = 0; index < pairs.length; index += 2) {
            joiner.add(value(pairs[index]) + "=" + value(pairs[index + 1]));
        }
        return joiner.toString();
    }

    public static String value(Object value) {
        if (value == null) {
            return EMPTY_VALUE;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? EMPTY_VALUE : text;
    }
}
