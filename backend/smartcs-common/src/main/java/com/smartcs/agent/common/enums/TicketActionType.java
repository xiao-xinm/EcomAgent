package com.smartcs.agent.common.enums;

/**
 * Auditable action on a human work order.
 */
public enum TicketActionType {
    CREATE,
    ASSIGN,
    APPROVE,
    REJECT,
    MODIFY_AND_APPROVE,
    TAKEOVER,
    ESCALATE,
    CLOSE
}
