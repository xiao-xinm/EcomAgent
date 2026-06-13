package com.smartcs.agent.common.enums;

/**
 * Routing decision produced after risk evaluation.
 */
public enum RouteDecision {
    AUTO_REPLY,
    AUTO_EXECUTE,
    CONFIRM_BEFORE_EXECUTE,
    HUMAN_REVIEW,
    HUMAN_TAKEOVER,
    REJECT
}
